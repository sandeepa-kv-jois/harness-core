/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.approval.notification;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.BRIJESH;
import static io.harness.rule.OwnerRule.vivekveman;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.logging.LogLevel;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.logstreaming.NGLogCallback;
import io.harness.ng.core.dto.UserGroupDTO;
import io.harness.ng.core.notification.EmailConfigDTO;
import io.harness.ng.core.notification.NotificationSettingConfigDTO;
import io.harness.ng.core.notification.SlackConfigDTO;
import io.harness.notification.notificationclient.NotificationClient;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.EdgeLayoutList;
import io.harness.pms.contracts.plan.GraphLayoutNode;
import io.harness.pms.execution.ExecutionStatus;
import io.harness.pms.notification.NotificationHelper;
import io.harness.pms.pipeline.mappers.GraphLayoutDtoMapper;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.beans.dto.GraphLayoutNodeDTO;
import io.harness.pms.plan.execution.service.PMSExecutionService;
import io.harness.remote.client.NGRestUtils;
import io.harness.rule.Owner;
import io.harness.steps.approval.step.beans.ApprovalType;
import io.harness.steps.approval.step.entities.ApprovalInstance;
import io.harness.steps.approval.step.harness.beans.ApproversDTO;
import io.harness.steps.approval.step.harness.entities.HarnessApprovalInstance;
import io.harness.usergroups.UserGroupClient;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@OwnedBy(PIPELINE)
@RunWith(PowerMockRunner.class)
@PrepareForTest({ApprovalNotificationHandlerImpl.class, NGLogCallback.class, NGRestUtils.class})
@PowerMockIgnore({"javax.net.ssl.*"})
public class ApprovalNotificationHandlerImplTest extends CategoryTest {
  @Mock private UserGroupClient userGroupClient;
  @Mock private NotificationClient notificationClient;
  @Mock private NotificationHelper notificationHelper;
  @Mock private PMSExecutionService pmsExecutionService;
  @Mock private ApprovalInstance approvalInstance;
  @Mock private LogStreamingStepClientFactory logStreamingStepClientFactory;
  @InjectMocks ApprovalNotificationHandlerImpl approvalNotificationHandler;
  private static String accountId = "accountId";

  private static String userGroupIdentifier = "userGroupIdentifier";
  private static String orgIdentifier = "orgIdentifier";
  private static String projectIdentifier = "projectIdentifier";
  private static String pipelineIdentifier = "pipelineIdentifier";
  private static String startingNodeId = "startingNodeId";
  private NGLogCallback ngLogCallback;

