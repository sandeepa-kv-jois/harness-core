/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.service.instancedashboardservice;

import static java.lang.System.currentTimeMillis;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.dtos.InstanceDTO;
import io.harness.entities.Instance;
import io.harness.mappers.InstanceDetailsMapper;
import io.harness.mappers.InstanceMapper;
import io.harness.models.ActiveServiceInstanceInfo;
import io.harness.models.ActiveServiceInstanceInfoV2;
import io.harness.models.BuildsByEnvironment;
import io.harness.models.EnvBuildInstanceCount;
import io.harness.models.InstanceDTOsByBuildId;
import io.harness.models.InstanceDetailsByBuildId;
import io.harness.models.InstancesByBuildId;
import io.harness.models.constants.InstanceSyncConstants;
import io.harness.models.dashboard.InstanceCountDetails;
import io.harness.models.dashboard.InstanceCountDetailsByEnvTypeAndServiceId;
import io.harness.models.dashboard.InstanceCountDetailsByEnvTypeBase;
import io.harness.models.dashboard.InstanceCountDetailsByService;
import io.harness.ng.core.environment.beans.EnvironmentType;
import io.harness.service.instance.InstanceService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;

@Singleton
@OwnedBy(HarnessTeam.DX)
@AllArgsConstructor(onConstructor = @__({ @Inject }))
public class InstanceDashboardServiceImpl implements InstanceDashboardService {
  private InstanceService instanceService;
  private InstanceDetailsMapper instanceDetailsMapper;

  /**
   * API to fetch active instance count overview for given account+org+project group by env type
   * @param accountIdentifier
   * @param orgIdentifier
   * @param projectIdentifier
   * @return total overall instance count group by env type combined with same details per service level
   */
  @Override
  public InstanceCountDetails getActiveInstanceCountDetailsByEnvType(
      String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    List<InstanceDTO> instances =
        instanceService.getActiveInstances(accountIdentifier, orgIdentifier, projectIdentifier, currentTimeMillis());

    Map<String, Map<EnvironmentType, Integer>> serviceVsInstanceCountMap = new HashMap<>();
    instances.forEach(instance -> {
      if (!serviceVsInstanceCountMap.containsKey(instance.getServiceIdentifier())) {
        serviceVsInstanceCountMap.put(instance.getServiceIdentifier(), new HashMap<>());
      }
      incrementValueForGivenEnvType(
          serviceVsInstanceCountMap.get(instance.getServiceIdentifier()), instance.getEnvType(), 1);
    });

    return prepareInstanceCountDetailsResponse(serviceVsInstanceCountMap);
  }

  /**
   * API to fetch all active instances for given account+org+project+service at a given time grouped by environment and
   * build
   * @param accountIdentifier
   * @param orgIdentifier
   * @param projectIdentifier
   * @param timestampInMs
   * @return List of instances grouped by environment and build
   */
  @Override
  public List<BuildsByEnvironment> getActiveInstancesByServiceIdGroupedByEnvironmentAndBuild(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String serviceId, long timestampInMs) {
    List<InstanceDTO> instances = instanceService.getActiveInstancesByServiceId(
        accountIdentifier, orgIdentifier, projectIdentifier, serviceId, timestampInMs);

    // used to map a list of instances to build and map it further to environment
    Map<String, Map<String, List<InstanceDTO>>> instanceGroupMap = new HashMap<>();
    instances.forEach(instance -> {
      String envId = instance.getEnvIdentifier();
      String buildId = instance.getPrimaryArtifact().getTag();
      if (!instanceGroupMap.containsKey(envId)) {
        instanceGroupMap.put(envId, new HashMap<>());
      }
      if (!instanceGroupMap.get(envId).containsKey(buildId)) {
        instanceGroupMap.get(envId).put(buildId, new ArrayList());
      }
      instanceGroupMap.get(envId).get(buildId).add(instance);
    });

    return prepareInstanceGroupedByEnvironmentAndBuildData(instanceGroupMap);
  }

