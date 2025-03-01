/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.execution.export.background;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;
import static io.harness.mongo.iterator.MongoPersistenceIterator.SchedulingType.REGULAR;

import static java.time.Duration.ofMinutes;

import io.harness.annotations.dev.OwnedBy;
import io.harness.execution.export.ExportExecutionsRequestLogContext;
import io.harness.execution.export.request.ExportExecutionsRequest;
import io.harness.execution.export.request.ExportExecutionsRequest.ExportExecutionsRequestKeys;
import io.harness.execution.export.request.ExportExecutionsRequest.Status;
import io.harness.iterator.IteratorExecutionHandler;
import io.harness.iterator.IteratorPumpModeHandler;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.iterator.PersistenceIteratorFactory.PumpExecutorOptions;
import io.harness.logging.AccountLogContext;
import io.harness.logging.AutoLogContext;
import io.harness.mongo.iterator.MongoPersistenceIterator;
import io.harness.mongo.iterator.MongoPersistenceIterator.Handler;
import io.harness.mongo.iterator.filter.MorphiaFilterExpander;
import io.harness.mongo.iterator.provider.MorphiaPersistenceProvider;

import com.google.inject.Inject;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDC)
@Slf4j
public class ExportExecutionsRequestCleanupHandler
    extends IteratorPumpModeHandler implements Handler<ExportExecutionsRequest> {
  private static final int ACCEPTABLE_DELAY_MINUTES = 45;

  @Inject private PersistenceIteratorFactory persistenceIteratorFactory;
  @Inject private ExportExecutionsService exportExecutionsService;
  @Inject private MorphiaPersistenceProvider<ExportExecutionsRequest> persistenceProvider;

  @Override
  protected void createAndStartIterator(PumpExecutorOptions executorOptions, Duration targetInterval) {
    iterator = (MongoPersistenceIterator<ExportExecutionsRequest, MorphiaFilterExpander<ExportExecutionsRequest>>)
                   persistenceIteratorFactory.createPumpIteratorWithDedicatedThreadPool(executorOptions,
                       ExportExecutionsRequestCleanupHandler.class,
                       MongoPersistenceIterator
                           .<ExportExecutionsRequest, MorphiaFilterExpander<ExportExecutionsRequest>>builder()
                           .clazz(ExportExecutionsRequest.class)
                           .fieldName(ExportExecutionsRequestKeys.nextCleanupIteration)
                           .targetInterval(targetInterval)
                           .acceptableNoAlertDelay(ofMinutes(ACCEPTABLE_DELAY_MINUTES))
                           .handler(this)
                           .filterExpander(query
                               -> query.field(ExportExecutionsRequestKeys.status)
                                      .equal(Status.READY)
                                      .field(ExportExecutionsRequestKeys.expiresAt)
                                      .greaterThan(0)
                                      .field(ExportExecutionsRequestKeys.expiresAt)
                                      .lessThan(System.currentTimeMillis()))
                           .schedulingType(REGULAR)
                           .persistenceProvider(persistenceProvider)
                           .redistribute(true));
  }

  public void registerIterator(IteratorExecutionHandler iteratorExecutionHandler) {
    iteratorName = "ExportExecutionsRequestCleanupHandler";

    // Register the iterator with the iterator config handler.
    iteratorExecutionHandler.registerIteratorHandler(iteratorName, this);
  }

  @Override
  public void handle(ExportExecutionsRequest request) {
    if (request == null) {
      log.warn("ExportExecutionsRequest is null");
      return;
    }

    try (AutoLogContext ignore1 = new AccountLogContext(request.getAccountId(), OVERRIDE_ERROR);
         AutoLogContext ignore2 = new ExportExecutionsRequestLogContext(request.getUuid(), OVERRIDE_ERROR)) {
      try {
        exportExecutionsService.expireRequest(request);
      } catch (Exception ex) {
        // NOTE: This operation will be tried again. Just log an error and don't rethrow exception.
        log.error("Unable to cleanup export executions request", ex);
      }
    }
  }
}
