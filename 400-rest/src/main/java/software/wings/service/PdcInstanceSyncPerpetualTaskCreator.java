/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.perpetualtask.PdcPTClientParams;
import io.harness.perpetualtask.PerpetualTaskClientContext;
import io.harness.perpetualtask.PerpetualTaskSchedule;
import io.harness.perpetualtask.PerpetualTaskType;
import io.harness.perpetualtask.internal.PerpetualTaskRecord;

import software.wings.api.DeploymentSummary;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.PhysicalInfrastructureMappingBase;

import com.google.common.collect.ImmutableMap;
import com.google.protobuf.util.Durations;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(CDP)
public class PdcInstanceSyncPerpetualTaskCreator extends AbstractInstanceSyncPerpetualTaskCreator {
  @Override
  public List<String> createPerpetualTasks(InfrastructureMapping infrastructureMapping) {
    return createPerpetualTaskInternal(infrastructureMapping);
  }

  @Override
  public List<String> createPerpetualTasksForNewDeployment(List<DeploymentSummary> deploymentSummaries,
      List<PerpetualTaskRecord> existingPerpetualTasks, InfrastructureMapping infrastructureMapping) {
    if (isNotEmpty(existingPerpetualTasks)) {
      existingPerpetualTasks.stream().distinct().forEach(
          task -> perpetualTaskService.deleteTask(task.getAccountId(), task.getUuid()));
    }
    return createPerpetualTaskInternal(infrastructureMapping);
  }

  private List<String> create(PdcPTClientParams clientParams, InfrastructureMapping infraMapping) {
    PerpetualTaskSchedule schedule = PerpetualTaskSchedule.newBuilder()
                                         .setInterval(Durations.fromMinutes(InstanceSyncConstants.INTERVAL_MINUTES))
                                         .setTimeout(Durations.fromSeconds(InstanceSyncConstants.TIMEOUT_SECONDS))
                                         .build();

    List<String> hosts = ((PhysicalInfrastructureMappingBase) infraMapping).getHostNames();
    List<String> tasks = new ArrayList<>();
    String concatenatedHosts = String.join(",", hosts);

    Map<String, String> clientParamMap = ImmutableMap.of(InstanceSyncConstants.INFRASTRUCTURE_MAPPING_ID,
        clientParams.getInframappingId(), InstanceSyncConstants.HARNESS_APPLICATION_ID, clientParams.getAppId(),
        InstanceSyncConstants.HOSTNAMES, concatenatedHosts);

    PerpetualTaskClientContext clientContext =
        PerpetualTaskClientContext.builder().clientParams(clientParamMap).build();

    String task = perpetualTaskService.createTask(PerpetualTaskType.PDC_INSTANCE_SYNC, infraMapping.getAccountId(),
        clientContext, schedule, false, getTaskDescription(infraMapping));
    tasks.add(task);

    return tasks;
  }

  private List<String> createPerpetualTaskInternal(InfrastructureMapping infrastructureMapping) {
    PdcPTClientParams params = PdcPTClientParams.builder()
                                   .appId(infrastructureMapping.getAppId())
                                   .inframappingId(infrastructureMapping.getUuid())
                                   .build();
    return create(params, infrastructureMapping);
  }
}
