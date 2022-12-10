/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.provision.terraform;

import io.harness.EntityType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.cdng.provision.terraform.executions.TerraformPlanExectionDetailsService;
import io.harness.cdng.provision.terraform.functor.TerraformHumanReadablePlanFunctor;
import io.harness.cdng.provision.terraform.functor.TerraformPlanJsonFunctor;
import io.harness.cdng.provision.terraform.outcome.TerraformPlanOutcome;
import io.harness.cdng.provision.terraform.outcome.TerraformPlanOutcome.TerraformPlanOutcomeBuilder;
import io.harness.common.ParameterFieldHelper;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.task.terraform.TFTaskType;
import io.harness.delegate.task.terraform.TerraformCommand;
import io.harness.delegate.task.terraform.TerraformCommandUnit;
import io.harness.delegate.task.terraform.TerraformTaskNGParameters;
import io.harness.delegate.task.terraform.TerraformTaskNGParameters.TerraformTaskNGParametersBuilder;
import io.harness.delegate.task.terraform.TerraformTaskNGResponse;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.UnitProgress;
import io.harness.ng.core.EntityDetail;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.plancreator.steps.common.rollback.TaskExecutableWithRollbackAndRbac;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.rbac.PipelineRbacHelper;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepResponseBuilder;
import io.harness.pms.yaml.ParameterField;
import io.harness.provision.TerraformConstants;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.StepHelper;
import io.harness.steps.StepUtils;
import io.harness.supplier.ThrowingSupplier;
import io.harness.utils.IdentifierRefHelper;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class TerraformPlanStep extends TaskExecutableWithRollbackAndRbac<TerraformTaskNGResponse> {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.TERRAFORM_PLAN.getYamlType())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();

  @Inject private KryoSerializer kryoSerializer;
  @Inject private TerraformStepHelper helper;
  @Inject private PipelineRbacHelper pipelineRbacHelper;
  @Inject private StepHelper stepHelper;
  @Inject private CDFeatureFlagHelper featureFlagHelper;
  @Inject TerraformPlanExectionDetailsService terraformPlanExectionDetailsService;

  @Override
  public Class<StepElementParameters> getStepParametersClass() {
    return StepElementParameters.class;
  }

  @Override
  public void validateResources(Ambiance ambiance, StepElementParameters stepParameters) {
    List<EntityDetail> entityDetailList = new ArrayList<>();

    String accountId = AmbianceUtils.getAccountId(ambiance);
    String orgIdentifier = AmbianceUtils.getOrgIdentifier(ambiance);
    String projectIdentifier = AmbianceUtils.getProjectIdentifier(ambiance);

    TerraformPlanStepParameters stepParametersSpec = (TerraformPlanStepParameters) stepParameters.getSpec();

    // Config Files connector
    String connectorRef =
        stepParametersSpec.configuration.configFiles.store.getSpec().getConnectorReference().getValue();
    IdentifierRef identifierRef =
        IdentifierRefHelper.getIdentifierRef(connectorRef, accountId, orgIdentifier, projectIdentifier);
    EntityDetail entityDetail = EntityDetail.builder().type(EntityType.CONNECTORS).entityRef(identifierRef).build();
    entityDetailList.add(entityDetail);

    // Var Files connectors
    LinkedHashMap<String, TerraformVarFile> varFiles = stepParametersSpec.getConfiguration().getVarFiles();
    List<EntityDetail> varFilesEntityDetails =
        TerraformStepHelper.prepareEntityDetailsForVarFiles(accountId, orgIdentifier, projectIdentifier, varFiles);
    entityDetailList.addAll(varFilesEntityDetails);

    // Backend Config connector
    TerraformBackendConfig backendConfig = stepParametersSpec.getConfiguration().getBackendConfig();
    Optional<EntityDetail> bcFileEntityDetails = TerraformStepHelper.prepareEntityDetailForBackendConfigFiles(
        accountId, orgIdentifier, projectIdentifier, backendConfig);
    bcFileEntityDetails.ifPresent(entityDetailList::add);

    // Secret Manager Connector
    String secretManagerRef = stepParametersSpec.getConfiguration().getSecretManagerRef().getValue();
    identifierRef = IdentifierRefHelper.getIdentifierRef(secretManagerRef, accountId, orgIdentifier, projectIdentifier);
    entityDetail = EntityDetail.builder().type(EntityType.CONNECTORS).entityRef(identifierRef).build();
    entityDetailList.add(entityDetail);

    pipelineRbacHelper.checkRuntimePermissions(ambiance, entityDetailList, true);
  }

  @Override
  public TaskRequest obtainTaskAfterRbac(
      Ambiance ambiance, StepElementParameters stepElementParameters, StepInputPackage inputPackage) {
    log.info("Starting execution ObtainTask after Rbac for the Plan Step");
    TerraformPlanStepParameters planStepParameters = (TerraformPlanStepParameters) stepElementParameters.getSpec();
    helper.validatePlanStepConfigFiles(planStepParameters);
    TerraformPlanExecutionDataParameters configuration = planStepParameters.getConfiguration();
    TerraformTaskNGParametersBuilder builder = TerraformTaskNGParameters.builder();
    String accountId = AmbianceUtils.getAccountId(ambiance);
    builder.accountId(accountId);
    String entityId = helper.generateFullIdentifier(
        ParameterFieldHelper.getParameterFieldValue(planStepParameters.getProvisionerIdentifier()), ambiance);
    ParameterField<Boolean> exportTfPlanJsonField = planStepParameters.getConfiguration().getExportTerraformPlanJson();
    ParameterField<Boolean> exportTfHumanReadablePlanField =
        planStepParameters.getConfiguration().getExportTerraformHumanReadablePlan();
    TerraformTaskNGParameters terraformTaskNGParameters =
        builder.taskType(TFTaskType.PLAN)
            .terraformCommandUnit(TerraformCommandUnit.Plan)
            .entityId(entityId)
            .tfModuleSourceInheritSSH(helper.isExportCredentialForSourceModule(
                configuration.getConfigFiles(), stepElementParameters.getType()))
            .currentStateFileId(helper.getLatestFileId(entityId))
            .workspace(ParameterFieldHelper.getParameterFieldValue(configuration.getWorkspace()))
            .configFile(helper.getGitFetchFilesConfig(
                configuration.getConfigFiles().getStore().getSpec(), ambiance, TerraformStepHelper.TF_CONFIG_FILES))
            .fileStoreConfigFiles(helper.getFileStoreFetchFilesConfig(
                configuration.getConfigFiles().getStore().getSpec(), ambiance, TerraformStepHelper.TF_CONFIG_FILES))
            .varFileInfos(helper.toTerraformVarFileInfo(configuration.getVarFiles(), ambiance))
            .backendConfig(helper.getBackendConfig(configuration.getBackendConfig()))
            .backendConfigFileInfo(helper.toTerraformBackendFileInfo(configuration.getBackendConfig(), ambiance))
            .targets(ParameterFieldHelper.getParameterFieldValue(configuration.getTargets()))
            .saveTerraformStateJson(!ParameterField.isNull(exportTfPlanJsonField) && exportTfPlanJsonField.getValue())
            .saveTerraformHumanReadablePlan(
                !ParameterField.isNull(exportTfHumanReadablePlanField) && exportTfHumanReadablePlanField.getValue())
            .environmentVariables(helper.getEnvironmentVariablesMap(configuration.getEnvironmentVariables()) == null
                    ? new HashMap<>()
                    : helper.getEnvironmentVariablesMap(configuration.getEnvironmentVariables()))
            .encryptionConfig(helper.getEncryptionConfig(ambiance, planStepParameters))
            .terraformCommand(TerraformPlanCommand.APPLY == planStepParameters.getConfiguration().getCommand()
                    ? TerraformCommand.APPLY
                    : TerraformCommand.DESTROY)
            .planName(helper.getTerraformPlanName(planStepParameters.getConfiguration().getCommand(), ambiance,
                planStepParameters.getProvisionerIdentifier().getValue()))
            .timeoutInMillis(
                StepUtils.getTimeoutMillis(stepElementParameters.getTimeout(), TerraformConstants.DEFAULT_TIMEOUT))
            .useOptimizedTfPlan(
                featureFlagHelper.isEnabled(AmbianceUtils.getAccountId(ambiance), FeatureName.OPTIMIZED_TF_PLAN_NG))
            .build();

    TaskData taskData =
        TaskData.builder()
            .async(true)
            .taskType(terraformTaskNGParameters.getDelegateTaskType().name())
            .timeout(StepUtils.getTimeoutMillis(stepElementParameters.getTimeout(), TerraformConstants.DEFAULT_TIMEOUT))
            .parameters(new Object[] {builder.build()})
            .build();
    return StepUtils.prepareCDTaskRequest(ambiance, taskData, kryoSerializer,
        Collections.singletonList(TerraformCommandUnit.Plan.name()),
        terraformTaskNGParameters.getDelegateTaskType().getDisplayName(),
        TaskSelectorYaml.toTaskSelector(planStepParameters.getDelegateSelectors()),
        stepHelper.getEnvironmentType(ambiance));
  }

  @Override
  public StepResponse handleTaskResultWithSecurityContext(Ambiance ambiance,
      StepElementParameters stepElementParameters, ThrowingSupplier<TerraformTaskNGResponse> responseSupplier)
      throws Exception {
    log.info("Handling Task result with Security Context for the Plan Step");
    TerraformPlanStepParameters planStepParameters = (TerraformPlanStepParameters) stepElementParameters.getSpec();
    StepResponseBuilder stepResponseBuilder = StepResponse.builder();
    TerraformTaskNGResponse terraformTaskNGResponse = responseSupplier.get();
    List<UnitProgress> unitProgresses = terraformTaskNGResponse.getUnitProgressData() == null
        ? Collections.emptyList()
        : terraformTaskNGResponse.getUnitProgressData().getUnitProgresses();
    stepResponseBuilder.unitProgressList(unitProgresses);

    switch (terraformTaskNGResponse.getCommandExecutionStatus()) {
      case SUCCESS:
        stepResponseBuilder.status(Status.SUCCEEDED);
        break;
      case FAILURE:
        stepResponseBuilder.status(Status.FAILED);
        break;
      case RUNNING:
        stepResponseBuilder.status(Status.RUNNING);
        break;
      case QUEUED:
        stepResponseBuilder.status(Status.QUEUED);
        break;
      default:
        throw new InvalidRequestException(
            "Unhandled type CommandExecutionStatus: " + terraformTaskNGResponse.getCommandExecutionStatus().name(),
            WingsException.USER);
    }

    if (CommandExecutionStatus.SUCCESS == terraformTaskNGResponse.getCommandExecutionStatus()) {
      TerraformPlanOutcomeBuilder tfPlanOutcomeBuilder = TerraformPlanOutcome.builder();
      String provisionerIdentifier =
          ParameterFieldHelper.getParameterFieldValue(planStepParameters.getProvisionerIdentifier());
      helper.saveTerraformInheritOutput(planStepParameters, terraformTaskNGResponse, ambiance);
      helper.updateParentEntityIdAndVersion(
          helper.generateFullIdentifier(
              ParameterFieldHelper.getParameterFieldValue(planStepParameters.getProvisionerIdentifier()), ambiance),
          terraformTaskNGResponse.getStateFileId());
      tfPlanOutcomeBuilder.detailedExitCode(terraformTaskNGResponse.getDetailedExitCode());

      ParameterField<Boolean> exportTfPlanJsonField =
          planStepParameters.getConfiguration().getExportTerraformPlanJson();
      boolean exportTfPlanJson = !ParameterField.isNull(exportTfPlanJsonField)
          && ParameterFieldHelper.getBooleanParameterFieldValue(exportTfPlanJsonField);

      ParameterField<Boolean> exportTfHumanReadablePlanField =
          planStepParameters.getConfiguration().getExportTerraformHumanReadablePlan();
      boolean exportHumanReadablePlan = !ParameterField.isNull(exportTfHumanReadablePlanField)
          && ParameterFieldHelper.getBooleanParameterFieldValue(exportTfHumanReadablePlanField);

      if (exportHumanReadablePlan || exportTfPlanJson) {
        // First we save the terraform plan execution detail

        helper.saveTerraformPlanExecutionDetails(
            ambiance, terraformTaskNGResponse, provisionerIdentifier, planStepParameters);

        if (exportHumanReadablePlan) {
          String humanReadableOutputName =
              helper.saveTerraformPlanHumanReadableOutput(ambiance, terraformTaskNGResponse, provisionerIdentifier);

          if (humanReadableOutputName != null) {
            tfPlanOutcomeBuilder.humanReadableFilePath(TerraformHumanReadablePlanFunctor.getExpression(
                planStepParameters.getStepFqn(), humanReadableOutputName));
          }
        }

        if (exportTfPlanJson) {
          String planJsonOutputName =
              helper.saveTerraformPlanJsonOutput(ambiance, terraformTaskNGResponse, provisionerIdentifier);

          if (planJsonOutputName != null) {
            tfPlanOutcomeBuilder.jsonFilePath(
                TerraformPlanJsonFunctor.getExpression(planStepParameters.getStepFqn(), planJsonOutputName));
          }
        }
      }

      stepResponseBuilder.stepOutcome(StepResponse.StepOutcome.builder()
                                          .name(TerraformPlanOutcome.OUTCOME_NAME)
                                          .outcome(tfPlanOutcomeBuilder.build())
                                          .build());
    }
    return stepResponseBuilder.build();
  }
}
