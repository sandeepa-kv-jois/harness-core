/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator.strategy;

import static io.harness.rule.OwnerRule.SAHIL;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.facilitators.FacilitatorObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.contracts.plan.Dependency;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.plan.creation.PlanCreatorUtils;
import io.harness.pms.sdk.core.PmsSdkCoreTestBase;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.matrix.StrategyConstants;
import io.harness.steps.matrix.StrategyMetadata;
import io.harness.steps.matrix.StrategyStep;
import io.harness.steps.matrix.StrategyStepParameters;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import com.google.protobuf.ByteString;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public class StrategyConfigPlanCreatorTest extends PmsSdkCoreTestBase {
  @Mock KryoSerializer kryoSerializer;
  @InjectMocks StrategyConfigPlanCreator strategyConfigPlanCreator;

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testCreatePlanForParentNode() throws IOException {
    MockitoAnnotations.initMocks(this);
    ClassLoader classLoader = this.getClass().getClassLoader();
    final URL testFile = classLoader.getResource("pipeline-with-strategy.yaml");
    assertThat(testFile).isNotNull();
    String pipelineYaml = Resources.toString(testFile, Charsets.UTF_8);
    String pipelineYamlWithUuid = YamlUtils.injectUuid(pipelineYaml);

    YamlField pipelineYamlField = YamlUtils.readTree(pipelineYamlWithUuid).getNode().getField("pipeline");
    assertThat(pipelineYamlField).isNotNull();
    YamlField stagesYamlField = pipelineYamlField.getNode().getField("stages");
    assertThat(stagesYamlField).isNotNull();
    List<YamlNode> stageYamlNodes = stagesYamlField.getNode().asArray();

    YamlField approvalStageYamlField = stageYamlNodes.get(0).getField("stage");

    String strategyNodeId = approvalStageYamlField.getNode().getField("strategy").getNode().getUuid();
    String childNodeId = "childNodeId";
    Map<String, ByteString> metadataMap = new HashMap<>();
    StrategyMetadata strategyMetadata = StrategyMetadata.builder()
                                            .childNodeId(childNodeId)
                                            .strategyNodeId(strategyNodeId)
                                            .adviserObtainments(new ArrayList<>())
                                            .build();
    metadataMap.put(StrategyConstants.STRATEGY_METADATA + strategyNodeId, ByteString.EMPTY);
    Mockito.when(kryoSerializer.asInflatedObject(Mockito.any())).thenReturn(strategyMetadata);
    PlanCreationContext context = PlanCreationContext.builder()
                                      .dependency(Dependency.newBuilder().putAllMetadata(metadataMap).build())
                                      .currentField(approvalStageYamlField.getNode().getField("strategy"))
                                      .build();
    StrategyConfig strategyConfig = StrategyConfig.builder().build();
    StepParameters stepParameters = StrategyStepParameters.builder()
                                        .childNodeId(childNodeId)
                                        .strategyConfig(strategyConfig)
                                        .strategyType(StrategyType.MATRIX)
                                        .build();

    PlanNode planNode =
        PlanNode.builder()
            .uuid(strategyNodeId)
            .identifier(null)
            .stepType(StrategyStep.STEP_TYPE)
            .group(StepOutcomeGroup.STRATEGY.name())
            .name(null)
            .stepParameters(stepParameters)
            .facilitatorObtainment(
                FacilitatorObtainment.newBuilder()
                    .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.CHILDREN).build())
                    .build())
            .skipExpressionChain(true)
            .adviserObtainments(new ArrayList<>())
            .build();
    assertThat(strategyConfigPlanCreator.createPlanForParentNode(context, strategyConfig, new ArrayList<>()))
        .isEqualTo(planNode);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testGetFieldClass() {
    assertThat(strategyConfigPlanCreator.getFieldClass()).isEqualTo(StrategyConfig.class);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testGetSupportedTypes() {
    assertThat(strategyConfigPlanCreator.getSupportedTypes())
        .isEqualTo(Collections.singletonMap(
            YAMLFieldNameConstants.STRATEGY, Collections.singleton(PlanCreatorUtils.ANY_TYPE)));
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testCreatePlanForChildrenNodes() {
    assertThat(strategyConfigPlanCreator.createPlanForChildrenNodes(
                   PlanCreationContext.builder().build(), StrategyConfig.builder().build()))
        .isEqualTo(new LinkedHashMap<>());
  }
}
