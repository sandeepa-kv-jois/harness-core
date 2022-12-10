/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.service.instancedashboardservice;

import static io.harness.rule.OwnerRule.ABHISHEK;
import static io.harness.rule.OwnerRule.PIYUSH_BHUWALKA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.InstancesTestBase;
import io.harness.category.element.UnitTests;
import io.harness.dtos.InstanceDTO;
import io.harness.entities.ArtifactDetails;
import io.harness.entities.Instance;
import io.harness.entities.Instance.InstanceBuilder;
import io.harness.entities.InstanceType;
import io.harness.entities.instanceinfo.GitopsInstanceInfo;
import io.harness.entities.instanceinfo.K8sInstanceInfo;
import io.harness.mappers.InstanceDetailsMapper;
import io.harness.models.ActiveServiceInstanceInfoV2;
import io.harness.models.BuildsByEnvironment;
import io.harness.models.CountByServiceIdAndEnvType;
import io.harness.models.EnvBuildInstanceCount;
import io.harness.models.InstanceDetailsByBuildId;
import io.harness.models.InstanceDetailsDTO;
import io.harness.models.InstancesByBuildId;
import io.harness.models.constants.InstanceSyncConstants;
import io.harness.models.dashboard.InstanceCountDetails;
import io.harness.models.dashboard.InstanceCountDetailsByEnvTypeAndServiceId;
import io.harness.models.dashboard.InstanceCountDetailsByEnvTypeBase;
import io.harness.ng.core.environment.beans.EnvironmentType;
import io.harness.repositories.instance.InstanceRepository;
import io.harness.rule.Owner;
import io.harness.service.instance.InstanceService;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bson.Document;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;

public class InstanceDashboardServiceImplTest extends InstancesTestBase {
  private final String ACCOUNT_IDENTIFIER = "acc";
  private final String PROJECT_IDENTIFIER = "proj";
  private final String ORG_IDENTIFIER = "org";
  private final String SERVICE_IDENTIFIER = "serv";
  private final String ENV_IDENTIFIER = "env";
  private final List<String> BUILD_IDS = Arrays.asList("id1", "id2");
  @Mock InstanceService instanceService;
  @Mock InstanceDetailsMapper instanceDetailsMapper;
  @InjectMocks InstanceDashboardServiceImpl instanceDashboardService;
  @Inject InstanceDashboardServiceImpl instanceDashboardService1;
  @Inject InstanceRepository instanceRepository;

