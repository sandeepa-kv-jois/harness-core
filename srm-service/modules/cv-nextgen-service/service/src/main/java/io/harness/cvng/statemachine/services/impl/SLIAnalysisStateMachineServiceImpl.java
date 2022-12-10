/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.statemachine.services.impl;

import static io.harness.cvng.CVConstants.STATE_MACHINE_IGNORE_MINUTES_FOR_SLI;

import io.harness.cvng.core.services.api.ExecutionLogService;
import io.harness.cvng.core.services.api.VerificationTaskService;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelIndicator;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelIndicatorService;
import io.harness.cvng.statemachine.beans.AnalysisInput;
import io.harness.cvng.statemachine.beans.AnalysisState;
import io.harness.cvng.statemachine.beans.AnalysisStatus;
import io.harness.cvng.statemachine.entities.AnalysisStateMachine;
import io.harness.cvng.statemachine.entities.SLIMetricAnalysisState;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;

public class SLIAnalysisStateMachineServiceImpl extends AnalysisStateMachineServiceImpl {
  @Inject private VerificationTaskService verificationTaskService;
  @Inject private ServiceLevelIndicatorService serviceLevelIndicatorService;
  @Inject private ExecutionLogService executionLogService;

  @Override
  public AnalysisStateMachine createStateMachine(AnalysisInput inputForAnalysis) {
    AnalysisStateMachine stateMachine = AnalysisStateMachine.builder()
                                            .verificationTaskId(inputForAnalysis.getVerificationTaskId())
                                            .analysisStartTime(inputForAnalysis.getStartTime())
                                            .analysisEndTime(inputForAnalysis.getEndTime())
                                            .status(AnalysisStatus.CREATED)
                                            .build();

    String sliId = verificationTaskService.getSliId(inputForAnalysis.getVerificationTaskId());
    ServiceLevelIndicator serviceLevelIndicator = serviceLevelIndicatorService.get(sliId);
    Preconditions.checkNotNull(serviceLevelIndicator, "Service Level Indicator can't be null");
    AnalysisState firstState = SLIMetricAnalysisState.builder().build();
    firstState.setStatus(AnalysisStatus.CREATED);
    firstState.setInputs(inputForAnalysis);
    stateMachine.setAccountId(serviceLevelIndicator.getAccountId());
    stateMachine.setStateMachineIgnoreMinutes(STATE_MACHINE_IGNORE_MINUTES_FOR_SLI);
    stateMachine.setCurrentState(firstState);
    executionLogService.getLogger(stateMachine)
        .log(stateMachine.getLogLevel(), "Analysis state machine status: " + stateMachine.getStatus());
    return stateMachine;
  }
}
