/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.entities.cvnglogs;

import io.harness.cvng.beans.cvnglog.CVNGLogDTO;
import io.harness.cvng.beans.cvnglog.ExecutionLogDTO;
import io.harness.metrics.service.api.MetricService;

import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Data
@Builder
@Slf4j
public class ExecutionLogRecord extends CVNGLogRecord {
  private String log;
  private ExecutionLogDTO.LogLevel logLevel;

  public static CVNGLogRecord toCVNGLogRecord(CVNGLogDTO cvngLogDTO) {
    return ExecutionLogRecord.builder()
        .log(((ExecutionLogDTO) cvngLogDTO).getLog())
        .logLevel(((ExecutionLogDTO) cvngLogDTO).getLogLevel())
        .build();
  }

  @Override
  public CVNGLogDTO toCVNGLogDTO() {
    return ExecutionLogDTO.builder().log(log).logLevel(logLevel).build();
  }

  @Override
  public void recordsMetrics(MetricService metricService, Map<String, String> tags) {
    // recordsMetrics is not required for ExecutionLogs
  }
}