  /**
   * API to fetch all unique combinations of environment and build with instance count
   * @param accountIdentifier
   * @param orgIdentifier
   * @param projectIdentifier
   * @param serviceId
   * @param timestampInMs
   * @return List of unique environment and build ids with instance count
   */
  @Override
  public List<EnvBuildInstanceCount> getEnvBuildInstanceCountByServiceId(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String serviceId, long timestampInMs) {
    AggregationResults<EnvBuildInstanceCount> envBuildInstanceCountAggregationResults =
        instanceService.getEnvBuildInstanceCountByServiceId(
            accountIdentifier, orgIdentifier, projectIdentifier, serviceId, timestampInMs);
    List<EnvBuildInstanceCount> envBuildInstanceCounts = new ArrayList<>();

    envBuildInstanceCountAggregationResults.getMappedResults().forEach(envBuildInstanceCount -> {
      final String envId = envBuildInstanceCount.getEnvIdentifier();
      final String envName = envBuildInstanceCount.getEnvName();
      final String buildId = envBuildInstanceCount.getTag();
      final Integer count = envBuildInstanceCount.getCount();
      envBuildInstanceCounts.add(new EnvBuildInstanceCount(envId, envName, buildId, count));
    });

    return envBuildInstanceCounts;
  }

  @Override
  public List<ActiveServiceInstanceInfo> getActiveServiceInstanceInfo(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String serviceId) {
    AggregationResults<ActiveServiceInstanceInfo> activeServiceInstanceInfoAggregationResults =
        instanceService.getActiveServiceInstanceInfo(accountIdentifier, orgIdentifier, projectIdentifier, serviceId);
    List<ActiveServiceInstanceInfo> activeServiceInstanceInfoList = new ArrayList<>();

    activeServiceInstanceInfoAggregationResults.getMappedResults().forEach(activeServiceInstanceInfo -> {
      final String infraIdentifier = activeServiceInstanceInfo.getInfraIdentifier();
      final String infraName = activeServiceInstanceInfo.getInfraName();
      final String clusterIdentifier = activeServiceInstanceInfo.getClusterIdentifier();
      final String agentIdentifier = activeServiceInstanceInfo.getAgentIdentifier();
      final String lastPipelineExecutionId = activeServiceInstanceInfo.getLastPipelineExecutionId();
      final String lastPipelineExecutionName = activeServiceInstanceInfo.getLastPipelineExecutionName();
      final String lastDeployedAt = activeServiceInstanceInfo.getLastDeployedAt();
      final String envId = activeServiceInstanceInfo.getEnvIdentifier();
      final String envName = activeServiceInstanceInfo.getEnvName();
      final String buildId = activeServiceInstanceInfo.getTag();
      final String displayName = activeServiceInstanceInfo.getDisplayName();
      final Integer count = activeServiceInstanceInfo.getCount();
      activeServiceInstanceInfoList.add(new ActiveServiceInstanceInfo(infraIdentifier, infraName, clusterIdentifier,
          agentIdentifier, lastPipelineExecutionId, lastPipelineExecutionName, lastDeployedAt, envId, envName, buildId,
          displayName, count));
    });

    return activeServiceInstanceInfoList;
  }

  @Override
  public List<ActiveServiceInstanceInfoV2> getActiveServiceInstanceInfo(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String envIdentifier, String serviceIdentifier, String buildIdentifier,
      boolean isGitOps) {
    AggregationResults<ActiveServiceInstanceInfoV2> activeServiceInstanceInfoAggregationResults;
    if (!isGitOps) {
      activeServiceInstanceInfoAggregationResults = instanceService.getActiveServiceInstanceInfo(
          accountIdentifier, orgIdentifier, projectIdentifier, envIdentifier, serviceIdentifier, buildIdentifier);
    } else {
      activeServiceInstanceInfoAggregationResults = instanceService.getActiveServiceGitOpsInstanceInfo(
          accountIdentifier, orgIdentifier, projectIdentifier, envIdentifier, serviceIdentifier, buildIdentifier);
    }
    List<ActiveServiceInstanceInfoV2> activeServiceInstanceInfoList = new ArrayList<>();

    activeServiceInstanceInfoAggregationResults.getMappedResults().forEach(activeServiceInstanceInfo -> {
      final String serviceId = activeServiceInstanceInfo.getServiceIdentifier();
      final String serviceName = activeServiceInstanceInfo.getServiceName();
      final String envId = activeServiceInstanceInfo.getEnvIdentifier();
      final String envName = activeServiceInstanceInfo.getEnvName();
      final String infraIdentifier = activeServiceInstanceInfo.getInfraIdentifier();
      final String infraName = activeServiceInstanceInfo.getInfraName();
      final String agentIdentifier = activeServiceInstanceInfo.getAgentIdentifier();
      final String clusterIdentifier = activeServiceInstanceInfo.getClusterIdentifier();
      final String lastPipelineExecutionId = activeServiceInstanceInfo.getLastPipelineExecutionId();
      final String lastPipelineExecutionName = activeServiceInstanceInfo.getLastPipelineExecutionName();
      final Long lastDeployedAt = activeServiceInstanceInfo.getLastDeployedAt();
      final String buildId = activeServiceInstanceInfo.getTag();
      final String displayName = activeServiceInstanceInfo.getDisplayName();
      final Integer count = activeServiceInstanceInfo.getCount();
      activeServiceInstanceInfoList.add(new ActiveServiceInstanceInfoV2(serviceId, serviceName, envId, envName,
          infraIdentifier, infraName, clusterIdentifier, agentIdentifier, lastPipelineExecutionId,
          lastPipelineExecutionName, lastDeployedAt, buildId, displayName, count));
    });

    return activeServiceInstanceInfoList;
  }

