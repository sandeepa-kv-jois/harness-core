/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.debezium;

import com.google.inject.Singleton;
import io.debezium.engine.ChangeEvent;
import io.debezium.engine.DebeziumEngine;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class DebeziumChangeConsumer implements DebeziumEngine.ChangeConsumer<ChangeEvent<String, String>> {
  ChangeHandler changeHandler;
  public DebeziumChangeConsumer(ChangeHandler changeHandler) {
    this.changeHandler = changeHandler;
  }

  private boolean handleEvent(ChangeEvent<String, String> changeEvent) {
    // TODO: invoke the handler
    return true;
  }

  @Override
  public void handleBatch(List<ChangeEvent<String, String>> changeEvents,
      DebeziumEngine.RecordCommitter<ChangeEvent<String, String>> recordCommitter) throws InterruptedException {
    for (ChangeEvent<String, String> changeEvent : changeEvents) {
      try {
        handleEvent(changeEvent);
      } catch (Exception exception) {
        // TODO: Handle Failure
        log.error(String.format("Exception caught when trying to process event: [%s].", changeEvent), exception);
      }
      recordCommitter.markProcessed(changeEvent);
    }
    recordCommitter.markBatchFinished();
  }
}
