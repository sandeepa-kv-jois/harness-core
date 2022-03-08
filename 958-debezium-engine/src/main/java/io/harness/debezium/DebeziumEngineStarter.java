/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.debezium;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DebeziumEngineStarter {
  DebeziumConfiguration debeziumConfiguration;

  public DebeziumEngineStarter() {
    this.debeziumConfiguration = new DebeziumConfiguration();
  }

  public void startDebeziumEngine(DebeziumConfig debeziumConfig, ChangeHandler changeConsumer) {
    ExecutorService debeziumExecutorService =
        Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat("debezium-controller").build());
    DebeziumController debeziumController =
        new DebeziumController(debeziumConfiguration.getDebeziumProperties(debeziumConfig), changeConsumer);
    debeziumExecutorService.submit(debeziumController);
  }
}
