/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution;

import static io.harness.gitcaching.GitCachingConstants.BOOLEAN_FALSE_VALUE;
import static io.harness.pms.rbac.PipelineRbacPermissions.PIPELINE_EXECUTE;
import static io.harness.pms.utils.PmsConstants.PIPELINE;

import static java.lang.String.format;

import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.accesscontrol.OrgIdentifier;
import io.harness.accesscontrol.ProjectIdentifier;
import io.harness.accesscontrol.ResourceIdentifier;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.executions.plan.PlanExecutionService;
import io.harness.engine.executions.retry.RetryHistoryResponseDto;
import io.harness.engine.executions.retry.RetryInfo;
import io.harness.engine.executions.retry.RetryLatestExecutionResponseDto;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.InvalidYamlException;
import io.harness.gitsync.interceptor.GitEntityFindInfoDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.pms.annotations.PipelineServiceAuth;
import io.harness.pms.inputset.MergeInputSetRequestDTOPMS;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.PipelineResourceConstants;
import io.harness.pms.pipeline.service.PMSPipelineService;
import io.harness.pms.pipeline.service.PMSPipelineTemplateHelper;
import io.harness.pms.pipeline.yaml.BasicPipeline;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.beans.dto.InterruptDTO;
import io.harness.pms.plan.execution.beans.dto.RunStageRequestDTO;
import io.harness.pms.plan.execution.service.PMSExecutionService;
import io.harness.pms.preflight.PreFlightDTO;
import io.harness.pms.preflight.service.PreflightService;
import io.harness.pms.rbac.PipelineRbacPermissions;
import io.harness.pms.stages.StageExecutionResponse;
import io.harness.pms.stages.StageExecutionSelectorHelper;
import io.harness.pms.yaml.YamlUtils;
import io.harness.repositories.orchestrationEventLog.OrchestrationEventLogRepository;

import com.google.inject.Inject;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.validator.constraints.NotEmpty;

@OwnedBy(HarnessTeam.PIPELINE)
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@PipelineServiceAuth
@Slf4j
public class PlanExecutionResourceImpl implements PlanExecutionResource {
  @Inject private final PipelineExecutor pipelineExecutor;
  @Inject private final PMSExecutionService pmsExecutionService;
  @Inject private final OrchestrationEventLogRepository orchestrationEventLogRepository;
  @Inject private final AccessControlClient accessControlClient;
  @Inject PlanExecutionService planExecutionService;

  @Inject private final PreflightService preflightService;
  @Inject private final PMSPipelineService pmsPipelineService;
  @Inject private final RetryExecutionHelper retryExecutionHelper;
  @Inject private final PMSPipelineTemplateHelper pipelineTemplateHelper;

