/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.approval.harness;

import static io.harness.eraro.ErrorCode.APPROVAL_REJECTION;
import static io.harness.rule.OwnerRule.PRABU;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.delegate.task.shell.ShellScriptTaskNG;
import io.harness.exception.InvalidRequestException;
import io.harness.logstreaming.ILogStreamingStepClient;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.ng.core.dto.UserGroupDTO;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.steps.approval.ApprovalNotificationHandler;
import io.harness.steps.approval.step.ApprovalInstanceService;
import io.harness.steps.approval.step.beans.ApprovalStatus;
import io.harness.steps.approval.step.beans.ApprovalUserGroupDTO;
import io.harness.steps.approval.step.entities.ApprovalInstance;
import io.harness.steps.approval.step.harness.HarnessApprovalOutcome;
import io.harness.steps.approval.step.harness.HarnessApprovalResponseData;
import io.harness.steps.approval.step.harness.HarnessApprovalSpecParameters;
import io.harness.steps.approval.step.harness.HarnessApprovalStep;
import io.harness.steps.approval.step.harness.beans.Approvers;
import io.harness.steps.approval.step.harness.entities.HarnessApprovalInstance;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class HarnessApprovalStepTest {
  public static final String APPROVAL_MESSAGE = "Approval_Message";
  public static final String USER_GROUP = "User_Group";
  public static final String INSTANCE_ID = "INSTANCE_ID";
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @Mock ApprovalInstanceService approvalInstanceService;
  @Mock ExecutorService executorService;
  @Mock ApprovalNotificationHandler approvalNotificationHandler;
  @Mock LogStreamingStepClientFactory logStreamingStepClientFactory;
  @InjectMocks private HarnessApprovalStep harnessApprovalStep;
  private ILogStreamingStepClient logStreamingStepClient;

  @Before
  public void setup() {
    logStreamingStepClient = mock(ILogStreamingStepClient.class);
    when(logStreamingStepClientFactory.getLogStreamingStepClient(any())).thenReturn(logStreamingStepClient);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testExecuteAsync() {
    Ambiance ambiance = buildAmbiance();
    StepElementParameters parameters = getStepElementParameters();
    doAnswer(invocationOnMock -> {
      HarnessApprovalInstance instance = invocationOnMock.getArgument(0, HarnessApprovalInstance.class);
      instance.setId(INSTANCE_ID);
      return instance;
    })
        .when(approvalInstanceService)
        .save(any());
    when(approvalNotificationHandler.getUserGroups(HarnessApprovalInstance.fromStepParameters(ambiance, parameters)))
        .thenReturn(Collections.emptyList());
    assertThatThrownBy(() -> harnessApprovalStep.executeAsync(ambiance, parameters, null, null))
        .isInstanceOf(InvalidRequestException.class);
    when(approvalNotificationHandler.getUserGroups(any())).thenReturn(Collections.singletonList(buildUserGroup()));
    assertThat(harnessApprovalStep.executeAsync(ambiance, parameters, null, null).getCallbackIds(0))
        .isEqualTo(INSTANCE_ID);
    ArgumentCaptor<ApprovalInstance> approvalInstanceArgumentCaptor = ArgumentCaptor.forClass(ApprovalInstance.class);
    verify(approvalInstanceService).save(approvalInstanceArgumentCaptor.capture());
    assertThat(approvalInstanceArgumentCaptor.getValue().getStatus()).isEqualTo(ApprovalStatus.WAITING);
    assertThat(approvalInstanceArgumentCaptor.getValue().getAmbiance()).isEqualTo(ambiance);
    HarnessApprovalInstance instance = (HarnessApprovalInstance) approvalInstanceArgumentCaptor.getValue();
    assertThat(instance.getApprovalMessage()).isEqualTo(APPROVAL_MESSAGE);
    assertThat(instance.getApprovers().getUserGroups().get(0)).isEqualTo(USER_GROUP);
    assertThat(instance.getApprovers().getMinimumCount()).isEqualTo(1);
    assertThat(instance.getValidatedApprovalUserGroups().size()).isEqualTo(1);
    assertThat(instance.getValidatedApprovalUserGroups().get(0))
        .isEqualTo(ApprovalUserGroupDTO.toApprovalUserGroupDTO(buildUserGroup()));
    assertThat(instance.getValidatedUserGroups().size()).isEqualTo(1);
    assertThat(instance.getValidatedUserGroups().get(0)).isEqualTo(buildUserGroup());
    verify(logStreamingStepClient, times(2)).openStream(ShellScriptTaskNG.COMMAND_UNIT);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testHandleAsyncResponseFailure() {
    Ambiance ambiance = buildAmbiance();
    StepElementParameters parameters = getStepElementParameters();

    HarnessApprovalResponseData responseData =
        HarnessApprovalResponseData.builder().approvalInstanceId(INSTANCE_ID).build();
    HarnessApprovalInstance approvalInstance = HarnessApprovalInstance.builder().build();
    approvalInstance.setStatus(ApprovalStatus.FAILED);
    approvalInstance.setErrorMessage("error");
    doReturn(approvalInstance).when(approvalInstanceService).get(INSTANCE_ID);
    StepResponse stepResponse =
        harnessApprovalStep.handleAsyncResponse(ambiance, parameters, Collections.singletonMap("key", responseData));
    assertThat(stepResponse.getStatus()).isEqualTo(Status.FAILED);
    verify(logStreamingStepClient).closeStream(ShellScriptTaskNG.COMMAND_UNIT);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testHandleAsyncResponseSuccess() {
    Ambiance ambiance = buildAmbiance();
    StepElementParameters parameters = getStepElementParameters();

    HarnessApprovalResponseData responseData =
        HarnessApprovalResponseData.builder().approvalInstanceId(INSTANCE_ID).build();
    HarnessApprovalInstance approvalInstance = HarnessApprovalInstance.builder().build();
    approvalInstance.setStatus(ApprovalStatus.APPROVED);
    doReturn(approvalInstance).when(approvalInstanceService).get(INSTANCE_ID);
    StepResponse response =
        harnessApprovalStep.handleAsyncResponse(ambiance, parameters, Collections.singletonMap("key", responseData));
    assertThat(response.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(response.getStepOutcomes().iterator().next().getOutcome())
        .isNotNull()
        .isInstanceOf(HarnessApprovalOutcome.class);
    verify(logStreamingStepClient).closeStream(ShellScriptTaskNG.COMMAND_UNIT);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testHandleAsyncResponseRejected() {
    Ambiance ambiance = buildAmbiance();
    StepElementParameters parameters = getStepElementParameters();

    HarnessApprovalResponseData responseData =
        HarnessApprovalResponseData.builder().approvalInstanceId(INSTANCE_ID).build();
    HarnessApprovalInstance approvalInstance = HarnessApprovalInstance.builder().build();
    approvalInstance.setStatus(ApprovalStatus.REJECTED);
    doReturn(approvalInstance).when(approvalInstanceService).get(INSTANCE_ID);
    StepResponse response =
        harnessApprovalStep.handleAsyncResponse(ambiance, parameters, Collections.singletonMap("key", responseData));
    assertThat(response.getStatus()).isEqualTo(Status.APPROVAL_REJECTED);
    assertThat(response.getFailureInfo().getFailureData(0).getFailureTypesList())
        .containsExactly(FailureType.APPROVAL_REJECTION);
    assertThat(response.getFailureInfo().getFailureData(0).getMessage()).isEqualTo("Approval Step has been Rejected");
    assertThat(response.getFailureInfo().getFailureData(0).getCode()).isEqualTo(APPROVAL_REJECTION.name());
    assertThat(response.getStepOutcomes().iterator().next().getOutcome())
        .isNotNull()
        .isInstanceOf(HarnessApprovalOutcome.class);
    verify(logStreamingStepClient).closeStream(ShellScriptTaskNG.COMMAND_UNIT);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testAbort() {
    Ambiance ambiance = buildAmbiance();
    StepElementParameters parameters = getStepElementParameters();
    harnessApprovalStep.handleAbort(ambiance, parameters, null);
    verify(approvalInstanceService).expireByNodeExecutionId(null);
    verify(logStreamingStepClient).closeStream(ShellScriptTaskNG.COMMAND_UNIT);
  }

  private StepElementParameters getStepElementParameters() {
    return StepElementParameters.builder()
        .type("HARNESS_APPROVAL")
        .spec(
            HarnessApprovalSpecParameters.builder()
                .approvalMessage(ParameterField.<String>builder().value(APPROVAL_MESSAGE).build())
                .includePipelineExecutionHistory(ParameterField.<Boolean>builder().value(false).build())
                .approvers(
                    Approvers.builder()
                        .userGroups(
                            ParameterField.<List<String>>builder().value(Collections.singletonList(USER_GROUP)).build())
                        .minimumCount(ParameterField.<Integer>builder().value(1).build())
                        .disallowPipelineExecutor(ParameterField.<Boolean>builder().value(false).build())
                        .build())
                .build())
        .build();
  }

  private Ambiance buildAmbiance() {
    return Ambiance.newBuilder()
        .putSetupAbstractions(SetupAbstractionKeys.accountId, "accId")
        .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, "orgId")
        .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, "projId")
        .build();
  }

  private UserGroupDTO buildUserGroup() {
    return UserGroupDTO.builder()
        .accountIdentifier("accId")
        .orgIdentifier("orgId")
        .projectIdentifier("projId")
        .name("UG NAME")
        .identifier("UG_NAME")
        .build();
  }
}
