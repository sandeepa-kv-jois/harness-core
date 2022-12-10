/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.azure.appservice.webapp.handler;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.azure.context.AzureWebClientContext;
import io.harness.azure.model.AzureConfig;
import io.harness.delegate.task.azure.appservice.AzureAppServicePreDeploymentData;
import io.harness.delegate.task.azure.appservice.deployment.context.AzureAppServiceDockerDeploymentContext;
import io.harness.delegate.task.azure.appservice.deployment.context.AzureAppServicePackageDeploymentContext;
import io.harness.delegate.task.azure.appservice.webapp.ng.request.AzureWebAppFetchPreDeploymentDataRequest;
import io.harness.delegate.task.azure.appservice.webapp.ng.response.AzureWebAppFetchPreDeploymentDataResponse;
import io.harness.delegate.task.azure.appservice.webapp.ng.response.AzureWebAppRequestResponse;
import io.harness.delegate.task.azure.artifact.AzureArtifactConfig;
import io.harness.delegate.task.azure.common.AzureLogCallbackProvider;

@OwnedBy(CDP)
public class AzureWebAppFetchPreDeploymentDataRequestHandler
    extends AbstractSlotDataRequestHandler<AzureWebAppFetchPreDeploymentDataRequest> {
  @Override
  protected AzureWebAppRequestResponse execute(AzureWebAppFetchPreDeploymentDataRequest taskRequest,
      AzureConfig azureConfig, AzureLogCallbackProvider logCallbackProvider) {
    AzureArtifactConfig artifactConfig = taskRequest.getArtifact();
    switch (artifactConfig.getArtifactType()) {
      case CONTAINER:
        return fetchDockerPreDeploymentData(taskRequest, azureConfig, logCallbackProvider);
      case PACKAGE:
        return fetchPackagePreDeploymentData(taskRequest, azureConfig, logCallbackProvider);
      default:
        throw new UnsupportedOperationException(
            format("Artifact type [%s] is not supported yet", artifactConfig.getArtifactType()));
    }
  }

  @Override
  protected Class<AzureWebAppFetchPreDeploymentDataRequest> getRequestType() {
    return AzureWebAppFetchPreDeploymentDataRequest.class;
  }

  private AzureWebAppRequestResponse fetchDockerPreDeploymentData(AzureWebAppFetchPreDeploymentDataRequest taskRequest,
      AzureConfig azureConfig, AzureLogCallbackProvider logCallbackProvider) {
    AzureWebClientContext azureWebClientContext =
        buildAzureWebClientContext(taskRequest.getInfrastructure(), azureConfig);
    AzureAppServiceDockerDeploymentContext dockerDeploymentContext =
        toAzureAppServiceDockerDeploymentContext(taskRequest, azureWebClientContext, logCallbackProvider);
    AzureAppServicePreDeploymentData preDeploymentData =
        azureAppServiceService.getDockerDeploymentPreDeploymentData(dockerDeploymentContext);
    azureSecretHelper.encryptAzureAppServicePreDeploymentData(preDeploymentData, taskRequest.getAccountId());
    return AzureWebAppFetchPreDeploymentDataResponse.builder().preDeploymentData(preDeploymentData).build();
  }

  private AzureWebAppRequestResponse fetchPackagePreDeploymentData(AzureWebAppFetchPreDeploymentDataRequest taskRequest,
      AzureConfig azureConfig, AzureLogCallbackProvider logCallbackProvider) {
    AzureWebClientContext azureWebClientContext =
        buildAzureWebClientContext(taskRequest.getInfrastructure(), azureConfig);
    // Artifact file is not required for fetching pre deployment data
    AzureAppServicePackageDeploymentContext packageDeploymentContext =
        toAzureAppServicePackageDeploymentContext(taskRequest, azureWebClientContext, null, logCallbackProvider);
    AzureAppServicePreDeploymentData preDeploymentData =
        azureAppServiceService.getPackageDeploymentPreDeploymentData(packageDeploymentContext);
    azureSecretHelper.encryptAzureAppServicePreDeploymentData(preDeploymentData, taskRequest.getAccountId());
    return AzureWebAppFetchPreDeploymentDataResponse.builder().preDeploymentData(preDeploymentData).build();
  }
}
