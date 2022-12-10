/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.execution.strategy.identity;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.data.structure.UUIDGenerator;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.utils.PmsLevelUtils;
import io.harness.execution.IdentityNodeExecutionMetadata;
import io.harness.execution.NodeExecution;
import io.harness.execution.NodeExecution.NodeExecutionBuilder;
import io.harness.execution.NodeExecution.NodeExecutionKeys;
import io.harness.graph.stepDetail.service.PmsGraphStepDetailsService;
import io.harness.interrupts.InterruptEffect;
import io.harness.plan.IdentityPlanNode;
import io.harness.plan.Node;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.execution.utils.AmbianceUtils;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public class IdentityNodeExecutionStrategyHelper {
  @Inject private PmsGraphStepDetailsService pmsGraphStepDetailsService;
  @Inject private NodeExecutionService nodeExecutionService;

  public NodeExecution createNodeExecution(@NotNull Ambiance ambiance, @NotNull IdentityPlanNode node,
      IdentityNodeExecutionMetadata metadata, String notifyId, String parentId, String previousId) {
    String uuid = AmbianceUtils.obtainCurrentRuntimeId(ambiance);
    NodeExecution originalExecution = nodeExecutionService.get(node.getOriginalNodeExecutionId());
    NodeExecution execution = NodeExecution.builder()
                                  .uuid(uuid)
                                  .planNode(node)
                                  .ambiance(ambiance)
                                  .levelCount(ambiance.getLevelsCount())
                                  .status(Status.QUEUED)
                                  .unitProgresses(new ArrayList<>())
                                  .startTs(AmbianceUtils.getCurrentLevelStartTs(ambiance))
                                  .originalNodeExecutionId(node.getOriginalNodeExecutionId())
                                  .module(node.getServiceName())
                                  .name(node.getName())
                                  .skipGraphType(node.getSkipGraphType())
                                  .identifier(node.getIdentifier())
                                  .stepType(node.getStepType())
                                  .nodeId(node.getUuid())
                                  .stageFqn(node.getStageFqn())
                                  .group(node.getGroup())
                                  .notifyId(notifyId)
                                  .parentId(parentId)
                                  .previousId(previousId)
                                  .mode(originalExecution.getMode())
                                  .nodeRunInfo(originalExecution.getNodeRunInfo())
                                  .skipInfo(originalExecution.getSkipInfo())
                                  .failureInfo(originalExecution.getFailureInfo())
                                  .progressData(originalExecution.getProgressData())
                                  .adviserResponse(originalExecution.getAdviserResponse())
                                  .timeoutInstanceIds(originalExecution.getTimeoutInstanceIds())
                                  .timeoutDetails(originalExecution.getTimeoutDetails())
                                  .adviserResponse(originalExecution.getAdviserResponse())
                                  .adviserTimeoutInstanceIds(originalExecution.getAdviserTimeoutInstanceIds())
                                  .interruptHistories(originalExecution.getInterruptHistories())
                                  .resolvedParams(originalExecution.getResolvedParams())
                                  .resolvedInputs(originalExecution.getResolvedInputs())
                                  .executionInputConfigured(originalExecution.getExecutionInputConfigured())
                                  .build();
    NodeExecution nodeExecution = nodeExecutionService.save(execution);
    pmsGraphStepDetailsService.copyStepDetailsForRetry(
        ambiance.getPlanExecutionId(), originalExecution.getUuid(), uuid);
    return nodeExecution;
  }

  // Cloning the nodeExecution. Also copying the original retryIds. We will update the retryIds later in the caller
  // method.
  NodeExecutionBuilder cloneNodeExecutionForRetries(NodeExecution originalNodeExecution, Ambiance ambiance) {
    String uuid = UUIDGenerator.generateUuid();
    Ambiance finalAmbiance = AmbianceUtils.cloneForFinish(ambiance)
                                 .toBuilder()
                                 .addLevels(PmsLevelUtils.buildLevelFromNode(uuid, originalNodeExecution.getNode()))
                                 .build();

    String parentId = AmbianceUtils.obtainParentRuntimeId(finalAmbiance);
    String notifyId = parentId == null ? null : AmbianceUtils.obtainCurrentRuntimeId(finalAmbiance);

    Node node = originalNodeExecution.getNode();
    return NodeExecution.builder()
        .uuid(uuid)
        .planNode(node)
        .ambiance(finalAmbiance)
        .oldRetry(true)
        .retryIds(originalNodeExecution.getRetryIds())
        .levelCount(finalAmbiance.getLevelsCount())
        .status(originalNodeExecution.getStatus())
        .unitProgresses(new ArrayList<>())
        .createdAt(AmbianceUtils.getCurrentLevelStartTs(finalAmbiance))
        .startTs(AmbianceUtils.getCurrentLevelStartTs(finalAmbiance))
        .endTs(System.currentTimeMillis())
        .originalNodeExecutionId(originalNodeExecution.getUuid())
        .module(node.getServiceName())
        .name(node.getName())
        .skipGraphType(node.getSkipGraphType())
        .identifier(node.getIdentifier())
        .stepType(node.getStepType())
        .nodeId(node.getUuid())
        .stageFqn(node.getStageFqn())
        .group(node.getGroup())
        .notifyId(notifyId)
        .parentId(parentId)
        .mode(originalNodeExecution.getMode())
        .nodeRunInfo(originalNodeExecution.getNodeRunInfo())
        .skipInfo(originalNodeExecution.getSkipInfo())
        .failureInfo(originalNodeExecution.getFailureInfo())
        .progressData(originalNodeExecution.getProgressData())
        .adviserResponse(originalNodeExecution.getAdviserResponse())
        .timeoutInstanceIds(originalNodeExecution.getTimeoutInstanceIds())
        .timeoutDetails(originalNodeExecution.getTimeoutDetails())
        .adviserResponse(originalNodeExecution.getAdviserResponse())
        .adviserTimeoutInstanceIds(originalNodeExecution.getAdviserTimeoutInstanceIds())
        .resolvedParams(originalNodeExecution.getResolvedParams())
        .resolvedInputs(originalNodeExecution.getResolvedInputs())
        .executionInputConfigured(originalNodeExecution.getExecutionInputConfigured())
        .interruptHistories(originalNodeExecution.getInterruptHistories());
  }

  // Update retryIds in retryInterruptConfigs to point to new nodeExecutions if it was pointing to original
  // nodeExecution.
  @VisibleForTesting
  List<InterruptEffect> getUpdatedInterruptHistory(
      List<InterruptEffect> originalInterruptHistory, Map<String, String> originalRetryIdToNewRetryIdMap) {
    List<InterruptEffect> newInterruptHistory = new ArrayList<>();
    for (InterruptEffect interruptEffect : originalInterruptHistory) {
      if (interruptEffect.getInterruptConfig().hasRetryInterruptConfig()) {
        io.harness.pms.contracts.interrupts.RetryInterruptConfig retryInterruptConfig =
            interruptEffect.getInterruptConfig().getRetryInterruptConfig();
        String newRetryId = originalRetryIdToNewRetryIdMap.get(retryInterruptConfig.getRetryId());
        if (newRetryId == null) {
          continue;
        }
        retryInterruptConfig = retryInterruptConfig.toBuilder().setRetryId(newRetryId).build();
        newInterruptHistory.add(InterruptEffect.builder()
                                    .interruptConfig(interruptEffect.getInterruptConfig()
                                                         .toBuilder()
                                                         .setRetryInterruptConfig(retryInterruptConfig)
                                                         .build())
                                    .interruptId(interruptEffect.getInterruptId())
                                    .interruptType(interruptEffect.getInterruptType())
                                    .tookEffectAt(interruptEffect.getTookEffectAt())
                                    .build());
      } else {
        newInterruptHistory.add(interruptEffect);
      }
    }
    return newInterruptHistory;
  }
  // Copying the nodeExecutions for retried nodes. Will create clone nodeExecution for each retried NodeExecution and
  // update retriedIds with newly created NodeExecutions.
  @VisibleForTesting
  void copyNodeExecutionsForRetriedNodes(NodeExecution nodeExecution, List<String> originalOldRetryIds) {
    if (EmptyPredicate.isEmpty(originalOldRetryIds)) {
      return;
    }
    Map<String, String> originalRetryIdToNewRetryIdMap = new HashMap<>();
    List<NodeExecutionBuilder> clonedNodeExecutions = new ArrayList<>();

    List<NodeExecution> nodeExecutions = nodeExecutionService.getAll(new HashSet<>(originalOldRetryIds));

    for (NodeExecution originalNodeExecution : nodeExecutions) {
      NodeExecutionBuilder newNodeExecution =
          cloneNodeExecutionForRetries(originalNodeExecution, nodeExecution.getAmbiance());
      clonedNodeExecutions.add(newNodeExecution);
      originalRetryIdToNewRetryIdMap.put(originalNodeExecution.getUuid(), newNodeExecution.build().getUuid());
    }

    for (NodeExecutionBuilder clonedNodeExecutionBuilder : clonedNodeExecutions) {
      clonedNodeExecutionBuilder.retryIds(getNewRetryIdsFromOriginalRetryIds(
          clonedNodeExecutionBuilder.build().getRetryIds(), originalRetryIdToNewRetryIdMap));
      clonedNodeExecutionBuilder.interruptHistories(getUpdatedInterruptHistory(
          clonedNodeExecutionBuilder.build().getInterruptHistories(), originalRetryIdToNewRetryIdMap));
    }
    nodeExecutionService.saveAll(
        clonedNodeExecutions.stream().map(NodeExecutionBuilder::build).collect(Collectors.toList()));
    updateFinalRetriedNode(nodeExecution, originalOldRetryIds, originalRetryIdToNewRetryIdMap);
  }

  private void updateFinalRetriedNode(NodeExecution finalNodeExecution, List<String> originalRetryIds,
      Map<String, String> originalRetryIdToNewRetryIdMap) {
    List<String> finalNodeRetryIds =
        getNewRetryIdsFromOriginalRetryIds(originalRetryIds, originalRetryIdToNewRetryIdMap);
    List<InterruptEffect> finalInterruptHistory =
        getUpdatedInterruptHistory(finalNodeExecution.getInterruptHistories(), originalRetryIdToNewRetryIdMap);
    nodeExecutionService.update(finalNodeExecution.getUuid(), update -> {
      update.set(NodeExecutionKeys.retryIds, finalNodeRetryIds);
      update.set(NodeExecutionKeys.interruptHistories, finalInterruptHistory);
      update.set(NodeExecutionKeys.startTs, System.currentTimeMillis());
    });
  }

  // This method returns list of new retry Ids corresponding to original retry ids.
  @VisibleForTesting
  List<String> getNewRetryIdsFromOriginalRetryIds(
      List<String> originalRetryIds, Map<String, String> originalRetryIdToNewRetryIdMap) {
    return originalRetryIds.stream().map(originalRetryIdToNewRetryIdMap::get).collect(Collectors.toList());
  }
}
