/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.azure.common;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.azure.model.AzureConstants.DEPLOYMENT_SLOT_PRODUCTION_NAME;
import static io.harness.azure.model.AzureConstants.SAVE_EXISTING_CONFIGURATIONS;
import static io.harness.azure.model.AzureConstants.SLOT_NAME_BLANK_ERROR_MSG;
import static io.harness.azure.model.AzureConstants.TARGET_SLOT_CANNOT_BE_IN_STOPPED_STATE;
import static io.harness.azure.model.AzureConstants.WEB_APP_INSTANCE_STATUS_RUNNING;
import static io.harness.delegate.task.azure.appservice.deployment.verifier.SlotStatusVerifier.SlotStatus.STOPPED;
import static io.harness.delegate.task.azure.appservice.webapp.AppServiceDeploymentProgress.SAVE_CONFIGURATION;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.logging.LogLevel.ERROR;
import static io.harness.logging.LogLevel.INFO;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.OwnedBy;
import io.harness.azure.client.AzureWebClient;
import io.harness.azure.context.AzureWebClientContext;
import io.harness.azure.model.AzureAppServiceApplicationSetting;
import io.harness.azure.model.AzureAppServiceConnectionString;
import io.harness.delegate.beans.azure.mapper.AzureAppServiceConfigurationDTOMapper;
import io.harness.delegate.task.azure.appservice.AzureAppServicePreDeploymentData;
import io.harness.delegate.task.azure.appservice.AzureAppServicePreDeploymentData.AzureAppServicePreDeploymentDataBuilder;
import io.harness.delegate.task.azure.appservice.deployment.context.AzureAppServiceDockerDeploymentContext;
import io.harness.delegate.task.azure.appservice.deployment.context.AzureAppServicePackageDeploymentContext;
import io.harness.delegate.task.azure.appservice.webapp.response.AzureAppDeploymentData;
import io.harness.exception.InvalidRequestException;
import io.harness.logging.LogCallback;

import com.azure.resourcemanager.appservice.fluent.models.WebSiteInstanceStatusInner;
import com.azure.resourcemanager.appservice.models.DeploymentSlot;
import com.azure.resourcemanager.appservice.models.WebApp;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

@Singleton
@NoArgsConstructor
@Slf4j
@OwnedBy(CDP)
public class AzureAppServiceService {
  @Inject private AzureWebClient azureWebClient;

  public AzureAppServicePreDeploymentData getDockerDeploymentPreDeploymentData(
      AzureAppServiceDockerDeploymentContext dockerDeploymentContext) {
    return getAzureAppServicePreDeploymentDataAndLog(dockerDeploymentContext.getAzureWebClientContext(),
        dockerDeploymentContext.getSlotName(), dockerDeploymentContext.getTargetSlotName(),
        dockerDeploymentContext.getAppSettingsToAdd(), dockerDeploymentContext.getConnSettingsToAdd(), true,
        dockerDeploymentContext.getLogCallbackProvider(), dockerDeploymentContext.isSkipTargetSlotValidation(),
        dockerDeploymentContext.isBasicDeployment());
  }

  public AzureAppServicePreDeploymentData getPackageDeploymentPreDeploymentData(
      AzureAppServicePackageDeploymentContext packageDeploymentContext) {
    return getAzureAppServicePreDeploymentDataAndLog(packageDeploymentContext.getAzureWebClientContext(),
        packageDeploymentContext.getSlotName(), packageDeploymentContext.getTargetSlotName(),
        packageDeploymentContext.getAppSettingsToAdd(), packageDeploymentContext.getConnSettingsToAdd(), false,
        packageDeploymentContext.getLogCallbackProvider(), packageDeploymentContext.isSkipTargetSlotValidation(),
        packageDeploymentContext.isBasicDeployment());
  }

