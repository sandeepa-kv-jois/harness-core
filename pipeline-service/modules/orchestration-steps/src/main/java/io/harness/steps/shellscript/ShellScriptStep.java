/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.steps.shellscript;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import static java.util.Collections.emptyList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.CollectionUtils;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.shell.ShellScriptTaskNG;
import io.harness.delegate.task.shell.ShellScriptTaskResponseNG;
import io.harness.delegate.task.shell.WinRmShellScriptTaskNG;
import io.harness.exception.UnsupportedOperationException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.UnitProgress;
import io.harness.logstreaming.ILogStreamingStepClient;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.plancreator.steps.common.rollback.TaskExecutableWithRollback;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.TaskExecutableResponse;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepResponseBuilder;
import io.harness.serializer.KryoSerializer;
import io.harness.shell.ShellExecutionData;
import io.harness.steps.OutputExpressionConstants;
import io.harness.steps.StepHelper;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.StepUtils;
import io.harness.supplier.ThrowingSupplier;

import software.wings.beans.TaskType;

import com.google.inject.Inject;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDC)
@Slf4j
public class ShellScriptStep extends TaskExecutableWithRollback<ShellScriptTaskResponseNG> {
  public static final StepType STEP_TYPE = StepSpecTypeConstants.SHELL_SCRIPT_STEP_TYPE;

  @Inject private KryoSerializer kryoSerializer;
  @Inject private StepHelper stepHelper;
  @Inject private ShellScriptHelperService shellScriptHelperService;
  @Inject private LogStreamingStepClientFactory logStreamingStepClientFactory;

  @Override
  public Class<StepElementParameters> getStepParametersClass() {
    return StepElementParameters.class;
  }

  @Override
  public TaskRequest obtainTask(
      Ambiance ambiance, StepElementParameters stepParameters, StepInputPackage inputPackage) {
    ShellScriptStepParameters shellScriptStepParameters = (ShellScriptStepParameters) stepParameters.getSpec();
    TaskParameters taskParameters =
        shellScriptHelperService.buildShellScriptTaskParametersNG(ambiance, shellScriptStepParameters);

    switch (shellScriptStepParameters.getShell()) {
      case Bash:
        return obtainBashTask(ambiance, stepParameters, shellScriptStepParameters, taskParameters);
      case PowerShell:
        return obtainPowerShellTask(ambiance, stepParameters, shellScriptStepParameters, taskParameters);
      default:
        throw new UnsupportedOperationException("Shell type not supported: " + shellScriptStepParameters.getShell());
    }
  }

  private TaskRequest obtainBashTask(Ambiance ambiance, StepElementParameters stepParameters,
      ShellScriptStepParameters shellScriptStepParameters, TaskParameters taskParameters) {
    ILogStreamingStepClient logStreamingStepClient = logStreamingStepClientFactory.getLogStreamingStepClient(ambiance);
    logStreamingStepClient.openStream(ShellScriptTaskNG.COMMAND_UNIT);
    TaskData taskData =
        TaskData.builder()
            .async(true)
            .taskType(TaskType.SHELL_SCRIPT_TASK_NG.name())
            .parameters(new Object[] {taskParameters})
            .timeout(StepUtils.getTimeoutMillis(stepParameters.getTimeout(), StepUtils.DEFAULT_STEP_TIMEOUT))
            .build();
    return StepUtils.prepareCDTaskRequest(ambiance, taskData, kryoSerializer,
        CollectionUtils.emptyIfNull(StepUtils.generateLogKeys(
            StepUtils.generateLogAbstractions(ambiance), Collections.singletonList(ShellScriptTaskNG.COMMAND_UNIT))),
        null, null, TaskSelectorYaml.toTaskSelector(shellScriptStepParameters.getDelegateSelectors()),
        stepHelper.getEnvironmentType(ambiance));
  }

