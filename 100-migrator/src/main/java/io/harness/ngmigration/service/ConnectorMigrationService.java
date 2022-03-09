/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.MigratedEntityMapping;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.encryption.Scope;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ngmigration.beans.BaseEntityInput;
import io.harness.ngmigration.beans.BaseInputDefinition;
import io.harness.ngmigration.beans.BaseProvidedInput;
import io.harness.ngmigration.beans.MigrationInputDTO;
import io.harness.ngmigration.beans.MigratorInputType;
import io.harness.ngmigration.beans.NgEntityDetail;
import io.harness.ngmigration.client.NGClient;
import io.harness.ngmigration.client.PmsClient;
import io.harness.ngmigration.connector.ConnectorFactory;
import io.harness.serializer.JsonUtils;

import software.wings.beans.SettingAttribute;
import software.wings.ngmigration.CgBasicInfo;
import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.CgEntityNode;
import software.wings.ngmigration.DiscoveryNode;
import software.wings.ngmigration.NGMigrationEntity;
import software.wings.ngmigration.NGMigrationEntityType;
import software.wings.ngmigration.NGMigrationStatus;
import software.wings.ngmigration.NGYamlFile;
import software.wings.service.intfc.SettingsService;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import retrofit2.Response;

@Slf4j
@OwnedBy(HarnessTeam.CDC)
public class ConnectorMigrationService implements NgMigrationService {
  @Inject private SettingsService settingsService;

  @Override
  public MigratedEntityMapping generateMappingEntity(NGYamlFile yamlFile) {
    CgBasicInfo basicInfo = yamlFile.getCgBasicInfo();
    ConnectorInfoDTO connectorInfo = ((ConnectorDTO) yamlFile.getYaml()).getConnectorInfo();
    return MigratedEntityMapping.builder()
        .appId(basicInfo.getAppId())
        .accountId(basicInfo.getAccountId())
        .cgEntityId(basicInfo.getId())
        .entityType(NGMigrationEntityType.CONNECTOR.name())
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
  public DiscoveryNode discover(NGMigrationEntity entity) {
    SettingAttribute settingAttribute = (SettingAttribute) entity;
    String entityId = settingAttribute.getUuid();
    CgEntityId connectorEntityId = CgEntityId.builder().type(NGMigrationEntityType.CONNECTOR).id(entityId).build();
    CgEntityNode connectorNode = CgEntityNode.builder()
                                     .id(entityId)
                                     .type(NGMigrationEntityType.CONNECTOR)
                                     .entityId(connectorEntityId)
                                     .entity(settingAttribute)
                                     .build();
    Set<CgEntityId> children = new HashSet<>();
    String secret = ConnectorFactory.getSecretId(settingAttribute);
    if (StringUtils.isNotBlank(secret)) {
      children.add(CgEntityId.builder().id(secret).type(NGMigrationEntityType.SECRET).build());
    }
    return DiscoveryNode.builder().children(children).entityNode(connectorNode).build();
  }

  @Override
  public DiscoveryNode discover(String accountId, String appId, String entityId) {
    return discover(settingsService.getByAccountAndId(accountId, entityId));
  }

  @Override
  public NGMigrationStatus canMigrate(
      Map<CgEntityId, CgEntityNode> entities, Map<CgEntityId, Set<CgEntityId>> graph, CgEntityId entityId) {
    return null;
  }

  @Override
  public void migrate(String auth, NGClient ngClient, PmsClient pmsClient, MigrationInputDTO inputDTO,
      NGYamlFile yamlFile) throws IOException {
    if (!yamlFile.isExists()) {
      Response<ResponseDTO<ConnectorResponseDTO>> resp =
          ngClient.createConnector(auth, inputDTO.getAccountIdentifier(), JsonUtils.asTree(yamlFile.getYaml()))
              .execute();
      log.info("Connector creation Response details {} {}", resp.code(), resp.message());
    }
  }

  @Override
  public List<NGYamlFile> getYamls(MigrationInputDTO inputDTO, Map<CgEntityId, CgEntityNode> entities,
      Map<CgEntityId, Set<CgEntityId>> graph, CgEntityId entityId, Map<CgEntityId, NgEntityDetail> migratedEntities) {
    SettingAttribute settingAttribute = (SettingAttribute) entities.get(entityId).getEntity();
    String name = settingAttribute.getName();
    String identifier = MigratorUtility.generateIdentifier(settingAttribute.getName());
    String projectIdentifier = null;
    String orgIdentifier = null;
    if (inputDTO.getInputs() != null && inputDTO.getInputs().containsKey(entityId)) {
      // TODO: @deepakputhraya We should handle if the connector needs to be reused.
      BaseProvidedInput input = inputDTO.getInputs().get(entityId);
      identifier = StringUtils.isNotBlank(input.getIdentifier()) ? input.getIdentifier() : identifier;
      name = StringUtils.isNotBlank(input.getIdentifier()) ? input.getName() : name;
      if (Scope.PROJECT.equals(input.getScope())) {
        projectIdentifier = inputDTO.getProjectIdentifier();
        orgIdentifier = inputDTO.getOrgIdentifier();
      }
      if (Scope.ORG.equals(input.getScope())) {
        orgIdentifier = inputDTO.getOrgIdentifier();
      }
    }

    List<NGYamlFile> files = new ArrayList<>();
    Set<CgEntityId> childEntities = graph.get(entityId);
    files.add(NGYamlFile.builder()
                  .type(NGMigrationEntityType.CONNECTOR)
                  .filename("connector/" + settingAttribute.getName() + ".yaml")
                  .yaml(ConnectorDTO.builder()
                            .connectorInfo(ConnectorInfoDTO.builder()
                                               .name(name)
                                               .identifier(identifier)
                                               .description(null)
                                               .tags(null)
                                               .orgIdentifier(orgIdentifier)
                                               .projectIdentifier(projectIdentifier)
                                               .connectorType(ConnectorFactory.getConnectorType(settingAttribute))
                                               .connectorConfig(ConnectorFactory.getConfigDTO(
                                                   settingAttribute, childEntities, migratedEntities))
                                               .build())
                            .build())
                  .type(NGMigrationEntityType.CONNECTOR)
                  .cgBasicInfo(CgBasicInfo.builder()
                                   .accountId(settingAttribute.getAccountId())
                                   .appId(null)
                                   .id(settingAttribute.getUuid())
                                   .type(NGMigrationEntityType.CONNECTOR)
                                   .build())
                  .build());
    migratedEntities.putIfAbsent(entityId,
        NgEntityDetail.builder()
            .identifier(identifier)
            .orgIdentifier(orgIdentifier)
            .projectIdentifier(projectIdentifier)
            .build());
    return files;
  }

  @Override
  public BaseEntityInput generateInput(
      Map<CgEntityId, CgEntityNode> entities, Map<CgEntityId, Set<CgEntityId>> graph, CgEntityId entityId) {
    SettingAttribute settingAttribute = (SettingAttribute) entities.get(entityId).getEntity();
    return BaseEntityInput.builder()
        .migrationStatus(MigratorInputType.CREATE_NEW)
        .identifier(BaseInputDefinition.buildIdentifier(MigratorUtility.generateIdentifier(settingAttribute.getName())))
        .name(BaseInputDefinition.buildName(settingAttribute.getName()))
        .scope(BaseInputDefinition.buildScope(Scope.PROJECT))
        .spec(null)
        .build();
  }
}
