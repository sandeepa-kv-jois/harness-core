/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.terragrunt;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.beans.DelegateFile.Builder.aDelegateFile;
import static io.harness.delegate.beans.FileBucket.TERRAFORM_STATE;
import static io.harness.filesystem.FileIo.deleteDirectoryAndItsContentIfExists;
import static io.harness.provision.TerraformConstants.TERRAFORM_STATE_FILE_NAME;
import static io.harness.provision.TerraformConstants.TF_BACKEND_CONFIG_DIR;
import static io.harness.provision.TerraformConstants.TF_VAR_FILES_DIR;
import static io.harness.provision.TerraformConstants.WORKSPACE_STATE_FILE_PATH_FORMAT;
import static io.harness.provision.TerragruntConstants.TERRAGRUNT_INTERNAL_CACHE_FOLDER;
import static io.harness.provision.TerragruntConstants.TERRAGRUNT_LOCK_FILE_NAME;
import static io.harness.provision.TerragruntConstants.TG_BASE_DIR;
import static io.harness.provision.TerragruntConstants.TG_SCRIPT_DIR;

import static software.wings.beans.LogHelper.color;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.defaultString;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;
import io.harness.cli.CliResponse;
import io.harness.connector.helper.DecryptionHelper;
import io.harness.delegate.beans.DelegateFile;
import io.harness.delegate.beans.FileBucket;
import io.harness.delegate.beans.logstreaming.CommandUnitProgress;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.logstreaming.NGDelegateLogCallback;
import io.harness.delegate.beans.storeconfig.StoreDelegateConfig;
import io.harness.delegate.beans.terragrunt.request.AbstractTerragruntTaskParameters;
import io.harness.delegate.beans.terragrunt.request.TerragruntTaskRunType;
import io.harness.delegate.task.terragrunt.files.DownloadResult;
import io.harness.delegate.task.terragrunt.files.FetchFilesResult;
import io.harness.delegate.task.terragrunt.files.TerragruntDownloadService;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.runtime.TerragruntCliRuntimeException;
import io.harness.exception.runtime.TerragruntFetchFilesRuntimeException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
import io.harness.logging.PlanJsonLogOutputStream;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.terragrunt.v2.TerragruntClient;
import io.harness.terragrunt.v2.TerragruntClientFactory;
import io.harness.terragrunt.v2.TerragruntExecutable;
import io.harness.terragrunt.v2.request.AbstractTerragruntCliRequest;
import io.harness.terragrunt.v2.request.AbstractTerragruntCliRequest.AbstractTerragruntCliRequestBuilder;
import io.harness.terragrunt.v2.request.TerragruntCliArgs;
import io.harness.terragrunt.v2.request.TerragruntRunType;

import software.wings.beans.LogColor;
import software.wings.beans.LogWeight;
import software.wings.delegatetasks.DelegateFileManager;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

@Slf4j
@Singleton
@OwnedBy(CDP)
public class TerragruntTaskService {
  @Inject private TerragruntDownloadService terragruntDownloadService;
  @Inject private DelegateFileManager delegateFileManager;
  @Inject private TerragruntClientFactory terragruntClientFactory;
  @Inject private DecryptionHelper decryptionHelper;

  public void decryptTaskParameters(AbstractTerragruntTaskParameters taskParameters) {
    List<Pair<DecryptableEntity, List<EncryptedDataDetail>>> decryptionDetails =
        taskParameters.fetchDecryptionDetails();
    for (Pair<DecryptableEntity, List<EncryptedDataDetail>> decryptionDetail : decryptionDetails) {
      DecryptableEntity decryptableEntity = decryptionDetail.getKey();
      decryptionHelper.decrypt(decryptableEntity, decryptionDetail.getValue());
      ExceptionMessageSanitizer.storeAllSecretsForSanitizing(decryptableEntity, decryptionDetail.getValue());
    }
  }

  public PlanJsonLogOutputStream getPlanJsonLogOutputStream() {
    return new PlanJsonLogOutputStream(true);
  }

  public LogCallback getLogCallback(ILogStreamingTaskClient logStreamingTaskClient, String commandUnitName,
      CommandUnitsProgress commandUnitsProgress) {
    boolean shouldOpenStream = shouldOpenStream(commandUnitName, commandUnitsProgress);

    return new NGDelegateLogCallback(logStreamingTaskClient, commandUnitName, shouldOpenStream, commandUnitsProgress);
  }

