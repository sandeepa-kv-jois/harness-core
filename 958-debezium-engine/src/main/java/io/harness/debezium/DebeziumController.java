/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.debezium;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Singleton;
import io.debezium.engine.ChangeEvent;
import io.debezium.engine.DebeziumEngine;
import io.debezium.engine.format.Json;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class DebeziumController implements Runnable {
  protected final ExecutorService executorService =
      Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat("debezium-engine").build());
  private Properties props;
  ChangeHandler changeConsumer;

  public DebeziumController(Properties props, ChangeHandler changeConsumer) {
    this.changeConsumer = changeConsumer;
    this.props = props;
  }

  @Override
  public void run() {
    DebeziumEngine<ChangeEvent<String, String>> debeziumEngine;
    debeziumEngine = getEngine(props);
    executorService.submit(debeziumEngine);
  }

  protected DebeziumEngine<ChangeEvent<String, String>> getEngine(Properties props) {
    return DebeziumEngine.create(Json.class).using(props).notifying(new DebeziumChangeConsumer(changeConsumer)).build();
  }
}
