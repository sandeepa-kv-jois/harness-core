/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.azure;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.azure.AzureEnvironmentType;
import io.harness.azure.model.AzureAuthenticationType;
import io.harness.azure.model.AzureConfig;
import io.harness.delegate.beans.azure.registry.AzureRegistryType;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureAuthDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureClientSecretKeyDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureConnectorDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureCredentialDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureCredentialType;
import io.harness.delegate.beans.connector.azureconnector.AzureManualDetailsDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureSecretType;
import io.harness.delegate.beans.connector.docker.DockerAuthType;
import io.harness.delegate.beans.connector.docker.DockerAuthenticationDTO;
import io.harness.delegate.beans.connector.docker.DockerConnectorDTO;
import io.harness.delegate.beans.connector.docker.DockerUserNamePasswordDTO;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.delegate.task.azure.appservice.AzureAppServicePreDeploymentData;
import io.harness.delegate.task.azure.appservice.webapp.AppServiceDeploymentProgress;
import io.harness.delegate.task.azure.appservice.webapp.ng.AzureWebAppInfraDelegateConfig;
import io.harness.delegate.task.azure.artifact.ArtifactoryAzureArtifactRequestDetails;
import io.harness.delegate.task.azure.artifact.AzureArtifactConfig;
import io.harness.delegate.task.azure.artifact.AzureContainerArtifactConfig;
import io.harness.delegate.task.azure.artifact.AzurePackageArtifactConfig;
import io.harness.encryption.SecretRefData;

import java.util.Collections;
import lombok.experimental.UtilityClass;

@OwnedBy(CDP)
@UtilityClass
public class AzureTestUtils {
  public static final String APP_NAME = "app-name";
  public static final String SUBSCRIPTION_ID = "subscription-id";
  public static final String RESOURCE_GROUP = "resource-group";
  public static final String DEPLOYMENT_SLOT = "deployment-slot";
  public static final String TARGET_SLOT = "target-slot";
  public static final String ROLLBACK = "rollback";
  public static final double TRAFFIC_WEIGHT = 20.0;
  public static final String TEST_IMAGE = "test.registry.io/test-image:tag";
  public static final String TEST_IMAGE_TAG = "tag-image";
  public static final String TENANT_ID = "tenant-id";
  public static final String CLIENT_ID = "client-id";
  public static final byte[] CERT = "test-cert".getBytes();
  public static final String TAG = "tag";
  public static final String IMAGE = "image";
  public static final String REGISTRY_HOSTNAME = "test.registry.io";
  public static final String TEST_REGION = "test-region";
  public static final String ARTIFACT_PATH = "artifact";
  public static final String ARTIFACT_REPOSITORY = "repository";

  public AzureContainerArtifactConfig createTestContainerArtifactConfig() {
    return createTestContainerArtifactConfig(null);
  }

  public AzureContainerArtifactConfig createTestContainerArtifactConfig(ConnectorConfigDTO connector) {
    return AzureContainerArtifactConfig.builder()
        .connectorConfig(connector)
        .image(TEST_IMAGE)
        .tag(TEST_IMAGE_TAG)
        .registryType(AzureRegistryType.DOCKER_HUB_PRIVATE)
        .registryHostname(REGISTRY_HOSTNAME)
        .region(TEST_REGION)
        .build();
  }

  public AzureArtifactConfig createTestPackageArtifactConfig() {
    return AzurePackageArtifactConfig.builder()
        .sourceType(ArtifactSourceType.ARTIFACTORY_REGISTRY)
        .artifactDetails(ArtifactoryAzureArtifactRequestDetails.builder()
                             .artifactPaths(Collections.singletonList(ARTIFACT_PATH))
                             .repository(ARTIFACT_REPOSITORY)
                             .build())
        .build();
  }

  public AzureWebAppInfraDelegateConfig createTestWebAppInfraDelegateConfig() {
    return AzureWebAppInfraDelegateConfig.builder()
        .subscription(SUBSCRIPTION_ID)
        .resourceGroup(RESOURCE_GROUP)
        .appName(APP_NAME)
        .deploymentSlot(DEPLOYMENT_SLOT)
        .build();
  }

  public AzureConfig createTestAzureConfig() {
    return AzureConfig.builder()
        .azureAuthenticationType(AzureAuthenticationType.SERVICE_PRINCIPAL_CERT)
        .azureEnvironmentType(AzureEnvironmentType.AZURE)
        .cert(CERT)
        .tenantId(TENANT_ID)
        .clientId(CLIENT_ID)
        .build();
  }

  public AzureAppServicePreDeploymentData buildTestPreDeploymentData(AppServiceDeploymentProgress deploymentProgress) {
    return AzureAppServicePreDeploymentData.builder()
        .trafficWeight(TRAFFIC_WEIGHT)
        .appSettingsToAdd(Collections.emptyMap())
        .appSettingsToRemove(Collections.emptyMap())
        .connStringsToAdd(Collections.emptyMap())
        .connStringsToRemove(Collections.emptyMap())
        .dockerSettingsToAdd(Collections.emptyMap())
        .appName(APP_NAME)
        .slotName(DEPLOYMENT_SLOT)
        .imageNameAndTag("imageNameAndTag")
        .deploymentProgressMarker(deploymentProgress.name())
        .build();
  }

  public AzureArtifactConfig createTestAzureContainerConfig() {
    return AzureContainerArtifactConfig.builder()
        .connectorConfig(
            DockerConnectorDTO.builder()
                .auth(DockerAuthenticationDTO.builder()
                          .authType(DockerAuthType.USER_PASSWORD)
                          .credentials(DockerUserNamePasswordDTO.builder()
                                           .username("test")
                                           .passwordRef(SecretRefData.builder().identifier("password").build())
                                           .build())
                          .build())
                .build())
        .tag(TAG)
        .image(IMAGE)
        .build();
  }

  public AzureConnectorDTO createAzureConnectorDTO() {
    return AzureConnectorDTO.builder()
        .credential(AzureCredentialDTO.builder()
                        .azureCredentialType(AzureCredentialType.MANUAL_CREDENTIALS)
                        .config(AzureManualDetailsDTO.builder()
                                    .tenantId(TENANT_ID)
                                    .clientId(CLIENT_ID)
                                    .authDTO(AzureAuthDTO.builder()
                                                 .azureSecretType(AzureSecretType.SECRET_KEY)
                                                 .credentials(AzureClientSecretKeyDTO.builder().build())
                                                 .build())
                                    .build())
                        .build())
        .build();
  }
}
