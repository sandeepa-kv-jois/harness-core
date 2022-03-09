/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.citasks.cik8handler.helper;

import static io.harness.annotations.dev.HarnessTeam.CI;
import static io.harness.data.encoding.EncodingUtils.encodeBase64;
import static io.harness.delegate.beans.ci.pod.SecretParams.Type.FILE;
import static io.harness.delegate.beans.ci.pod.SecretParams.Type.TEXT;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.ci.pod.EnvVariableEnum;
import io.harness.delegate.beans.ci.pod.SecretParams;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryAuthType;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryAuthenticationDTO;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryConnectorDTO;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryUsernamePasswordAuthDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialType;
import io.harness.delegate.beans.connector.awsconnector.AwsManualConfigSpecDTO;
import io.harness.delegate.beans.connector.docker.DockerAuthType;
import io.harness.delegate.beans.connector.docker.DockerConnectorDTO;
import io.harness.delegate.beans.connector.docker.DockerUserNamePasswordDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpCredentialType;
import io.harness.delegate.beans.connector.gcpconnector.GcpManualDetailsDTO;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.WingsException;
import io.harness.security.encryption.SecretDecryptionService;
import io.harness.utils.FieldWithPlainTextOrSecretValueHelper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * Helper class to create spec for image registry and GIT secrets. Generated spec can be used for creation of secrets on
 * a K8 cluster.
 */

@OwnedBy(CI)
@Slf4j
@Singleton
public class ConnectorEnvVariablesHelper {
  @Inject private SecretDecryptionService secretDecryptionService;

  public Map<String, SecretParams> getArtifactorySecretVariables(ConnectorDetails connectorDetails) {
    Map<String, SecretParams> secretData = new HashMap<>();
    ArtifactoryConnectorDTO artifactoryConnectorDTO = (ArtifactoryConnectorDTO) connectorDetails.getConnectorConfig();
    ArtifactoryAuthenticationDTO artifactoryAuthenticationDTO = artifactoryConnectorDTO.getAuth();
    ArtifactoryAuthType authType = artifactoryAuthenticationDTO.getAuthType();
    if (authType == ArtifactoryAuthType.USER_PASSWORD) {
      ArtifactoryUsernamePasswordAuthDTO usernamePasswordAuthDTO =
          (ArtifactoryUsernamePasswordAuthDTO) secretDecryptionService.decrypt(
              artifactoryAuthenticationDTO.getCredentials(), connectorDetails.getEncryptedDataDetails());
      String usernameEnvVarName = connectorDetails.getEnvToSecretsMap().get(EnvVariableEnum.ARTIFACTORY_USERNAME);
      String passwordEnvVarName = connectorDetails.getEnvToSecretsMap().get(EnvVariableEnum.ARTIFACTORY_PASSWORD);
      String endpointEnvVarName = connectorDetails.getEnvToSecretsMap().get(EnvVariableEnum.ARTIFACTORY_ENDPOINT);
      if (isNotBlank(usernameEnvVarName)) {
        secretData.put(usernameEnvVarName,
            getVariableSecret(usernameEnvVarName + connectorDetails.getIdentifier(),
                encodeBase64(usernamePasswordAuthDTO.getUsername())));
      }

      if (usernamePasswordAuthDTO == null || usernamePasswordAuthDTO.getPasswordRef() == null
          || usernamePasswordAuthDTO.getPasswordRef().getDecryptedValue() == null) {
        throw new InvalidArgumentsException(
            format("Artifactory connector password secret does not exist for connector with identifier %s",
                connectorDetails.getIdentifier()),
            WingsException.USER);
      }

      if (isNotBlank(passwordEnvVarName)) {
        secretData.put(passwordEnvVarName,
            getVariableSecret(passwordEnvVarName + connectorDetails.getIdentifier(),
                encodeBase64(String.valueOf(usernamePasswordAuthDTO.getPasswordRef().getDecryptedValue()))));
      }
      if (isNotBlank(endpointEnvVarName)) {
        secretData.put(endpointEnvVarName,
            getVariableSecret(endpointEnvVarName + connectorDetails.getIdentifier(),
                encodeBase64(artifactoryConnectorDTO.getArtifactoryServerUrl())));
      }
    }
    return secretData;
  }

