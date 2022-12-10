/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.entity;

import static software.wings.ngmigration.NGMigrationEntityType.SECRET;

import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.MigratedEntityMapping;
import io.harness.beans.SecretManagerConfig;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResourceClient;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.data.structure.EmptyPredicate;
import io.harness.encryption.Scope;
import io.harness.exception.InvalidRequestException;
import io.harness.gitsync.beans.YamlDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ngmigration.beans.MigrationInputDTO;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.beans.NgEntityDetail;
import io.harness.ngmigration.beans.summary.BaseSummary;
import io.harness.ngmigration.beans.summary.SecretManagerSummary;
import io.harness.ngmigration.client.NGClient;
import io.harness.ngmigration.client.PmsClient;
import io.harness.ngmigration.client.TemplateClient;
import io.harness.ngmigration.dto.ImportError;
import io.harness.ngmigration.dto.MigrationImportSummaryDTO;
import io.harness.ngmigration.dto.SecretManagerCreatedDTO;
import io.harness.ngmigration.secrets.SecretFactory;
import io.harness.ngmigration.service.MigratorMappingService;
import io.harness.ngmigration.service.MigratorUtility;
import io.harness.ngmigration.service.NgMigrationService;
import io.harness.remote.client.NGRestUtils;
import io.harness.secretmanagers.SecretManagerConfigService;
import io.harness.security.encryption.EncryptionType;
import io.harness.serializer.JsonUtils;

import software.wings.ngmigration.CgBasicInfo;
import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.CgEntityNode;
import software.wings.ngmigration.DiscoveryNode;
import software.wings.ngmigration.NGMigrationEntity;
import software.wings.ngmigration.NGMigrationEntityType;
import software.wings.ngmigration.NGMigrationStatus;
import software.wings.service.intfc.security.SecretManager;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import retrofit2.Response;

@Slf4j
@OwnedBy(HarnessTeam.CDC)
public class SecretManagerMigrationService extends NgMigrationService {
  @Inject private SecretManager secretManager;
  @Inject private SecretManagerConfigService secretManagerConfigService;
  @Inject private ConnectorResourceClient connectorResourceClient;
  @Inject private SecretFactory secretFactory;

  @Override
  public MigratedEntityMapping generateMappingEntity(NGYamlFile yamlFile) {
    CgBasicInfo basicInfo = yamlFile.getCgBasicInfo();
    ConnectorInfoDTO connectorInfo = ((ConnectorDTO) yamlFile.getYaml()).getConnectorInfo();
    return MigratedEntityMapping.builder()
        .appId(basicInfo.getAppId())
        .accountId(basicInfo.getAccountId())
        .cgEntityId(basicInfo.getId())
        .entityType(NGMigrationEntityType.SECRET_MANAGER.name())
        .accountIdentifier(basicInfo.getAccountId())
        .orgIdentifier(connectorInfo.getOrgIdentifier())
        .projectIdentifier(connectorInfo.getProjectIdentifier())
        .identifier(connectorInfo.getIdentifier())
        .scope(MigratorMappingService.getScope(connectorInfo.getOrgIdentifier(), connectorInfo.getProjectIdentifier()))
        .fullyQualifiedIdentifier(MigratorMappingService.getFullyQualifiedIdentifier(basicInfo.getAccountId(),
            connectorInfo.getOrgIdentifier(), connectorInfo.getProjectIdentifier(), connectorInfo.getIdentifier()))
        .build();
  }

  @Override
  public BaseSummary getSummary(List<CgEntityNode> entities) {
    if (EmptyPredicate.isEmpty(entities)) {
      return null;
    }
    Map<String, Long> typeSummary = entities.stream()
                                        .map(entity -> ((SecretManagerConfig) entity.getEntity()).getEncryptionType())
                                        .collect(groupingBy(EncryptionType::name, counting()));
    return new SecretManagerSummary(entities.size(), typeSummary);
  }

  @Override
  public DiscoveryNode discover(NGMigrationEntity entity) {
    if (entity == null) {
      return null;
    }
    SecretManagerConfig managerConfig = (SecretManagerConfig) entity;
    String entityId = managerConfig.getUuid();
    CgEntityId managerEntityId = CgEntityId.builder().type(NGMigrationEntityType.SECRET_MANAGER).id(entityId).build();
    CgEntityNode secretManagerNode = CgEntityNode.builder()
                                         .id(entityId)
                                         .type(NGMigrationEntityType.SECRET_MANAGER)
                                         .entityId(managerEntityId)
                                         .entity(managerConfig)
                                         .build();
    Set<CgEntityId> children = new HashSet<>();
    return DiscoveryNode.builder().children(children).entityNode(secretManagerNode).build();
  }

  @Override
  public DiscoveryNode discover(String accountId, String appId, String entityId) {
    return discover(secretManagerConfigService.getSecretManager(accountId, entityId, false));
  }

  @Override
  public NGMigrationStatus canMigrate(NGMigrationEntity entity) {
    return NGMigrationStatus.builder().status(true).build();
  }

