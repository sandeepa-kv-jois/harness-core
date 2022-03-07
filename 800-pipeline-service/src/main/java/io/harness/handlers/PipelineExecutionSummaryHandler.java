/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.handlers;

import io.harness.debezium.ChangeHandler;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;

import com.google.inject.Singleton;

@Singleton
public class PipelineExecutionSummaryHandler implements ChangeHandler<PipelineExecutionSummaryEntity> {
  @Override
  public void handleUpdateEvent(String id, PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity) {}

  @Override
  public void handleDeleteEvent(String id) {}

  @Override
  public void handleCreateEvent(String id, PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity) {}
}