  private boolean shouldOpenStream(String commandUnitName, CommandUnitsProgress commandUnitsProgress) {
    if (commandUnitsProgress.getCommandUnitProgressMap() == null) {
      return true;
    }

    CommandUnitProgress unitProgress = commandUnitsProgress.getCommandUnitProgressMap().get(commandUnitName);
    if (unitProgress != null) {
      return CommandExecutionStatus.RUNNING == unitProgress.getStatus();
    }
    return true;
  }

  public TerragruntContext prepareTerragrunt(
      LogCallback logCallback, AbstractTerragruntTaskParameters parameters, String baseDir) {
    String workingDirectory = getScriptDir(baseDir);
    String varFilesDirectory = getVarFilesDir(baseDir);
    String backendFilesDirectory = getBackendFilesDir(baseDir);
    String configFilesDirectory = null;
    String configFilesSourceReference = null;
    String backendFileSourceReference = null;
    Map<String, String> varFilesSourceReference = new HashMap<>();
    log.info("Using base dir: {}, script dir: {}, var files dir: {}, backend dir: {}", baseDir, workingDirectory,
        varFilesDirectory, backendFilesDirectory);

    log.info("Downloading terragrunt config files from store type {}", parameters.getConfigFilesStore().getType());
    logCallback.saveExecutionLog(color("Downloading terragrunt config files", LogColor.White, LogWeight.Bold));
    try {
      DownloadResult result = terragruntDownloadService.download(
          parameters.getConfigFilesStore(), parameters.getAccountId(), workingDirectory, logCallback);
      configFilesDirectory = defaultString(result.getRootDirectory());
      configFilesSourceReference = result.getSourceReference();
    } catch (Exception e) {
      handleFetchFilesException(workingDirectory, "config files", logCallback, e);
    }
    logCallback.saveExecutionLog(
        color("Successfully downloaded terragrunt config files\n", LogColor.White, LogWeight.Bold));

    List<String> varFiles = new ArrayList<>();
    if (isNotEmpty(parameters.getVarFiles())) {
      log.info("Downloading terragrunt var files from store types: {}",
          parameters.getVarFiles().stream().map(StoreDelegateConfig::getType));
      logCallback.saveExecutionLog(color("Downloading var files", LogColor.White, LogWeight.Bold));
      for (StoreDelegateConfig varFileStoreConfig : parameters.getVarFiles()) {
        try {
          FetchFilesResult fetchFilesResult = terragruntDownloadService.fetchFiles(
              varFileStoreConfig, parameters.getAccountId(), varFilesDirectory, logCallback);
          varFiles.addAll(fetchFilesResult.getFiles());
          varFilesSourceReference.put(fetchFilesResult.getIdentifier(), fetchFilesResult.getFilesSourceReference());
        } catch (Exception e) {
          handleFetchFilesException(varFilesDirectory, "var files", logCallback, e);
        }
      }

      logCallback.saveExecutionLog(
          color(format("Successfully downloaded (%d) var files %n", varFiles.size()), LogColor.White, LogWeight.Bold));
    }

    if (configFilesDirectory == null) {
      throw new TerragruntFetchFilesRuntimeException("Root path for config files is not found", StringUtils.EMPTY);
    }

    String scriptDirectory = Paths.get(configFilesDirectory, defaultString(parameters.getRunConfiguration().getPath()))
                                 .toAbsolutePath()
                                 .toString();

    TerragruntClient terragruntClient =
        terragruntClientFactory.getClient(scriptDirectory, parameters.getTimeoutInMillis());
    String terragruntWorkingDirectory = terragruntClient.terragruntWorkingDirectory();
    String backendFile = null;
    if (parameters.getBackendFilesStore() != null) {
      log.info("Downloading terragrunt backend file from store type: {}", parameters.getBackendFilesStore().getType());
      logCallback.saveExecutionLog(color("Downloading backend file", LogColor.White, LogWeight.Bold));
      try {
        FetchFilesResult backendFetchResult = terragruntDownloadService.fetchFiles(
            parameters.getBackendFilesStore(), parameters.getAccountId(), backendFilesDirectory, logCallback);
        if (backendFetchResult.getFiles().size() > 1) {
          log.warn("Downloaded multiple backend files, expected only a single file");
          logCallback.saveExecutionLog(
              "Found multiple backend files, only first file will be used. Please check your backend configuration",
              LogLevel.WARN);
        }

        backendFile = backendFetchResult.getFiles().get(0);
        backendFileSourceReference = backendFetchResult.getFilesSourceReference();
        logCallback.saveExecutionLog(color("Successfully downloaded backend file\n", LogColor.White, LogWeight.Bold));
      } catch (Exception e) {
        handleFetchFilesException(backendFilesDirectory, "backend file", logCallback, e);
      }
    }
    if (isNotEmpty(parameters.getStateFileId())) {
      log.info("Downloading harness terraform state file: {}", parameters.getStateFileId());
      logCallback.saveExecutionLog(color(
          format("Downloading local state file to: %s", terragruntWorkingDirectory), LogColor.White, LogWeight.Bold));
      downloadTfStateFile(parameters.getWorkspace(), parameters.getStateFileId(), terragruntWorkingDirectory,
          parameters.getAccountId(), logCallback);
    }

    logCallback.saveExecutionLog(color("All files downloaded successfully", LogColor.White, LogWeight.Bold),
        LogLevel.INFO, CommandExecutionStatus.SUCCESS);

    return TerragruntContext.builder()
        .varFilesDirectory(varFilesDirectory)
        .workingDirectory(workingDirectory)
        .scriptDirectory(scriptDirectory)
        .terragruntWorkingDirectory(terragruntWorkingDirectory)
        .varFiles(varFiles)
        .backendFile(backendFile)
        .configFilesSourceReference(configFilesSourceReference)
        .backendFileSourceReference(backendFileSourceReference)
        .varFilesSourceReference(varFilesSourceReference)
        .client(terragruntClient)
        .build();
  }

