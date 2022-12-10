/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.plancreator.stages;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.data.structure.EmptyPredicate;
import io.harness.plancreator.stages.stage.StageElementConfig;
import io.harness.plancreator.steps.StepParameterCommonUtils;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.plancreator.steps.common.StageElementParameters.StageElementParametersBuilder;
import io.harness.plancreator.strategy.StrategyUtils;
import io.harness.pms.contracts.facilitators.FacilitatorObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.contracts.plan.GraphLayoutNode;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.execution.utils.SkipInfoUtils;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.creation.beans.GraphLayoutResponse;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.plan.creation.creators.ChildrenPlanCreator;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.serializer.KryoSerializer;
import io.harness.when.utils.RunInfoUtils;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.SneakyThrows;

@OwnedBy(PIPELINE)
@TargetModule(HarnessModule._882_PMS_SDK_CORE)
public abstract class GenericStagePlanCreator extends ChildrenPlanCreator<StageElementConfig> {
  @Inject private KryoSerializer kryoSerializer;

  public abstract Set<String> getSupportedStageTypes();

  public abstract StepType getStepType(StageElementConfig stageElementConfig);

  public abstract SpecParameters getSpecParameters(
      String childNodeId, PlanCreationContext ctx, StageElementConfig stageElementConfig);

  @Override
  public Class<StageElementConfig> getFieldClass() {
    return StageElementConfig.class;
  }

  @Override
  public Map<String, Set<String>> getSupportedTypes() {
    Set<String> stageTypes = getSupportedStageTypes();
    if (EmptyPredicate.isEmpty(stageTypes)) {
      return Collections.emptyMap();
    }
    return Collections.singletonMap(YAMLFieldNameConstants.STAGE, stageTypes);
  }

  @SneakyThrows
  @Override
  public PlanNode createPlanForParentNode(
      PlanCreationContext ctx, StageElementConfig stageElementConfig, List<String> childrenNodeIds) {
    stageElementConfig.setIdentifier(
        StrategyUtils.getIdentifierWithExpression(ctx, stageElementConfig.getIdentifier()));
    stageElementConfig.setName(StrategyUtils.getIdentifierWithExpression(ctx, stageElementConfig.getName()));

    StageElementParametersBuilder stageParameters = StepParameterCommonUtils.getStageParameters(stageElementConfig);
    YamlField specField =
        Preconditions.checkNotNull(ctx.getCurrentField().getNode().getField(YAMLFieldNameConstants.SPEC));
    stageParameters.specConfig(getSpecParameters(specField.getNode().getUuid(), ctx, stageElementConfig));
    return PlanNode.builder()
        .uuid(StrategyUtils.getSwappedPlanNodeId(ctx, stageElementConfig.getUuid()))
        .name(stageElementConfig.getName())
        .identifier(stageElementConfig.getIdentifier())
        .group(StepOutcomeGroup.STAGE.name())
        .stepParameters(stageParameters.build())
        .stepType(getStepType(stageElementConfig))
        .skipCondition(SkipInfoUtils.getSkipCondition(stageElementConfig.getSkipCondition()))
        .whenCondition(RunInfoUtils.getRunCondition(stageElementConfig.getWhen()))
        .facilitatorObtainment(
            FacilitatorObtainment.newBuilder()
                .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.CHILD).build())
                .build())
        .adviserObtainments(StrategyUtils.getAdviserObtainments(ctx.getCurrentField(), kryoSerializer, true))
        .build();
  }

  /**
   * Adds a strategy node as a dependency of the stage if present.
   * Please note that strategy uses uuid of the stage node because the stage is using the uuid of strategy field as we
   * want to wrap stage around strategy.
   *
   * @param ctx
   * @param field
   * @param metadataMap
   */
  protected void addStrategyFieldDependencyIfPresent(PlanCreationContext ctx, StageElementConfig field,
      LinkedHashMap<String, PlanCreationResponse> planCreationResponseMap, Map<String, ByteString> metadataMap) {
    StrategyUtils.addStrategyFieldDependencyIfPresent(kryoSerializer, ctx, field.getUuid(), field.getIdentifier(),
        field.getName(), planCreationResponseMap, metadataMap,
        StrategyUtils.getAdviserObtainments(ctx.getCurrentField(), kryoSerializer, false));
  }

  @Override
  public GraphLayoutResponse getLayoutNodeInfo(PlanCreationContext context, StageElementConfig config) {
    Map<String, GraphLayoutNode> stageYamlFieldMap = new LinkedHashMap<>();
    YamlField stageYamlField = context.getCurrentField();
    if (StrategyUtils.isWrappedUnderStrategy(context.getCurrentField())) {
      stageYamlFieldMap = StrategyUtils.modifyStageLayoutNodeGraph(stageYamlField);
    }
    return GraphLayoutResponse.builder().layoutNodes(stageYamlFieldMap).build();
  }
}
