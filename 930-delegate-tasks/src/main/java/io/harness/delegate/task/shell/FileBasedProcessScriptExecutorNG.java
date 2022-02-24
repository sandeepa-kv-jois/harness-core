/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.shell;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.shell.AbstractScriptExecutor;
import io.harness.shell.FileBasedProcessScriptExecutorHelper;

@OwnedBy(HarnessTeam.CDC)
public class FileBasedProcessScriptExecutorNG extends FileBasedAbstractScriptExecutorNG {
  public FileBasedProcessScriptExecutorNG(LogCallback logCallback, boolean shouldSaveExecutionLogs) {
    super(logCallback, shouldSaveExecutionLogs);
  }

  @Override
  public CommandExecutionStatus scpOneFile(String remoteFilePath, AbstractScriptExecutor.FileProvider fileProvider) {
    return FileBasedProcessScriptExecutorHelper.scpOneFile(
        remoteFilePath, fileProvider, logCallback, shouldSaveExecutionLogs);
  }
}
