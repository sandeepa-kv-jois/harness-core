/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.shell;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.shell.AbstractScriptExecutor;
import io.harness.shell.SshHelperUtils;

import java.util.List;

@OwnedBy(CDP)
public abstract class FileBasedAbstractScriptExecutorNG implements FileBasedScriptExecutorNG {
  protected LogCallback logCallback;

  protected boolean shouldSaveExecutionLogs;

  public FileBasedAbstractScriptExecutorNG(LogCallback logCallback, boolean shouldSaveExecutionLogs) {
    this.logCallback = logCallback;
    this.shouldSaveExecutionLogs = shouldSaveExecutionLogs;
  }

  public abstract CommandExecutionStatus scpOneFile(
      String remoteFilePath, AbstractScriptExecutor.FileProvider fileProvider);

  @Override
  public CommandExecutionStatus copyFiles(String destinationDirectoryPath, List<String> files) {
    // TODO implement some code where scpOneFile(...) is invoked
    return CommandExecutionStatus.SUCCESS;
  }

  @Override
  public CommandExecutionStatus copyConfigFiles(String destinationDirectoryPath, List<String> files) {
    // TODO implement some code where scpOneFile(...) is invoked
    return CommandExecutionStatus.SUCCESS;
  }

  protected void saveExecutionLog(String line) {
    SshHelperUtils.checkAndSaveExecutionLog(line, logCallback, shouldSaveExecutionLogs);
  }

  protected void saveExecutionLogWarn(String line) {
    SshHelperUtils.checkAndSaveExecutionLogWarn(line, logCallback, shouldSaveExecutionLogs);
  }

  protected void saveExecutionLogError(String line) {
    SshHelperUtils.checkAndSaveExecutionLogError(line, logCallback, shouldSaveExecutionLogs);
  }
}