  private TaskRequest obtainPowerShellTask(Ambiance ambiance, StepElementParameters stepParameters,
      ShellScriptStepParameters shellScriptStepParameters, TaskParameters taskParameters) {
    ILogStreamingStepClient logStreamingStepClient = logStreamingStepClientFactory.getLogStreamingStepClient(ambiance);

    List<String> units = Arrays.asList(WinRmShellScriptTaskNG.INIT_UNIT, WinRmShellScriptTaskNG.COMMAND_UNIT);
    for (String unit : units) {
      logStreamingStepClient.openStream(unit);
    }

    TaskData taskData =
        TaskData.builder()
            .async(true)
            .taskType(TaskType.WIN_RM_SHELL_SCRIPT_TASK_NG.name())
            .parameters(new Object[] {taskParameters})
            .timeout(StepUtils.getTimeoutMillis(stepParameters.getTimeout(), StepUtils.DEFAULT_STEP_TIMEOUT))
            .build();
    return StepUtils.prepareCDTaskRequest(ambiance, taskData, kryoSerializer, units,
        TaskType.WIN_RM_SHELL_SCRIPT_TASK_NG.getDisplayName(),
        TaskSelectorYaml.toTaskSelector(shellScriptStepParameters.getDelegateSelectors()),
        stepHelper.getEnvironmentType(ambiance));
  }

  @Override
  public StepResponse handleTaskResult(Ambiance ambiance, StepElementParameters stepParameters,
      ThrowingSupplier<ShellScriptTaskResponseNG> responseSupplier) throws Exception {
    try {
      StepResponseBuilder stepResponseBuilder = StepResponse.builder();
      ShellScriptTaskResponseNG taskResponse = responseSupplier.get();
      ShellScriptStepParameters shellScriptStepParameters = (ShellScriptStepParameters) stepParameters.getSpec();
      List<UnitProgress> unitProgresses = taskResponse.getUnitProgressData() == null
          ? emptyList()
          : taskResponse.getUnitProgressData().getUnitProgresses();
      stepResponseBuilder.unitProgressList(unitProgresses);

      stepResponseBuilder.status(StepUtils.getStepStatus(taskResponse.getStatus()));

      FailureInfo.Builder failureInfoBuilder = FailureInfo.newBuilder();
      if (taskResponse.getErrorMessage() != null) {
        failureInfoBuilder.setErrorMessage(taskResponse.getErrorMessage());
      }
      stepResponseBuilder.failureInfo(failureInfoBuilder.build());

      if (taskResponse.getStatus() == CommandExecutionStatus.SUCCESS) {
        ShellExecutionData commandExecutionData =
            (ShellExecutionData) taskResponse.getExecuteCommandResponse().getCommandExecutionData();
        ShellScriptOutcome shellScriptOutcome =
            ShellScriptHelperService.prepareShellScriptOutcome(commandExecutionData.getSweepingOutputEnvVariables(),
                shellScriptStepParameters.getOutputVariables(), shellScriptStepParameters.getSecretOutputVariables());
        if (shellScriptOutcome != null) {
          stepResponseBuilder.stepOutcome(StepResponse.StepOutcome.builder()
                                              .name(OutputExpressionConstants.OUTPUT)
                                              .outcome(shellScriptOutcome)
                                              .build());
        }
      }
      return stepResponseBuilder.build();
    } finally {
      closeLogStream(ambiance);
    }
  }

  @Override
  public void handleAbort(
      Ambiance ambiance, StepElementParameters stepParameters, TaskExecutableResponse executableResponse) {
    closeLogStream(ambiance);
  }

  private void closeLogStream(Ambiance ambiance) {
    try {
      Thread.sleep(500, 0);
    } catch (InterruptedException e) {
      log.error("Close Log Stream was interrupted", e);
    } finally {
      ILogStreamingStepClient logStreamingStepClient =
          logStreamingStepClientFactory.getLogStreamingStepClient(ambiance);
      logStreamingStepClient.closeAllOpenStreamsWithPrefix(StepUtils.generateLogKeys(ambiance, emptyList()).get(0));
    }
  }
}
