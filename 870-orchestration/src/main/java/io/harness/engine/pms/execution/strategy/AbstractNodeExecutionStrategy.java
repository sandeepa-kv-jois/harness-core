/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.execution.strategy;

import io.harness.engine.OrchestrationEngine;
import io.harness.engine.executions.plan.PlanService;
import io.harness.engine.utils.PmsLevelUtils;
import io.harness.event.handlers.SdkResponseProcessor;
import io.harness.execution.NodeExecution;
import io.harness.execution.PmsNodeExecutionMetadata;
import io.harness.logging.AutoLogContext;
import io.harness.plan.Node;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.events.SdkResponseEventProto;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.registries.SdkResponseProcessorFactory;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.concurrent.ExecutorService;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class AbstractNodeExecutionStrategy<P extends Node, M extends PmsNodeExecutionMetadata>
    implements NodeExecutionStrategy<P, NodeExecution, M> {
  @Inject private PlanService planService;
  @Inject private OrchestrationEngine orchestrationEngine;
  @Inject private SdkResponseProcessorFactory sdkResponseProcessorFactory;
  @Inject @Named("EngineExecutorService") private ExecutorService executorService;

  @Override
  public NodeExecution initiateNode(@NonNull Ambiance ambiance, @NonNull String nodeId, String runtimeId, M metadata) {
    try {
      P node = planService.fetchNode(ambiance.getPlanId(), nodeId);
      Ambiance clonedAmbiance =
          AmbianceUtils.cloneForChild(ambiance, PmsLevelUtils.buildLevelFromNode(runtimeId, node));
      return runNode(clonedAmbiance, node, metadata);
    } catch (Exception ex) {
      log.error("Error happened while triggering node", ex);
      handleError(ambiance, ex);
      return null;
    }
  }

  @Override
  public NodeExecution runNode(@NonNull Ambiance ambiance, @NonNull P node, M metadata) {
    try (AutoLogContext ignore = AmbianceUtils.autoLogContext(ambiance)) {
      String parentId = AmbianceUtils.obtainParentRuntimeId(ambiance);
      String notifyId = parentId == null ? null : AmbianceUtils.obtainCurrentRuntimeId(ambiance);
      return createAndRunNodeExecution(ambiance, node, metadata, notifyId, parentId, null);
    }
  }

  @Override
  public NodeExecution runNextNode(
      @NonNull Ambiance ambiance, @NonNull P node, NodeExecution prevExecution, M metadata) {
    try (AutoLogContext ignore = AmbianceUtils.autoLogContext(ambiance)) {
      return createAndRunNodeExecution(
          ambiance, node, metadata, prevExecution.getNotifyId(), prevExecution.getParentId(), prevExecution.getUuid());
    }
  }

  private NodeExecution createAndRunNodeExecution(
      Ambiance ambiance, P node, M metadata, String notifyId, String parentId, String previousId) {
    NodeExecution savedExecution = createNodeExecution(ambiance, node, metadata, notifyId, parentId, previousId);
    executorService.submit(() -> orchestrationEngine.startNodeExecution(savedExecution.getAmbiance()));
    return savedExecution;
  }

  @Override
  public void handleSdkResponseEvent(SdkResponseEventProto event) {
    try (AutoLogContext ignore = AmbianceUtils.autoLogContext(event.getAmbiance())) {
      log.info("Event for SdkResponseEvent received for eventType {}", event.getSdkResponseEventType());
      SdkResponseProcessor handler = sdkResponseProcessorFactory.getHandler(event.getSdkResponseEventType());
      handler.handleEvent(event);
      log.info("Event for SdkResponseEvent for event type {} completed successfully", event.getSdkResponseEventType());
    } catch (Exception ex) {
      handleError(event.getAmbiance(), ex);
    }
  }

  public abstract NodeExecution createNodeExecution(
      Ambiance ambiance, P node, M metadata, String notifyId, String parentId, String previousId);
}
