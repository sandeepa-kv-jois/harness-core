/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.statemachine.services.api;

import io.harness.cvng.analysis.entities.LearningEngineTask;
import io.harness.cvng.analysis.services.api.LogAnalysisService;
import io.harness.cvng.statemachine.beans.AnalysisInput;
import io.harness.cvng.statemachine.beans.AnalysisState;
import io.harness.cvng.statemachine.beans.AnalysisStatus;
import io.harness.cvng.statemachine.entities.LogAnalysisState;
import io.harness.cvng.statemachine.exception.AnalysisStateMachineException;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import java.util.Arrays;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class LogAnalysisStateExecutor<T extends LogAnalysisState> extends AnalysisStateExecutor<T> {
  @Inject protected transient LogAnalysisService logAnalysisService;

  protected abstract String scheduleAnalysis(AnalysisInput analysisInput);

  @Override
  public AnalysisState execute(T analysisState) {
    analysisState.setWorkerTaskId(scheduleAnalysis(analysisState.getInputs()));
    Preconditions.checkNotNull(analysisState.getWorkerTaskId(), "workerId can not be null");
    analysisState.setStatus(AnalysisStatus.RUNNING);
    log.info("Executing service guard log analysis for {}", analysisState.getInputs());
    return analysisState;
  }

  @Override
  public AnalysisStatus getExecutionStatus(T analysisState) {
    if (analysisState.getStatus() != AnalysisStatus.SUCCESS) {
      Map<String, LearningEngineTask.ExecutionStatus> taskStatuses =
          logAnalysisService.getTaskStatus(Arrays.asList(analysisState.getWorkerTaskId()));
      LearningEngineTask.ExecutionStatus taskStatus = taskStatuses.get(analysisState.getWorkerTaskId());
      // This could be common code for all states.
      switch (taskStatus) {
        case SUCCESS:
          return AnalysisStatus.SUCCESS;
        case FAILED:
        case TIMEOUT:
          return AnalysisStatus.RETRY;
        case QUEUED:
        case RUNNING:
          return AnalysisStatus.RUNNING;
        default:
          throw new AnalysisStateMachineException(
              "Unknown worker state when executing service guard log analysis: " + taskStatus);
      }
    }
    return AnalysisStatus.SUCCESS;
  }

  @Override
  public AnalysisState handleRerun(T analysisState) {
    // increment the retryCount without caring for the max
    // clean up state in underlying worker and then execute

    analysisState.setRetryCount(analysisState.getRetryCount() + 1);
    log.info("In serviceguard log analysis for Inputs {}, cleaning up worker task. Old taskID: {}",
        analysisState.getInputs(), analysisState.getWorkerTaskId());
    analysisState.setWorkerTaskId(null);
    return execute(analysisState);
  }

  @Override
  public AnalysisState handleRunning(T analysisState) {
    return analysisState;
  }

  @Override
  public AnalysisState handleSuccess(T analysisState) {
    analysisState.setStatus(AnalysisStatus.SUCCESS);
    return analysisState;
  }

  @Override
  public AnalysisState handleTransition(T analysisState) {
    analysisState.setStatus(AnalysisStatus.SUCCESS);
    return analysisState;
  }
}
