/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.pms.instrumentaion.PipelineInstrumentationConstants.ORG_IDENTIFIER;
import static io.harness.pms.instrumentaion.PipelineInstrumentationConstants.PIPELINE_ID;
import static io.harness.pms.instrumentaion.PipelineInstrumentationConstants.PROJECT_IDENTIFIER;

import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.executions.plan.PlanExecutionMetadataService;
import io.harness.engine.executions.retry.RetryExecutionParameters;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.PlanExecution;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.execution.StagesExecutionMetadata;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitsync.sdk.EntityGitDetailsMapper;
import io.harness.pms.contracts.plan.ExecutionTriggerInfo;
import io.harness.pms.contracts.plan.PipelineStageInfo;
import io.harness.pms.instrumentaion.PipelineTelemetryHelper;
import io.harness.pms.ngpipeline.inputset.helpers.ValidateAndMergeHelper;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.mappers.PMSPipelineDtoMapper;
import io.harness.pms.pipeline.service.PMSPipelineTemplateHelper;
import io.harness.pms.plan.execution.beans.ExecArgs;
import io.harness.pms.plan.execution.beans.dto.RunStageRequestDTO;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PIPELINE)
@Singleton
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
public class PipelineExecutor {
  private final String START_PIPELINE_EXECUTION_EVENT = "ng_start_pipeline_execution";
  ExecutionHelper executionHelper;
  ValidateAndMergeHelper validateAndMergeHelper;
  PlanExecutionMetadataService planExecutionMetadataService;
  RetryExecutionHelper retryExecutionHelper;
  PMSPipelineTemplateHelper pipelineTemplateHelper;
  PipelineTelemetryHelper pipelineTelemetryHelper;

  public PlanExecutionResponseDto runPipelineWithInputSetPipelineYaml(@NotNull String accountId,
      @NotNull String orgIdentifier, @NotNull String projectIdentifier, @NotNull String pipelineIdentifier,
      String moduleType, String runtimeInputYaml, boolean useV2, boolean notifyOnlyUser) {
    return startPlanExecution(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, null, moduleType,
        runtimeInputYaml, Collections.emptyList(), Collections.emptyMap(), useV2, notifyOnlyUser);
  }

  public PlanExecutionResponseDto runPipelineWithInputSetReferencesList(String accountId, String orgIdentifier,
      String projectIdentifier, String pipelineIdentifier, String moduleType, List<String> inputSetReferences,
      String pipelineBranch, String pipelineRepoID) {
    String mergedRuntimeInputYaml = validateAndMergeHelper.getMergeInputSetFromPipelineTemplate(accountId,
        orgIdentifier, projectIdentifier, pipelineIdentifier, inputSetReferences, pipelineBranch, pipelineRepoID, null);
    return startPlanExecution(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, null, moduleType,
        mergedRuntimeInputYaml, Collections.emptyList(), Collections.emptyMap(), false, false);
  }

  public PlanExecutionResponseDto runStagesWithRuntimeInputYaml(@NotNull String accountId,
      @NotNull String orgIdentifier, @NotNull String projectIdentifier, @NotNull String pipelineIdentifier,
      String moduleType, RunStageRequestDTO runStageRequestDTO, boolean useV2) {
    return startPlanExecution(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, null, moduleType,
        runStageRequestDTO.getRuntimeInputYaml(), runStageRequestDTO.getStageIdentifiers(),
        runStageRequestDTO.getExpressionValues(), useV2, false);
  }

  public PlanExecutionResponseDto rerunStagesWithRuntimeInputYaml(@NotNull String accountId,
      @NotNull String orgIdentifier, @NotNull String projectIdentifier, @NotNull String pipelineIdentifier,
      String moduleType, String originalExecutionId, RunStageRequestDTO runStageRequestDTO, boolean useV2) {
    return startPlanExecution(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, originalExecutionId,
        moduleType, runStageRequestDTO.getRuntimeInputYaml(), runStageRequestDTO.getStageIdentifiers(),
        runStageRequestDTO.getExpressionValues(), useV2, false);
  }

  public PlanExecutionResponseDto rerunPipelineWithInputSetPipelineYaml(String accountId, String orgIdentifier,
      String projectIdentifier, String pipelineIdentifier, String moduleType, String originalExecutionId,
      String runtimeInputYaml, boolean useV2) {
    return startPlanExecution(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, originalExecutionId,
        moduleType, runtimeInputYaml, Collections.emptyList(), Collections.emptyMap(), useV2, false);
  }