  @Override
  public List<ActiveServiceInstanceInfo> getActiveServiceGitOpsInstanceInfo(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String serviceId) {
    AggregationResults<ActiveServiceInstanceInfo> activeServiceInstanceInfoAggregationResults =
        instanceService.getActiveServiceGitOpsInstanceInfo(
            accountIdentifier, orgIdentifier, projectIdentifier, serviceId);
    List<ActiveServiceInstanceInfo> activeServiceInstanceInfoList = new ArrayList<>();

    activeServiceInstanceInfoAggregationResults.getMappedResults().forEach(activeServiceInstanceInfo -> {
      final String clusterIdentifier = activeServiceInstanceInfo.getClusterIdentifier();
      final String agentIdentifier = activeServiceInstanceInfo.getAgentIdentifier();
      final String lastPipelineExecutionId = activeServiceInstanceInfo.getLastPipelineExecutionId();
      final String lastPipelineExecutionName = activeServiceInstanceInfo.getLastPipelineExecutionName();
      final String lastDeployedAt = activeServiceInstanceInfo.getLastDeployedAt();
      final String envId = activeServiceInstanceInfo.getEnvIdentifier();
      final String envName = activeServiceInstanceInfo.getEnvName();
      final String buildId = activeServiceInstanceInfo.getTag();
      final String displayName = activeServiceInstanceInfo.getDisplayName();
      final Integer count = activeServiceInstanceInfo.getCount();
      activeServiceInstanceInfoList.add(
          new ActiveServiceInstanceInfo(null, null, clusterIdentifier, agentIdentifier, lastPipelineExecutionId,
              lastPipelineExecutionName, lastDeployedAt, envId, envName, buildId, displayName, count));
    });

    return activeServiceInstanceInfoList;
  }

  /**
   * API to fetch all active instances for given account+org+project+service+env and list of buildIds at a given time
   * @param accountIdentifier
   * @param orgIdentifier
   * @param projectIdentifier
   * @param serviceId
   * @param envId
   * @param buildIds
   * @param timestampInMs
   * @param infraId
   * @param clusterId
   * @param pipelineExecutionId
   * @param lastDeployedAt
   * @return List of buildId and instances
   */
  @Override
  public List<InstanceDetailsByBuildId> getActiveInstancesByServiceIdEnvIdAndBuildIds(String accountIdentifier,
      String orgIdentifier, String projectIdentifier, String serviceId, String envId, List<String> buildIds,
      long timestampInMs, String infraId, String clusterId, String pipelineExecutionId, long lastDeployedAt) {
    AggregationResults<InstancesByBuildId> buildIdAndInstancesAggregationResults =
        instanceService.getActiveInstancesByServiceIdEnvIdAndBuildIds(accountIdentifier, orgIdentifier,
            projectIdentifier, serviceId, envId, buildIds, timestampInMs, InstanceSyncConstants.INSTANCE_LIMIT, infraId,
            clusterId, pipelineExecutionId, lastDeployedAt);
    List<InstanceDetailsByBuildId> buildIdAndInstancesList = new ArrayList<>();

    buildIdAndInstancesAggregationResults.getMappedResults().forEach(buildIdAndInstances -> {
      String buildId = buildIdAndInstances.getBuildId();
      List<Instance> instances = buildIdAndInstances.getInstances();
      buildIdAndInstancesList.add(new InstanceDetailsByBuildId(
          buildId, instanceDetailsMapper.toInstanceDetailsDTOList(InstanceMapper.toDTO(instances))));
    });

    return buildIdAndInstancesList;
  }

  @Override
  public InstanceDetailsByBuildId getActiveInstanceDetails(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String serviceId, String envId, String infraId, String clusterIdentifier,
      String pipelineExecutionId, String buildId) {
    List<Instance> instancesByBuildId =
        instanceService.getActiveInstanceDetails(accountIdentifier, orgIdentifier, projectIdentifier, serviceId, envId,
            infraId, clusterIdentifier, pipelineExecutionId, buildId, InstanceSyncConstants.INSTANCE_LIMIT);

    return InstanceDetailsByBuildId.builder()
        .buildId(buildId)
        .instances(instanceDetailsMapper.toInstanceDetailsDTOList(InstanceMapper.toDTO(instancesByBuildId)))
        .build();
  }

