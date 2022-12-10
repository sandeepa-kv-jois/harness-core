/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.secrets;

import static io.harness.secretmanagerclient.SecretType.SecretFile;
import static io.harness.secretmanagerclient.SecretType.SecretText;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EncryptedData;
import io.harness.beans.SecretManagerConfig;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.dto.secrets.SecretDTOV2;
import io.harness.ng.core.dto.secrets.SecretDTOV2.SecretDTOV2Builder;
import io.harness.ng.core.dto.secrets.SecretFileSpecDTO;
import io.harness.ng.core.dto.secrets.SecretSpecDTO;
import io.harness.ng.core.dto.secrets.SecretTextSpecDTO;
import io.harness.ngmigration.beans.CustomSecretRequestWrapper;
import io.harness.ngmigration.beans.MigrationInputDTO;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.beans.NgEntityDetail;
import io.harness.ngmigration.dto.SecretManagerCreatedDTO;
import io.harness.ngmigration.service.MigratorUtility;
import io.harness.secretmanagerclient.SecretType;
import io.harness.secretmanagerclient.ValueType;

import software.wings.beans.AwsSecretsManagerConfig;
import software.wings.beans.AzureVaultConfig;
import software.wings.beans.GcpKmsConfig;
import software.wings.beans.GcpSecretsManagerConfig;
import software.wings.beans.LocalEncryptionConfig;
import software.wings.beans.SSHVaultConfig;
import software.wings.beans.VaultConfig;
import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.CgEntityNode;
import software.wings.ngmigration.NGMigrationEntityType;
import software.wings.settings.SettingVariableTypes;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import java.util.Map;

@OwnedBy(HarnessTeam.CDC)
public class SecretFactory {
  @Inject private VaultSecretMigrator vaultSecretMigrator;
  @Inject private HarnessSecretMigrator harnessSecretMigrator;
  @Inject private AwsSecretMigrator awsSecretMigrator;
  @Inject private GcpSecretMigrator gcpSecretMigrator;
  @Inject private VaultSshSecretMigrator vaultSshSecretMigrator;
  @Inject private AzureVaultSecretMigrator azureVaultSecretMigrator;

  public static ConnectorType getConnectorType(SecretManagerConfig secretManagerConfig) {
    if (secretManagerConfig instanceof AzureVaultConfig) {
      return ConnectorType.AZURE_KEY_VAULT;
    }
    if (secretManagerConfig instanceof GcpSecretsManagerConfig) {
      return ConnectorType.GCP_SECRET_MANAGER;
    }
    if (secretManagerConfig instanceof GcpKmsConfig) {
      return ConnectorType.GCP_KMS;
    }
    if (secretManagerConfig instanceof LocalEncryptionConfig) {
      return ConnectorType.LOCAL;
    }
    if (secretManagerConfig instanceof VaultConfig) {
      return ConnectorType.VAULT;
    }
    if (secretManagerConfig instanceof SSHVaultConfig) {
      return ConnectorType.VAULT;
    }
    if (secretManagerConfig instanceof AwsSecretsManagerConfig) {
      return ConnectorType.AWS_SECRET_MANAGER;
    }
    throw new InvalidRequestException("Unsupported secret manager");
  }

  public SecretMigrator getSecretMigrator(SecretManagerConfig secretManagerConfig) {
    if (secretManagerConfig instanceof AzureVaultConfig) {
      return azureVaultSecretMigrator;
    }
    if (secretManagerConfig instanceof VaultConfig) {
      return vaultSecretMigrator;
    }
    if (secretManagerConfig instanceof SSHVaultConfig) {
      return vaultSshSecretMigrator;
    }
    if (secretManagerConfig instanceof LocalEncryptionConfig) {
      return harnessSecretMigrator;
    }
    if (secretManagerConfig instanceof AwsSecretsManagerConfig) {
      return awsSecretMigrator;
    }
    if (secretManagerConfig instanceof GcpSecretsManagerConfig) {
      return gcpSecretMigrator;
    }
    // Handle special case for Harness Secret managers
    if (secretManagerConfig instanceof GcpKmsConfig && isHarnessSecretManager(secretManagerConfig)) {
      return harnessSecretMigrator;
    }
    throw new InvalidRequestException("Unsupported secret manager");
  }

  public static boolean isHarnessSecretManager(SecretManagerConfig secretManagerConfig) {
    if (secretManagerConfig instanceof LocalEncryptionConfig) {
      return true;
    }
    return "Harness Secrets Manager".equals(secretManagerConfig.getName().trim())
        || "__GLOBAL_ACCOUNT_ID__".equals(secretManagerConfig.getAccountId());
  }

