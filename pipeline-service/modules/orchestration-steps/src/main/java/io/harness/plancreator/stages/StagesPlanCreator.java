/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.plancreator.stages;

import io.harness.plancreator.NGCommonUtilPlanCreationConstants;
import io.harness.pms.contracts.facilitators.FacilitatorObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.contracts.plan.EdgeLayoutList;
import io.harness.pms.contracts.plan.GraphLayoutNode;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.plan.creation.PlanCreatorUtils;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.creation.beans.GraphLayoutResponse;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.plan.creation.creators.ChildrenPlanCreator;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.yaml.DependenciesUtils;
import io.harness.pms.yaml.PipelineVersion;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.StagesStep;
import io.harness.steps.common.NGSectionStepParameters;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@NoArgsConstructor(onConstructor = @__({ @Inject }))
@AllArgsConstructor(onConstructor = @__({ @Inject }))
public class StagesPlanCreator extends ChildrenPlanCreator<StagesConfig> {
  @Inject KryoSerializer kryoSerializer;

  @Override
  public LinkedHashMap<String, PlanCreationResponse> createPlanForChildrenNodes(
      PlanCreationContext ctx, StagesConfig config) {
    LinkedHashMap<String, PlanCreationResponse> responseMap = new LinkedHashMap<>();
    List<YamlField> stageYamlFields = getStageYamlFields(ctx);
    for (YamlField stageYamlField : stageYamlFields) {
      Map<String, YamlField> stageYamlFieldMap = new HashMap<>();
      stageYamlFieldMap.put(stageYamlField.getNode().getUuid(), stageYamlField);
      responseMap.put(stageYamlField.getNode().getUuid(),
          PlanCreationResponse.builder()
              .dependencies(DependenciesUtils.toDependenciesProto(stageYamlFieldMap))
              .build());
      PlanCreationResponse planForRollbackStage =
          RollbackStagePlanCreator.createPlanForRollbackStage(stageYamlField, kryoSerializer);
      responseMap.put(stageYamlField.getNode().getUuid() + NGCommonUtilPlanCreationConstants.ROLLBACK_STAGE_UUID_SUFFIX,
          planForRollbackStage);
    }
    return responseMap;
  }

  @Override
  public GraphLayoutResponse getLayoutNodeInfo(PlanCreationContext ctx, StagesConfig config) {
    Map<String, GraphLayoutNode> stageYamlFieldMap = new LinkedHashMap<>();
    List<YamlField> stagesYamlField = getStageYamlFields(ctx);
    List<EdgeLayoutList> edgeLayoutLists = new ArrayList<>();
    for (YamlField stageYamlField : stagesYamlField) {
      EdgeLayoutList.Builder stageEdgesBuilder = EdgeLayoutList.newBuilder();
      stageEdgesBuilder.addNextIds(stageYamlField.getNode().getUuid());
      edgeLayoutLists.add(stageEdgesBuilder.build());
    }
    for (int i = 0; i < edgeLayoutLists.size(); i++) {
      YamlField stageYamlField = stagesYamlField.get(i);
      if (stageYamlField.getName().equals("parallel")) {
        continue;
      }
      stageYamlFieldMap.put(stageYamlField.getNode().getUuid(),
          GraphLayoutNode.newBuilder()
              .setNodeUUID(stageYamlField.getNode().getUuid())
              .setNodeType(stageYamlField.getNode().getType())
              .setName(stageYamlField.getNode().getName())
              .setNodeGroup(StepOutcomeGroup.STAGE.name())
              .setNodeIdentifier(stageYamlField.getNode().getIdentifier())
              .setEdgeLayoutList(
                  i + 1 < edgeLayoutLists.size() ? edgeLayoutLists.get(i + 1) : EdgeLayoutList.newBuilder().build())
              .build());

      // create node for corresponding rollback Stage
      stageYamlFieldMap.put(
          stageYamlField.getNode().getUuid() + NGCommonUtilPlanCreationConstants.ROLLBACK_STAGE_UUID_SUFFIX,
          GraphLayoutNode.newBuilder()
              .setNodeUUID(
                  stageYamlField.getNode().getUuid() + NGCommonUtilPlanCreationConstants.ROLLBACK_STAGE_UUID_SUFFIX)
              .setNodeType(stageYamlField.getNode().getType())
              .setName(
                  stageYamlField.getNode().getName() + " " + NGCommonUtilPlanCreationConstants.ROLLBACK_STAGE_NODE_NAME)
              .setNodeGroup(StepOutcomeGroup.STAGE.name())
              .setNodeIdentifier(
                  stageYamlField.getNode().getUuid() + NGCommonUtilPlanCreationConstants.ROLLBACK_STAGE_UUID_SUFFIX)
              .setEdgeLayoutList(i == 0 ? EdgeLayoutList.newBuilder().build()
                                        : EdgeLayoutList.newBuilder()
                                              .addNextIds(stagesYamlField.get(i - 1).getNode().getUuid()
                                                  + NGCommonUtilPlanCreationConstants.ROLLBACK_STAGE_UUID_SUFFIX)
                                              .build())
              .setIsRollbackStageNode(true)
              .build());
    }
    return GraphLayoutResponse.builder()
        .layoutNodes(stageYamlFieldMap)
        .startingNodeId(stagesYamlField.get(0).getNode().getUuid())
        .build();
  }

  @Override
  public PlanNode createPlanForParentNode(PlanCreationContext ctx, StagesConfig config, List<String> childrenNodeIds) {
    StepParameters stepParameters =
        NGSectionStepParameters.builder().childNodeId(childrenNodeIds.get(0)).logMessage("Stages").build();
    return PlanNode.builder()
        .uuid(ctx.getCurrentField().getNode().getUuid())
        .identifier(YAMLFieldNameConstants.STAGES)
        .stepType(StagesStep.STEP_TYPE)
        .group(StepOutcomeGroup.STAGES.name())
        .name(YAMLFieldNameConstants.STAGES)
        .stepParameters(stepParameters)
        .facilitatorObtainment(
            FacilitatorObtainment.newBuilder()
                .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.CHILD).build())
                .build())
        .skipExpressionChain(false)
        .build();
  }

  @Override
  public Class<StagesConfig> getFieldClass() {
    return StagesConfig.class;
  }

  @Override
  public Map<String, Set<String>> getSupportedTypes() {
    return Collections.singletonMap("stages", Collections.singleton(PlanCreatorUtils.ANY_TYPE));
  }

  @Override
  public Set<String> getSupportedYamlVersions() {
    return Set.of(PipelineVersion.V0);
  }

  private List<YamlField> getStageYamlFields(PlanCreationContext planCreationContext) {
    List<YamlNode> yamlNodes =
        Optional.of(planCreationContext.getCurrentField().getNode().asArray()).orElse(Collections.emptyList());
    List<YamlField> stageFields = new LinkedList<>();

    yamlNodes.forEach(yamlNode -> {
      YamlField stageField = yamlNode.getField("stage");
      YamlField parallelStageField = yamlNode.getField("parallel");
      if (stageField != null) {
        stageFields.add(stageField);
      } else if (parallelStageField != null) {
        stageFields.add(parallelStageField);
      }
    });
    return stageFields;
  }
}
