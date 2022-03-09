/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.interrupts.helpers;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.PRASHANT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.OrchestrationTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.OrchestrationEngine;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.executions.plan.PlanService;
import io.harness.engine.utils.PmsLevelUtils;
import io.harness.execution.NodeExecution;
import io.harness.plan.PlanNode;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.ExecutableResponse;
import io.harness.pms.contracts.execution.ExecutionMode;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.TaskExecutableResponse;
import io.harness.pms.contracts.execution.tasks.TaskCategory;
import io.harness.pms.contracts.interrupts.InterruptConfig;
import io.harness.pms.contracts.interrupts.InterruptType;
import io.harness.pms.contracts.interrupts.IssuedBy;
import io.harness.pms.contracts.interrupts.ManualIssuer;
import io.harness.pms.contracts.steps.SkipType;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class RetryHelperTest extends OrchestrationTestBase {
  @Mock OrchestrationEngine engine;
  @Mock NodeExecutionService nodeExecutionService;
  @Mock PlanService planService;
  @Mock ExecutorService executorService;
  @Inject @InjectMocks RetryHelper retryHelper;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @After
  public void verifyMocks() {
    Mockito.verifyNoMoreInteractions(engine);
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestRetryNodeExecution() {
    String nodeExecutionId = generateUuid();
    String nodeId = generateUuid();
    String planId = generateUuid();
    String interruptId = generateUuid();
    PlanNode planNode = PlanNode.builder()
                            .uuid(nodeId)
                            .identifier("DUMMY")
                            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
                            .serviceName("CD")
                            .build();
    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(generateUuid())
                            .setPlanId(planId)
                            .addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecutionId, planNode))
                            .build();

    NodeExecution nodeExecution =
        NodeExecution.builder()
            .uuid(nodeExecutionId)
            .ambiance(ambiance)
            .status(Status.FAILED)
            .mode(ExecutionMode.TASK)
            .nodeId(nodeId)
            .identifier("DUMMY")
            .stepType(StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build())
            .module("CD")
            .skipGraphType(SkipType.NOOP)
            .stageFqn(generateUuid())
            .group("STEP")
            .executableResponse(ExecutableResponse.newBuilder()
                                    .setTask(TaskExecutableResponse.newBuilder()
                                                 .setTaskId(generateUuid())
                                                 .setTaskCategory(TaskCategory.UNKNOWN_CATEGORY)
                                                 .build())
                                    .build())
            .interruptHistories(new ArrayList<>())
            .startTs(System.currentTimeMillis())
            .build();

    InterruptConfig interruptConfig =
        InterruptConfig.newBuilder()
            .setIssuedBy(IssuedBy.newBuilder()
                             .setManualIssuer(ManualIssuer.newBuilder().setIdentifier("admin@admin").build())
                             .build())
            .build();

    when(nodeExecutionService.get(nodeExecutionId)).thenReturn(nodeExecution);
    when(nodeExecutionService.save(any())).thenReturn(nodeExecution);
    when(nodeExecutionService.updateRelationShipsForRetryNode(any(), any())).thenReturn(true);
    when(nodeExecutionService.markRetried(any())).thenReturn(true);
    when(planService.fetchNode(planId, nodeId)).thenReturn(planNode);

    retryHelper.retryNodeExecution(nodeExecution.getUuid(), interruptId, interruptConfig);
    verify(executorService).submit(any(Runnable.class));
  }
  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestCloneForRetry() {
    String nodeId = generateUuid();
    String nodeExecutionId = generateUuid();

    PlanNode planNode = PlanNode.builder()
                            .uuid(nodeId)
                            .identifier("DUMMY")
                            .stepType(StepType.newBuilder().setType("DUMMY").build())
                            .serviceName("DUMMY")
                            .build();
    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(generateUuid())
                            .addLevels(PmsLevelUtils.buildLevelFromNode(nodeExecutionId, planNode))
                            .build();

    NodeExecution nodeExecution =
        NodeExecution.builder()
            .uuid(nodeExecutionId)
            .ambiance(ambiance)
            .nodeId(nodeId)
            .identifier("DUMMY")
            .name("name")
            .skipGraphType(SkipType.NOOP)
            .stageFqn(generateUuid())
            .group("STEP")
            .stepType(StepType.newBuilder().setType("DUMMY").build())
            .module("DUMMY")
            .status(Status.FAILED)
            .mode(ExecutionMode.TASK)
            .executableResponse(ExecutableResponse.newBuilder()
                                    .setTask(TaskExecutableResponse.newBuilder()
                                                 .setTaskId(generateUuid())
                                                 .setTaskCategory(TaskCategory.UNKNOWN_CATEGORY)
                                                 .build())
                                    .build())
            .interruptHistories(new ArrayList<>())
            .startTs(System.currentTimeMillis())
            .startTs(System.currentTimeMillis())
            .build();
    String newNodeUuid = generateUuid();
    InterruptConfig interruptConfig =
        InterruptConfig.newBuilder()
            .setIssuedBy(IssuedBy.newBuilder()
                             .setManualIssuer(ManualIssuer.newBuilder().setIdentifier("admin@admin").build())
                             .build())
            .build();
    NodeExecution clonedNodeExecution = retryHelper.cloneForRetry(
        nodeExecution, newNodeUuid, nodeExecution.getAmbiance(), interruptConfig, generateUuid());

    assertThat(clonedNodeExecution).isNotNull();
    assertThat(clonedNodeExecution.getUuid()).isEqualTo(newNodeUuid);
    assertThat(clonedNodeExecution.getRetryIds()).containsExactly(nodeExecution.getUuid());
    assertThat(clonedNodeExecution.getInterruptHistories()).hasSize(1);
    assertThat(clonedNodeExecution.getInterruptHistories().get(0).getInterruptType()).isEqualTo(InterruptType.RETRY);
    assertThat(clonedNodeExecution.getStartTs()).isEqualTo(AmbianceUtils.getCurrentLevelStartTs(ambiance));
    assertThat(clonedNodeExecution.getEndTs()).isNull();
    assertThat(clonedNodeExecution.getStatus()).isEqualTo(Status.QUEUED);
    assertThat(clonedNodeExecution.getName()).isEqualTo("name");
    assertThat(clonedNodeExecution.getIdentifier()).isEqualTo("DUMMY");
  }
}
