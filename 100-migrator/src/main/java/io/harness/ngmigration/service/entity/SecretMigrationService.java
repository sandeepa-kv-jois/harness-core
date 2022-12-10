/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.entity;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EncryptedData;
import io.harness.beans.MigratedEntityMapping;
import io.harness.encryption.Scope;
import io.harness.exception.InvalidRequestException;
import io.harness.gitsync.beans.YamlDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.dto.secrets.SecretDTOV2;
import io.harness.ng.core.dto.secrets.SecretDTOV2.SecretDTOV2Builder;
import io.harness.ng.core.dto.secrets.SecretResponseWrapper;
import io.harness.ngmigration.beans.CustomSecretRequestWrapper;
import io.harness.ngmigration.beans.MigrationInputDTO;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.beans.NgEntityDetail;
import io.harness.ngmigration.client.NGClient;
import io.harness.ngmigration.client.PmsClient;
import io.harness.ngmigration.client.TemplateClient;
import io.harness.ngmigration.dto.ImportError;
import io.harness.ngmigration.dto.MigrationImportSummaryDTO;
import io.harness.ngmigration.secrets.SecretFactory;
import io.harness.ngmigration.service.MigratorMappingService;
import io.harness.ngmigration.service.MigratorUtility;
import io.harness.ngmigration.service.NgMigrationService;
import io.harness.remote.client.NGRestUtils;
import io.harness.secretmanagerclient.SecretType;
import io.harness.secrets.SecretService;
import io.harness.secrets.remote.SecretNGManagerClient;
import io.harness.serializer.JsonUtils;

import software.wings.ngmigration.CgBasicInfo;
import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.CgEntityNode;
import software.wings.ngmigration.DiscoveryNode;
import software.wings.ngmigration.NGMigrationEntity;
import software.wings.ngmigration.NGMigrationEntityType;
import software.wings.ngmigration.NGMigrationStatus;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import retrofit2.Response;

@Slf4j
@OwnedBy(HarnessTeam.CDC)
public class SecretMigrationService extends NgMigrationService {
  @Inject private SecretService secretService;
  @Inject private SecretNGManagerClient secretNGManagerClient;
  @Inject private SecretFactory secretFactory;

  @Override
  public MigratedEntityMapping generateMappingEntity(NGYamlFile yamlFile) {
    CgBasicInfo basicInfo = yamlFile.getCgBasicInfo();
    if (basicInfo == null) {
      return null;
    }
    SecretDTOV2 secretYaml = ((CustomSecretRequestWrapper) yamlFile.getYaml()).getSecret();
    return MigratedEntityMapping.builder()
        .appId(basicInfo.getAppId())
        .accountId(basicInfo.getAccountId())
        .cgEntityId(basicInfo.getId())
        .entityType(NGMigrationEntityType.SECRET.name())
        .accountIdentifier(basicInfo.getAccountId())
        .orgIdentifier(secretYaml.getOrgIdentifier())
        .projectIdentifier(secretYaml.getProjectIdentifier())
        .identifier(secretYaml.getIdentifier())
        .scope(MigratorMappingService.getScope(secretYaml.getOrgIdentifier(), secretYaml.getProjectIdentifier()))
        .fullyQualifiedIdentifier(MigratorMappingService.getFullyQualifiedIdentifier(basicInfo.getAccountId(),
            secretYaml.getOrgIdentifier(), secretYaml.getProjectIdentifier(), secretYaml.getIdentifier()))
        .build();
  }

  @Override
  public DiscoveryNode discover(NGMigrationEntity entity) {
    EncryptedData encryptedData = (EncryptedData) entity;
    if (encryptedData == null) {
      return null;
    }
    String entityId = encryptedData.getUuid();
    CgEntityId connectorEntityId = CgEntityId.builder().type(NGMigrationEntityType.SECRET).id(entityId).build();
    CgEntityNode connectorNode = CgEntityNode.builder()
                                     .id(entityId)
                                     .type(NGMigrationEntityType.SECRET)
                                     .entityId(connectorEntityId)
                                     .entity(encryptedData)
                                     .build();
    Set<CgEntityId> children = new HashSet<>();
    children.add(CgEntityId.builder().type(NGMigrationEntityType.SECRET_MANAGER).id(encryptedData.getKmsId()).build());
    return DiscoveryNode.builder().children(children).entityNode(connectorNode).build();
  }

  @Override
  public DiscoveryNode discover(String accountId, String appId, String entityId) {
    return discover(secretService.getSecretById(accountId, entityId).orElse(null));
  }

  @Override
  public NGMigrationStatus canMigrate(NGMigrationEntity entity) {
    return NGMigrationStatus.builder().status(true).build();
  }