  /*
    Returns breakup of active instances by envType at a given timestamp for specified accountIdentifier,
    projectIdentifier, orgIdentifier and serviceIds
  */
  @Override
  public InstanceCountDetailsByEnvTypeAndServiceId getActiveServiceInstanceCountBreakdown(String accountIdentifier,
      String orgIdentifier, String projectIdentifier, List<String> serviceId, long timestampInMs) {
    Map<String, Map<EnvironmentType, Integer>> serviceIdToEnvTypeVsInstanceCountMap = new HashMap<>();
    instanceService
        .getActiveServiceInstanceCountBreakdown(
            accountIdentifier, orgIdentifier, projectIdentifier, serviceId, timestampInMs)
        .getMappedResults()
        .forEach(countByEnvType -> {
          final String currentServiceId = countByEnvType.getServiceIdentifier();
          serviceIdToEnvTypeVsInstanceCountMap.putIfAbsent(currentServiceId, new HashMap<>());
          serviceIdToEnvTypeVsInstanceCountMap.get(currentServiceId)
              .put(countByEnvType.getEnvType(), countByEnvType.getCount());
        });

    Map<String, InstanceCountDetailsByEnvTypeBase> instanceCountDetailsByEnvTypeBaseMap = new HashMap<>();
    serviceIdToEnvTypeVsInstanceCountMap.forEach(
        (String currentServiceId, Map<EnvironmentType, Integer> envTypeVsInstanceCountMap) -> {
          final InstanceCountDetailsByEnvTypeBase instanceCountDetailsByEnvTypeBase =
              InstanceCountDetailsByEnvTypeBase.builder().envTypeVsInstanceCountMap(envTypeVsInstanceCountMap).build();
          instanceCountDetailsByEnvTypeBaseMap.putIfAbsent(currentServiceId, instanceCountDetailsByEnvTypeBase);
        });
    return InstanceCountDetailsByEnvTypeAndServiceId.builder()
        .instanceCountDetailsByEnvTypeBaseMap(instanceCountDetailsByEnvTypeBaseMap)
        .build();
  }

  // ----------------------------- PRIVATE METHODS -----------------------------

  private InstanceCountDetails prepareInstanceCountDetailsResponse(
      Map<String, Map<EnvironmentType, Integer>> serviceVsInstanceCountMap) {
    Map<EnvironmentType, Integer> envTypeVsIntegerCountMap = new HashMap<>();
    List<InstanceCountDetailsByService> instanceCountDetailsByServiceList = new ArrayList<>();

    serviceVsInstanceCountMap.keySet().forEach(serviceId -> {
      instanceCountDetailsByServiceList.add(
          new InstanceCountDetailsByService(serviceVsInstanceCountMap.get(serviceId), serviceId));
      incrementValueForGivenEnvType(envTypeVsIntegerCountMap, EnvironmentType.PreProduction,
          serviceVsInstanceCountMap.get(serviceId).get(EnvironmentType.PreProduction));
      incrementValueForGivenEnvType(envTypeVsIntegerCountMap, EnvironmentType.Production,
          serviceVsInstanceCountMap.get(serviceId).get(EnvironmentType.Production));
    });

    return new InstanceCountDetails(envTypeVsIntegerCountMap, instanceCountDetailsByServiceList);
  }

  private void incrementValueForGivenEnvType(
      Map<EnvironmentType, Integer> envTypeVsIntegerCountMap, EnvironmentType environmentType, int value) {
    envTypeVsIntegerCountMap.put(environmentType, value + envTypeVsIntegerCountMap.getOrDefault(environmentType, 0));
  }

  private List<BuildsByEnvironment> prepareInstanceGroupedByEnvironmentAndBuildData(
      Map<String, Map<String, List<InstanceDTO>>> instanceGroupMap) {
    List<BuildsByEnvironment> buildsByEnvironment = new ArrayList<>();
    for (String envId : instanceGroupMap.keySet()) {
      List<InstanceDTOsByBuildId> instancesByBuilds = new ArrayList<>();
      for (String buildId : instanceGroupMap.get(envId).keySet()) {
        instancesByBuilds.add(new InstanceDTOsByBuildId(buildId, instanceGroupMap.get(envId).get(buildId)));
      }
      buildsByEnvironment.add(new BuildsByEnvironment(envId, instancesByBuilds));
    }
    return buildsByEnvironment;
  }
}
