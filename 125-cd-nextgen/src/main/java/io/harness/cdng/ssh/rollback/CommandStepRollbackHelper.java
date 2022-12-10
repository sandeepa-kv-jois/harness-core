/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.ssh.rollback;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.cdng.ssh.utils.CommandStepUtils.getOutputVariables;
import static io.harness.cdng.ssh.utils.CommandStepUtils.mergeEnvironmentVariables;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.artifact.outcome.ArtifactOutcome;
import io.harness.cdng.execution.ExecutionDetails;
import io.harness.cdng.execution.ExecutionInfoKey;
import io.harness.cdng.execution.ExecutionInfoKeyOutput;
import io.harness.cdng.execution.StageExecutionInfo.StageExecutionInfoKeys;
import io.harness.cdng.execution.helper.StageExecutionHelper;
import io.harness.cdng.execution.service.StageExecutionInfoService;
import io.harness.cdng.execution.sshwinrm.SshWinRmStageExecutionDetails;
import io.harness.cdng.ssh.SshWinRmArtifactHelper;
import io.harness.cdng.ssh.SshWinRmConfigFileHelper;
import io.harness.delegate.task.ssh.artifact.SshWinRmArtifactDelegateConfig;
import io.harness.delegate.task.ssh.config.FileDelegateConfig;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.steps.OutputExpressionConstants;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Singleton
@OwnedBy(CDP)
@Slf4j
public class CommandStepRollbackHelper extends CDStepHelper {
  private static final String UPDATE_QUERY_PATH = "%s.%s";
  private static final String ENV_VARIABLES_PROPERTY = "envVariables";
  private static final String OUT_VARIABLES_PROPERTY = "outVariables";

  @Inject private StageExecutionHelper stageExecutionHelper;
  @Inject private StageExecutionInfoService stageExecutionInfoService;
  @Inject private ExecutionSweepingOutputService executionSweepingOutputService;
  @Inject private SshWinRmConfigFileHelper sshWinRmConfigFileHelper;
  @Inject private SshWinRmArtifactHelper sshWinRmArtifactHelper;

  public Optional<SshWinRmRollbackData> getRollbackData(Ambiance ambiance, Map<String, String> builtInEnvVariables) {
    OptionalSweepingOutput executionInfoKeyOptional = executionSweepingOutputService.resolveOptional(
        ambiance, RefObjectUtils.getSweepingOutputRefObject(OutputExpressionConstants.EXECUTION_INFO_KEY_OUTPUT_NAME));
    if (!executionInfoKeyOptional.isFound()) {
      throw new InvalidRequestException(format(
          "No found stage execution info key for rollback, accountIdentifier: %s, orgIdentifier: %s, projectIdentifier: %s, stageExecutionId: %s",
          AmbianceUtils.getAccountId(ambiance), AmbianceUtils.getOrgIdentifier(ambiance),
          AmbianceUtils.getProjectIdentifier(ambiance), ambiance.getStageExecutionId()));
    }

    ExecutionInfoKeyOutput executionInfoKeyOutput = (ExecutionInfoKeyOutput) executionInfoKeyOptional.getOutput();
    ExecutionInfoKey executionInfoKey = executionInfoKeyOutput.getExecutionInfoKey();
    log.info("Found execution info key for rollback, executionInfoKey: {}, stageExecutionId: {}", executionInfoKey,
        ambiance.getStageExecutionId());
    Optional<ExecutionDetails> latestSuccessfulStageExecutionDetails =
        stageExecutionHelper.getLatestSuccessfulStageExecutionDetails(executionInfoKey, ambiance.getStageExecutionId());
    if (!latestSuccessfulStageExecutionDetails.isPresent()) {
      return Optional.empty();
    }

    SshWinRmStageExecutionDetails sshWinRmExecutionDetails =
        (SshWinRmStageExecutionDetails) latestSuccessfulStageExecutionDetails.get();
    List<ArtifactOutcome> artifactsOutcome = sshWinRmExecutionDetails.getArtifactsOutcome();
    SshWinRmArtifactDelegateConfig artifactDelegateConfig = isNotEmpty(artifactsOutcome)
        ? sshWinRmArtifactHelper.getArtifactDelegateConfigConfig(artifactsOutcome.get(0), ambiance)
        : null;
    FileDelegateConfig fileDelegateConfig = sshWinRmExecutionDetails.getConfigFilesOutcome() != null
        ? sshWinRmConfigFileHelper.getFileDelegateConfig(sshWinRmExecutionDetails.getConfigFilesOutcome(), ambiance)
        : null;
    Map<String, String> environmentVariables =
        mergeEnvironmentVariables(sshWinRmExecutionDetails.getEnvVariables(), builtInEnvVariables);
    List<String> outputVariables = getOutputVariables(sshWinRmExecutionDetails.getOutVariables());

    return Optional.of(SshWinRmRollbackData.builder()
                           .artifactDelegateConfig(artifactDelegateConfig)
                           .fileDelegateConfig(fileDelegateConfig)
                           .envVariables(environmentVariables)
                           .outVariables(outputVariables)
                           .build());
  }

  public void updateRollbackData(
      Scope scope, final String stageExecutionId, Map<String, Object> envVariables, Map<String, Object> outVariables) {
    if (isEmpty(envVariables) && isEmpty(outVariables)) {
      return;
    }

    Map<String, Object> updates = new HashMap<>();
    if (isNotEmpty(envVariables)) {
      updates.put(
          format(UPDATE_QUERY_PATH, StageExecutionInfoKeys.executionDetails, ENV_VARIABLES_PROPERTY), envVariables);
    }

    if (isNotEmpty(outVariables)) {
      updates.put(
          format(UPDATE_QUERY_PATH, StageExecutionInfoKeys.executionDetails, OUT_VARIABLES_PROPERTY), outVariables);
    }

    stageExecutionInfoService.updateOnce(scope, stageExecutionId, updates);
  }
}