  @Before
  public void setup() throws Exception {
    ngLogCallback = Mockito.mock(NGLogCallback.class);
    PowerMockito.whenNew(NGLogCallback.class).withAnyArguments().thenReturn(ngLogCallback);
    //        MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testSendNotification() throws Exception {
    String url =
        "https://qa.harness.io/ng/#/account/zEaak-FLS425IEO7OLzMUg/cd/orgs/CV/projects/Brijesh_Dhakar/pipelines/DockerTest/executions/szmvyw4wQR2W4_iKkq9bfQ/pipeline";
    Mockito.mockStatic(NGRestUtils.class);
    Ambiance ambiance = Ambiance.newBuilder()
                            .putSetupAbstractions("accountId", accountId)
                            .putSetupAbstractions("orgIdentifier", orgIdentifier)
                            .putSetupAbstractions("projectIdentifier", projectIdentifier)
                            .putSetupAbstractions("pipelineIdentifier", pipelineIdentifier)
                            .build();
    HarnessApprovalInstance approvalInstance =
        HarnessApprovalInstance.builder()
            .approvers(
                ApproversDTO.builder()
                    .userGroups(new ArrayList<>(Arrays.asList("proj_faulty", "proj_right", "org.org_faulty",
                        "org.org_right", "account.acc_faulty", "account.acc_right", "proj_faulty", "proj_right")))
                    .build())
            .build();
    approvalInstance.setAmbiance(ambiance);
    approvalInstance.setCreatedAt(System.currentTimeMillis());
    approvalInstance.setDeadline(2L * System.currentTimeMillis());
    approvalInstance.setType(ApprovalType.HARNESS_APPROVAL);

    PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity = PipelineExecutionSummaryEntity.builder()
                                                                        .accountId(accountId)
                                                                        .orgIdentifier(orgIdentifier)
                                                                        .projectIdentifier(projectIdentifier)
                                                                        .pipelineIdentifier(pipelineIdentifier)
                                                                        .build();
    doReturn(pipelineExecutionSummaryEntity)
        .when(pmsExecutionService)
        .getPipelineExecutionSummaryEntity(anyString(), anyString(), anyString(), anyString(), anyBoolean());
    List<NotificationSettingConfigDTO> notificationSettingConfigDTOS = new ArrayList<>();
    notificationSettingConfigDTOS.add(SlackConfigDTO.builder().build());
    notificationSettingConfigDTOS.add(EmailConfigDTO.builder().build());

    List<UserGroupDTO> userGroupDTOS =
        new ArrayList<>(Arrays.asList(UserGroupDTO.builder()
                                          .identifier("proj_right")
                                          .accountIdentifier(accountId)
                                          .orgIdentifier(orgIdentifier)
                                          .projectIdentifier(projectIdentifier)
                                          .notificationConfigs(notificationSettingConfigDTOS)
                                          .build(),
            UserGroupDTO.builder()
                .identifier("org_right")
                .accountIdentifier(accountId)
                .orgIdentifier(orgIdentifier)
                .notificationConfigs(notificationSettingConfigDTOS)
                .build(),
            UserGroupDTO.builder()
                .identifier("acc_right")
                .accountIdentifier(accountId)
                .notificationConfigs(notificationSettingConfigDTOS)
                .build()));
    when(userGroupClient.getFilteredUserGroups(any())).thenReturn(null);
    when(NGRestUtils.getResponse(any())).thenReturn(userGroupDTOS);
    approvalInstance.setValidatedUserGroups(userGroupDTOS);

    doReturn(url).when(notificationHelper).generateUrl(ambiance);

    approvalNotificationHandler.sendNotification(approvalInstance, ambiance);
    verify(notificationClient, times(6)).sendNotificationAsync(any());
    verify(pmsExecutionService, times(1))
        .getPipelineExecutionSummaryEntity(anyString(), anyString(), anyString(), anyString(), anyBoolean());
    verify(ngLogCallback, times(2)).saveExecutionLog(anyString());
    ArgumentCaptor<String> stringArgumentCaptor = ArgumentCaptor.forClass(String.class);
    verify(ngLogCallback, times(1)).saveExecutionLog(stringArgumentCaptor.capture(), eq(LogLevel.WARN));

    String invalidUserGroups = stringArgumentCaptor.getValue().split(":")[1].trim();
    List<String> invalidUserGroupsList =
        Arrays.stream(invalidUserGroups.substring(1, invalidUserGroups.length() - 1).split(","))
            .map(String::trim)
            .collect(Collectors.toList());
    List<String> expectedInvalidUserGroupsList =
        new ArrayList<>(Arrays.asList("proj_faulty", "org.org_faulty", "account.acc_faulty"));
    assertThat(invalidUserGroupsList.size() == expectedInvalidUserGroupsList.size()
        && invalidUserGroupsList.containsAll(expectedInvalidUserGroupsList)
        && expectedInvalidUserGroupsList.containsAll(invalidUserGroupsList))
        .isTrue();
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testSendNotification1() {
    String url =
        "https://qa.harness.io/ng/#/account/zEaak-FLS425IEO7OLzMUg/cd/orgs/CV/projects/Brijesh_Dhakar/pipelines/DockerTest/executions/szmvyw4wQR2W4_iKkq9bfQ/pipeline";
    Mockito.mockStatic(NGRestUtils.class);

    GraphLayoutNode graphLayoutNode = GraphLayoutNode.newBuilder()
                                          .setNodeIdentifier("nodeIdentifier")
                                          .setNodeType("Approval")
                                          .setNodeUUID("aBcDeFgH")
                                          .setName("Node name")
                                          .setNodeGroup("STAGE")
                                          .build();
    GraphLayoutNodeDTO graphLayoutNodeDTO = GraphLayoutDtoMapper.toDto(graphLayoutNode);
    HashMap<String, GraphLayoutNodeDTO> layoutNodeDTOMap = new HashMap<>();
    layoutNodeDTOMap.put(startingNodeId, graphLayoutNodeDTO);

    Ambiance ambiance = Ambiance.newBuilder()
                            .putSetupAbstractions("accountId", accountId)
                            .putSetupAbstractions("orgIdentifier", orgIdentifier)
                            .putSetupAbstractions("projectIdentifier", projectIdentifier)
                            .putSetupAbstractions("pipelineIdentifier", pipelineIdentifier)
                            .build();
    HarnessApprovalInstance approvalInstance =
        HarnessApprovalInstance.builder()
            .approvers(ApproversDTO.builder().userGroups(Collections.singletonList("user")).build())
            .build();
    approvalInstance.setAmbiance(ambiance);
    approvalInstance.setCreatedAt(System.currentTimeMillis());
    approvalInstance.setDeadline(2L * System.currentTimeMillis());
    approvalInstance.setType(ApprovalType.HARNESS_APPROVAL);
    approvalInstance.setIncludePipelineExecutionHistory(true);

    PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity = PipelineExecutionSummaryEntity.builder()
                                                                        .accountId(accountId)
                                                                        .orgIdentifier(orgIdentifier)
                                                                        .projectIdentifier(projectIdentifier)
                                                                        .pipelineIdentifier(pipelineIdentifier)
                                                                        .startingNodeId(startingNodeId)
                                                                        .layoutNodeMap(layoutNodeDTOMap)
                                                                        .build();
    doReturn(pipelineExecutionSummaryEntity)
        .when(pmsExecutionService)
        .getPipelineExecutionSummaryEntity(anyString(), anyString(), anyString(), anyString(), anyBoolean());
    List<NotificationSettingConfigDTO> notificationSettingConfigDTOS = new ArrayList<>();
    notificationSettingConfigDTOS.add(SlackConfigDTO.builder().build());
    notificationSettingConfigDTOS.add(EmailConfigDTO.builder().build());

    List<UserGroupDTO> userGroupDTOS = Collections.singletonList(UserGroupDTO.builder()
                                                                     .identifier(userGroupIdentifier)
                                                                     .notificationConfigs(notificationSettingConfigDTOS)
                                                                     .build());
    when(userGroupClient.getFilteredUserGroups(any())).thenReturn(null);
    when(NGRestUtils.getResponse(any())).thenReturn(userGroupDTOS);
    approvalInstance.setValidatedUserGroups(userGroupDTOS);

    doReturn(url).when(notificationHelper).generateUrl(ambiance);
    approvalNotificationHandler.sendNotification(approvalInstance, ambiance);
    verify(ngLogCallback, times(2)).saveExecutionLog(anyString());
    verify(ngLogCallback, times(1)).saveExecutionLog(anyString(), eq(LogLevel.WARN));
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testSendNotification2() {
    String url =
        "https://qa.harness.io/ng/#/account/zEaak-FLS425IEO7OLzMUg/cd/orgs/CV/projects/Brijesh_Dhakar/pipelines/DockerTest/executions/szmvyw4wQR2W4_iKkq9bfQ/pipeline";
    Mockito.mockStatic(NGRestUtils.class);

    GraphLayoutNode graphLayoutNode = GraphLayoutNode.newBuilder()
                                          .setNodeIdentifier("nodeIdentifier")
                                          .setNodeType("Approval")
                                          .setNodeUUID("aBcDeFgH")
                                          .setName("Node name")
                                          //                    .status(ExecutionStatus.SUCCESS)
                                          .setNodeGroup("STAGE")
                                          .build();

    GraphLayoutNodeDTO graphLayoutNodeDTO = GraphLayoutDtoMapper.toDto(graphLayoutNode);
    graphLayoutNodeDTO.setStatus(ExecutionStatus.SUCCESS);

    //    graphLayoutNodeDTO.status= ExecutionStatus.SUCCESS;
    HashMap<String, GraphLayoutNodeDTO> layoutNodeDTOMap = new HashMap<>();
    layoutNodeDTOMap.put(startingNodeId, graphLayoutNodeDTO);

    Ambiance ambiance = Ambiance.newBuilder()
                            .putSetupAbstractions("accountId", accountId)
                            .putSetupAbstractions("orgIdentifier", orgIdentifier)
                            .putSetupAbstractions("projectIdentifier", projectIdentifier)
                            .putSetupAbstractions("pipelineIdentifier", pipelineIdentifier)
                            .build();
    HarnessApprovalInstance approvalInstance =
        HarnessApprovalInstance.builder()
            .approvers(ApproversDTO.builder().userGroups(Collections.singletonList("user")).build())
            .build();
    approvalInstance.setAmbiance(ambiance);
    approvalInstance.setCreatedAt(System.currentTimeMillis());
    approvalInstance.setDeadline(2L * System.currentTimeMillis());
    approvalInstance.setType(ApprovalType.HARNESS_APPROVAL);
    approvalInstance.setIncludePipelineExecutionHistory(true);

    PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity = PipelineExecutionSummaryEntity.builder()
                                                                        .accountId(accountId)
                                                                        .orgIdentifier(orgIdentifier)
                                                                        .projectIdentifier(projectIdentifier)
                                                                        .pipelineIdentifier(pipelineIdentifier)
                                                                        .startingNodeId(startingNodeId)
                                                                        .layoutNodeMap(layoutNodeDTOMap)
                                                                        .build();
    doReturn(pipelineExecutionSummaryEntity)
        .when(pmsExecutionService)
        .getPipelineExecutionSummaryEntity(anyString(), anyString(), anyString(), anyString(), anyBoolean());
    List<NotificationSettingConfigDTO> notificationSettingConfigDTOS = new ArrayList<>();
    notificationSettingConfigDTOS.add(SlackConfigDTO.builder().build());
    notificationSettingConfigDTOS.add(EmailConfigDTO.builder().build());

    List<UserGroupDTO> userGroupDTOS = Collections.singletonList(UserGroupDTO.builder()
                                                                     .identifier(userGroupIdentifier)
                                                                     .notificationConfigs(notificationSettingConfigDTOS)
                                                                     .build());
    when(userGroupClient.getFilteredUserGroups(any())).thenReturn(null);
    when(NGRestUtils.getResponse(any())).thenReturn(userGroupDTOS);
    approvalInstance.setValidatedUserGroups(userGroupDTOS);

    doReturn(url).when(notificationHelper).generateUrl(ambiance);
    approvalNotificationHandler.sendNotification(approvalInstance, ambiance);

    verify(ngLogCallback, times(2)).saveExecutionLog(anyString());
    verify(ngLogCallback, times(1)).saveExecutionLog(anyString(), eq(LogLevel.WARN));
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testSendNotification3() {
    String url =
        "https://qa.harness.io/ng/#/account/zEaak-FLS425IEO7OLzMUg/cd/orgs/CV/projects/Brijesh_Dhakar/pipelines/DockerTest/executions/szmvyw4wQR2W4_iKkq9bfQ/pipeline";
    Mockito.mockStatic(NGRestUtils.class);

    GraphLayoutNode graphLayoutNode = GraphLayoutNode.newBuilder()
                                          .setNodeIdentifier("nodeIdentifier")
                                          .setNodeType("Approval")
                                          .setNodeUUID("aBcDeFgH")
                                          .setName("Node name")
                                          //                    .status(ExecutionStatus.SUCCESS)
                                          .setNodeGroup("STAGE")
                                          .build();

    GraphLayoutNodeDTO graphLayoutNodeDTO = GraphLayoutDtoMapper.toDto(graphLayoutNode);
    graphLayoutNodeDTO.setStatus(ExecutionStatus.ASYNCWAITING);

    //    graphLayoutNodeDTO.status= ExecutionStatus.SUCCESS;
    HashMap<String, GraphLayoutNodeDTO> layoutNodeDTOMap = new HashMap<>();
    layoutNodeDTOMap.put(startingNodeId, graphLayoutNodeDTO);

    Ambiance ambiance = Ambiance.newBuilder()
                            .putSetupAbstractions("accountId", accountId)
                            .putSetupAbstractions("orgIdentifier", orgIdentifier)
                            .putSetupAbstractions("projectIdentifier", projectIdentifier)
                            .putSetupAbstractions("pipelineIdentifier", pipelineIdentifier)
                            .build();
    HarnessApprovalInstance approvalInstance =
        HarnessApprovalInstance.builder()
            .approvers(ApproversDTO.builder().userGroups(Collections.singletonList("user")).build())
            .build();
    approvalInstance.setAmbiance(ambiance);
    approvalInstance.setCreatedAt(System.currentTimeMillis());
    approvalInstance.setDeadline(2L * System.currentTimeMillis());
    approvalInstance.setType(ApprovalType.HARNESS_APPROVAL);
    approvalInstance.setIncludePipelineExecutionHistory(true);

    PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity = PipelineExecutionSummaryEntity.builder()
                                                                        .accountId(accountId)
                                                                        .orgIdentifier(orgIdentifier)
                                                                        .projectIdentifier(projectIdentifier)
                                                                        .pipelineIdentifier(pipelineIdentifier)
                                                                        .startingNodeId(startingNodeId)
                                                                        .layoutNodeMap(layoutNodeDTOMap)
                                                                        .build();
    doReturn(pipelineExecutionSummaryEntity)
        .when(pmsExecutionService)
        .getPipelineExecutionSummaryEntity(anyString(), anyString(), anyString(), anyString(), anyBoolean());
    List<NotificationSettingConfigDTO> notificationSettingConfigDTOS = new ArrayList<>();
    notificationSettingConfigDTOS.add(SlackConfigDTO.builder().build());
    notificationSettingConfigDTOS.add(EmailConfigDTO.builder().build());

    List<UserGroupDTO> userGroupDTOS = Collections.singletonList(UserGroupDTO.builder()
                                                                     .identifier(userGroupIdentifier)
                                                                     .notificationConfigs(notificationSettingConfigDTOS)
                                                                     .build());
    when(userGroupClient.getFilteredUserGroups(any())).thenReturn(null);
    when(NGRestUtils.getResponse(any())).thenReturn(userGroupDTOS);
    approvalInstance.setValidatedUserGroups(userGroupDTOS);

    doReturn(url).when(notificationHelper).generateUrl(ambiance);
    approvalNotificationHandler.sendNotification(approvalInstance, ambiance);

    verify(ngLogCallback, times(2)).saveExecutionLog(anyString());
    verify(ngLogCallback, times(1)).saveExecutionLog(anyString(), eq(LogLevel.WARN));
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testSendNotification4() {
    String url =
        "https://qa.harness.io/ng/#/account/zEaak-FLS425IEO7OLzMUg/cd/orgs/CV/projects/Brijesh_Dhakar/pipelines/DockerTest/executions/szmvyw4wQR2W4_iKkq9bfQ/pipeline";
    Mockito.mockStatic(NGRestUtils.class);

    GraphLayoutNode graphLayoutNode1 =
        GraphLayoutNode.newBuilder()
            .setNodeIdentifier("nodeIdentifier")
            .setNodeType("Approval")
            .setNodeUUID("aBcDeFgH")
            .setName("Node name")
            .setNodeGroup("STAGE")
            .setEdgeLayoutList(EdgeLayoutList.newBuilder().addNextIds("nextId").addCurrentNodeChildren("child").build())
            .build();
    GraphLayoutNodeDTO graphLayoutNodeDTO1 = GraphLayoutDtoMapper.toDto(graphLayoutNode1);
    HashMap<String, GraphLayoutNodeDTO> layoutNodeDTOMap = new HashMap<>();
    layoutNodeDTOMap.put(startingNodeId, graphLayoutNodeDTO1);

    GraphLayoutNode graphLayoutNode2 = GraphLayoutNode.newBuilder()
                                           .setNodeIdentifier("nodeIdentifier")
                                           .setNodeType("Approval")
                                           .setNodeUUID("aBcDeFgH")
                                           .setName("Node name")
                                           .setNodeGroup("STAGE")
                                           .build();
    GraphLayoutNodeDTO graphLayoutNodeDTO2 = GraphLayoutDtoMapper.toDto(graphLayoutNode2);
    layoutNodeDTOMap.put("nextId", graphLayoutNodeDTO2);

    Ambiance ambiance = Ambiance.newBuilder()
                            .putSetupAbstractions("accountId", accountId)
                            .putSetupAbstractions("orgIdentifier", orgIdentifier)
                            .putSetupAbstractions("projectIdentifier", projectIdentifier)
                            .putSetupAbstractions("pipelineIdentifier", pipelineIdentifier)
                            .build();
    HarnessApprovalInstance approvalInstance =
        HarnessApprovalInstance.builder()
            .approvers(ApproversDTO.builder().userGroups(Collections.singletonList("user")).build())
            .build();
    approvalInstance.setAmbiance(ambiance);
    approvalInstance.setCreatedAt(System.currentTimeMillis());
    approvalInstance.setDeadline(2L * System.currentTimeMillis());
    approvalInstance.setType(ApprovalType.HARNESS_APPROVAL);
    approvalInstance.setIncludePipelineExecutionHistory(true);

    PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity = PipelineExecutionSummaryEntity.builder()
                                                                        .accountId(accountId)
                                                                        .orgIdentifier(orgIdentifier)
                                                                        .projectIdentifier(projectIdentifier)
                                                                        .pipelineIdentifier(pipelineIdentifier)
                                                                        .startingNodeId(startingNodeId)
                                                                        .layoutNodeMap(layoutNodeDTOMap)
                                                                        .build();
    doReturn(pipelineExecutionSummaryEntity)
        .when(pmsExecutionService)
        .getPipelineExecutionSummaryEntity(anyString(), anyString(), anyString(), anyString(), anyBoolean());
    List<NotificationSettingConfigDTO> notificationSettingConfigDTOS = new ArrayList<>();
    notificationSettingConfigDTOS.add(SlackConfigDTO.builder().build());
    notificationSettingConfigDTOS.add(EmailConfigDTO.builder().build());

    List<UserGroupDTO> userGroupDTOS = Collections.singletonList(UserGroupDTO.builder()
                                                                     .identifier(userGroupIdentifier)
                                                                     .notificationConfigs(notificationSettingConfigDTOS)
                                                                     .build());
    when(userGroupClient.getFilteredUserGroups(any())).thenReturn(null);
    when(NGRestUtils.getResponse(any())).thenReturn(userGroupDTOS);
    approvalInstance.setValidatedUserGroups(userGroupDTOS);

    doReturn(url).when(notificationHelper).generateUrl(ambiance);
    approvalNotificationHandler.sendNotification(approvalInstance, ambiance);

    verify(ngLogCallback, times(2)).saveExecutionLog(anyString());
    verify(ngLogCallback, times(1)).saveExecutionLog(anyString(), eq(LogLevel.WARN));
  }
}