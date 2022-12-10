/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.service;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.cdng.artifact.steps.ArtifactsStepV2;
import io.harness.cdng.azure.webapp.AzureServiceSettingsStep;
import io.harness.cdng.configfile.steps.ConfigFilesStepV2;
import io.harness.cdng.creator.plan.PlanCreatorConstants;
import io.harness.cdng.creator.plan.stage.DeploymentStageConfig;
import io.harness.cdng.creator.plan.stage.DeploymentStageNode;
import io.harness.cdng.elastigroup.ElastigroupServiceSettingsStep;
import io.harness.cdng.envgroup.yaml.EnvironmentGroupYaml;
import io.harness.cdng.environment.yaml.EnvironmentYamlV2;
import io.harness.cdng.environment.yaml.EnvironmentsYaml;
import io.harness.cdng.manifest.steps.ManifestsStepV2;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.cdng.service.beans.ServiceUseFromStageV2;
import io.harness.cdng.service.beans.ServiceYamlV2;
import io.harness.cdng.service.steps.ServiceStepV3;
import io.harness.cdng.service.steps.ServiceStepV3Parameters;
import io.harness.cdng.steps.EmptyStepParameters;
import io.harness.cdng.visitor.YamlTypes;
import io.harness.data.structure.UUIDGenerator;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.contracts.advisers.AdviserObtainment;
import io.harness.pms.contracts.advisers.AdviserType;
import io.harness.pms.contracts.facilitators.FacilitatorObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.contracts.plan.ExpressionMode;
import io.harness.pms.contracts.steps.SkipType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.plan.creation.PlanCreatorUtils;
import io.harness.pms.sdk.core.adviser.OrchestrationAdviserTypes;
import io.harness.pms.sdk.core.adviser.success.OnSuccessAdviserParameters;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.serializer.KryoSerializer;

import com.google.protobuf.ByteString;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import lombok.experimental.UtilityClass;

@UtilityClass
public class ServiceAllInOnePlanCreatorUtils {
  /**
   * Add the following plan nodes
   * ServiceStepV3 ( 3 children )
   *      artifactsV2
   *      manifests
   *      config files
   *      azure settings
   */
  public LinkedHashMap<String, PlanCreationResponse> addServiceNode(YamlField specField, KryoSerializer kryoSerializer,
      ServiceYamlV2 serviceYamlV2, EnvironmentYamlV2 environmentYamlV2, String serviceNodeId, String nextNodeId,
      ServiceDefinitionType serviceType) {
    final ServiceYamlV2 finalServiceYaml = useFromStage(serviceYamlV2)
        ? useServiceYamlFromStage(serviceYamlV2.getUseFromStage(), specField)
        : serviceYamlV2;

    final LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap = new LinkedHashMap<>();

    // add nodes for artifacts/manifests/files
    final List<String> childrenNodeIds = addChildrenNodes(planCreationResponseMap, serviceType);
    final ServiceStepV3Parameters stepParameters =
        ServiceStepV3Parameters.builder()
            .serviceRef(finalServiceYaml.getServiceRef())
            .inputs(finalServiceYaml.getServiceInputs())
            .envRef(environmentYamlV2.getEnvironmentRef())
            .envInputs(environmentYamlV2.getEnvironmentInputs())
            .childrenNodeIds(childrenNodeIds)
            .serviceOverrideInputs(environmentYamlV2.getServiceOverrideInputs())
            .deploymentType(serviceType)
            .build();

    return createPlanNode(kryoSerializer, serviceNodeId, nextNodeId, planCreationResponseMap, stepParameters);
  }