  @Override
  public MigrationImportSummaryDTO migrate(String auth, NGClient ngClient, PmsClient pmsClient,
      TemplateClient templateClient, MigrationInputDTO inputDTO, NGYamlFile yamlFile) throws IOException {
    if (yamlFile.isExists()) {
      return MigrationImportSummaryDTO.builder()
          .errors(Collections.singletonList(
              ImportError.builder()
                  .message("Secret manager was not migrated as it was already imported before")
                  .entity(yamlFile.getCgBasicInfo())
                  .build()))
          .build();
    }
    if ("harnessSecretManager".equals(yamlFile.getNgEntityDetail().getIdentifier())) {
      return null;
    }
    Response<ResponseDTO<ConnectorResponseDTO>> resp =
        ngClient.createConnector(auth, inputDTO.getAccountIdentifier(), JsonUtils.asTree(yamlFile.getYaml())).execute();
    log.info("Secret manager creation Response details {} {}", resp.code(), resp.message());
    return handleResp(yamlFile, resp);
  }

  @Override
  public List<NGYamlFile> generateYaml(MigrationInputDTO inputDTO, Map<CgEntityId, CgEntityNode> entities,
      Map<CgEntityId, Set<CgEntityId>> graph, CgEntityId entityId, Map<CgEntityId, NGYamlFile> migratedEntities) {
    SecretManagerConfig secretManagerConfig = (SecretManagerConfig) entities.get(entityId).getEntity();
    String name = secretManagerConfig.getName().trim();
    String identifier;
    // Handle Harness secret manager
    if (SecretFactory.isHarnessSecretManager(secretManagerConfig)) {
      identifier = "harnessSecretManager";
    } else {
      name = MigratorUtility.generateName(inputDTO.getOverrides(), entityId, secretManagerConfig.getName());
      identifier = MigratorUtility.generateIdentifierDefaultName(inputDTO.getOverrides(), entityId, name);
    }
    Scope scope = MigratorUtility.getDefaultScope(inputDTO, entityId, Scope.PROJECT);
    String projectIdentifier = MigratorUtility.getProjectIdentifier(scope, inputDTO);
    String orgIdentifier = MigratorUtility.getOrgIdentifier(scope, inputDTO);

    List<NGYamlFile> files = new ArrayList<>();
    SecretManagerCreatedDTO connectorConfigDTO =
        secretFactory.getConfigDTO(secretManagerConfig, inputDTO, migratedEntities);
    NGYamlFile ngYamlFile =
        NGYamlFile.builder()
            .filename("connector/" + name + ".yaml")
            .yaml(ConnectorDTO.builder()
                      .connectorInfo(ConnectorInfoDTO.builder()
                                         .name(name)
                                         .identifier(identifier)
                                         .description(null)
                                         .tags(null)
                                         .orgIdentifier(orgIdentifier)
                                         .projectIdentifier(projectIdentifier)
                                         .connectorType(SecretFactory.getConnectorType(secretManagerConfig))
                                         .connectorConfig(connectorConfigDTO.getConnector())
                                         .build())
                      .build())
            .ngEntityDetail(NgEntityDetail.builder()
                                .identifier(identifier)
                                .orgIdentifier(inputDTO.getOrgIdentifier())
                                .projectIdentifier(inputDTO.getProjectIdentifier())
                                .build())
            .type(NGMigrationEntityType.SECRET_MANAGER)
            .cgBasicInfo(CgBasicInfo.builder()
                             .accountId(secretManagerConfig.getAccountId())
                             .appId(null)
                             .id(secretManagerConfig.getUuid())
                             .name(secretManagerConfig.getName())
                             .type(NGMigrationEntityType.SECRET_MANAGER)
                             .build())
            .build();
    files.add(ngYamlFile);

    if (EmptyPredicate.isNotEmpty(connectorConfigDTO.getSecrets())) {
      files.addAll(connectorConfigDTO.getSecrets()
                       .stream()
                       .map(secretDTO
                           -> NGYamlFile.builder()
                                  .yaml(secretDTO)
                                  .type(SECRET)
                                  .ngEntityDetail(NgEntityDetail.builder()
                                                      .projectIdentifier(secretDTO.getSecret().getProjectIdentifier())
                                                      .orgIdentifier(secretDTO.getSecret().getOrgIdentifier())
                                                      .identifier(secretDTO.getSecret().getIdentifier())
                                                      .build())
                                  .filename(String.format("secret/%s.yaml", secretDTO.getSecret().getName()))
                                  .exists(false)
                                  .cgBasicInfo(null)
                                  .build())
                       .collect(Collectors.toList()));
    }

    migratedEntities.putIfAbsent(entityId, ngYamlFile);

    return files;
  }

  @Override
  protected YamlDTO getNGEntity(NgEntityDetail ngEntityDetail, String accountIdentifier) {
    try {
      Optional<ConnectorDTO> response =
          NGRestUtils.getResponse(connectorResourceClient.get(ngEntityDetail.getIdentifier(), accountIdentifier,
              ngEntityDetail.getOrgIdentifier(), ngEntityDetail.getProjectIdentifier()));
      return response.orElse(null);
    } catch (InvalidRequestException ex) {
      log.error("Error when getting connector - ", ex);
      return null;
    } catch (Exception ex) {
      throw ex;
    }
  }

  @Override
  protected boolean isNGEntityExists() {
    return true;
  }
}
