/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import io.harness.cvng.core.beans.SLODebugResponse;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.entities.DataCollectionTask;
import io.harness.cvng.core.entities.VerificationTask;
import io.harness.cvng.core.services.api.DataCollectionTaskService;
import io.harness.cvng.core.services.api.DebugService;
import io.harness.cvng.core.services.api.VerificationTaskService;
import io.harness.cvng.servicelevelobjective.entities.SLIRecord;
import io.harness.cvng.servicelevelobjective.entities.SLOHealthIndicator;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelIndicator;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.services.api.SLIRecordService;
import io.harness.cvng.servicelevelobjective.services.api.SLOHealthIndicatorService;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelIndicatorService;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelObjectiveService;
import io.harness.cvng.statemachine.entities.AnalysisStateMachine;
import io.harness.cvng.statemachine.services.api.AnalysisStateMachineService;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DebugServiceImpl implements DebugService {
  @Inject ServiceLevelObjectiveService serviceLevelObjectiveService;
  @Inject ServiceLevelIndicatorService serviceLevelIndicatorService;
  @Inject SLOHealthIndicatorService sloHealthIndicatorService;
  @Inject VerificationTaskService verificationTaskService;
  @Inject DataCollectionTaskService dataCollectionTaskService;
  @Inject SLIRecordService sliRecordService;
  @Inject AnalysisStateMachineService analysisStateMachineService;

  @Override
  public SLODebugResponse getSLODebugResponse(ProjectParams projectParams, String identifier) {
    ServiceLevelObjective serviceLevelObjective = serviceLevelObjectiveService.getEntity(projectParams, identifier);

    Preconditions.checkNotNull(serviceLevelObjective, "Value of Identifier is not present in database");

    List<ServiceLevelIndicator> serviceLevelIndicatorList =
        serviceLevelIndicatorService.getEntities(projectParams, serviceLevelObjective.getServiceLevelIndicators());

    SLOHealthIndicator sloHealthIndicator =
        sloHealthIndicatorService.getBySLOIdentifier(projectParams, serviceLevelObjective.getIdentifier());

    Map<String, VerificationTask> sliIdentifierToVerificationTaskMap = new HashMap<>();

    Map<String, List<DataCollectionTask>> sliIdentifierToDataCollectionTaskMap = new HashMap<>();

    Map<String, AnalysisStateMachine> sliIdentifierToAnalysisStateMachineMap = new HashMap<>();

    Map<String, List<SLIRecord>> sliIdentifierToSLIRecordMap = new HashMap<>();

    for (ServiceLevelIndicator serviceLevelIndicator : serviceLevelIndicatorList) {
      sliIdentifierToVerificationTaskMap.put(serviceLevelIndicator.getIdentifier(),
          verificationTaskService.getSLITask(projectParams.getAccountIdentifier(), serviceLevelIndicator.getUuid()));

      sliIdentifierToDataCollectionTaskMap.put(serviceLevelIndicator.getIdentifier(),
          dataCollectionTaskService.getLatestDataCollectionTasks(
              projectParams.getAccountIdentifier(), serviceLevelIndicator.getUuid(), 3));

      sliIdentifierToSLIRecordMap.put(serviceLevelIndicator.getIdentifier(),
          sliRecordService.getLatestCountSLIRecords(serviceLevelIndicator.getUuid(), 100));

      sliIdentifierToAnalysisStateMachineMap.put(serviceLevelIndicator.getIdentifier(),
          analysisStateMachineService.getExecutingStateMachine(verificationTaskService.getSLIVerificationTaskId(
              projectParams.getAccountIdentifier(), serviceLevelIndicator.getUuid())));
    }

    return SLODebugResponse.builder()
        .projectParams(projectParams)
        .serviceLevelObjective(serviceLevelObjective)
        .serviceLevelIndicatorList(serviceLevelIndicatorList)
        .sloHealthIndicator(sloHealthIndicator)
        .sliIdentifierToVerificationTaskMap(sliIdentifierToVerificationTaskMap)
        .sliIdentifierToDataCollectionTaskMap(sliIdentifierToDataCollectionTaskMap)
        .sliIdentifierToSLIRecordMap(sliIdentifierToSLIRecordMap)
        .sliIdentifierToAnalysisStateMachineMap(sliIdentifierToAnalysisStateMachineMap)
        .build();
  }

  @Override
  public DataCollectionTask retryDataCollectionTask(ProjectParams projectParams, String identifier) {
    return dataCollectionTaskService.updateRetry(projectParams, identifier);
  }
}
