/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.interrupts.statusupdate;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.pms.contracts.execution.Status.INPUT_WAITING;

import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.executions.plan.PlanExecutionService;
import io.harness.engine.observers.NodeStatusUpdateHandler;
import io.harness.engine.observers.NodeUpdateInfo;
import io.harness.execution.NodeExecution;
import io.harness.pms.contracts.execution.Status;

import com.google.inject.Inject;
import java.util.EnumSet;

@OwnedBy(CDC)
public class InputWaitingStepStatusUpdate implements NodeStatusUpdateHandler {
  @Inject private NodeExecutionService nodeExecutionService;
  @Inject private PlanExecutionService planExecutionService;

  @Override
  public void handleNodeStatusUpdate(NodeUpdateInfo nodeStatusUpdateInfo) {
    NodeExecution nodeExecution = nodeStatusUpdateInfo.getNodeExecution();
    if (nodeExecution.getParentId() == null) {
      planExecutionService.updateCalculatedStatus(nodeStatusUpdateInfo.getPlanExecutionId());
      return;
    }
    // flowingChildren will always be empty currently. Will see later.
    long flowingChildrenCount =
        nodeExecutionService.findCountByParentIdAndStatusIn(nodeExecution.getParentId(), EnumSet.noneOf(Status.class));
    if (flowingChildrenCount == 0) {
      nodeExecutionService.updateStatusWithOps(
          nodeExecution.getParentId(), INPUT_WAITING, null, EnumSet.noneOf(Status.class));
    }
    planExecutionService.updateCalculatedStatus(nodeStatusUpdateInfo.getPlanExecutionId());
  }
}