  @VisibleForTesting
  AzureAppServicePreDeploymentData getAzureAppServicePreDeploymentDataAndLog(
      AzureWebClientContext azureWebClientContext, final String slotName, String targetSlotName,
      Map<String, AzureAppServiceApplicationSetting> userAddedAppSettings,
      Map<String, AzureAppServiceConnectionString> userAddedConnStrings, boolean includeDockerSettings,
      AzureLogCallbackProvider logCallbackProvider, boolean skipTargetSlotValidation, boolean isBasicDeployment) {
    LogCallback logCallback = logCallbackProvider.obtainLogCallback(SAVE_EXISTING_CONFIGURATIONS);
    logCallback.saveExecutionLog(String.format("Saving existing configurations for slot - [%s] of App Service - [%s]",
        slotName, azureWebClientContext.getAppName()));

    try {
      if (!skipTargetSlotValidation && !isBasicDeployment) {
        validateSlotStatus(azureWebClientContext, slotName, targetSlotName, logCallback);
      }

      AzureAppServicePreDeploymentDataBuilder preDeploymentDataBuilder =
          getDefaultPreDeploymentDataBuilder(azureWebClientContext.getAppName(), slotName);

      saveApplicationSettings(
          azureWebClientContext, slotName, userAddedAppSettings, preDeploymentDataBuilder, logCallback);
      saveConnectionStrings(
          azureWebClientContext, slotName, userAddedConnStrings, preDeploymentDataBuilder, logCallback);
      saveStartupCommand(azureWebClientContext, slotName, preDeploymentDataBuilder, logCallback);
      if (includeDockerSettings) {
        saveDockerSettings(azureWebClientContext, slotName, preDeploymentDataBuilder, logCallback);
      }
      if (!isBasicDeployment) {
        saveTrafficWeight(azureWebClientContext, slotName, preDeploymentDataBuilder, logCallback);
      }
      logCallback.saveExecutionLog(
          String.format("All configurations saved successfully for slot - [%s] of App Service - [%s]", slotName,
              azureWebClientContext.getAppName()),
          INFO, SUCCESS);
      return preDeploymentDataBuilder.build();
    } catch (Exception exception) {
      logCallback.saveExecutionLog(
          String.format("Failed to save the deployment slot existing configurations - %n[%s]", exception.getMessage()),
          ERROR, FAILURE);
      throw exception;
    }
  }

  public AzureAppServicePreDeploymentDataBuilder getDefaultPreDeploymentDataBuilder(String appName, String slotName) {
    return AzureAppServicePreDeploymentData.builder()
        .deploymentProgressMarker(SAVE_CONFIGURATION.name())
        .slotName(slotName)
        .appName(appName)
        .appSettingsToAdd(Collections.emptyMap())
        .appSettingsToRemove(Collections.emptyMap())
        .connStringsToAdd(Collections.emptyMap())
        .connStringsToRemove(Collections.emptyMap())
        .dockerSettingsToAdd(Collections.emptyMap());
  }

  public List<AzureAppDeploymentData> fetchDeploymentData(
      AzureWebClientContext azureWebClientContext, String slotName) {
    log.info("Start fetching deployment data for app name: {}, slot: {}", azureWebClientContext.getAppName(), slotName);
    String deploySlotId;
    String appServicePlanId;
    String hostName;
    if (DEPLOYMENT_SLOT_PRODUCTION_NAME.equals(slotName)) {
      Optional<WebApp> azureApp = azureWebClient.getWebAppByName(azureWebClientContext);
      if (!azureApp.isPresent()) {
        throw new InvalidRequestException(format("WebApp - [%s] not found", azureWebClientContext.getAppName()));
      }
      deploySlotId = azureApp.get().id();
      appServicePlanId = azureApp.get().appServicePlanId();
      hostName = azureApp.get().defaultHostname();
    } else {
      Optional<DeploymentSlot> deploymentSlot = azureWebClient.getDeploymentSlotByName(azureWebClientContext, slotName);

      if (!deploymentSlot.isPresent()) {
        throw new InvalidRequestException(format(
            "Deployment slot - [%s] not found for Web App - [%s]", slotName, azureWebClientContext.getAppName()));
      }
      deploySlotId = deploymentSlot.get().id();
      appServicePlanId = deploymentSlot.get().appServicePlanId();
      hostName = deploymentSlot.get().defaultHostname();
    }

    List<WebSiteInstanceStatusInner> siteInstanceInners =
        azureWebClient.listInstanceIdentifiersSlot(azureWebClientContext, slotName);

    return siteInstanceInners.stream()
        .map(siteInstanceInner
            -> AzureAppDeploymentData.builder()
                   .subscriptionId(azureWebClientContext.getSubscriptionId())
                   .resourceGroup(azureWebClientContext.getResourceGroupName())
                   .appName(azureWebClientContext.getAppName())
                   .deploySlot(slotName)
                   .deploySlotId(deploySlotId)
                   .appServicePlanId(appServicePlanId)
                   .hostName(hostName)
                   .instanceId(siteInstanceInner.id())
                   .instanceName(siteInstanceInner.name())
                   .instanceType(siteInstanceInner.type())
                   .instanceState(WEB_APP_INSTANCE_STATUS_RUNNING)
                   .build())
        .collect(Collectors.toList());
  }