  public LinkedHashMap<String, PlanCreationResponse> addServiceNodeForGitOpsEnvGroup(YamlField specField,
      KryoSerializer kryoSerializer, ServiceYamlV2 serviceYamlV2, EnvironmentGroupYaml environmentGroupYaml,
      String serviceNodeId, String nextNodeId, ServiceDefinitionType serviceType) {
    final ServiceYamlV2 finalServiceYaml = useFromStage(serviceYamlV2)
        ? useServiceYamlFromStage(serviceYamlV2.getUseFromStage(), specField)
        : serviceYamlV2;

    final LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap = new LinkedHashMap<>();

    // add nodes for artifacts/manifests/files
    final List<String> childrenNodeIds = addChildrenNodes(planCreationResponseMap, serviceType);
    final ServiceStepV3Parameters stepParameters =
        ServiceStepV3Parameters.builder()
            .serviceRef(finalServiceYaml.getServiceRef())
            .inputs(finalServiceYaml.getServiceInputs())
            .envGroupRef(environmentGroupYaml.getEnvGroupRef())
            .envRefs(environmentGroupYaml.getEnvironments()
                         .getValue()
                         .stream()
                         .map(e -> e.getEnvironmentRef())
                         .collect(Collectors.toList()))
            .envToEnvInputs(getMergedEnvironmentRuntimeInputs(environmentGroupYaml.getEnvironments().getValue()))
            .envToSvcOverrideInputs(getMergedServiceOverrideInputs(environmentGroupYaml.getEnvironments().getValue()))
            .childrenNodeIds(childrenNodeIds)
            .deploymentType(serviceType)
            .gitOpsMultiSvcEnvEnabled(ParameterField.<Boolean>builder().value(true).build())
            .build();

    return createPlanNode(kryoSerializer, serviceNodeId, nextNodeId, planCreationResponseMap, stepParameters);
  }

  public LinkedHashMap<String, PlanCreationResponse> addServiceNodeForGitOpsEnvironments(YamlField specField,
      KryoSerializer kryoSerializer, ServiceYamlV2 serviceYamlV2, EnvironmentsYaml environmentsYaml,
      String serviceNodeId, String nextNodeId, ServiceDefinitionType serviceType) {
    final ServiceYamlV2 finalServiceYaml = useFromStage(serviceYamlV2)
        ? useServiceYamlFromStage(serviceYamlV2.getUseFromStage(), specField)
        : serviceYamlV2;

    final LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap = new LinkedHashMap<>();

    // add nodes for artifacts/manifests/files
    final List<String> childrenNodeIds = addChildrenNodes(planCreationResponseMap, serviceType);
    final ServiceStepV3Parameters stepParameters =
        ServiceStepV3Parameters.builder()
            .serviceRef(finalServiceYaml.getServiceRef())
            .envRefs(environmentsYaml.getValues()
                         .getValue()
                         .stream()
                         .map(e -> e.getEnvironmentRef())
                         .collect(Collectors.toList()))
            .envToEnvInputs(getMergedEnvironmentRuntimeInputs(environmentsYaml.getValues().getValue()))
            .envToSvcOverrideInputs(getMergedServiceOverrideInputs(environmentsYaml.getValues().getValue()))
            .inputs(finalServiceYaml.getServiceInputs())
            .childrenNodeIds(childrenNodeIds)
            .deploymentType(serviceType)
            .gitOpsMultiSvcEnvEnabled(ParameterField.<Boolean>builder().value(true).build())
            .build();

    return createPlanNode(kryoSerializer, serviceNodeId, nextNodeId, planCreationResponseMap, stepParameters);
  }