  public PlanExecutionResponseDto rerunPipelineWithInputSetReferencesList(String accountId, String orgIdentifier,
      String projectIdentifier, String pipelineIdentifier, String moduleType, String originalExecutionId,
      List<String> inputSetReferences, String pipelineBranch, String pipelineRepoID) {
    String mergedRuntimeInputYaml = validateAndMergeHelper.getMergeInputSetFromPipelineTemplate(accountId,
        orgIdentifier, projectIdentifier, pipelineIdentifier, inputSetReferences, pipelineBranch, pipelineRepoID, null);
    return startPlanExecution(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, originalExecutionId,
        moduleType, mergedRuntimeInputYaml, Collections.emptyList(), Collections.emptyMap(), false, false);
  }

  private PlanExecutionResponseDto startPlanExecution(String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, String originalExecutionId, String moduleType, String runtimeInputYaml,
      List<String> stagesToRun, Map<String, String> expressionValues, boolean useV2, boolean notifyOnlyUser) {
    sendExecutionStartTelemetryEvent(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier);
    PipelineEntity pipelineEntity =
        executionHelper.fetchPipelineEntity(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier);
    if (pipelineEntity.getIsDraft() != null && pipelineEntity.getIsDraft()) {
      throw new InvalidRequestException(String.format(
          "Cannot execute a Draft Pipeline with PipelineID: %s, ProjectID %s", pipelineIdentifier, projectIdentifier));
    }
    ExecArgs execArgs = getExecArgs(originalExecutionId, moduleType, runtimeInputYaml, stagesToRun, expressionValues,
        notifyOnlyUser, pipelineEntity);

    return getPlanExecutionResponseDto(accountId, orgIdentifier, projectIdentifier, useV2, pipelineEntity, execArgs);
  }

  private PlanExecutionResponseDto getPlanExecutionResponseDto(String accountId, String orgIdentifier,
      String projectIdentifier, boolean useV2, PipelineEntity pipelineEntity, ExecArgs execArgs) {
    PlanExecution planExecution;
    if (useV2) {
      planExecution = executionHelper.startExecutionV2(accountId, orgIdentifier, projectIdentifier,
          execArgs.getMetadata(), execArgs.getPlanExecutionMetadata(), false, null, null, null);
    } else {
      planExecution = executionHelper.startExecution(accountId, orgIdentifier, projectIdentifier,
          execArgs.getMetadata(), execArgs.getPlanExecutionMetadata(), false, null, null, null);
    }
    return PlanExecutionResponseDto.builder()
        .planExecution(planExecution)
        .gitDetails(PMSPipelineDtoMapper.getEntityGitDetails(pipelineEntity))
        .build();
  }

  private ExecArgs getExecArgs(String originalExecutionId, String moduleType, String runtimeInputYaml,
      List<String> stagesToRun, Map<String, String> expressionValues, boolean notifyOnlyUser,
      PipelineEntity pipelineEntity) {
    ExecutionTriggerInfo triggerInfo = executionHelper.buildTriggerInfo(originalExecutionId);

    // RetryExecutionParameters
    RetryExecutionParameters retryExecutionParameters = buildRetryExecutionParameters(false, null, null, null);

    return executionHelper.buildExecutionArgs(pipelineEntity, moduleType, runtimeInputYaml, stagesToRun,
        expressionValues, triggerInfo, originalExecutionId, retryExecutionParameters, notifyOnlyUser);
  }

  public PlanExecutionResponseDto retryPipelineWithInputSetPipelineYaml(@NotNull String accountId,
      @NotNull String orgIdentifier, @NotNull String projectIdentifier, @NotNull String pipelineIdentifier,
      String moduleType, String inputSetPipelineYaml, String previousExecutionId, List<String> retryStagesIdentifier,
      boolean runAllStages, boolean useV2) {
    PipelineEntity pipelineEntity =
        executionHelper.fetchPipelineEntity(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier);

    if (!runAllStages && retryStagesIdentifier.size() > 1) {
      // run only failed stage
      retryStagesIdentifier = retryExecutionHelper.fetchOnlyFailedStages(previousExecutionId, retryStagesIdentifier);
    }

    ExecutionTriggerInfo triggerInfo = executionHelper.buildTriggerInfo(null);
    Optional<PlanExecutionMetadata> optionalPlanExecutionMetadata =
        planExecutionMetadataService.findByPlanExecutionId(previousExecutionId);

    if (!optionalPlanExecutionMetadata.isPresent()) {
      throw new InvalidRequestException(String.format("No plan exist for %s planExecutionId", previousExecutionId));
    }
    PlanExecutionMetadata planExecutionMetadata = optionalPlanExecutionMetadata.get();
    String previousProcessedYaml = planExecutionMetadata.getProcessedYaml();
    List<String> identifierOfSkipStages = new ArrayList<>();

    // RetryExecutionParameters
    // TODO(BRIJESH): Stage identifiers should be same as YAML and not with the matrix prefix. Its temp. Do something
    // here.
    RetryExecutionParameters retryExecutionParameters =
        buildRetryExecutionParameters(true, previousProcessedYaml, retryStagesIdentifier, identifierOfSkipStages);

    StagesExecutionMetadata stagesExecutionMetadata = planExecutionMetadata.getStagesExecutionMetadata();
    ExecArgs execArgs = executionHelper.buildExecutionArgs(pipelineEntity, moduleType, inputSetPipelineYaml,
        stagesExecutionMetadata == null ? null : stagesExecutionMetadata.getStageIdentifiers(),
        stagesExecutionMetadata == null ? null : stagesExecutionMetadata.getExpressionValues(), triggerInfo,
        previousExecutionId, retryExecutionParameters, false);
    PlanExecution planExecution;
    if (useV2) {
      planExecution = executionHelper.startExecutionV2(accountId, orgIdentifier, projectIdentifier,
          execArgs.getMetadata(), execArgs.getPlanExecutionMetadata(), true, identifierOfSkipStages,
          previousExecutionId, retryStagesIdentifier);
    } else {
      planExecution = executionHelper.startExecution(accountId, orgIdentifier, projectIdentifier,
          execArgs.getMetadata(), execArgs.getPlanExecutionMetadata(), true, identifierOfSkipStages,
          previousExecutionId, retryStagesIdentifier);
    }
    return PlanExecutionResponseDto.builder()
        .planExecution(planExecution)
        .gitDetails(EntityGitDetailsMapper.mapEntityGitDetails(pipelineEntity))
        .build();
  }

