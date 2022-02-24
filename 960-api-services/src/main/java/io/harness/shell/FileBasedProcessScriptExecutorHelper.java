/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.shell;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.filesystem.FileIo.createDirectoryIfDoesNotExist;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;

import io.harness.annotations.dev.OwnedBy;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@Slf4j
@OwnedBy(CDP)
public class FileBasedProcessScriptExecutorHelper {
  public static CommandExecutionStatus scpOneFile(String remoteFilePath,
      AbstractScriptExecutor.FileProvider fileProvider, LogCallback logCallback, boolean shouldSaveExecutionLogs) {
    Consumer<String> saveExecutionLog =
        SshHelperUtils.checkAndSaveExecutionLogFunction(logCallback, shouldSaveExecutionLogs);
    CommandExecutionStatus commandExecutionStatus = FAILURE;
    try {
      Pair<String, Long> fileInfo = fileProvider.getInfo();
      createDirectoryIfDoesNotExist(remoteFilePath);
      OutputStream out = new FileOutputStream(remoteFilePath + "/" + fileInfo.getKey());
      fileProvider.downloadToStream(out);
      out.flush();
      out.close();
      commandExecutionStatus = SUCCESS;
      saveExecutionLog.accept("File successfully downloaded to " + remoteFilePath);
    } catch (ExecutionException | IOException e) {
      log.error("Command execution failed with error", e);
    }
    return commandExecutionStatus;
  }
}