  private static LinkedHashMap<String, PlanCreationResponse> createPlanNode(KryoSerializer kryoSerializer,
      String serviceNodeId, String nextNodeId, LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap,
      ServiceStepV3Parameters stepParameters) {
    final PlanNode node =
        PlanNode.builder()
            .uuid(serviceNodeId)
            .stepType(ServiceStepV3.STEP_TYPE)
            .expressionMode(ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED)
            .name(PlanCreatorConstants.SERVICE_NODE_NAME)
            .identifier(YamlTypes.SERVICE_ENTITY)
            .stepParameters(stepParameters)
            .facilitatorObtainment(
                FacilitatorObtainment.newBuilder()
                    .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.CHILDREN).build())
                    .build())
            .adviserObtainment(
                AdviserObtainment.newBuilder()
                    .setType(AdviserType.newBuilder().setType(OrchestrationAdviserTypes.ON_SUCCESS.name()).build())
                    .setParameters(ByteString.copyFrom(
                        kryoSerializer.asBytes(OnSuccessAdviserParameters.builder().nextNodeId(nextNodeId).build())))
                    .build())
            .skipExpressionChain(true)
            .build();
    planCreationResponseMap.put(node.getUuid(), PlanCreationResponse.builder().planNode(node).build());

    return planCreationResponseMap;
  }

  private boolean useFromStage(ServiceYamlV2 serviceYamlV2) {
    return serviceYamlV2.getUseFromStage() != null && isNotEmpty(serviceYamlV2.getUseFromStage().getStage());
  }

  private List<String> addChildrenNodes(
      LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap, ServiceDefinitionType serviceType) {
    final List<String> nodeIds = new ArrayList<>();

    // Add artifacts node
    final PlanNode artifactsNode =
        PlanNode.builder()
            .uuid("artifacts-" + UUIDGenerator.generateUuid())
            .stepType(ArtifactsStepV2.STEP_TYPE)
            .name(PlanCreatorConstants.ARTIFACTS_NODE_NAME)
            .identifier(YamlTypes.ARTIFACT_LIST_CONFIG)
            .stepParameters(new EmptyStepParameters())
            .facilitatorObtainment(
                FacilitatorObtainment.newBuilder()
                    .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.ASYNC).build())
                    .build())
            .skipExpressionChain(true)
            .skipGraphType(SkipType.SKIP_TREE)
            .build();
    nodeIds.add(artifactsNode.getUuid());
    planCreationResponseMap.put(
        artifactsNode.getUuid(), PlanCreationResponse.builder().planNode(artifactsNode).build());

    // Add manifests node
    final PlanNode manifestsNode =
        PlanNode.builder()
            .uuid("manifests-" + UUIDGenerator.generateUuid())
            .stepType(ManifestsStepV2.STEP_TYPE)
            .name(PlanCreatorConstants.MANIFESTS_NODE_NAME)
            .identifier(YamlTypes.MANIFEST_LIST_CONFIG)
            .stepParameters(new EmptyStepParameters())
            .facilitatorObtainment(
                FacilitatorObtainment.newBuilder()
                    .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.SYNC).build())
                    .build())
            .skipExpressionChain(true)
            .skipGraphType(SkipType.SKIP_TREE)
            .build();
    nodeIds.add(manifestsNode.getUuid());
    planCreationResponseMap.put(
        manifestsNode.getUuid(), PlanCreationResponse.builder().planNode(manifestsNode).build());

    // Add configFiles node
    final PlanNode configFilesNode =
        PlanNode.builder()
            .uuid("configFiles-" + UUIDGenerator.generateUuid())
            .stepType(ConfigFilesStepV2.STEP_TYPE)
            .name(PlanCreatorConstants.CONFIG_FILES_NODE_NAME)
            .identifier(YamlTypes.CONFIG_FILES)
            .stepParameters(new EmptyStepParameters())
            .facilitatorObtainment(
                FacilitatorObtainment.newBuilder()
                    .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.SYNC).build())
                    .build())
            .skipExpressionChain(true)
            .skipGraphType(SkipType.SKIP_TREE)
            .build();
    nodeIds.add(configFilesNode.getUuid());
    planCreationResponseMap.put(
        configFilesNode.getUuid(), PlanCreationResponse.builder().planNode(configFilesNode).build());

    // Add Azure settings node
    if (serviceType == ServiceDefinitionType.AZURE_WEBAPP) {
      PlanNode azureSettingsNode =
          PlanNode.builder()
              .uuid("azure-settings-" + UUIDGenerator.generateUuid())
              .stepType(AzureServiceSettingsStep.STEP_TYPE)
              .name(PlanCreatorConstants.CONNECTION_STRINGS)
              .identifier(YamlTypes.AZURE_SERVICE_SETTINGS_STEP)
              .stepParameters(new EmptyStepParameters())
              .facilitatorObtainment(
                  FacilitatorObtainment.newBuilder()
                      .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.SYNC).build())
                      .build())
              .skipExpressionChain(true)
              .build();
      nodeIds.add(azureSettingsNode.getUuid());
      planCreationResponseMap.put(
          azureSettingsNode.getUuid(), PlanCreationResponse.builder().planNode(azureSettingsNode).build());
    }

    // Add Elastigroup settings node
    if (serviceType == ServiceDefinitionType.ELASTIGROUP) {
      PlanNode elastigroupSettingsNode =
          PlanNode.builder()
              .uuid("elastigroup-settings-" + UUIDGenerator.generateUuid())
              .stepType(ElastigroupServiceSettingsStep.STEP_TYPE)
              .name(PlanCreatorConstants.ELASTIGROUP_SERVICE_SETTINGS_NODE)
              .identifier(YamlTypes.ELASTIGROUP_SERVICE_SETTINGS_STEP)
              .stepParameters(new EmptyStepParameters())
              .facilitatorObtainment(
                  FacilitatorObtainment.newBuilder()
                      .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.SYNC).build())
                      .build())
              .skipExpressionChain(true)
              .build();
      nodeIds.add(elastigroupSettingsNode.getUuid());
      planCreationResponseMap.put(
          elastigroupSettingsNode.getUuid(), PlanCreationResponse.builder().planNode(elastigroupSettingsNode).build());
    }
    return nodeIds;
  }

  private Map<String, ParameterField<Map<String, Object>>> getMergedEnvironmentRuntimeInputs(
      List<EnvironmentYamlV2> envYamlV2List) {
    Map<String, ParameterField<Map<String, Object>>> mergedEnvironmentInputs = new HashMap<>();
    for (EnvironmentYamlV2 environmentYamlV2 : envYamlV2List) {
      ParameterField<Map<String, Object>> environmentInputs = environmentYamlV2.getEnvironmentInputs();
      if (environmentInputs != null) {
        mergedEnvironmentInputs.put(environmentYamlV2.getEnvironmentRef().getValue(), environmentInputs);
      }
    }
    return mergedEnvironmentInputs;
  }

  private Map<String, ParameterField<Map<String, Object>>> getMergedServiceOverrideInputs(
      List<EnvironmentYamlV2> envYamlV2List) {
    Map<String, ParameterField<Map<String, Object>>> mergedServiceOverrideInputs = new HashMap<>();
    for (EnvironmentYamlV2 environmentYamlV2 : envYamlV2List) {
      ParameterField<Map<String, Object>> serviceOverrideInputs = environmentYamlV2.getServiceOverrideInputs();
      if (serviceOverrideInputs != null) {
        mergedServiceOverrideInputs.put(environmentYamlV2.getEnvironmentRef().getValue(), serviceOverrideInputs);
      }
    }
    return mergedServiceOverrideInputs;
  }

  private ServiceYamlV2 useServiceYamlFromStage(@NotNull ServiceUseFromStageV2 useFromStage, YamlField specField) {
    final YamlField serviceField = specField.getNode().getField(YamlTypes.SERVICE_ENTITY);
    String stage = useFromStage.getStage();
    if (stage == null) {
      throw new InvalidRequestException("Stage identifier not present in useFromStage");
    }

    try {
      DeploymentStageNode stageElementConfig = YamlUtils.read(
          PlanCreatorUtils.getStageConfig(serviceField, stage).getNode().toString(), DeploymentStageNode.class);
      DeploymentStageConfig deploymentStage = stageElementConfig.getDeploymentStageConfig();
      if (deploymentStage != null) {
        if (deploymentStage.getService() != null && useFromStage(deploymentStage.getService())) {
          throw new InvalidArgumentsException(
              "Invalid identifier given in useFromStage. Cannot reference a stage which also has useFromStage parameter");
        }
        return deploymentStage.getService();
      } else {
        throw new InvalidArgumentsException("Stage identifier given in useFromStage doesn't exist");
      }
    } catch (IOException ex) {
      throw new InvalidRequestException("Cannot parse stage: " + stage);
    }
  }
}