  @Override
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_EXECUTE)
  public ResponseDTO<PlanExecutionResponseDto> runPipelineWithInputSetPipelineYaml(
      @NotNull @AccountIdentifier String accountId, @NotNull @OrgIdentifier String orgIdentifier,
      @NotNull @ProjectIdentifier String projectIdentifier, String moduleType,

      @ResourceIdentifier @NotEmpty String pipelineIdentifier, GitEntityFindInfoDTO gitEntityBasicInfo,
      boolean useFQNIfErrorResponse, boolean notifyOnlyUser, String inputSetPipelineYaml) {
    PlanExecutionResponseDto planExecutionResponseDto = pipelineExecutor.runPipelineWithInputSetPipelineYaml(accountId,
        orgIdentifier, projectIdentifier, pipelineIdentifier, moduleType, inputSetPipelineYaml, false, notifyOnlyUser);
    return ResponseDTO.newResponse(planExecutionResponseDto);
  }

  @Override
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_EXECUTE)
  public ResponseDTO<PlanExecutionResponseDto> runPipelineWithInputSetPipelineYamlV2(
      @NotNull @AccountIdentifier String accountId, @NotNull @OrgIdentifier String orgIdentifier,
      @NotNull @ProjectIdentifier String projectIdentifier, String moduleType,

      @ResourceIdentifier @NotEmpty String pipelineIdentifier, GitEntityFindInfoDTO gitEntityBasicInfo,
      boolean useFQNIfErrorResponse, String inputSetPipelineYaml) {
    PlanExecutionResponseDto planExecutionResponseDto = pipelineExecutor.runPipelineWithInputSetPipelineYaml(
        accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, moduleType, inputSetPipelineYaml, true, false);
    return ResponseDTO.newResponse(planExecutionResponseDto);
  }

  @Override
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_EXECUTE)
  public ResponseDTO<PlanExecutionResponseDto> runStagesWithRuntimeInputYaml(
      @NotNull @AccountIdentifier String accountId, @NotNull @OrgIdentifier String orgIdentifier,
      @NotNull @ProjectIdentifier String projectIdentifier, String moduleType,
      @ResourceIdentifier @NotEmpty String pipelineIdentifier, GitEntityFindInfoDTO gitEntityBasicInfo,
      boolean useFQNIfErrorResponse, RunStageRequestDTO runStageRequestDTO) {
    return ResponseDTO.newResponse(pipelineExecutor.runStagesWithRuntimeInputYaml(
        accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, moduleType, runStageRequestDTO, false));
  }

  @Override
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_EXECUTE)
  public ResponseDTO<PlanExecutionResponseDto> rerunStagesWithRuntimeInputYaml(
      @NotNull @AccountIdentifier String accountId, @NotNull @OrgIdentifier String orgIdentifier,
      @NotNull @ProjectIdentifier String projectIdentifier, String moduleType,
      @ResourceIdentifier @NotEmpty String pipelineIdentifier,
      @NotNull

      String originalExecutionId, GitEntityFindInfoDTO gitEntityBasicInfo, boolean useFQNIfErrorResponse,
      RunStageRequestDTO runStageRequestDTO) {
    return ResponseDTO.newResponse(pipelineExecutor.rerunStagesWithRuntimeInputYaml(accountId, orgIdentifier,
        projectIdentifier, pipelineIdentifier, moduleType, originalExecutionId, runStageRequestDTO, false));
  }

  @Override
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_EXECUTE)
  public ResponseDTO<PlanExecutionResponseDto> rerunPipelineWithInputSetPipelineYaml(
      @NotNull @AccountIdentifier String accountId, @NotNull @OrgIdentifier String orgIdentifier,
      @NotNull @ProjectIdentifier String projectIdentifier, String moduleType, @NotNull String originalExecutionId,

      @ResourceIdentifier @NotEmpty String pipelineIdentifier, GitEntityFindInfoDTO gitEntityBasicInfo,
      boolean useFQNIfErrorResponse, String inputSetPipelineYaml) {
    PlanExecutionResponseDto planExecutionResponseDto =
        pipelineExecutor.rerunPipelineWithInputSetPipelineYaml(accountId, orgIdentifier, projectIdentifier,
            pipelineIdentifier, moduleType, originalExecutionId, inputSetPipelineYaml, false);
    return ResponseDTO.newResponse(planExecutionResponseDto);
  }

  @Override
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_EXECUTE)
  public ResponseDTO<PlanExecutionResponseDto> rerunPipelineWithInputSetPipelineYamlV2(
      @NotNull @AccountIdentifier String accountId, @NotNull @OrgIdentifier String orgIdentifier,
      @NotNull @ProjectIdentifier String projectIdentifier, String moduleType, @NotNull String originalExecutionId,

      @ResourceIdentifier @NotEmpty String pipelineIdentifier, GitEntityFindInfoDTO gitEntityBasicInfo,
      boolean useFQNIfErrorResponse, String inputSetPipelineYaml) {
    PlanExecutionResponseDto planExecutionResponseDto =
        pipelineExecutor.rerunPipelineWithInputSetPipelineYaml(accountId, orgIdentifier, projectIdentifier,
            pipelineIdentifier, moduleType, originalExecutionId, inputSetPipelineYaml, true);
    return ResponseDTO.newResponse(planExecutionResponseDto);
  }

  @Override
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_EXECUTE)
  public ResponseDTO<io.harness.engine.executions.retry.RetryInfo> getRetryStages(
      @NotNull @AccountIdentifier String accountId, @NotNull @OrgIdentifier String orgIdentifier,
      @NotNull @ProjectIdentifier String projectIdentifier, @ResourceIdentifier @NotEmpty String pipelineIdentifier,
      @NotNull String planExecutionId, GitEntityFindInfoDTO gitEntityBasicInfo) {
    return ResponseDTO.newResponse(retryExecutionHelper.validateRetry(
        accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, planExecutionId));
  }

  @Override
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_EXECUTE)
  public ResponseDTO<PlanExecutionResponseDto> runPipelineWithInputSetIdentifierList(
      @NotNull @AccountIdentifier String accountId, @NotNull @OrgIdentifier String orgIdentifier,
      @NotNull @ProjectIdentifier String projectIdentifier,
      @Parameter(description = PlanExecutionResourceConstants.MODULE_TYPE_PARAM_MESSAGE) String moduleType,

      @ResourceIdentifier @NotEmpty String pipelineIdentifier, GitEntityFindInfoDTO gitEntityBasicInfo,
      boolean useFQNIfErrorResponse, @NotNull @Valid MergeInputSetRequestDTOPMS mergeInputSetRequestDTO) {
    PlanExecutionResponseDto planExecutionResponseDto =
        pipelineExecutor.runPipelineWithInputSetReferencesList(accountId, orgIdentifier, projectIdentifier,
            pipelineIdentifier, moduleType, mergeInputSetRequestDTO.getInputSetReferences(),
            gitEntityBasicInfo.getBranch(), gitEntityBasicInfo.getYamlGitConfigId());
    return ResponseDTO.newResponse(planExecutionResponseDto);
  }

  @Override
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_EXECUTE)
  public ResponseDTO<PlanExecutionResponseDto> rerunPipelineWithInputSetIdentifierList(
      @NotNull @AccountIdentifier String accountId, @NotNull @OrgIdentifier String orgIdentifier,
      @NotNull @ProjectIdentifier String projectIdentifier,
      @Parameter(description = PipelineResourceConstants.MODULE_TYPE_PARAM_MESSAGE) String moduleType,
      @NotNull @Parameter(description = PipelineResourceConstants.ORIGINAL_EXECUTION_ID_PARAM_MESSAGE,
          required = true) String originalExecutionId,
      @Parameter(description = PipelineResourceConstants.PIPELINE_ID_PARAM_MESSAGE, required = true) @ResourceIdentifier
      @NotEmpty String pipelineIdentifier, GitEntityFindInfoDTO gitEntityBasicInfo,
      @Parameter(description = PipelineResourceConstants.USE_FQN_IF_ERROR_RESPONSE_ERROR_MESSAGE,
          required = true) boolean useFQNIfErrorResponse,
      @RequestBody(required = true, description = "InputSet reference details") @NotNull
      @Valid MergeInputSetRequestDTOPMS mergeInputSetRequestDTO) {
    PlanExecutionResponseDto planExecutionResponseDto =
        pipelineExecutor.rerunPipelineWithInputSetReferencesList(accountId, orgIdentifier, projectIdentifier,
            pipelineIdentifier, moduleType, originalExecutionId, mergeInputSetRequestDTO.getInputSetReferences(),
            gitEntityBasicInfo.getBranch(), gitEntityBasicInfo.getYamlGitConfigId());
    return ResponseDTO.newResponse(planExecutionResponseDto);
  }

  @Override
  public ResponseDTO<InterruptDTO> handleInterrupt(
      @NotNull @Parameter(description = PipelineResourceConstants.ACCOUNT_PARAM_MESSAGE) String accountId,
      @NotNull @Parameter(description = PipelineResourceConstants.ORG_PARAM_MESSAGE) String orgId,
      @NotNull @Parameter(description = PipelineResourceConstants.PROJECT_PARAM_MESSAGE) String projectId,
      @Parameter(
          description = "The Interrupt type needed to be applied to the execution. Choose a value from the enum list.")
      @NotNull PlanExecutionInterruptType executionInterruptType,
      @Parameter(description = PlanExecutionResourceConstants.PLAN_EXECUTION_ID_PARAM_MESSAGE
              + " on which the Interrupt needs to be applied.") @NotNull String planExecutionId) {
    PipelineExecutionSummaryEntity executionSummaryEntity =
        pmsExecutionService.getPipelineExecutionSummaryEntity(accountId, orgId, projectId, planExecutionId, false);

    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgId, projectId),
        Resource.of("PIPELINE", executionSummaryEntity.getPipelineIdentifier()),
        PipelineRbacPermissions.PIPELINE_EXECUTE);

    return ResponseDTO.newResponse(
        pmsExecutionService.registerInterrupt(executionInterruptType, planExecutionId, null));
  }

  @Override
  // TODO(prashant) : This is a temp route for now merge it with the above. Need be done in sync with UI changes
  public ResponseDTO<InterruptDTO> handleStageInterrupt(@NotNull String accountId, @NotNull String orgId,
      @NotNull String projectId, @NotNull PlanExecutionInterruptType executionInterruptType,
      @NotNull String planExecutionId, @NotNull String nodeExecutionId) {
    return ResponseDTO.newResponse(
        pmsExecutionService.registerInterrupt(executionInterruptType, planExecutionId, nodeExecutionId));
  }

  @Override
  public ResponseDTO<InterruptDTO> handleManualInterventionInterrupt(@NotNull String accountId, @NotNull String orgId,
      @NotNull String projectId, @NotNull PlanExecutionInterruptType executionInterruptType,
      @NotNull String planExecutionId, @NotNull String nodeExecutionId) {
    String pipelineIdentifier =
        planExecutionService.getExecutionMetadataFromPlanExecution(planExecutionId).getPipelineIdentifier();
    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(accountId, orgId, projectId), Resource.of(PIPELINE, pipelineIdentifier), PIPELINE_EXECUTE);
    return ResponseDTO.newResponse(
        pmsExecutionService.registerInterrupt(executionInterruptType, planExecutionId, nodeExecutionId));
  }

  @Override
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_EXECUTE)
  public ResponseDTO<String> startPreFlightCheck(@NotNull @AccountIdentifier String accountId,
      @NotNull @OrgIdentifier String orgIdentifier, @NotNull @ProjectIdentifier String projectIdentifier,
      @ResourceIdentifier @NotEmpty String pipelineIdentifier, GitEntityFindInfoDTO gitEntityBasicInfo,
      String inputSetPipelineYaml) {
    try {
      return ResponseDTO.newResponse(preflightService.startPreflightCheck(
          accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, inputSetPipelineYaml));
    } catch (IOException ex) {
      log.error(format("Invalid YAML in node [%s]", YamlUtils.getErrorNodePartialFQN(ex)), ex);
      throw new InvalidYamlException(format("Invalid yaml in node [%s]", YamlUtils.getErrorNodePartialFQN(ex)), ex);
    }
  }

  @Override
  public ResponseDTO<PreFlightDTO> getPreflightCheckResponse(@NotNull String accountId, @NotNull String orgIdentifier,
      @NotNull String projectIdentifier, @NotNull String preflightCheckId, String inputSetPipelineYaml) {
    return ResponseDTO.newResponse(preflightService.getPreflightCheckResponse(preflightCheckId));
  }

  @Override
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_VIEW)
  public ResponseDTO<List<StageExecutionResponse>> getStagesExecutionList(@NotNull @AccountIdentifier String accountId,
      @NotNull @OrgIdentifier String orgIdentifier, @NotNull @ProjectIdentifier String projectIdentifier,
      @NotNull @ResourceIdentifier @NotEmpty String pipelineIdentifier, GitEntityFindInfoDTO gitEntityBasicInfo) {
    Optional<PipelineEntity> optionalPipelineEntity =
        pmsPipelineService.getPipeline(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, false, false);
    if (!optionalPipelineEntity.isPresent()) {
      throw new InvalidRequestException(format("Pipeline [%s] under Project[%s], Organization [%s] doesn't exist.",
          pipelineIdentifier, projectIdentifier, orgIdentifier));
    }
    PipelineEntity pipelineEntity = optionalPipelineEntity.get();
    String yaml = pipelineEntity.getYaml();
    if (Boolean.TRUE.equals(pipelineEntity.getTemplateReference())) {
      yaml = pipelineTemplateHelper
                 .resolveTemplateRefsInPipeline(
                     accountId, orgIdentifier, projectIdentifier, pipelineEntity.getYaml(), BOOLEAN_FALSE_VALUE)
                 .getMergedPipelineYaml();
    }
    boolean shouldAllowStageExecutions;
    try {
      BasicPipeline basicPipeline = YamlUtils.read(yaml, BasicPipeline.class);
      shouldAllowStageExecutions = basicPipeline.isAllowStageExecutions();
    } catch (IOException e) {
      throw new InvalidRequestException("Cannot create pipeline entity due to " + e.getMessage(), e);
    }

    if (!shouldAllowStageExecutions) {
      return ResponseDTO.newResponse(Collections.emptyList());
    }
    List<StageExecutionResponse> stageExecutionResponse = StageExecutionSelectorHelper.getStageExecutionResponse(yaml);

    return ResponseDTO.newResponse(stageExecutionResponse);
  }

  @Override
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_EXECUTE)
  public ResponseDTO<PlanExecutionResponseDto> retryPipelineWithInputSetPipelineYaml(
      @NotNull @AccountIdentifier String accountId, @NotNull @OrgIdentifier String orgIdentifier,
      @NotNull @ProjectIdentifier String projectIdentifier, String moduleType, @NotNull String previousExecutionId,
      @NotNull List<String> retryStagesIdentifier, boolean runAllStages,

      @ResourceIdentifier @NotEmpty String pipelineIdentifier, String inputSetPipelineYaml) {
    if (retryStagesIdentifier.size() == 0) {
      throw new InvalidRequestException("You need to select the stage to retry!!");
    }

    RetryInfo retryInfo = retryExecutionHelper.validateRetry(
        accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, previousExecutionId);
    if (!retryInfo.isResumable()) {
      throw new InvalidRequestException(retryInfo.getErrorMessage());
    }

    PlanExecutionResponseDto planExecutionResponseDto = pipelineExecutor.retryPipelineWithInputSetPipelineYaml(
        accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, moduleType, inputSetPipelineYaml,
        previousExecutionId, retryStagesIdentifier, runAllStages, false);
    return ResponseDTO.newResponse(planExecutionResponseDto);
  }

  @Override
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_EXECUTE)
  public ResponseDTO<RetryHistoryResponseDto> getRetryHistory(@NotNull @AccountIdentifier String accountId,
      @NotNull @OrgIdentifier String orgIdentifier, @NotNull @ProjectIdentifier String projectIdentifier,
      @NotNull @ResourceIdentifier String pipelineIdentifier, @NotNull String planExecutionId) {
    PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity =
        pmsExecutionService.getPipelineExecutionSummaryEntity(
            accountId, orgIdentifier, projectIdentifier, planExecutionId, false);
    String rootParentId = pipelineExecutionSummaryEntity.getRetryExecutionMetadata().getRootExecutionId();
    return ResponseDTO.newResponse(retryExecutionHelper.getRetryHistory(rootParentId));
  }

  @Override
  @NGAccessControlCheck(resourceType = "PIPELINE", permission = PipelineRbacPermissions.PIPELINE_EXECUTE)
  public ResponseDTO<RetryLatestExecutionResponseDto> getRetryLatestExecutionId(
      @NotNull @AccountIdentifier String accountId, @NotNull @OrgIdentifier String orgIdentifier,
      @NotNull @ProjectIdentifier String projectIdentifier, @NotNull @ResourceIdentifier String pipelineIdentifier,
      @NotNull String planExecutionId) {
    PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity =
        pmsExecutionService.getPipelineExecutionSummaryEntity(
            accountId, orgIdentifier, projectIdentifier, planExecutionId, false);
    String rootParentId = pipelineExecutionSummaryEntity.getRetryExecutionMetadata().getRootExecutionId();
    return ResponseDTO.newResponse(retryExecutionHelper.getRetryLatestExecutionId(rootParentId));
  }
}
