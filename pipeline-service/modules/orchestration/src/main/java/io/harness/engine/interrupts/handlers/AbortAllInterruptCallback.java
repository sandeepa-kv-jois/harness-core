/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.interrupts.handlers;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.interrupts.Interrupt.State.PROCESSED_SUCCESSFULLY;
import static io.harness.interrupts.Interrupt.State.PROCESSED_UNSUCCESSFULLY;

import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.interrupts.InterruptService;
import io.harness.interrupts.Interrupt;
import io.harness.tasks.ResponseData;
import io.harness.waiter.OldNotifyCallback;

import com.google.inject.Inject;
import java.util.Map;
import lombok.Builder;

@OwnedBy(CDC)
public class AbortAllInterruptCallback implements OldNotifyCallback {
  @Inject private InterruptService interruptService;

  Interrupt interrupt;

  @Builder
  public AbortAllInterruptCallback(Interrupt interrupt) {
    this.interrupt = interrupt;
  }

  @Override
  public void notify(Map<String, ResponseData> response) {
    interruptService.markProcessed(interrupt.getUuid(), PROCESSED_SUCCESSFULLY);
  }

  @Override
  public void notifyError(Map<String, ResponseData> response) {
    interruptService.markProcessed(interrupt.getUuid(), PROCESSED_UNSUCCESSFULLY);
  }
}