  public Map<String, SecretParams> getGcpSecretVariables(ConnectorDetails connectorDetails) {
    Map<String, SecretParams> secretData = new HashMap<>();
    GcpConnectorDTO gcpConnectorConfig = (GcpConnectorDTO) connectorDetails.getConnectorConfig();
    if (gcpConnectorConfig.getCredential().getGcpCredentialType() == GcpCredentialType.MANUAL_CREDENTIALS) {
      GcpManualDetailsDTO credentialConfig = (GcpManualDetailsDTO) secretDecryptionService.decrypt(
          (GcpManualDetailsDTO) gcpConnectorConfig.getCredential().getConfig(),
          connectorDetails.getEncryptedDataDetails());

      if (credentialConfig == null || credentialConfig.getSecretKeyRef() == null
          || credentialConfig.getSecretKeyRef().getDecryptedValue() == null) {
        throw new InvalidArgumentsException(
            format("GCP connector GCP_KEY secret does not exist for connector with identifier  %s",
                connectorDetails.getIdentifier()),
            WingsException.USER);
      }

      if (connectorDetails.getEnvToSecretsMap().containsKey(EnvVariableEnum.GCP_KEY)) {
        String gcpKeyEnvVarName = connectorDetails.getEnvToSecretsMap().get(EnvVariableEnum.GCP_KEY);
        if (isNotBlank(gcpKeyEnvVarName)) {
          secretData.put(gcpKeyEnvVarName,
              getVariableSecret(gcpKeyEnvVarName + connectorDetails.getIdentifier(),
                  encodeBase64(String.valueOf(credentialConfig.getSecretKeyRef().getDecryptedValue()))));
        }
      }
      if (connectorDetails.getEnvToSecretsMap().containsKey(EnvVariableEnum.GCP_KEY_AS_FILE)) {
        String gcpKeyEnvVarName = connectorDetails.getEnvToSecretsMap().get(EnvVariableEnum.GCP_KEY_AS_FILE);
        if (isNotBlank(gcpKeyEnvVarName)) {
          secretData.put(gcpKeyEnvVarName,
              getFileSecret(gcpKeyEnvVarName + connectorDetails.getIdentifier(),
                  encodeBase64(String.valueOf(credentialConfig.getSecretKeyRef().getDecryptedValue()))));
        }
      }
    }
    return secretData;
  }

  public Map<String, SecretParams> getAwsSecretVariables(ConnectorDetails connectorDetails) {
    Map<String, SecretParams> secretData = new HashMap<>();
    AwsConnectorDTO awsConnectorConfig = (AwsConnectorDTO) connectorDetails.getConnectorConfig();
    if (awsConnectorConfig.getCredential().getAwsCredentialType() == AwsCredentialType.MANUAL_CREDENTIALS) {
      AwsManualConfigSpecDTO manualConfig = (AwsManualConfigSpecDTO) secretDecryptionService.decrypt(
          (AwsManualConfigSpecDTO) awsConnectorConfig.getCredential().getConfig(),
          connectorDetails.getEncryptedDataDetails());
      String accessKeyEnvVarName = connectorDetails.getEnvToSecretsMap().get(EnvVariableEnum.AWS_ACCESS_KEY);
      String secretKeyEnvVarName = connectorDetails.getEnvToSecretsMap().get(EnvVariableEnum.AWS_SECRET_KEY);

      if (manualConfig == null || manualConfig.getSecretKeyRef() == null
          || manualConfig.getSecretKeyRef().getDecryptedValue() == null) {
        throw new InvalidArgumentsException(
            format("AWS connector secret key does not exist for connector with identifier  %s",
                connectorDetails.getIdentifier()),
            WingsException.USER);
      }

      if (manualConfig == null || (manualConfig.getAccessKey() == null && manualConfig.getAccessKeyRef() == null)) {
        throw new InvalidArgumentsException(
            format("AWS connector access key does not exist for connector with identifier %s",
                connectorDetails.getIdentifier()),
            WingsException.USER);
      }

      String awsAccessKey = FieldWithPlainTextOrSecretValueHelper.getSecretAsStringFromPlainTextOrSecretRef(
          manualConfig.getAccessKey(), manualConfig.getAccessKeyRef());

      if (isNotBlank(accessKeyEnvVarName)) {
        secretData.put(accessKeyEnvVarName,
            getVariableSecret(accessKeyEnvVarName + connectorDetails.getIdentifier(), encodeBase64(awsAccessKey)));
      }
      if (isNotBlank(secretKeyEnvVarName)) {
        secretData.put(secretKeyEnvVarName,
            getVariableSecret(secretKeyEnvVarName + connectorDetails.getIdentifier(),
                encodeBase64(String.valueOf(manualConfig.getSecretKeyRef().getDecryptedValue()))));
      }
    }

    if (awsConnectorConfig.getCredential() != null
        && awsConnectorConfig.getCredential().getCrossAccountAccess() != null) {
      String crossAccountRoleArn = awsConnectorConfig.getCredential().getCrossAccountAccess().getCrossAccountRoleArn();

      String crossAccountArnEnvVarName =
          connectorDetails.getEnvToSecretsMap().get(EnvVariableEnum.AWS_CROSS_ACCOUNT_ROLE_ARN);
      if (isNotBlank(crossAccountArnEnvVarName)) {
        secretData.put(crossAccountArnEnvVarName,
            getVariableSecret(
                crossAccountArnEnvVarName + connectorDetails.getIdentifier(), encodeBase64(crossAccountRoleArn)));
      }
    }
    return secretData;
  }

