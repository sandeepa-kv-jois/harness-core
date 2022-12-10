/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.plan.execution;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ExecutionErrorInfo;
import io.harness.dto.converter.FailureInfoDTOConverter;
import io.harness.engine.utils.OrchestrationUtils;
import io.harness.execution.NodeExecution;
import io.harness.plan.NodeType;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.execution.ExecutionStatus;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys;
import io.harness.steps.StepSpecTypeConstants;

import java.util.Objects;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import org.springframework.data.mongodb.core.query.Update;

/**
 * A utility to generate updates for the layout graph used in the list api for stage layout
 */
@OwnedBy(HarnessTeam.PIPELINE)
@UtilityClass
public class ExecutionSummaryUpdateUtils {
  private static boolean isBarrierNode(Level level) {
    return Objects.equals(level.getStepType().getType(), StepSpecTypeConstants.BARRIER);
  }

  private static boolean performUpdatesOnBarrierNode(Update update, NodeExecution nodeExecution) {
    // Todo: Check here if the nodeExecution is under strategy then use executionId instead.
    Optional<Level> stage = AmbianceUtils.getStageLevelFromAmbiance(nodeExecution.getAmbiance());
    if (stage.isPresent()) {
      Level stageNode = stage.get();
      update.set(PlanExecutionSummaryKeys.layoutNodeMap + "." + stageNode.getSetupId() + ".barrierFound", true);
      return true;
    }
    return false;
  }

  public static boolean isStageStrategyNode(NodeExecution nodeExecution) {
    return nodeExecution.getStepType().getStepCategory() == StepCategory.STRATEGY
        && AmbianceUtils.isCurrentStrategyLevelAtStage(nodeExecution.getAmbiance());
  }

  /**
   * This function adds some information at the stage layoutNodeMap level.
   * Performs the following operation:
   * 1. Updates barrier related information on a stage node
   * 2. Updates strategy node with status and step parameters
   * 3. Updates stage node with generic updates and strategy information.
   * @param update
   * @param nodeExecution
   * @return
   */
  public static boolean addStageUpdateCriteria(Update update, NodeExecution nodeExecution) {
    Level level = Objects.requireNonNull(AmbianceUtils.obtainCurrentLevel(nodeExecution.getAmbiance()));
    boolean updated = false;
    if (isBarrierNode(level)) {
      updated = performUpdatesOnBarrierNode(update, nodeExecution);
    }
    ExecutionStatus status = ExecutionStatus.getExecutionStatus(nodeExecution.getStatus());
    if (isStageStrategyNode(nodeExecution)) {
      updated = updateStrategyNode(update, nodeExecution, status) || updated;
    }
    if (OrchestrationUtils.isStageNode(nodeExecution)) {
      updated = updateStageNode(update, nodeExecution, status, level) || updated;
    }
    return updated;
  }

  private static boolean updateStrategyNode(Update update, NodeExecution nodeExecution, ExecutionStatus status) {
    update.set(String.format(LayoutNodeGraphConstants.STATUS, nodeExecution.getNodeId()), status);
    update.set(PlanExecutionSummaryKeys.layoutNodeMap + "." + nodeExecution.getNodeId() + ".moduleInfo.stepParameters",
        nodeExecution.getResolvedStepParameters());
    return true;
  }

  public boolean addPipelineUpdateCriteria(Update update, NodeExecution nodeExecution) {
    if (OrchestrationUtils.isPipelineNode(nodeExecution)) {
      ExecutionStatus status = ExecutionStatus.getExecutionStatus(nodeExecution.getStatus());
      update.set(PlanExecutionSummaryKeys.internalStatus, nodeExecution.getStatus());
      update.set(PlanExecutionSummaryKeys.status, status);
      if (nodeExecution.getEndTs() != null) {
        update.set(PlanExecutionSummaryKeys.endTs, nodeExecution.getEndTs());
      }
      if (status == ExecutionStatus.FAILED) {
        update.set(PlanExecutionSummaryKeys.executionErrorInfo,
            ExecutionErrorInfo.builder().message(nodeExecution.getFailureInfo().getErrorMessage()).build());
        update.set(PlanExecutionSummaryKeys.failureInfo,
            FailureInfoDTOConverter.toFailureInfoDTO(nodeExecution.getFailureInfo()));
      }
      return true;
    }

    return false;
  }

  private boolean updateStageNode(Update update, NodeExecution nodeExecution, ExecutionStatus status, Level level) {
    // If the nodes is of type Identity, there is no need to update the status. We want to update the status only when
    // there is a PlanNode
    String stageUuid = nodeExecution.getNodeId();
    if (AmbianceUtils.getStrategyLevelFromAmbiance(nodeExecution.getAmbiance()).isPresent()) {
      // If nodeExecution is under strategy then we use nodeExecution.getUuid rather than the planNodeId
      stageUuid = nodeExecution.getUuid();
      updateStrategyBasedData(update, nodeExecution);
    }
    if (!level.getNodeType().equals(NodeType.IDENTITY_PLAN_NODE.toString())) {
      update.set(String.format(LayoutNodeGraphConstants.STATUS, stageUuid), status);
    }
    updateGenericData(update, stageUuid, nodeExecution);
    return true;
  }

  private void updateStrategyBasedData(Update update, NodeExecution nodeExecution) {
    String stageUuid = nodeExecution.getUuid();

    update.set(String.format(LayoutNodeGraphConstants.NODE_IDENTIFIER, stageUuid), nodeExecution.getIdentifier());
    update.set(String.format(LayoutNodeGraphConstants.NAME, stageUuid), nodeExecution.getName());
    update.set(String.format(LayoutNodeGraphConstants.STRATEGY_METADATA, stageUuid),
        AmbianceUtils.obtainCurrentLevel(nodeExecution.getAmbiance()).getStrategyMetadata());
  }

  private void updateGenericData(Update update, String stageUuid, NodeExecution nodeExecution) {
    update.set(String.format(LayoutNodeGraphConstants.START_TS, stageUuid), nodeExecution.getStartTs());
    update.set(String.format(LayoutNodeGraphConstants.NODE_EXECUTION_ID, stageUuid), nodeExecution.getUuid());
    update.set(String.format(LayoutNodeGraphConstants.NODE_RUN_INFO, stageUuid), nodeExecution.getNodeRunInfo());
    if (nodeExecution.getEndTs() != null) {
      update.set(String.format(LayoutNodeGraphConstants.END_TS, stageUuid), nodeExecution.getEndTs());
    }
    if (nodeExecution.getFailureInfo() != null) {
      update.set(String.format(LayoutNodeGraphConstants.FAILURE_INFO, stageUuid),
          ExecutionErrorInfo.builder().message(nodeExecution.getFailureInfo().getErrorMessage()).build());
      update.set(String.format(LayoutNodeGraphConstants.FAILURE_INFO_DTO, stageUuid),
          FailureInfoDTOConverter.toFailureInfoDTO(nodeExecution.getFailureInfo()));
    }
    if (nodeExecution.getSkipInfo() != null) {
      update.set(String.format(LayoutNodeGraphConstants.SKIP_INFO, stageUuid), nodeExecution.getSkipInfo());
    }
    update.set(String.format(LayoutNodeGraphConstants.EXECUTION_INPUT_CONFIGURED, stageUuid),
        nodeExecution.getExecutionInputConfigured());
  }
}
