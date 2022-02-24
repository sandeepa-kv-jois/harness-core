/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.jobs;

import io.harness.cvng.statemachine.services.api.AnalysisStateMachineService;
import io.harness.eventsframework.EventsFrameworkConstants;
import io.harness.eventsframework.api.Consumer;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.schemas.cv.StateMachineTrigger;
import io.harness.queue.QueueController;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class StatemachineEventConsumer extends AbstractStreamConsumer {
  private static final int MAX_WAIT_TIME_SEC = 10;
  AnalysisStateMachineService stateMachineService;

  @Inject
  public StatemachineEventConsumer(@Named(EventsFrameworkConstants.SRM_STATEMACHINE_EVENT) Consumer consumer,
      QueueController queueController, AnalysisStateMachineService stateMachineService) {
    super(MAX_WAIT_TIME_SEC, consumer, queueController);
    this.stateMachineService = stateMachineService;
  }

  @Override
  protected void processMessage(Message message) {
    try {
      StateMachineTrigger trigger = StateMachineTrigger.parseFrom(message.getMessage().getData());
      stateMachineService.executeStateMachine(trigger.getVerificationTaskId());
    } catch (Exception ex) {
      log.error("Exception when consuming event: ", ex);
    }
  }
}