  public static List<Instance> getInstanceList() {
    List<Instance> instances = new ArrayList<>();
    InstanceBuilder instanceBuilder =
        Instance.builder()
            .accountIdentifier("accountId")
            .orgIdentifier("orgId")
            .projectIdentifier("projectId")
            .createdAt(1l)
            .lastModifiedAt(1l)
            .serviceIdentifier("svc1")
            .serviceName("svcN1")
            .envIdentifier("env1")
            .envName("env1")
            .infraIdentifier("infra1")
            .infraName("infra1")
            .instanceInfo(K8sInstanceInfo.builder().podName("infra1").namespace("infra1").build())
            .primaryArtifact(ArtifactDetails.builder().tag("1").displayName("artifact1:1").build());
    instances.add(
        instanceBuilder.lastPipelineExecutionId("1").lastPipelineExecutionName("a").lastDeployedAt(1l).build());
    instances.add(
        instanceBuilder.lastPipelineExecutionId("1").lastPipelineExecutionName("a").lastDeployedAt(2l).build());
    instances.add(
        instanceBuilder.lastPipelineExecutionId("1").lastPipelineExecutionName("a").lastDeployedAt(2l).build());
    instances.add(
        instanceBuilder.lastPipelineExecutionId("2").lastPipelineExecutionName("b").lastDeployedAt(1l).build());

    instanceBuilder.infraIdentifier("infra2").infraName("infra2").instanceInfo(
        K8sInstanceInfo.builder().podName("infra2").namespace("infra2").build());
    instances.add(
        instanceBuilder.lastPipelineExecutionId("1").lastPipelineExecutionName("a").lastDeployedAt(1l).build());
    instanceBuilder.envIdentifier("env2").envName("env2").primaryArtifact(
        ArtifactDetails.builder().tag("2").displayName("artifact2:2").build());
    instances.add(
        instanceBuilder.lastPipelineExecutionId("2").lastPipelineExecutionName("b").lastDeployedAt(1l).build());
    instanceBuilder.serviceIdentifier("svc2")
        .serviceName("svcN2")
        .envIdentifier("env1")
        .envName("env1")
        .infraIdentifier("infra1")
        .infraName("infra1")
        .instanceInfo(K8sInstanceInfo.builder().podName("infra1").namespace("infra1").build())
        .primaryArtifact(ArtifactDetails.builder().tag("1").displayName("artifact1:1").build());
    instances.add(
        instanceBuilder.lastPipelineExecutionId("1").lastPipelineExecutionName("a").lastDeployedAt(1l).build());
    instances.add(
        instanceBuilder.lastPipelineExecutionId("1").lastPipelineExecutionName("a").lastDeployedAt(2l).build());
    instances.add(
        instanceBuilder.lastPipelineExecutionId("1").lastPipelineExecutionName("a").lastDeployedAt(2l).build());
    instances.add(
        instanceBuilder.lastPipelineExecutionId("2").lastPipelineExecutionName("b").lastDeployedAt(1l).build());
    instanceBuilder.infraIdentifier("infra2").infraName("infra2").instanceInfo(
        K8sInstanceInfo.builder().podName("infra2").namespace("infra2").build());
    instances.add(
        instanceBuilder.lastPipelineExecutionId("1").lastPipelineExecutionName("a").lastDeployedAt(1l).build());
    instanceBuilder.envIdentifier("env2").envName("env2").primaryArtifact(
        ArtifactDetails.builder().tag("2").displayName("artifact2:2").build());
    instances.add(
        instanceBuilder.lastPipelineExecutionId("2").lastPipelineExecutionName("b").lastDeployedAt(1l).build());

    instanceBuilder.serviceIdentifier("svc1")
        .serviceName("svcN1")
        .envIdentifier("env1")
        .envName("env1")
        .infraIdentifier(null)
        .infraName(null)
        .instanceInfo(GitopsInstanceInfo.builder()
                          .clusterIdentifier("infra1")
                          .agentIdentifier("infra1")
                          .podName("infra1")
                          .build())
        .primaryArtifact(ArtifactDetails.builder().tag("1").displayName("artifact1:1").build());
    instances.add(
        instanceBuilder.lastPipelineExecutionId("1").lastPipelineExecutionName("a").lastDeployedAt(1l).build());
    instances.add(
        instanceBuilder.lastPipelineExecutionId("1").lastPipelineExecutionName("a").lastDeployedAt(2l).build());
    instances.add(
        instanceBuilder.lastPipelineExecutionId("1").lastPipelineExecutionName("a").lastDeployedAt(2l).build());
    instances.add(
        instanceBuilder.lastPipelineExecutionId("2").lastPipelineExecutionName("b").lastDeployedAt(1l).build());

    instanceBuilder.instanceInfo(
        GitopsInstanceInfo.builder().clusterIdentifier("infra2").agentIdentifier("infra2").podName("infra2").build());
    instances.add(
        instanceBuilder.lastPipelineExecutionId("1").lastPipelineExecutionName("a").lastDeployedAt(1l).build());
    instanceBuilder.envIdentifier("env2").envName("env2").primaryArtifact(
        ArtifactDetails.builder().tag("2").displayName("artifact2:2").build());
    instances.add(
        instanceBuilder.lastPipelineExecutionId("2").lastPipelineExecutionName("b").lastDeployedAt(1l).build());
    instanceBuilder.serviceIdentifier("svc2")
        .serviceName("svcN2")
        .envIdentifier("env1")
        .envName("env1")
        .primaryArtifact(ArtifactDetails.builder().tag("1").displayName("artifact1:1").build())
        .instanceInfo(GitopsInstanceInfo.builder()
                          .clusterIdentifier("infra1")
                          .agentIdentifier("infra1")
                          .podName("infra1")
                          .build());
    instances.add(
        instanceBuilder.lastPipelineExecutionId("1").lastPipelineExecutionName("a").lastDeployedAt(1l).build());
    instances.add(
        instanceBuilder.lastPipelineExecutionId("1").lastPipelineExecutionName("a").lastDeployedAt(2l).build());
    instances.add(
        instanceBuilder.lastPipelineExecutionId("1").lastPipelineExecutionName("a").lastDeployedAt(2l).build());
    instances.add(
        instanceBuilder.lastPipelineExecutionId("2").lastPipelineExecutionName("b").lastDeployedAt(1l).build());
    instanceBuilder.instanceInfo(
        GitopsInstanceInfo.builder().clusterIdentifier("infra2").agentIdentifier("infra2").podName("infra2").build());
    instances.add(
        instanceBuilder.lastPipelineExecutionId("1").lastPipelineExecutionName("a").lastDeployedAt(1l).build());
    instanceBuilder.envIdentifier("env2").envName("env2").primaryArtifact(
        ArtifactDetails.builder().tag("2").displayName("artifact2:2").build());
    instances.add(
        instanceBuilder.lastPipelineExecutionId("2").lastPipelineExecutionName("b").lastDeployedAt(1l).build());
    return instances;
  }
  public void activateInstances() {
    for (Instance instance : getInstanceList()) {
      instanceRepository.save(instance);
    }
  }

