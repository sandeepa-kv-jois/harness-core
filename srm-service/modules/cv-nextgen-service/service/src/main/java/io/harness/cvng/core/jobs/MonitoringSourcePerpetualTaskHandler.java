/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.jobs;

import io.harness.cvng.core.entities.MonitoringSourcePerpetualTask;
import io.harness.cvng.core.services.api.MonitoringSourcePerpetualTaskService;
import io.harness.mongo.iterator.MongoPersistenceIterator.Handler;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class MonitoringSourcePerpetualTaskHandler implements Handler<MonitoringSourcePerpetualTask> {
  @Inject private MonitoringSourcePerpetualTaskService monitoringSourcePerpetualTaskService;
  @Override
  public void handle(MonitoringSourcePerpetualTask entity) {
    log.info("Enqueuing monitoring source {}", entity.getUuid());
    monitoringSourcePerpetualTaskService.createPerpetualTask(entity);
    log.info("Done enqueuing monitoring source {}", entity.getUuid());
  }
}
