/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.jobs;

import io.harness.cvng.statemachine.entities.AnalysisOrchestrator;
import io.harness.cvng.statemachine.services.api.OrchestrationService;
import io.harness.eventsframework.schemas.cv.StateMachineTrigger;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.PersistentLockException;

import com.google.inject.Inject;

public class StateMachineMessageProcessorImpl implements StateMachineMessageProcessor {
  @Inject private OrchestrationService orchestrationService;
  @Inject private StateMachineEventPublisherService stateMachineEventPublisherService;

  @Override
  public void processAnalysisStateMachine(StateMachineTrigger trigger) {
    try {
      String verificationTaskId = trigger.getVerificationTaskId();
      AnalysisOrchestrator orchestrator = orchestrationService.getAnalysisOrchestrator(verificationTaskId);
      orchestrationService.orchestrate(orchestrator);
    } catch (PersistentLockException ex) {
      processFailureMessage(trigger);
    } catch (Exception ex) {
      throw new InvalidRequestException(
          "Invalid state while processing message for srm_statemachine_event  " + trigger);
    }
  }

  private void processFailureMessage(StateMachineTrigger trigger) {
    // Add the same message again for retry
    stateMachineEventPublisherService.registerTaskComplete(trigger.getAccountId(), trigger.getVerificationTaskId());
  }
}