  List<ActiveServiceInstanceInfoV2> getSampleListActiveServiceInstanceInfo(String serviceId, String serviceName) {
    List<ActiveServiceInstanceInfoV2> activeServiceInstanceInfo = new ArrayList<>();
    ActiveServiceInstanceInfoV2 instance1 = new ActiveServiceInstanceInfoV2(
        serviceId, serviceName, "env1", "env1", "infra1", "infra1", null, null, "1", "a", 1l, "1", "artifact1:1", 1);
    activeServiceInstanceInfo.add(instance1);
    instance1 = new ActiveServiceInstanceInfoV2(
        serviceId, serviceName, "env1", "env1", "infra1", "infra1", null, null, "1", "a", 2l, "1", "artifact1:1", 2);
    activeServiceInstanceInfo.add(instance1);
    instance1 = new ActiveServiceInstanceInfoV2(
        serviceId, serviceName, "env1", "env1", "infra1", "infra1", null, null, "2", "b", 1l, "1", "artifact1:1", 1);
    activeServiceInstanceInfo.add(instance1);
    instance1 = new ActiveServiceInstanceInfoV2(
        serviceId, serviceName, "env1", "env1", "infra2", "infra2", null, null, "1", "a", 1l, "1", "artifact1:1", 1);
    activeServiceInstanceInfo.add(instance1);
    return activeServiceInstanceInfo;
  }

  List<ActiveServiceInstanceInfoV2> getSampleListActiveServiceInstanceInfoEnv2(String serviceId, String serviceName) {
    List<ActiveServiceInstanceInfoV2> activeServiceInstanceInfo = new ArrayList<>();
    ActiveServiceInstanceInfoV2 instance1 = new ActiveServiceInstanceInfoV2(
        serviceId, serviceName, "env2", "env2", "infra2", "infra2", null, null, "2", "b", 1l, "2", "artifact2:2", 1);
    activeServiceInstanceInfo.add(instance1);
    return activeServiceInstanceInfo;
  }

  List<ActiveServiceInstanceInfoV2> getSampleListActiveServiceInstanceInfoGitOps(String serviceId, String serviceName) {
    List<ActiveServiceInstanceInfoV2> activeServiceInstanceInfo = new ArrayList<>();
    ActiveServiceInstanceInfoV2 instance1 = new ActiveServiceInstanceInfoV2(
        serviceId, serviceName, "env1", "env1", null, null, "infra1", "infra1", "1", "a", 1l, "1", "artifact1:1", 1);
    activeServiceInstanceInfo.add(instance1);
    instance1 = new ActiveServiceInstanceInfoV2(
        serviceId, serviceName, "env1", "env1", null, null, "infra1", "infra1", "1", "a", 2l, "1", "artifact1:1", 2);
    activeServiceInstanceInfo.add(instance1);
    instance1 = new ActiveServiceInstanceInfoV2(
        serviceId, serviceName, "env1", "env1", null, null, "infra1", "infra1", "2", "b", 1l, "1", "artifact1:1", 1);
    activeServiceInstanceInfo.add(instance1);
    instance1 = new ActiveServiceInstanceInfoV2(
        serviceId, serviceName, "env1", "env1", null, null, "infra2", "infra2", "1", "a", 1l, "1", "artifact1:1", 1);
    activeServiceInstanceInfo.add(instance1);
    return activeServiceInstanceInfo;
  }

