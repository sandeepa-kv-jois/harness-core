/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.azure.appservice.webapp.taskhandler;

import static io.harness.azure.model.AzureConstants.DEPLOYMENT_SLOT_FULL_NAME_PATTERN;
import static io.harness.azure.model.AzureConstants.DEPLOYMENT_SLOT_NON_PRODUCTION_TYPE;
import static io.harness.azure.model.AzureConstants.DEPLOYMENT_SLOT_PRODUCTION_TYPE;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.azure.client.AzureWebClient;
import io.harness.azure.context.AzureWebClientContext;
import io.harness.azure.model.AzureConfig;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.azure.appservice.AzureAppServiceTaskParameters;
import io.harness.delegate.task.azure.appservice.AzureAppServiceTaskResponse;
import io.harness.delegate.task.azure.appservice.webapp.request.AzureWebAppListWebAppDeploymentSlotsParameters;
import io.harness.delegate.task.azure.appservice.webapp.response.AzureWebAppListWebAppDeploymentSlotsResponse;
import io.harness.delegate.task.azure.appservice.webapp.response.DeploymentSlotData;

import software.wings.delegatetasks.azure.appservice.webapp.AbstractAzureWebAppTaskHandler;

import com.azure.resourcemanager.appservice.models.WebDeploymentSlotBasic;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.stream.Collectors;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

@Singleton
@NoArgsConstructor
@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class AzureWebAppListWebAppDeploymentSlotNamesTaskHandler extends AbstractAzureWebAppTaskHandler {
  @Inject private AzureWebClient azureWebClient;

  @Override
  protected AzureAppServiceTaskResponse executeTaskInternal(AzureAppServiceTaskParameters azureAppServiceTaskParameters,
      AzureConfig azureConfig, ILogStreamingTaskClient logStreamingTaskClient) {
    String subscriptionId = azureAppServiceTaskParameters.getSubscriptionId();
    String resourceGroupName =
        ((AzureWebAppListWebAppDeploymentSlotsParameters) azureAppServiceTaskParameters).getResourceGroupName();
    String webAppName = ((AzureWebAppListWebAppDeploymentSlotsParameters) azureAppServiceTaskParameters).getAppName();

    AzureWebClientContext azureWebClientContext = AzureWebClientContext.builder()
                                                      .azureConfig(azureConfig)
                                                      .subscriptionId(subscriptionId)
                                                      .resourceGroupName(resourceGroupName)
                                                      .appName(webAppName)
                                                      .build();

    List<WebDeploymentSlotBasic> deploymentSlots =
        azureWebClient.listDeploymentSlotsByWebAppName(azureWebClientContext);
    List<DeploymentSlotData> deploymentSlotsData = toDeploymentSlotData(deploymentSlots, webAppName);

    return AzureWebAppListWebAppDeploymentSlotsResponse.builder()
        .deploymentSlots(addProductionDeploymentSlotData(deploymentSlotsData, webAppName))
        .build();
  }

  @NotNull
  private List<DeploymentSlotData> toDeploymentSlotData(
      List<WebDeploymentSlotBasic> deploymentSlots, String webAppName) {
    return deploymentSlots.stream()
        .map(WebDeploymentSlotBasic::name)
        .map(slotName
            -> DeploymentSlotData.builder()
                   .name(format(DEPLOYMENT_SLOT_FULL_NAME_PATTERN, webAppName, slotName))
                   .type(DEPLOYMENT_SLOT_NON_PRODUCTION_TYPE)
                   .build())
        .collect(Collectors.toList());
  }

  private List<DeploymentSlotData> addProductionDeploymentSlotData(
      List<DeploymentSlotData> deploymentSlots, String webAppName) {
    deploymentSlots.add(DeploymentSlotData.builder().name(webAppName).type(DEPLOYMENT_SLOT_PRODUCTION_TYPE).build());
    return deploymentSlots;
  }
}
