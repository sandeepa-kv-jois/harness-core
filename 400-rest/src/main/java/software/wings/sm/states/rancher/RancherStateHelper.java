/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states.rancher;

import static io.harness.annotations.dev.HarnessModule._870_CG_ORCHESTRATION;

import static software.wings.sm.StateExecutionData.StateExecutionDataBuilder.aStateExecutionData;

import io.harness.annotations.dev.TargetModule;
import io.harness.beans.ExecutionStatus;

import software.wings.beans.RancherKubernetesInfrastructureMapping;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionResponse;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@TargetModule(_870_CG_ORCHESTRATION)
public class RancherStateHelper {
  @Inject private transient InfrastructureMappingService infrastructureMappingService;

  public RancherKubernetesInfrastructureMapping fetchRancherKubernetesInfrastructureMapping(ExecutionContext context) {
    return (RancherKubernetesInfrastructureMapping) infrastructureMappingService.get(
        context.getAppId(), context.fetchInfraMappingId());
  }

  public ExecutionResponse getInvalidInfraDefFailedResponse() {
    return ExecutionResponse.builder()
        .executionStatus(ExecutionStatus.FAILED)
        .stateExecutionData(
            aStateExecutionData()
                .withErrorMsg("Step type is only supported with Rancher cloud provider based Infrastructure Definition")
                .build())
        .build();
  }

  public ExecutionResponse getResolvedClusterElementNotFoundResponse() {
    return ExecutionResponse.builder()
        .executionStatus(ExecutionStatus.FAILED)
        .stateExecutionData(
            aStateExecutionData()
                .withErrorMsg(
                    "Could not find a rancher resolved cluster to execute this step. Ensure there is a 'Rancher Resolve Clusters' step in the workflow before this")
                .build())
        .build();
  }
}