  List<ActiveServiceInstanceInfoV2> getSampleListActiveServiceInstanceInfoGitOpsEnv2(
      String serviceId, String serviceName) {
    List<ActiveServiceInstanceInfoV2> activeServiceInstanceInfo = new ArrayList<>();
    ActiveServiceInstanceInfoV2 instance1 = new ActiveServiceInstanceInfoV2(
        serviceId, serviceName, "env2", "env2", null, null, "infra2", "infra2", "2", "b", 1l, "2", "artifact2:2", 1);
    activeServiceInstanceInfo.add(instance1);
    return activeServiceInstanceInfo;
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void getActiveInstanceCountDetailsByEnvTypeTest() {
    InstanceDTO instanceDTO = InstanceDTO.builder()
                                  .serviceIdentifier(SERVICE_IDENTIFIER)
                                  .envIdentifier(ENV_IDENTIFIER)
                                  .envType(EnvironmentType.Production)
                                  .build();
    InstanceDTO instanceDTO1 = InstanceDTO.builder()
                                   .serviceIdentifier(SERVICE_IDENTIFIER)
                                   .envIdentifier(ENV_IDENTIFIER)
                                   .envType(EnvironmentType.PreProduction)
                                   .build();
    InstanceDTO instanceDTO2 = InstanceDTO.builder()
                                   .serviceIdentifier(SERVICE_IDENTIFIER + "2")
                                   .envIdentifier(ENV_IDENTIFIER)
                                   .envType(EnvironmentType.Production)
                                   .build();
    InstanceDTO instanceDTO3 = InstanceDTO.builder()
                                   .serviceIdentifier(SERVICE_IDENTIFIER + "2")
                                   .envIdentifier(ENV_IDENTIFIER)
                                   .envType(EnvironmentType.PreProduction)
                                   .build();
    InstanceDTO instanceDTO4 = InstanceDTO.builder()
                                   .serviceIdentifier(SERVICE_IDENTIFIER + "2")
                                   .envIdentifier(ENV_IDENTIFIER + "2")
                                   .envType(EnvironmentType.Production)
                                   .build();
    when(instanceService.getActiveInstances(anyString(), anyString(), anyString(), anyLong()))
        .thenReturn(Arrays.asList(instanceDTO, instanceDTO1, instanceDTO2, instanceDTO3, instanceDTO4));

    InstanceCountDetails instanceCountDetails = instanceDashboardService.getActiveInstanceCountDetailsByEnvType(
        ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER);
    assertThat(instanceCountDetails.getProdInstances()).isEqualTo(3);
    assertThat(instanceCountDetails.getNonProdInstances()).isEqualTo(2);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void getActiveInstancesByServiceIdGroupedByEnvironmentAndBuildTest() {
    InstanceDTO instanceDTO = InstanceDTO.builder()
                                  .serviceIdentifier(SERVICE_IDENTIFIER)
                                  .envIdentifier(ENV_IDENTIFIER)
                                  .primaryArtifact(ArtifactDetails.builder().tag("tag1").build())
                                  .envType(EnvironmentType.Production)
                                  .build();
    InstanceDTO instanceDTO1 = InstanceDTO.builder()
                                   .serviceIdentifier(SERVICE_IDENTIFIER)
                                   .envIdentifier(ENV_IDENTIFIER)
                                   .primaryArtifact(ArtifactDetails.builder().tag("tag1").build())
                                   .envType(EnvironmentType.PreProduction)
                                   .build();
    InstanceDTO instanceDTO2 = InstanceDTO.builder()
                                   .serviceIdentifier(SERVICE_IDENTIFIER + "2")
                                   .envIdentifier(ENV_IDENTIFIER)
                                   .primaryArtifact(ArtifactDetails.builder().tag("tag2").build())
                                   .envType(EnvironmentType.Production)
                                   .build();
    InstanceDTO instanceDTO3 = InstanceDTO.builder()
                                   .serviceIdentifier(SERVICE_IDENTIFIER + "2")
                                   .envIdentifier(ENV_IDENTIFIER)
                                   .primaryArtifact(ArtifactDetails.builder().tag("tag2").build())
                                   .envType(EnvironmentType.PreProduction)
                                   .build();
    InstanceDTO instanceDTO4 = InstanceDTO.builder()
                                   .serviceIdentifier(SERVICE_IDENTIFIER + "2")
                                   .envIdentifier(ENV_IDENTIFIER + "2")
                                   .primaryArtifact(ArtifactDetails.builder().tag("tag3").build())
                                   .envType(EnvironmentType.Production)
                                   .build();
    when(instanceService.getActiveInstancesByServiceId(anyString(), anyString(), anyString(), anyString(), anyLong()))
        .thenReturn(Arrays.asList(instanceDTO, instanceDTO1, instanceDTO2, instanceDTO3, instanceDTO4));

    List<BuildsByEnvironment> buildsByEnvironmentList =
        instanceDashboardService.getActiveInstancesByServiceIdGroupedByEnvironmentAndBuild(
            ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, SERVICE_IDENTIFIER, 10);
    assertThat(buildsByEnvironmentList.size()).isEqualTo(2);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void getEnvBuildInstanceCountByServiceIdTest() {
    EnvBuildInstanceCount envBuildInstanceCount = new EnvBuildInstanceCount(ENV_IDENTIFIER, "ENV", "TAG", 3);
    AggregationResults<EnvBuildInstanceCount> envBuildInstanceCountAggregationResults =
        new AggregationResults<>(Arrays.asList(envBuildInstanceCount), new Document());
    when(instanceService.getEnvBuildInstanceCountByServiceId(
             anyString(), anyString(), anyString(), anyString(), anyLong()))
        .thenReturn(envBuildInstanceCountAggregationResults);
    assertThat(instanceDashboardService
                   .getEnvBuildInstanceCountByServiceId(
                       ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, SERVICE_IDENTIFIER, 10)
                   .size())
        .isEqualTo(1);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void getActiveInstancesByServiceIdEnvIdAndBuildIdsTest() {
    String infraId = "infraId";
    String clusterId = "clusterId";
    String pipelineExecutionId = "pipelineExecutionId";
    long lastDeployedAt = System.currentTimeMillis();
    Instance instance = Instance.builder()
                            .accountIdentifier(ACCOUNT_IDENTIFIER)
                            .projectIdentifier(PROJECT_IDENTIFIER)
                            .orgIdentifier(ORG_IDENTIFIER)
                            .serviceIdentifier(SERVICE_IDENTIFIER)
                            .envIdentifier(ENV_IDENTIFIER)
                            .envName("env1")
                            .envType(EnvironmentType.Production)
                            .instanceKey("key")
                            .connectorRef("connector")
                            .id("id")
                            .createdAt(5L)
                            .deletedAt(10)
                            .instanceType(InstanceType.K8S_INSTANCE)
                            .infrastructureMappingId("mappingId")
                            .lastDeployedAt(3)
                            .lastDeployedByName("asdf")
                            .lastPipelineExecutionId("sdf")
                            .lastPipelineExecutionName("sdfasd")
                            .serviceName("serv")
                            .isDeleted(false)
                            .instanceInfo(K8sInstanceInfo.builder().build())
                            .lastModifiedAt(10L)
                            .build();
    InstancesByBuildId instanceDetailsByBuildId = new InstancesByBuildId("build1", Arrays.asList(instance));
    AggregationResults<InstancesByBuildId> instanceDetailsByBuildIdAggregationResults =
        new AggregationResults<>(Arrays.asList(instanceDetailsByBuildId), new Document());

    when(instanceService.getActiveInstancesByServiceIdEnvIdAndBuildIds(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER,
             PROJECT_IDENTIFIER, SERVICE_IDENTIFIER, ENV_IDENTIFIER, BUILD_IDS, 10,
             InstanceSyncConstants.INSTANCE_LIMIT, infraId, clusterId, pipelineExecutionId, lastDeployedAt))
        .thenReturn(instanceDetailsByBuildIdAggregationResults);
    List<InstanceDetailsByBuildId> instanceDetailsByBuildIdList =
        instanceDashboardService.getActiveInstancesByServiceIdEnvIdAndBuildIds(ACCOUNT_IDENTIFIER, ORG_IDENTIFIER,
            PROJECT_IDENTIFIER, SERVICE_IDENTIFIER, ENV_IDENTIFIER, BUILD_IDS, 10, infraId, clusterId,
            pipelineExecutionId, lastDeployedAt);
    assertThat(instanceDetailsByBuildIdList.size()).isEqualTo(1);
    assertThat(instanceDetailsByBuildIdList.get(0).getBuildId()).isEqualTo("build1");
    assertThat(instanceDetailsByBuildIdList.get(0).getInstances().size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void getActiveServiceInstanceCountBreakdownTest() {
    CountByServiceIdAndEnvType countByServiceIdAndEnvType =
        new CountByServiceIdAndEnvType(SERVICE_IDENTIFIER, EnvironmentType.Production, 2);
    AggregationResults<CountByServiceIdAndEnvType> countByServiceIdAndEnvTypeAggregationResults =
        new AggregationResults<>(Arrays.asList(countByServiceIdAndEnvType), new Document());
    when(instanceService.getActiveServiceInstanceCountBreakdown(
             ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, Arrays.asList(SERVICE_IDENTIFIER), 10))
        .thenReturn(countByServiceIdAndEnvTypeAggregationResults);
    Map<EnvironmentType, Integer> envTypeVsInstanceCountMap = new HashMap<>();
    envTypeVsInstanceCountMap.put(EnvironmentType.Production, 2);
    Map<String, InstanceCountDetailsByEnvTypeBase> instanceCountDetailsByEnvTypeBaseMap = new HashMap<>();
    instanceCountDetailsByEnvTypeBaseMap.put(SERVICE_IDENTIFIER,
        InstanceCountDetailsByEnvTypeBase.builder().envTypeVsInstanceCountMap(envTypeVsInstanceCountMap).build());
    InstanceCountDetailsByEnvTypeAndServiceId instanceCountDetailsByEnvTypeAndServiceId =
        instanceDashboardService.getActiveServiceInstanceCountBreakdown(
            ACCOUNT_IDENTIFIER, ORG_IDENTIFIER, PROJECT_IDENTIFIER, Arrays.asList(SERVICE_IDENTIFIER), 10);
    assertThat(instanceCountDetailsByEnvTypeAndServiceId.getInstanceCountDetailsByEnvTypeBaseMap().containsKey(
                   SERVICE_IDENTIFIER))
        .isTrue();
    assertThat(instanceCountDetailsByEnvTypeAndServiceId.getInstanceCountDetailsByEnvTypeBaseMap()
                   .get(SERVICE_IDENTIFIER)
                   .getProdInstances())
        .isEqualTo(2);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void getActiveServiceInstanceInfo_filterServiceId() {
    activateInstances();
    List<ActiveServiceInstanceInfoV2> activeServiceInstanceInfoV2List1 =
        getSampleListActiveServiceInstanceInfo("svc1", "svcN1");
    activeServiceInstanceInfoV2List1.addAll(getSampleListActiveServiceInstanceInfoEnv2("svc1", "svcN1"));
    List<ActiveServiceInstanceInfoV2> activeServiceInstanceInfoV2List =
        instanceDashboardService1.getActiveServiceInstanceInfo(
            "accountId", "orgId", "projectId", null, "svc1", null, false);
    assertThat(activeServiceInstanceInfoV2List1).isEqualTo(activeServiceInstanceInfoV2List);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void getActiveServiceInstanceInfo_filterServiceIdEnv() {
    activateInstances();
    List<ActiveServiceInstanceInfoV2> activeServiceInstanceInfoV2List1 =
        getSampleListActiveServiceInstanceInfo("svc1", "svcN1");
    List<ActiveServiceInstanceInfoV2> activeServiceInstanceInfoV2List =
        instanceDashboardService1.getActiveServiceInstanceInfo(
            "accountId", "orgId", "projectId", "env1", "svc1", null, false);
    assertThat(activeServiceInstanceInfoV2List1).isEqualTo(activeServiceInstanceInfoV2List);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void getActiveServiceInstanceInfo_filterServiceId_GitOps() {
    activateInstances();
    List<ActiveServiceInstanceInfoV2> activeServiceInstanceInfoV2List1 =
        getSampleListActiveServiceInstanceInfoGitOps("svc1", "svcN1");
    activeServiceInstanceInfoV2List1.addAll(getSampleListActiveServiceInstanceInfoGitOpsEnv2("svc1", "svcN1"));
    List<ActiveServiceInstanceInfoV2> activeServiceInstanceInfoV2List =
        instanceDashboardService1.getActiveServiceInstanceInfo(
            "accountId", "orgId", "projectId", null, "svc1", null, true);
    assertThat(activeServiceInstanceInfoV2List1).isEqualTo(activeServiceInstanceInfoV2List);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void getActiveServiceInstanceInfo_filterServiceIdEnv_GitOps() {
    activateInstances();
    List<ActiveServiceInstanceInfoV2> activeServiceInstanceInfoV2List1 =
        getSampleListActiveServiceInstanceInfoGitOps("svc1", "svcN1");
    List<ActiveServiceInstanceInfoV2> activeServiceInstanceInfoV2List =
        instanceDashboardService1.getActiveServiceInstanceInfo(
            "accountId", "orgId", "projectId", "env1", "svc1", null, true);
    assertThat(activeServiceInstanceInfoV2List1).isEqualTo(activeServiceInstanceInfoV2List);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void getActiveServiceInstanceInfo() {
    activateInstances();
    List<ActiveServiceInstanceInfoV2> activeServiceInstanceInfoV2List1 =
        getSampleListActiveServiceInstanceInfo("svc1", "svcN1");
    activeServiceInstanceInfoV2List1.addAll(getSampleListActiveServiceInstanceInfoEnv2("svc1", "svcN1"));
    activeServiceInstanceInfoV2List1.addAll(getSampleListActiveServiceInstanceInfo("svc2", "svcN2"));
    activeServiceInstanceInfoV2List1.addAll(getSampleListActiveServiceInstanceInfoEnv2("svc2", "svcN2"));
    List<ActiveServiceInstanceInfoV2> activeServiceInstanceInfoV2List =
        instanceDashboardService1.getActiveServiceInstanceInfo(
            "accountId", "orgId", "projectId", null, null, null, false);
    assertThat(activeServiceInstanceInfoV2List1).isEqualTo(activeServiceInstanceInfoV2List);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void getActiveServiceInstanceInfo_filterEnv() {
    activateInstances();
    List<ActiveServiceInstanceInfoV2> activeServiceInstanceInfoV2List1 =
        getSampleListActiveServiceInstanceInfo("svc1", "svcN1");
    activeServiceInstanceInfoV2List1.addAll(getSampleListActiveServiceInstanceInfo("svc2", "svcN2"));
    List<ActiveServiceInstanceInfoV2> activeServiceInstanceInfoV2List =
        instanceDashboardService1.getActiveServiceInstanceInfo(
            "accountId", "orgId", "projectId", "env1", null, null, false);
    assertThat(activeServiceInstanceInfoV2List1).isEqualTo(activeServiceInstanceInfoV2List);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void getActiveServiceInstanceInfo_GitOps() {
    activateInstances();
    List<ActiveServiceInstanceInfoV2> activeServiceInstanceInfoV2List1 =
        getSampleListActiveServiceInstanceInfoGitOps("svc1", "svcN1");
    activeServiceInstanceInfoV2List1.addAll(getSampleListActiveServiceInstanceInfoGitOpsEnv2("svc1", "svcN1"));
    activeServiceInstanceInfoV2List1.addAll(getSampleListActiveServiceInstanceInfoGitOps("svc2", "svcN2"));
    activeServiceInstanceInfoV2List1.addAll(getSampleListActiveServiceInstanceInfoGitOpsEnv2("svc2", "svcN2"));
    List<ActiveServiceInstanceInfoV2> activeServiceInstanceInfoV2List =
        instanceDashboardService1.getActiveServiceInstanceInfo(
            "accountId", "orgId", "projectId", null, null, null, true);
    assertThat(activeServiceInstanceInfoV2List1).isEqualTo(activeServiceInstanceInfoV2List);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void getActiveServiceInstanceInfo_GitOps_filterEnv() {
    activateInstances();
    List<ActiveServiceInstanceInfoV2> activeServiceInstanceInfoV2List1 =
        getSampleListActiveServiceInstanceInfoGitOps("svc1", "svcN1");
    activeServiceInstanceInfoV2List1.addAll(getSampleListActiveServiceInstanceInfoGitOps("svc2", "svcN2"));
    List<ActiveServiceInstanceInfoV2> activeServiceInstanceInfoV2List =
        instanceDashboardService1.getActiveServiceInstanceInfo(
            "accountId", "orgId", "projectId", "env1", null, null, true);
    assertThat(activeServiceInstanceInfoV2List1).isEqualTo(activeServiceInstanceInfoV2List);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void test_getActiveInstanceDetails_infra() {
    InstanceDetailsDTO instanceDetailsDTO1 = InstanceDetailsDTO.builder().build();
    InstanceDetailsDTO instanceDetailsDTO2 = InstanceDetailsDTO.builder().build();
    when(instanceDetailsMapper.toInstanceDetailsDTOList(anyList()))
        .thenReturn(Arrays.asList(instanceDetailsDTO1, instanceDetailsDTO2));

    InstanceDetailsByBuildId instanceDetailsByBuildId = instanceDashboardService.getActiveInstanceDetails(
        "accountId", "orgId", "projectId", "svc1", "env1", "infra1", null, "1", "1");

    verify(instanceService)
        .getActiveInstanceDetails("accountId", "orgId", "projectId", "svc1", "env1", "infra1", null, "1", "1",
            InstanceSyncConstants.INSTANCE_LIMIT);
    verify(instanceDetailsMapper).toInstanceDetailsDTOList(anyList());

    assertThat(instanceDetailsByBuildId.getInstances().get(0)).isEqualTo(instanceDetailsDTO1);
    assertThat(instanceDetailsByBuildId.getInstances().get(1)).isEqualTo(instanceDetailsDTO2);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void test_getActiveInstanceDetails_cluster() {
    InstanceDetailsDTO instanceDetailsDTO1 = InstanceDetailsDTO.builder().build();
    InstanceDetailsDTO instanceDetailsDTO2 = InstanceDetailsDTO.builder().build();
    when(instanceDetailsMapper.toInstanceDetailsDTOList(anyList()))
        .thenReturn(Arrays.asList(instanceDetailsDTO1, instanceDetailsDTO2));

    InstanceDetailsByBuildId instanceDetailsByBuildId = instanceDashboardService.getActiveInstanceDetails(
        "accountId", "orgId", "projectId", "svc1", "env1", null, "infra1", "1", "1");

    verify(instanceService)
        .getActiveInstanceDetails("accountId", "orgId", "projectId", "svc1", "env1", null, "infra1", "1", "1",
            InstanceSyncConstants.INSTANCE_LIMIT);
    verify(instanceDetailsMapper).toInstanceDetailsDTOList(anyList());

    assertThat(instanceDetailsByBuildId.getInstances().get(0)).isEqualTo(instanceDetailsDTO1);
    assertThat(instanceDetailsByBuildId.getInstances().get(1)).isEqualTo(instanceDetailsDTO2);
  }
}