  public SecretDTOV2Builder getSecret(EncryptedData encryptedData, Map<CgEntityId, CgEntityNode> entities,
      Map<CgEntityId, NGYamlFile> migratedEntities) {
    CgEntityId secretManagerId =
        CgEntityId.builder().type(NGMigrationEntityType.SECRET_MANAGER).id(encryptedData.getKmsId()).build();
    if (!entities.containsKey(secretManagerId)) {
      return null;
    }
    SecretManagerConfig secretManagerConfig = (SecretManagerConfig) entities.get(secretManagerId).getEntity();
    String secretManagerIdentifier = migratedEntities.get(secretManagerId).getNgEntityDetail().getIdentifier();
    // Support secret file
    if (encryptedData.getType().equals(SettingVariableTypes.CONFIG_FILE)) {
      return SecretDTOV2.builder()
          .spec(SecretFileSpecDTO.builder().secretManagerIdentifier(secretManagerIdentifier).build())
          .type(SecretFile);
    }
    // Support secret text
    return getSecretMigrator(secretManagerConfig)
        .getSecretDTOBuilder(encryptedData, secretManagerConfig, secretManagerIdentifier);
  }

  public String getSecretFileContent(EncryptedData encryptedData, Map<CgEntityId, CgEntityNode> entities) {
    CgEntityId secretManagerId =
        CgEntityId.builder().type(NGMigrationEntityType.SECRET_MANAGER).id(encryptedData.getKmsId()).build();
    if (!entities.containsKey(secretManagerId)) {
      return null;
    }
    SecretManagerConfig secretManagerConfig = (SecretManagerConfig) entities.get(secretManagerId).getEntity();
    // Support secret file
    if (!SettingVariableTypes.CONFIG_FILE.equals(encryptedData.getType())) {
      return null;
    }
    // Support secret text
    return getSecretMigrator(secretManagerConfig).getSecretFile(encryptedData, secretManagerConfig);
  }

  public static SecretDTOV2 getHarnessSecretManagerSpec(
      NgEntityDetail entityDetail, String secretName, String secretValue) {
    SecretSpecDTO secretSpecDTO = SecretTextSpecDTO.builder()
                                      .valueType(ValueType.Inline)
                                      .value(secretValue)
                                      .secretManagerIdentifier(MigratorUtility.getIdentifierWithScope(
                                          NgEntityDetail.builder()
                                              .identifier("harnessSecretManager")
                                              .orgIdentifier(entityDetail.getOrgIdentifier())
                                              .projectIdentifier(entityDetail.getProjectIdentifier())
                                              .build()))
                                      .build();
    return SecretDTOV2.builder()
        .type(SecretText)
        .name(secretName)
        .identifier(entityDetail.getIdentifier())
        .description(null)
        .orgIdentifier(entityDetail.getOrgIdentifier())
        .projectIdentifier(entityDetail.getProjectIdentifier())
        .spec(secretSpecDTO)
        .build();
  }

  public SecretManagerCreatedDTO getConfigDTO(SecretManagerConfig secretManagerConfig, MigrationInputDTO inputDTO,
      Map<CgEntityId, NGYamlFile> migratedEntities) {
    return getSecretMigrator(secretManagerConfig).getConfigDTO(secretManagerConfig, inputDTO, migratedEntities);
  }

  public static boolean isStoredInHarnessSecretManager(NGYamlFile yamlFile) {
    CustomSecretRequestWrapper secretDTOV2 = (CustomSecretRequestWrapper) yamlFile.getYaml();
    SecretType secretType = secretDTOV2.getSecret().getType();
    if (SecretText.equals(secretType)) {
      SecretTextSpecDTO specDTO = (SecretTextSpecDTO) secretDTOV2.getSecret().getSpec();
      return Sets.newHashSet("account.harnessSecretManager", "org.harnessSecretManager", "harnessSecretManager")
          .contains(specDTO.getSecretManagerIdentifier());
    }
    if (SecretFile.equals(secretType)) {
      SecretFileSpecDTO specDTO = (SecretFileSpecDTO) secretDTOV2.getSecret().getSpec();
      return Sets.newHashSet("account.harnessSecretManager", "org.harnessSecretManager", "harnessSecretManager")
          .contains(specDTO.getSecretManagerIdentifier());
    }
    return false;
  }
}