  private void validateSlotStatus(
      AzureWebClientContext azureWebClientContext, String slotName, String targetSlotName, LogCallback logCallback) {
    if (isBlank(slotName)) {
      throw new InvalidRequestException(SLOT_NAME_BLANK_ERROR_MSG);
    }
    String slotState = azureWebClient.getSlotState(azureWebClientContext, targetSlotName);
    if (STOPPED.name().equalsIgnoreCase(slotState)) {
      throw new InvalidRequestException(
          String.format("Pre validation failed. " + TARGET_SLOT_CANNOT_BE_IN_STOPPED_STATE, targetSlotName));
    }
    logCallback.saveExecutionLog("Pre validation was success");
  }

  private void saveApplicationSettings(AzureWebClientContext azureWebClientContext, String slotName,
      Map<String, AzureAppServiceApplicationSetting> userAddedAppSettings,
      AzureAppServicePreDeploymentDataBuilder preDeploymentDataBuilder, LogCallback logCallback) {
    Map<String, AzureAppServiceApplicationSetting> existingAppSettingsOnSlot =
        azureWebClient.listDeploymentSlotAppSettings(azureWebClientContext, slotName);

    Map<String, AzureAppServiceApplicationSetting> appSettingsNeedBeDeletedInRollback =
        getAppSettingsNeedBeDeletedInRollback(userAddedAppSettings, existingAppSettingsOnSlot);

    Map<String, AzureAppServiceApplicationSetting> appSettingsNeedBeUpdatedInRollback =
        getAppSettingsNeedToBeUpdatedInRollback(userAddedAppSettings, existingAppSettingsOnSlot);

    preDeploymentDataBuilder
        .appSettingsToRemove(
            AzureAppServiceConfigurationDTOMapper.getAzureAppServiceAppSettingDTOs(appSettingsNeedBeDeletedInRollback))
        .appSettingsToAdd(
            AzureAppServiceConfigurationDTOMapper.getAzureAppServiceAppSettingDTOs(appSettingsNeedBeUpdatedInRollback));

    logCallback.saveExecutionLog(String.format("Saved existing Application settings for slot - [%s]", slotName));
  }

  private void saveConnectionStrings(AzureWebClientContext azureWebClientContext, String slotName,
      Map<String, AzureAppServiceConnectionString> userAddedConnStrings,
      AzureAppServicePreDeploymentDataBuilder preDeploymentDataBuilder, LogCallback logCallback) {
    Map<String, AzureAppServiceConnectionString> existingConnSettingsOnSlot =
        azureWebClient.listDeploymentSlotConnectionStrings(azureWebClientContext, slotName);

    Map<String, AzureAppServiceConnectionString> connSettingsNeedBeDeletedInRollback =
        getConnSettingsNeedBeDeletedInRollback(userAddedConnStrings, existingConnSettingsOnSlot);

    Map<String, AzureAppServiceConnectionString> connSettingsNeedBeUpdatedInRollback =
        getConnSettingsNeedBeUpdatedInRollback(userAddedConnStrings, existingConnSettingsOnSlot);

    preDeploymentDataBuilder
        .connStringsToRemove(
            AzureAppServiceConfigurationDTOMapper.getAzureAppServiceConnStringDTOs(connSettingsNeedBeDeletedInRollback))
        .connStringsToAdd(AzureAppServiceConfigurationDTOMapper.getAzureAppServiceConnStringDTOs(
            connSettingsNeedBeUpdatedInRollback));

    logCallback.saveExecutionLog(String.format("Saved existing Connection strings for slot - [%s]", slotName));
  }

  private void saveStartupCommand(AzureWebClientContext azureWebClientContext, String slotName,
      AzureAppServicePreDeploymentDataBuilder preDeploymentDataBuilder, LogCallback logCallback) {
    String startupCommand = azureWebClient.getDeploymentSlotStartupCommand(azureWebClientContext, slotName);
    logCallback.saveExecutionLog(String.format("Saved existing startup command for slot - [%s]", slotName));
    preDeploymentDataBuilder.startupCommand(startupCommand);
  }