  public Map<String, SecretParams> getDockerSecretVariables(ConnectorDetails connectorDetails) {
    Map<String, SecretParams> secretData = new HashMap<>();
    DockerConnectorDTO dockerConnectorConfig = (DockerConnectorDTO) connectorDetails.getConnectorConfig();
    String registryUrl = dockerConnectorConfig.getDockerRegistryUrl();
    String registryEnvVarName = connectorDetails.getEnvToSecretsMap().get(EnvVariableEnum.DOCKER_REGISTRY);

    if (dockerConnectorConfig.getAuth().getAuthType() == DockerAuthType.USER_PASSWORD) {
      DockerUserNamePasswordDTO dockerUserNamePasswordDTO = (DockerUserNamePasswordDTO) secretDecryptionService.decrypt(
          dockerConnectorConfig.getAuth().getCredentials(), connectorDetails.getEncryptedDataDetails());

      String usernameEnvVarName = connectorDetails.getEnvToSecretsMap().get(EnvVariableEnum.DOCKER_USERNAME);
      String passwordEnvVarName = connectorDetails.getEnvToSecretsMap().get(EnvVariableEnum.DOCKER_PASSWORD);
      if (isNotBlank(usernameEnvVarName)) {
        secretData.put(usernameEnvVarName,
            getVariableSecret(usernameEnvVarName + connectorDetails.getIdentifier(),
                encodeBase64(dockerUserNamePasswordDTO.getUsername())));
      }

      if (dockerUserNamePasswordDTO == null || dockerUserNamePasswordDTO.getPasswordRef() == null
          || dockerUserNamePasswordDTO.getPasswordRef().getDecryptedValue() == null) {
        throw new InvalidArgumentsException(
            format("Docker connector password secret does not exist for connector with identifier %s",
                connectorDetails.getIdentifier()),
            WingsException.USER);
      }

      if (isNotBlank(passwordEnvVarName)) {
        secretData.put(passwordEnvVarName,
            getVariableSecret(passwordEnvVarName + connectorDetails.getIdentifier(),
                encodeBase64(String.valueOf(dockerUserNamePasswordDTO.getPasswordRef().getDecryptedValue()))));
      }
    }
    if (isNotBlank(registryEnvVarName)) {
      secretData.put(registryEnvVarName,
          getVariableSecret(registryEnvVarName + connectorDetails.getIdentifier(), encodeBase64(registryUrl)));
    }
    return secretData;
  }

  private SecretParams getVariableSecret(String key, String encodedSecret) {
    return SecretParams.builder().secretKey(key).value(encodedSecret).type(TEXT).build();
  }
  private SecretParams getFileSecret(String key, String encodedSecret) {
    return SecretParams.builder().secretKey(key).value(encodedSecret).type(FILE).build();
  }
}