  public void cleanupTerragruntLocalFiles(String scriptDirectory) {
    FileUtils.deleteQuietly(Paths.get(scriptDirectory, TERRAGRUNT_LOCK_FILE_NAME).toFile());
    try {
      deleteDirectoryAndItsContentIfExists(Paths.get(scriptDirectory, TERRAGRUNT_INTERNAL_CACHE_FOLDER).toString());
    } catch (IOException e) {
      log.warn("Failed to delete .terragrunt-cache folder", e);
    }
  }

  public String uploadStateFile(
      String scripDirectory, String workspace, String accountId, String entityId, String delegateId, String taskId) {
    File terraformStateFile = getTerraformStateFile(scripDirectory, workspace);
    if (terraformStateFile == null) {
      return null;
    }

    final DelegateFile delegateFile = aDelegateFile()
                                          .withAccountId(accountId)
                                          .withDelegateId(delegateId)
                                          .withTaskId(taskId)
                                          .withEntityId(entityId)
                                          .withBucket(FileBucket.TERRAFORM_STATE)
                                          .withFileName(TERRAFORM_STATE_FILE_NAME)
                                          .build();

    try (InputStream initialStream = new FileInputStream(terraformStateFile)) {
      delegateFileManager.upload(delegateFile, initialStream);
    } catch (FileNotFoundException e) {
      throw NestedExceptionUtils.hintWithExplanationException(
          "Unable to find terraform state file, probably it was deleted before uploading it",
          format("File %s not found", terraformStateFile.getPath()), e);
    } catch (IOException e) {
      throw NestedExceptionUtils.hintWithExplanationException(
          "Please check delegate fs permissions", format("Unable to read file %s", terraformStateFile.getPath()), e);
    }

    return delegateFile.getFileId();
  }

  public File getTerraformStateFile(String scripDirectory, String workspace) {
    if (!StringUtils.isBlank(scripDirectory)) {
      File tfStateFile = isEmpty(workspace)
          ? Paths.get(scripDirectory, TERRAFORM_STATE_FILE_NAME).toFile()
          : Paths.get(scripDirectory, format(WORKSPACE_STATE_FILE_PATH_FORMAT, workspace)).toFile();

      if (tfStateFile.exists()) {
        return tfStateFile;
      }
    }

    return null;
  }

