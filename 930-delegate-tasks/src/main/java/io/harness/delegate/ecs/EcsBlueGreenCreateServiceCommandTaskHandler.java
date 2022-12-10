/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.ecs;

import static software.wings.beans.LogHelper.color;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.ecs.EcsBlueGreenCreateServiceResult;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.exception.EcsNGException;
import io.harness.delegate.task.aws.AwsNgConfigMapper;
import io.harness.delegate.task.ecs.EcsCommandTaskNGHelper;
import io.harness.delegate.task.ecs.EcsInfraConfig;
import io.harness.delegate.task.ecs.EcsInfraConfigHelper;
import io.harness.delegate.task.ecs.EcsTaskHelperBase;
import io.harness.delegate.task.ecs.request.EcsBlueGreenCreateServiceRequest;
import io.harness.delegate.task.ecs.request.EcsCommandRequest;
import io.harness.delegate.task.ecs.response.EcsBlueGreenCreateServiceResponse;
import io.harness.delegate.task.ecs.response.EcsCommandResponse;
import io.harness.ecs.EcsCommandUnitConstants;
import io.harness.exception.InvalidArgumentsException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;

import software.wings.beans.LogColor;
import software.wings.beans.LogWeight;

import com.google.inject.Inject;
import java.util.List;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import software.amazon.awssdk.services.ecs.model.RegisterTaskDefinitionRequest;
import software.amazon.awssdk.services.ecs.model.RegisterTaskDefinitionResponse;
import software.amazon.awssdk.services.ecs.model.TaskDefinition;

@OwnedBy(HarnessTeam.CDP)
@NoArgsConstructor
@Slf4j
public class EcsBlueGreenCreateServiceCommandTaskHandler extends EcsCommandTaskNGHandler {
  @Inject private EcsTaskHelperBase ecsTaskHelperBase;
  @Inject private EcsInfraConfigHelper ecsInfraConfigHelper;
  @Inject private EcsCommandTaskNGHelper ecsCommandTaskHelper;
  @Inject private AwsNgConfigMapper awsNgConfigMapper;
  private EcsInfraConfig ecsInfraConfig;
  private long timeoutInMillis;

  @Override
  protected EcsCommandResponse executeTaskInternal(EcsCommandRequest ecsCommandRequest,
      ILogStreamingTaskClient iLogStreamingTaskClient, CommandUnitsProgress commandUnitsProgress) throws Exception {
    if (!(ecsCommandRequest instanceof EcsBlueGreenCreateServiceRequest)) {
      throw new InvalidArgumentsException(
          Pair.of("ecsCommandRequest", "Must be instance of EcsBlueGreenCreateServiceRequest"));
    }

    EcsBlueGreenCreateServiceRequest ecsBlueGreenCreateServiceRequest =
        (EcsBlueGreenCreateServiceRequest) ecsCommandRequest;

    timeoutInMillis = ecsBlueGreenCreateServiceRequest.getTimeoutIntervalInMin() * 60000;
    ecsInfraConfig = ecsBlueGreenCreateServiceRequest.getEcsInfraConfig();

    LogCallback deployLogCallback = ecsTaskHelperBase.getLogCallback(
        iLogStreamingTaskClient, EcsCommandUnitConstants.deploy.toString(), true, commandUnitsProgress);

    try {
      String ecsServiceDefinitionManifestContent =
          ecsBlueGreenCreateServiceRequest.getEcsServiceDefinitionManifestContent();
      List<String> ecsScalableTargetManifestContentList =
          ecsBlueGreenCreateServiceRequest.getEcsScalableTargetManifestContentList();
      List<String> ecsScalingPolicyManifestContentList =
          ecsBlueGreenCreateServiceRequest.getEcsScalingPolicyManifestContentList();
      String ecsTaskDefinitionManifestContent = ecsBlueGreenCreateServiceRequest.getEcsTaskDefinitionManifestContent();

      RegisterTaskDefinitionRequest.Builder registerTaskDefinitionRequestBuilder =
          ecsCommandTaskHelper.parseYamlAsObject(
              ecsTaskDefinitionManifestContent, RegisterTaskDefinitionRequest.serializableBuilderClass());
      RegisterTaskDefinitionRequest registerTaskDefinitionRequest = registerTaskDefinitionRequestBuilder.build();
      deployLogCallback.saveExecutionLog(
          format("Creating Task Definition with family %s %n", registerTaskDefinitionRequest.family()), LogLevel.INFO);

      RegisterTaskDefinitionResponse registerTaskDefinitionResponse = ecsCommandTaskHelper.createTaskDefinition(
          registerTaskDefinitionRequest, ecsInfraConfig.getRegion(), ecsInfraConfig.getAwsConnectorDTO());
      TaskDefinition taskDefinition = registerTaskDefinitionResponse.taskDefinition();
      String taskDefinitionName = taskDefinition.family() + ":" + taskDefinition.revision();
      String taskDefinitionArn = taskDefinition.taskDefinitionArn();

      deployLogCallback.saveExecutionLog(
          format("Created Task Definition %s with Arn %s..%n%n", taskDefinitionName, taskDefinitionArn), LogLevel.INFO);

      // target group arn from stage listener and stage listener rule arn
      String targetGroupArn = ecsBlueGreenCreateServiceRequest.getEcsLoadBalancerConfig().getStageTargetGroupArn();

      String serviceName = ecsCommandTaskHelper.createStageService(ecsServiceDefinitionManifestContent,
          ecsScalableTargetManifestContentList, ecsScalingPolicyManifestContentList, ecsInfraConfig, deployLogCallback,
          timeoutInMillis, ecsBlueGreenCreateServiceRequest, taskDefinitionArn, targetGroupArn);

      EcsBlueGreenCreateServiceResult ecsBlueGreenCreateServiceResult =
          EcsBlueGreenCreateServiceResult.builder()
              .region(ecsInfraConfig.getRegion())
              .ecsTasks(ecsCommandTaskHelper.getRunningEcsTasks(ecsInfraConfig.getAwsConnectorDTO(),
                  ecsInfraConfig.getCluster(), serviceName, ecsInfraConfig.getRegion()))
              .targetGroupArn(targetGroupArn)
              .isNewServiceCreated(true)
              .serviceName(serviceName)
              .loadBalancer(ecsBlueGreenCreateServiceRequest.getEcsLoadBalancerConfig().getLoadBalancer())
              .listenerArn(ecsBlueGreenCreateServiceRequest.getEcsLoadBalancerConfig().getStageListenerArn())
              .listenerRuleArn(ecsBlueGreenCreateServiceRequest.getEcsLoadBalancerConfig().getStageListenerRuleArn())
              .build();
      EcsBlueGreenCreateServiceResponse ecsBlueGreenCreateServiceResponse =
          EcsBlueGreenCreateServiceResponse.builder()
              .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
              .ecsBlueGreenCreateServiceResult(ecsBlueGreenCreateServiceResult)
              .build();
      deployLogCallback.saveExecutionLog(color(format("%n Deployment Successful."), LogColor.Green, LogWeight.Bold),
          LogLevel.INFO, CommandExecutionStatus.SUCCESS);

      return ecsBlueGreenCreateServiceResponse;
    } catch (Exception e) {
      deployLogCallback.saveExecutionLog(color(format("%n Deployment Failed."), LogColor.Red, LogWeight.Bold),
          LogLevel.ERROR, CommandExecutionStatus.FAILURE);
      throw new EcsNGException(e);
    }
  }
}
