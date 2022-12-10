/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.interrupts.handlers;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.interrupts.Interrupt.State.PROCESSED_SUCCESSFULLY;
import static io.harness.interrupts.Interrupt.State.PROCESSED_UNSUCCESSFULLY;
import static io.harness.pms.contracts.execution.Status.DISCONTINUING;

import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.interrupts.InterruptService;
import io.harness.execution.ExecutionModeUtils;
import io.harness.execution.NodeExecution;
import io.harness.interrupts.Interrupt;
import io.harness.pms.execution.utils.NodeProjectionUtils;
import io.harness.pms.execution.utils.StatusUtils;

import com.google.inject.Inject;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

/**
 * This serves as base class for the interrupts that are registered with parent but they recursively need to traverse
 * through  the tree and take appropriate action on the leaf node like ABORT_ALL, EXPIRE_ALL
 * <p>
 * TODO: Evaluate this an extract PAUSE_ALL and RESUME_ALL here too
 */

@OwnedBy(PIPELINE)
@Slf4j
public abstract class InterruptPropagatorHandler {
  private static int MAX_NODES_BATCH_SIZE = 1000;

  @Inject private InterruptService interruptService;
  @Inject private NodeExecutionService nodeExecutionService;

  public Interrupt handleAllNodes(Interrupt interrupt) {
    Interrupt updatedInterrupt = interruptService.markProcessing(interrupt.getUuid());
    // Marking all finalizable leaf nodes as DISCONTINUING
    long updatedCount = nodeExecutionService.markAllLeavesAndQueuedNodesDiscontinuing(
        interrupt.getPlanExecutionId(), StatusUtils.abortAndExpireStatuses());
    return handleDiscontinuingNodes(updatedInterrupt, updatedCount);
  }

  public Interrupt handleChildNodes(Interrupt interrupt, String nodeExecutionId) {
    Interrupt updatedInterrupt = interruptService.markProcessing(interrupt.getUuid());
    // Find all the nodeExecutions for this plan
    int currentPage = 0;
    int totalPages = 0;

    List<NodeExecution> allExecutions = new LinkedList<>();
    do {
      Page<NodeExecution> paginatedNodeExecutions =
          nodeExecutionService.fetchNodeExecutionsWithoutOldRetriesAndStatusIn(interrupt.getPlanExecutionId(),
              StatusUtils.abortAndExpireStatuses(), NodeProjectionUtils.fieldsForInterruptPropagatorHandler,
              PageRequest.of(currentPage, MAX_NODES_BATCH_SIZE));
      if (paginatedNodeExecutions == null || paginatedNodeExecutions.getTotalElements() == 0) {
        break;
      }
      totalPages = paginatedNodeExecutions.getTotalPages();
      allExecutions.addAll(new LinkedList<>(paginatedNodeExecutions.getContent()));
      currentPage++;
    } while (currentPage < totalPages);

    // Filter all the nodes that are in queued state
    List<NodeExecution> finalList =
        allExecutions.stream()
            .filter(nodeExecution -> StatusUtils.abortingStatuses().contains(nodeExecution.getStatus()))
            .collect(Collectors.toList());
    // Extract all the running leaf nodes with the parent id as nodeExecutionId passed in as param
    nodeExecutionService.extractChildExecutions(nodeExecutionId, true, finalList, allExecutions);

    List<String> targetIds = finalList.stream()
                                 .filter(ne
                                     -> ExecutionModeUtils.isLeafMode(ne.getMode())
                                         || StatusUtils.abortingStatuses().contains(ne.getStatus()))
                                 .map(NodeExecution::getUuid)
                                 .collect(Collectors.toList());

    long updatedCount = nodeExecutionService.markLeavesDiscontinuing(interrupt.getPlanExecutionId(), targetIds);
    return handleDiscontinuingNodes(updatedInterrupt, updatedCount);
  }

  protected Interrupt handleDiscontinuingNodes(Interrupt updatedInterrupt, long updatedCount) {
    if (updatedCount < 0) {
      // IF count is less than 0 then the update didn't go through
      return interruptService.markProcessed(updatedInterrupt.getUuid(), PROCESSED_UNSUCCESSFULLY);
    } else if (updatedCount == 0) {
      // If count is 0 that means no running leaf node and hence nothing to do
      return updatedInterrupt;
    } else {
      int currentPage = 0;
      int totalPages = 0;

      List<NodeExecution> discontinuingNodeExecutions = new LinkedList<>();
      do {
        Page<NodeExecution> paginatedNodeExecutions =
            nodeExecutionService.fetchNodeExecutionsByStatus(updatedInterrupt.getPlanExecutionId(), DISCONTINUING,
                NodeProjectionUtils.fieldsForDiscontinuingNodes, PageRequest.of(currentPage, MAX_NODES_BATCH_SIZE));
        if (paginatedNodeExecutions == null || paginatedNodeExecutions.getTotalElements() == 0) {
          break;
        }
        totalPages = paginatedNodeExecutions.getTotalPages();
        discontinuingNodeExecutions.addAll(new LinkedList<>(paginatedNodeExecutions.getContent()));
        currentPage++;
      } while (currentPage < totalPages);

      if (isEmpty(discontinuingNodeExecutions)) {
        log.warn(updatedInterrupt.getType()
                + " Interrupt being ignored as no running instance found for planExecutionId: {}",
            updatedInterrupt.getUuid());
        return interruptService.markProcessed(updatedInterrupt.getUuid(), PROCESSED_SUCCESSFULLY);
      }
      return processDiscontinuedInstances(updatedInterrupt, discontinuingNodeExecutions);
    }
  }

  protected Interrupt processDiscontinuedInstances(
      Interrupt updatedInterrupt, List<NodeExecution> discontinuingNodeExecutions) {
    try {
      for (NodeExecution discontinuingNodeExecution : discontinuingNodeExecutions) {
        handleMarkedInstance(discontinuingNodeExecution, updatedInterrupt);
      }
    } catch (Exception ex) {
      interruptService.markProcessed(updatedInterrupt.getUuid(), PROCESSED_UNSUCCESSFULLY);
      throw ex;
    }
    return updatedInterrupt;
  }

  protected abstract void handleMarkedInstance(NodeExecution nodeExecution, Interrupt interrupt);
}