  public static <T extends AbstractTerragruntCliRequest> boolean executeWithErrorHandling(
      TerragruntExecutable<T> commandExecutor, T request, LogCallback logCallback) {
    try {
      CliResponse response = commandExecutor.execute(request, logCallback);
      if (response.getCommandExecutionStatus() == CommandExecutionStatus.SKIPPED) {
        return false;
      }

      if (response.getExitCode() != 0) {
        throw new TerragruntCliRuntimeException(format("Terragrunt command '%s' failed with error code '%s'",
                                                    response.getCommand(), response.getExitCode()),
            response.getCommand(), response.getError());
      }

      return true;
    } catch (IOException e) {
      throw new TerragruntCliRuntimeException(
          "IO error while executing terragrunt command. Please check delegate permissions or terragrunt binary availability",
          e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new TerragruntCliRuntimeException("Terragrunt command execution was interrupted", e);
    } catch (TimeoutException e) {
      throw new TerragruntCliRuntimeException("Terragrunt command execution timed out", e);
    }
  }

  public static <C extends AbstractTerragruntCliRequest, T extends AbstractTerragruntCliRequestBuilder<C, ?>> T
  createCliRequest(T baseBuilder, TerragruntContext filesContext, AbstractTerragruntTaskParameters taskParameters) {
    baseBuilder.timeoutInMillis(taskParameters.getTimeoutInMillis())
        .args(TerragruntCliArgs.builder()
                  .targets(taskParameters.getTargets())
                  .backendConfigFile(filesContext.getBackendFile())
                  .varFiles(filesContext.getVarFiles())
                  .build())
        .runType(getCliRunType(taskParameters))
        .envVars(taskParameters.getEnvVars())
        .workingDirectory(filesContext.getScriptDirectory());

    return baseBuilder;
  }

  public static String getBaseDir(String accountId, String entityId) {
    return TG_BASE_DIR.replace("${ACCOUNT_ID}", accountId).replace("${ENTITY_ID}", entityId);
  }

  public static TerragruntRunType getCliRunType(AbstractTerragruntTaskParameters params) {
    return params.getRunConfiguration().getRunType() == TerragruntTaskRunType.RUN_ALL ? TerragruntRunType.RUN_ALL
                                                                                      : TerragruntRunType.RUN_MODULE;
  }

  private void handleFetchFilesException(
      String outputDirectory, String filesType, LogCallback logCallback, Exception e) {
    Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(e);
    String message = ExceptionUtils.getMessage(e);
    log.error("Failed to download terragrunt {} due to: {}", filesType, message, sanitizedException);
    logCallback.saveExecutionLog(format("Failed to download terragrunt %s due to: %s", filesType, message),
        LogLevel.ERROR, CommandExecutionStatus.FAILURE);
    throw new TerragruntFetchFilesRuntimeException(message, outputDirectory, e);
  }

  private void downloadTfStateFile(
      String workspace, String stateFileId, String configFilesDirectory, String accountId, LogCallback logCallback) {
    File tfStateFile = (isEmpty(workspace))
        ? Paths.get(configFilesDirectory, TERRAFORM_STATE_FILE_NAME).toFile()
        : Paths.get(configFilesDirectory, format(WORKSPACE_STATE_FILE_PATH_FORMAT, stateFileId)).toFile();

    try (InputStream stateRemoteInputStream =
             delegateFileManager.downloadByFileId(TERRAFORM_STATE, stateFileId, accountId)) {
      PushbackInputStream pushbackInputStream = new PushbackInputStream(stateRemoteInputStream);
      int firstByte = pushbackInputStream.read();
      if (firstByte == -1) {
        logCallback.saveExecutionLog(
            format("Invalid or corrupted terraform state file %s", stateFileId), LogLevel.WARN);
        FileUtils.deleteQuietly(tfStateFile);
      } else {
        pushbackInputStream.unread(firstByte);
        FileUtils.copyInputStreamToFile(pushbackInputStream, tfStateFile);
        logCallback.saveExecutionLog("Successfully downloaded terraform state file");
      }
    } catch (IOException exception) {
      throw new TerragruntFetchFilesRuntimeException(
          format("Failed to download terraform state file '%s'", stateFileId), configFilesDirectory, exception);
    }
  }

  private static String getScriptDir(String baseDir) {
    return Paths.get(baseDir, TG_SCRIPT_DIR).toString();
  }

  private static String getVarFilesDir(String baseDir) {
    return Paths.get(baseDir, TF_VAR_FILES_DIR).toString();
  }

  private static String getBackendFilesDir(String baseDir) {
    return Paths.get(baseDir, TF_BACKEND_CONFIG_DIR).toString();
  }
}
