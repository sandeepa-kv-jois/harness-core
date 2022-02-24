/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.winrm;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.task.shell.ConfigFileMetaData;
import io.harness.logging.LogCallback;

import java.io.IOException;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDP)
@Slf4j
public class FileBasedWinRmExecutorNG extends FileBasedAbstractWinRmExecutor {
  public FileBasedWinRmExecutorNG(LogCallback logCallback, boolean shouldSaveExecutionLogs, WinRmSessionConfig config,
      boolean disableCommandEncoding) {
    super(logCallback, shouldSaveExecutionLogs, config, disableCommandEncoding);
  }

  @Override
  public byte[] getConfigFileBytes(ConfigFileMetaData configFileMetaData) throws IOException {
    // TODO provide implementation for NG
    return new byte[0];
  }
}
