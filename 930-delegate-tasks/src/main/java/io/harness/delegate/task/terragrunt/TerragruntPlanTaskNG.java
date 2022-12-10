/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.terragrunt;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.beans.DelegateFile.Builder.aDelegateFile;
import static io.harness.delegate.task.terragrunt.TerragruntTaskService.createCliRequest;
import static io.harness.delegate.task.terragrunt.TerragruntTaskService.executeWithErrorHandling;
import static io.harness.logging.LogLevel.INFO;
import static io.harness.provision.TerraformConstants.TERRAFORM_DESTROY_PLAN_FILE_OUTPUT_NAME;
import static io.harness.provision.TerraformConstants.TERRAFORM_PLAN_FILE_OUTPUT_NAME;
import static io.harness.provision.TerragruntConstants.FETCH_CONFIG_FILES;
import static io.harness.provision.TerragruntConstants.PLAN;

import static software.wings.beans.LogHelper.color;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.DelegateFile;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.FileBucket;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.logstreaming.UnitProgressDataMapper;
import io.harness.delegate.beans.terragrunt.request.TerragruntCommandType;
import io.harness.delegate.beans.terragrunt.request.TerragruntPlanTaskParameters;
import io.harness.delegate.beans.terragrunt.request.TerragruntTaskRunType;
import io.harness.delegate.beans.terragrunt.response.TerragruntPlanTaskResponse;
import io.harness.delegate.exception.TaskNGDataException;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.common.AbstractDelegateRunnableTask;
import io.harness.delegate.task.terraform.TerraformBaseHelper;
import io.harness.delegate.utils.TaskExceptionUtils;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
import io.harness.logging.PlanJsonLogOutputStream;
import io.harness.logging.PlanLogOutputStream;
import io.harness.secretmanagerclient.EncryptDecryptHelper;
import io.harness.security.encryption.EncryptedRecordData;
import io.harness.terragrunt.v2.TerragruntClient;
import io.harness.terragrunt.v2.request.TerragruntCliRequest;
import io.harness.terragrunt.v2.request.TerragruntPlanCliRequest;
import io.harness.terragrunt.v2.request.TerragruntShowCliRequest;
import io.harness.terragrunt.v2.request.TerragruntWorkspaceCliRequest;

import software.wings.beans.LogColor;
import software.wings.beans.LogWeight;

import com.google.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.jose4j.lang.JoseException;

@Slf4j
@OwnedBy(CDP)
public class TerragruntPlanTaskNG extends AbstractDelegateRunnableTask {
  @Inject private TerragruntTaskService taskService;
  @Inject private EncryptDecryptHelper encryptDecryptHelper;
  @Inject private TerraformBaseHelper terraformHelper;