  @Override
  public MigrationImportSummaryDTO migrate(String auth, NGClient ngClient, PmsClient pmsClient,
      TemplateClient templateClient, MigrationInputDTO inputDTO, NGYamlFile yamlFile) throws IOException {
    if (yamlFile.isExists()) {
      log.info("Skipping creation of secret as it already exists");
      return MigrationImportSummaryDTO.builder()
          .errors(Collections.singletonList(ImportError.builder()
                                                .message("Secret was not migrated as it was already imported before")
                                                .entity(yamlFile.getCgBasicInfo())
                                                .build()))
          .build();
    }
    CustomSecretRequestWrapper secretRequestWrapper = (CustomSecretRequestWrapper) yamlFile.getYaml();
    // If we have input stream that means it is secret file & we have the actual secret
    if (secretRequestWrapper.getFileContent() != null) {
      return migrateSecretFile(auth, ngClient, inputDTO, yamlFile);
    }
    // If the secret type is SecretFile then we use the YAML API to create.
    if (secretRequestWrapper.getSecret().getType().equals(SecretType.SecretFile)) {
      Response<ResponseDTO<SecretResponseWrapper>> resp =
          ngClient
              .createSecretUsingYaml(auth, inputDTO.getAccountIdentifier(),
                  secretRequestWrapper.getSecret().getOrgIdentifier(),
                  secretRequestWrapper.getSecret().getProjectIdentifier(), JsonUtils.asTree(yamlFile.getYaml()))
              .execute();
      log.info("Secret creation using YAML Response details {} {}", resp.code(), resp.message());
      return handleResp(yamlFile, resp);
    }
    // By default, we use the standard API to create the secret. Note this API fails if it is called for SecretFile
    Response<ResponseDTO<SecretResponseWrapper>> resp =
        ngClient
            .createSecret(auth, inputDTO.getAccountIdentifier(), secretRequestWrapper.getSecret().getOrgIdentifier(),
                secretRequestWrapper.getSecret().getProjectIdentifier(), JsonUtils.asTree(yamlFile.getYaml()))
            .execute();
    log.info("Secret creation Response details {} {}", resp.code(), resp.message());
    return handleResp(yamlFile, resp);
  }

  private MigrationImportSummaryDTO migrateSecretFile(
      String auth, NGClient ngClient, MigrationInputDTO inputDTO, NGYamlFile yamlFile) throws IOException {
    CustomSecretRequestWrapper secretRequestWrapper = (CustomSecretRequestWrapper) yamlFile.getYaml();
    RequestBody spec = RequestBody.create(MediaType.parse("text/plain"), JsonUtils.asJson(secretRequestWrapper));
    RequestBody content =
        RequestBody.create(MediaType.parse("application/octet-stream"), secretRequestWrapper.getFileContent());
    Response<ResponseDTO<SecretResponseWrapper>> resp =
        ngClient
            .createSecretFile(auth, inputDTO.getAccountIdentifier(),
                secretRequestWrapper.getSecret().getOrgIdentifier(),
                secretRequestWrapper.getSecret().getProjectIdentifier(), content, spec)
            .execute();
    log.info("Secret file creation Response details {} {}", resp.code(), resp.message());
    return handleResp(yamlFile, resp);
  }

  @Override
  public List<NGYamlFile> generateYaml(MigrationInputDTO inputDTO, Map<CgEntityId, CgEntityNode> entities,
      Map<CgEntityId, Set<CgEntityId>> graph, CgEntityId entityId, Map<CgEntityId, NGYamlFile> migratedEntities) {
    EncryptedData encryptedData = (EncryptedData) entities.get(entityId).getEntity();
    List<NGYamlFile> files = new ArrayList<>();
    String name = MigratorUtility.generateName(inputDTO.getOverrides(), entityId, encryptedData.getName());
    String identifier = MigratorUtility.generateIdentifierDefaultName(inputDTO.getOverrides(), entityId, name);
    Scope scope = MigratorUtility.getDefaultScope(inputDTO, entityId, Scope.PROJECT);
    String projectIdentifier = MigratorUtility.getProjectIdentifier(scope, inputDTO);
    String orgIdentifier = MigratorUtility.getOrgIdentifier(scope, inputDTO);
    SecretDTOV2Builder secretDTOV2Builder = secretFactory.getSecret(encryptedData, entities, migratedEntities);
    if (secretDTOV2Builder == null) {
      return files;
    }
    SecretDTOV2 secretDTOV2 = secretDTOV2Builder.projectIdentifier(projectIdentifier)
                                  .orgIdentifier(orgIdentifier)
                                  .identifier(identifier)
                                  .name(name)
                                  .build();
    NGYamlFile yamlFile = NGYamlFile.builder()
                              .type(NGMigrationEntityType.SECRET)
                              .filename("secret/" + name + ".yaml")
                              .yaml(CustomSecretRequestWrapper.builder()
                                        .fileContent(secretFactory.getSecretFileContent(encryptedData, entities))
                                        .secret(secretDTOV2)
                                        .build())
                              .ngEntityDetail(NgEntityDetail.builder()
                                                  .identifier(identifier)
                                                  .orgIdentifier(orgIdentifier)
                                                  .projectIdentifier(projectIdentifier)
                                                  .build())
                              .cgBasicInfo(CgBasicInfo.builder()
                                               .id(encryptedData.getUuid())
                                               .accountId(encryptedData.getAccountId())
                                               .appId(null)
                                               .name(encryptedData.getName())
                                               .type(NGMigrationEntityType.SECRET)
                                               .build())
                              .build();
    files.add(yamlFile);

    // TODO: make it more obvious that migratedEntities needs to be updated by having compile-time check
    migratedEntities.putIfAbsent(entityId, yamlFile);

    return files;
  }

  @Override
  protected YamlDTO getNGEntity(NgEntityDetail ngEntityDetail, String accountIdentifier) {
    try {
      SecretResponseWrapper response =
          NGRestUtils.getResponse(secretNGManagerClient.getSecret(ngEntityDetail.getIdentifier(), accountIdentifier,
              ngEntityDetail.getOrgIdentifier(), ngEntityDetail.getProjectIdentifier()));
      return response == null ? null : CustomSecretRequestWrapper.builder().secret(response.getSecret()).build();
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