  private void saveDockerSettings(AzureWebClientContext azureWebClientContext, String slotName,
      AzureAppServicePreDeploymentDataBuilder preDeploymentDataBuilder, LogCallback logCallback) {
    Map<String, AzureAppServiceApplicationSetting> dockerSettingsNeedBeUpdatedInRollback =
        azureWebClient.listDeploymentSlotDockerSettings(azureWebClientContext, slotName);
    String dockerImageNameAndTag =
        azureWebClient.getSlotDockerImageNameAndTag(azureWebClientContext, slotName).orElse(EMPTY);
    preDeploymentDataBuilder
        .dockerSettingsToAdd(AzureAppServiceConfigurationDTOMapper.getAzureAppServiceAppSettingDTOs(
            dockerSettingsNeedBeUpdatedInRollback))
        .imageNameAndTag(dockerImageNameAndTag);
    logCallback.saveExecutionLog(String.format("Saved existing Container settings for slot - [%s]", slotName));
  }

  private void saveTrafficWeight(AzureWebClientContext azureWebClientContext, String slotName,
      AzureAppServicePreDeploymentDataBuilder preDeploymentDataBuilder, LogCallback logCallback) {
    double slotTrafficWeight = getSlotTrafficWeight(azureWebClientContext, slotName);
    logCallback.saveExecutionLog(String.format("Saved existing Traffic percentage for slot - [%s]", slotName));
    preDeploymentDataBuilder.trafficWeight(slotTrafficWeight);
  }

  public double getSlotTrafficWeight(AzureWebClientContext azureWebClientContext, String slotName) {
    return azureWebClient.getDeploymentSlotTrafficWeight(azureWebClientContext, slotName);
  }

  @NotNull
  private Map<String, AzureAppServiceConnectionString> getConnSettingsNeedBeDeletedInRollback(
      Map<String, AzureAppServiceConnectionString> userAddedConnSettings,
      Map<String, AzureAppServiceConnectionString> existingConnSettingsOnSlot) {
    Sets.SetView<String> newConnSettingsNeedBeAddedInSlotSetup =
        Sets.difference(userAddedConnSettings.keySet(), existingConnSettingsOnSlot.keySet());

    return userAddedConnSettings.entrySet()
        .stream()
        .filter(entry -> newConnSettingsNeedBeAddedInSlotSetup.contains(entry.getKey()))
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  @NotNull
  private Map<String, AzureAppServiceConnectionString> getConnSettingsNeedBeUpdatedInRollback(
      Map<String, AzureAppServiceConnectionString> userAddedConnSettings,
      Map<String, AzureAppServiceConnectionString> existingConnSettingsOnSlot) {
    Sets.SetView<String> commonConnSettingsNeedBeUpdatedInSlotSetup =
        Sets.intersection(userAddedConnSettings.keySet(), existingConnSettingsOnSlot.keySet());

    return existingConnSettingsOnSlot.entrySet()
        .stream()
        .filter(entry -> commonConnSettingsNeedBeUpdatedInSlotSetup.contains(entry.getKey()))
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  @NotNull
  private Map<String, AzureAppServiceApplicationSetting> getAppSettingsNeedBeDeletedInRollback(
      Map<String, AzureAppServiceApplicationSetting> userAddedAppSettings,
      Map<String, AzureAppServiceApplicationSetting> existingAppSettingsOnSlot) {
    Sets.SetView<String> newAppSettingsNeedBeAddedInSlotSetup =
        Sets.difference(userAddedAppSettings.keySet(), existingAppSettingsOnSlot.keySet());

    return userAddedAppSettings.entrySet()
        .stream()
        .filter(entry -> newAppSettingsNeedBeAddedInSlotSetup.contains(entry.getKey()))
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  @NotNull
  private Map<String, AzureAppServiceApplicationSetting> getAppSettingsNeedToBeUpdatedInRollback(
      Map<String, AzureAppServiceApplicationSetting> userAddedAppSettings,
      Map<String, AzureAppServiceApplicationSetting> existingAppSettingsOnSlot) {
    Sets.SetView<String> commonAppSettingsNeedBeUpdatedInSlotSetup =
        Sets.intersection(userAddedAppSettings.keySet(), existingAppSettingsOnSlot.keySet());

    return existingAppSettingsOnSlot.entrySet()
        .stream()
        .filter(entry -> commonAppSettingsNeedBeUpdatedInSlotSetup.contains(entry.getKey()))
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }
}