  public RetryExecutionParameters buildRetryExecutionParameters(
      boolean isRetry, String processedYaml, List<String> stagesIdentifier, List<String> identifierOfSkipStages) {
    if (!isRetry) {
      return RetryExecutionParameters.builder().isRetry(false).build();
    }

    return RetryExecutionParameters.builder()
        .isRetry(true)
        .previousProcessedYaml(processedYaml)
        .retryStagesIdentifier(stagesIdentifier)
        .identifierOfSkipStages(identifierOfSkipStages)
        .build();
  }

  private void sendExecutionStartTelemetryEvent(
      String accountId, String orgId, String projectId, String pipelineIdentifier) {
    HashMap<String, Object> propertiesMap = new HashMap<>();
    propertiesMap.put(PROJECT_IDENTIFIER, projectId);
    propertiesMap.put(ORG_IDENTIFIER, orgId);
    propertiesMap.put(PIPELINE_ID, pipelineIdentifier);
    pipelineTelemetryHelper.sendTelemetryEventWithAccountName(START_PIPELINE_EXECUTION_EVENT, accountId, propertiesMap);
  }

  public PlanExecutionResponseDto runPipelineAsChildPipeline(String accountId, String orgIdentifier,
      String projectIdentifier, String pipelineIdentifier, String moduleType, String runtimeYaml, boolean useV2,
      boolean notifyOnlyUser, List<String> inputSetReferences, PipelineStageInfo info) {
    String inputSetYaml = runtimeYaml;
    if (!isEmpty(inputSetReferences)) {
      GitEntityInfo gitEntityInfo = GitAwareContextHelper.getGitRequestParamsInfo();
      inputSetYaml =
          validateAndMergeHelper.getMergeInputSetFromPipelineTemplate(accountId, orgIdentifier, projectIdentifier,
              pipelineIdentifier, inputSetReferences, gitEntityInfo.getBranch(), gitEntityInfo.getRepoName(), null);
    }
    return startPlanExecutionForChildPipeline(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, null,
        moduleType, inputSetYaml, Collections.emptyList(), Collections.emptyMap(), useV2, notifyOnlyUser, info);
  }

  private PlanExecutionResponseDto startPlanExecutionForChildPipeline(String accountId, String orgIdentifier,
      String projectIdentifier, String pipelineIdentifier, String originalExecutionId, String moduleType,
      String runtimeInputYaml, List<String> stagesToRun, Map<String, String> expressionValues, boolean useV2,
      boolean notifyOnlyUser, PipelineStageInfo info) {
    PipelineEntity pipelineEntity =
        executionHelper.fetchPipelineEntity(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier);
    if (pipelineEntity.getIsDraft() != null && pipelineEntity.getIsDraft()) {
      throw new InvalidRequestException(String.format(
          "Cannot execute a Draft Pipeline with PipelineID: %s, ProjectID %s", pipelineIdentifier, projectIdentifier));
    }
    ExecArgs execArgs = getExecArgs(originalExecutionId, moduleType, runtimeInputYaml, stagesToRun, expressionValues,
        notifyOnlyUser, pipelineEntity);

    if (info != null) {
      execArgs.setMetadata(execArgs.getMetadata().toBuilder().setPipelineStageInfo(info).build());
    }
    return getPlanExecutionResponseDto(accountId, orgIdentifier, projectIdentifier, useV2, pipelineEntity, execArgs);
  }
}