  public TerragruntPlanTaskNG(DelegateTaskPackage delegateTaskPackage, ILogStreamingTaskClient logStreamingTaskClient,
      Consumer<DelegateTaskResponse> consumer, BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  public DelegateResponseData run(Object[] parameters) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public DelegateResponseData run(TaskParameters parameters) throws IOException, JoseException {
    if (!(parameters instanceof TerragruntPlanTaskParameters)) {
      throw new InvalidArgumentsException(Pair.of("parameters",
          format("Invalid task parameters type provided '%s', expected '%s'", parameters.getClass().getSimpleName(),
              TerragruntPlanTaskParameters.class.getSimpleName())));
    }

    TerragruntPlanTaskParameters planTaskParameters = (TerragruntPlanTaskParameters) parameters;
    CommandUnitsProgress commandUnitsProgress = planTaskParameters.getCommandUnitsProgress() != null
        ? planTaskParameters.getCommandUnitsProgress()
        : CommandUnitsProgress.builder().build();

    String baseDir =
        TerragruntTaskService.getBaseDir(planTaskParameters.getAccountId(), planTaskParameters.getEntityId());
    try (PlanJsonLogOutputStream planJsonLogOutputStream = taskService.getPlanJsonLogOutputStream();
         PlanLogOutputStream planLogOutputStream = new PlanLogOutputStream()) {
      taskService.decryptTaskParameters(planTaskParameters);

      LogCallback fetchFilesLogCallback =
          taskService.getLogCallback(getLogStreamingTaskClient(), FETCH_CONFIG_FILES, commandUnitsProgress);
      TerragruntContext terragruntContext =
          taskService.prepareTerragrunt(fetchFilesLogCallback, planTaskParameters, baseDir);
      taskService.cleanupTerragruntLocalFiles(terragruntContext.getScriptDirectory());

      TerragruntClient client = terragruntContext.getClient();
      LogCallback planLogCallback = taskService.getLogCallback(getLogStreamingTaskClient(), PLAN, commandUnitsProgress);

      executeWithErrorHandling(client::init,
          createCliRequest(TerragruntCliRequest.builder(), terragruntContext, planTaskParameters).build(),
          planLogCallback);

      if (isNotEmpty(planTaskParameters.getWorkspace())) {
        log.info("Create or select workspace {}", planTaskParameters.getWorkspace());
        planLogCallback.saveExecutionLog(
            color(format("Create or select workspace %s", planTaskParameters.getWorkspace()), LogColor.White,
                LogWeight.Bold));
        executeWithErrorHandling(client::workspace,
            createCliRequest(TerragruntWorkspaceCliRequest.builder(), terragruntContext, planTaskParameters)
                .workspace(planTaskParameters.getWorkspace())
                .build(),
            planLogCallback);
        planLogCallback.saveExecutionLog(
            color(format("Use workspace: %s\n", planTaskParameters.getWorkspace()), LogColor.White, LogWeight.Bold));
      }

      String planName = getPlanName(planTaskParameters);
      planLogCallback.saveExecutionLog(
          color(format("Create terragrunt plan '%s'", planName), LogColor.White, LogWeight.Bold));
      executeWithErrorHandling(client::plan,
          createCliRequest(TerragruntPlanCliRequest.builder(), terragruntContext, planTaskParameters)
              .planOutputStream(planLogOutputStream)
              .tfPlanName(planName)
              .destroy(TerragruntCommandType.DESTROY == planTaskParameters.getCommandType())
              .build(),
          planLogCallback);
      planLogCallback.saveExecutionLog(
          color(format("Terragrunt plan '%s' successfully created %n", planName), LogColor.White, LogWeight.Bold));

      EncryptedRecordData tfPlanEncryptedRecord = null;
      String planJsonFileId = null;
      String stateFileId = null;
      if (TerragruntTaskRunType.RUN_MODULE == planTaskParameters.getRunConfiguration().getRunType()) {
        byte[] planFile = Files.readAllBytes(Paths.get(terragruntContext.getTerragruntWorkingDirectory(), planName));
        planLogCallback.saveExecutionLog(format("Encrypting terraform plan: %s", planName));
        DelegateFile planDelegateFile = aDelegateFile()
                                            .withAccountId(planTaskParameters.getAccountId())
                                            .withDelegateId(getDelegateId())
                                            .withTaskId(getTaskId())
                                            .withEntityId(planTaskParameters.getEntityId())
                                            .withBucket(FileBucket.TERRAFORM_PLAN)
                                            .withFileName(planName)
                                            .build();

        tfPlanEncryptedRecord = (EncryptedRecordData) encryptDecryptHelper.encryptFile(
            planFile, planName, planTaskParameters.getPlanSecretManager(), planDelegateFile);
        planLogCallback.saveExecutionLog("Terraform plan successfully encrypted.\n");

        if (isNotEmpty(terragruntContext.getBackendFileSourceReference())) {
          planLogCallback.saveExecutionLog("Uploading terraform state file");
          stateFileId =
              taskService.uploadStateFile(terragruntContext.getScriptDirectory(), planTaskParameters.getWorkspace(),
                  planTaskParameters.getAccountId(), planTaskParameters.getEntityId(), getDelegateId(), getTaskId());
          planLogCallback.saveExecutionLog("Terraform state file successfully uploaded.\n");
        }

        if (planTaskParameters.isExportJsonPlan()) {
          planLogCallback.saveExecutionLog(
              color(format("Export terragrunt plan '%s' as json", planName), LogColor.White, LogWeight.Bold));
          boolean executed = executeWithErrorHandling(client::show,
              createCliRequest(TerragruntShowCliRequest.builder(), terragruntContext, planTaskParameters)
                  .planName(planName)
                  .json(true)
                  .outputStream(planJsonLogOutputStream)
                  .build(),
              planLogCallback);

          if (!executed) {
            planLogCallback.saveExecutionLog(
                "Terragrunt export json plan is not supported by current terraform version", LogLevel.WARN);
          }

          String tfPlanJsonFilePath = planJsonLogOutputStream.getTfPlanJsonLocalPath();
          planLogCallback.saveExecutionLog(format("Uploading json plan '%s' to file service", planName));
          planJsonFileId = terraformHelper.uploadTfPlanJson(planTaskParameters.getAccountId(), getDelegateId(),
              getTaskId(), planTaskParameters.getEntityId(), planName, tfPlanJsonFilePath);

          planLogCallback.saveExecutionLog(
              format("%nTerraform JSON plan will be available at: %s%n", tfPlanJsonFilePath), INFO,
              CommandExecutionStatus.RUNNING);
        }
      }

      planLogCallback.saveExecutionLog(
          color("%n Terragrunt plan successfully completed", LogColor.White, LogWeight.Bold), INFO,
          CommandExecutionStatus.SUCCESS);

      return TerragruntPlanTaskResponse.builder()
          .encryptedPlan(tfPlanEncryptedRecord)
          .stateFileId(stateFileId)
          .planJsonFileId(planJsonFileId)
          .configFilesSourceReference(terragruntContext.getConfigFilesSourceReference())
          .backendFileSourceReference(terragruntContext.getBackendFileSourceReference())
          .varFilesSourceReference(terragruntContext.getVarFilesSourceReference())
          .unitProgressData(UnitProgressDataMapper.toUnitProgressData(commandUnitsProgress))
          .build();

    } catch (Exception e) {
      Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Terragrunt plan task failed", sanitizedException);
      TaskExceptionUtils.handleExceptionCommandUnits(commandUnitsProgress,
          unitName
          -> taskService.getLogCallback(getLogStreamingTaskClient(), unitName, commandUnitsProgress),
          sanitizedException);

      throw new TaskNGDataException(
          UnitProgressDataMapper.toUnitProgressData(commandUnitsProgress), sanitizedException);
    } finally {
      FileUtils.deleteQuietly(new File(baseDir));
    }
  }

  @Override
  public boolean isSupportingErrorFramework() {
    return true;
  }

  private String getPlanName(TerragruntPlanTaskParameters planTaskParameters) {
    switch (planTaskParameters.getCommandType()) {
      case APPLY:
        return TERRAFORM_PLAN_FILE_OUTPUT_NAME;
      case DESTROY:
        return TERRAFORM_DESTROY_PLAN_FILE_OUTPUT_NAME;
      default:
        throw new InvalidArgumentsException(
            Pair.of("commandType", "Invalid Terragrunt Command : " + planTaskParameters.getCommandType().name()));
    }
  }
}
