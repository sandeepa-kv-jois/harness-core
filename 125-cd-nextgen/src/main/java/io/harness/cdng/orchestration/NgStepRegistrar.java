/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.orchestration;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.artifact.steps.ArtifactStep;
import io.harness.cdng.artifact.steps.ArtifactSyncStep;
import io.harness.cdng.artifact.steps.ArtifactsStep;
import io.harness.cdng.artifact.steps.ArtifactsStepV2;
import io.harness.cdng.artifact.steps.SidecarsStep;
import io.harness.cdng.azure.webapp.ApplicationSettingsStep;
import io.harness.cdng.azure.webapp.AzureServiceSettingsStep;
import io.harness.cdng.azure.webapp.AzureWebAppRollbackStep;
import io.harness.cdng.azure.webapp.AzureWebAppSlotDeploymentStep;
import io.harness.cdng.azure.webapp.AzureWebAppSwapSlotStep;
import io.harness.cdng.azure.webapp.AzureWebAppTrafficShiftStep;
import io.harness.cdng.azure.webapp.ConnectionStringsStep;
import io.harness.cdng.azure.webapp.StartupCommandStep;
import io.harness.cdng.chaos.ChaosStep;
import io.harness.cdng.configfile.steps.ConfigFilesStep;
import io.harness.cdng.configfile.steps.ConfigFilesStepV2;
import io.harness.cdng.configfile.steps.IndividualConfigFileStep;
import io.harness.cdng.customDeployment.FetchInstanceScriptStep;
import io.harness.cdng.ecs.EcsBlueGreenCreateServiceStep;
import io.harness.cdng.ecs.EcsBlueGreenRollbackStep;
import io.harness.cdng.ecs.EcsBlueGreenSwapTargetGroupsStep;
import io.harness.cdng.ecs.EcsCanaryDeleteStep;
import io.harness.cdng.ecs.EcsCanaryDeployStep;
import io.harness.cdng.ecs.EcsRollingDeployStep;
import io.harness.cdng.ecs.EcsRollingRollbackStep;
import io.harness.cdng.ecs.EcsRunTaskStep;
import io.harness.cdng.elastigroup.ElastigroupServiceSettingsStep;
import io.harness.cdng.elastigroup.ElastigroupSetupStep;
import io.harness.cdng.gitops.CreatePRStep;
import io.harness.cdng.gitops.MergePRStep;
import io.harness.cdng.gitops.UpdateReleaseRepoStep;
import io.harness.cdng.gitops.steps.FetchLinkedAppsStep;
import io.harness.cdng.gitops.steps.GitopsClustersStep;
import io.harness.cdng.helm.HelmDeployStep;
import io.harness.cdng.helm.HelmRollbackStep;
import io.harness.cdng.infra.steps.EnvironmentStep;
import io.harness.cdng.infra.steps.InfrastructureSectionStep;
import io.harness.cdng.infra.steps.InfrastructureStep;
import io.harness.cdng.infra.steps.InfrastructureTaskExecutableStep;
import io.harness.cdng.infra.steps.InfrastructureTaskExecutableStepV2;
import io.harness.cdng.jenkins.jenkinsstep.JenkinsBuildStep;
import io.harness.cdng.k8s.K8sApplyStep;
import io.harness.cdng.k8s.K8sBGSwapServicesStep;
import io.harness.cdng.k8s.K8sBlueGreenStep;
import io.harness.cdng.k8s.K8sCanaryDeleteStep;
import io.harness.cdng.k8s.K8sCanaryStep;
import io.harness.cdng.k8s.K8sDeleteStep;
import io.harness.cdng.k8s.K8sRollingRollbackStep;
import io.harness.cdng.k8s.K8sRollingStep;
import io.harness.cdng.k8s.K8sScaleStep;
import io.harness.cdng.manifest.steps.ManifestStep;
import io.harness.cdng.manifest.steps.ManifestsStep;
import io.harness.cdng.manifest.steps.ManifestsStepV2;
import io.harness.cdng.pipeline.steps.DeploymentStageStep;
import io.harness.cdng.pipeline.steps.MultiDeploymentSpawnerStep;
import io.harness.cdng.pipeline.steps.NGSectionStep;
import io.harness.cdng.pipeline.steps.RollbackOptionalChildChainStep;
import io.harness.cdng.pipeline.steps.RollbackOptionalChildrenStep;
import io.harness.cdng.provision.azure.AzureARMRollbackStep;
import io.harness.cdng.provision.azure.AzureCreateARMResourceStep;
import io.harness.cdng.provision.azure.AzureCreateBPStep;
import io.harness.cdng.provision.cloudformation.CloudformationCreateStackStep;
import io.harness.cdng.provision.cloudformation.CloudformationDeleteStackStep;
import io.harness.cdng.provision.cloudformation.CloudformationRollbackStep;
import io.harness.cdng.provision.shellscript.ShellScriptProvisionStep;
import io.harness.cdng.provision.terraform.TerraformApplyStep;
import io.harness.cdng.provision.terraform.TerraformDestroyStep;
import io.harness.cdng.provision.terraform.TerraformPlanStep;
import io.harness.cdng.provision.terraform.steps.rolllback.TerraformRollbackStep;
import io.harness.cdng.provision.terragrunt.TerragruntApplyStep;
import io.harness.cdng.provision.terragrunt.TerragruntDestroyStep;
import io.harness.cdng.provision.terragrunt.TerragruntPlanStep;
import io.harness.cdng.provision.terragrunt.TerragruntRollbackStep;
import io.harness.cdng.rollback.steps.InfrastructureDefinitionStep;
import io.harness.cdng.rollback.steps.InfrastructureProvisionerStep;
import io.harness.cdng.rollback.steps.RollbackStepsStep;
import io.harness.cdng.serverless.ServerlessAwsLambdaDeployStep;
import io.harness.cdng.serverless.ServerlessAwsLambdaRollbackStep;
import io.harness.cdng.service.steps.ServiceConfigStep;
import io.harness.cdng.service.steps.ServiceDefinitionStep;
import io.harness.cdng.service.steps.ServiceSectionStep;
import io.harness.cdng.service.steps.ServiceSpecStep;
import io.harness.cdng.service.steps.ServiceStep;
import io.harness.cdng.service.steps.ServiceStepV3;
import io.harness.cdng.spot.elastigroup.deploy.ElastigroupDeployStep;
import io.harness.cdng.spot.elastigroup.rollback.ElastigroupRollbackStep;
import io.harness.cdng.ssh.CommandStep;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.steps.Step;
import io.harness.registrar.NGCommonUtilStepsRegistrar;

import java.util.HashMap;
import java.util.Map;
import lombok.experimental.UtilityClass;

@OwnedBy(CDC)
@UtilityClass
public class NgStepRegistrar {
  public Map<StepType, Class<? extends Step>> getEngineSteps() {
    Map<StepType, Class<? extends Step>> engineSteps = new HashMap<>();

    // Add CDNG steps here
    engineSteps.put(CreatePRStep.STEP_TYPE, CreatePRStep.class);
    engineSteps.put(MergePRStep.STEP_TYPE, MergePRStep.class);
    engineSteps.put(UpdateReleaseRepoStep.STEP_TYPE, UpdateReleaseRepoStep.class);
    engineSteps.put(FetchLinkedAppsStep.STEP_TYPE, FetchLinkedAppsStep.class);
    engineSteps.put(RollbackOptionalChildChainStep.STEP_TYPE, RollbackOptionalChildChainStep.class);
    engineSteps.put(RollbackOptionalChildrenStep.STEP_TYPE, RollbackOptionalChildrenStep.class);
    engineSteps.put(NGSectionStep.STEP_TYPE, NGSectionStep.class);
    engineSteps.put(InfrastructureSectionStep.STEP_TYPE, InfrastructureSectionStep.class);
    engineSteps.put(InfrastructureStep.STEP_TYPE, InfrastructureStep.class);
    engineSteps.put(InfrastructureTaskExecutableStep.STEP_TYPE, InfrastructureTaskExecutableStep.class);
    engineSteps.put(InfrastructureTaskExecutableStepV2.STEP_TYPE, InfrastructureTaskExecutableStepV2.class);
    engineSteps.put(DeploymentStageStep.STEP_TYPE, DeploymentStageStep.class);
    engineSteps.put(ServiceConfigStep.STEP_TYPE, ServiceConfigStep.class);
    engineSteps.put(ServiceSectionStep.STEP_TYPE, ServiceSectionStep.class);
    engineSteps.put(ServiceStep.STEP_TYPE, ServiceStep.class);
    engineSteps.put(ServiceStepV3.STEP_TYPE, ServiceStepV3.class);
    engineSteps.put(ServiceDefinitionStep.STEP_TYPE, ServiceDefinitionStep.class);
    engineSteps.put(ServiceSpecStep.STEP_TYPE, ServiceSpecStep.class);
    engineSteps.put(ArtifactsStep.STEP_TYPE, ArtifactsStep.class);
    engineSteps.put(ArtifactsStepV2.STEP_TYPE, ArtifactsStepV2.class);
    engineSteps.put(SidecarsStep.STEP_TYPE, SidecarsStep.class);
    engineSteps.put(ArtifactStep.STEP_TYPE, ArtifactStep.class);
    engineSteps.put(ArtifactSyncStep.STEP_TYPE, ArtifactSyncStep.class);
    engineSteps.put(ManifestsStep.STEP_TYPE, ManifestsStep.class);
    engineSteps.put(ManifestStep.STEP_TYPE, ManifestStep.class);
    engineSteps.put(ManifestsStepV2.STEP_TYPE, ManifestsStepV2.class);
    engineSteps.put(K8sDeleteStep.STEP_TYPE, K8sDeleteStep.class);
    engineSteps.put(K8sRollingStep.STEP_TYPE, K8sRollingStep.class);
    engineSteps.put(K8sRollingRollbackStep.STEP_TYPE, K8sRollingRollbackStep.class);
    engineSteps.put(K8sScaleStep.STEP_TYPE, K8sScaleStep.class);
    engineSteps.put(K8sCanaryStep.STEP_TYPE, K8sCanaryStep.class);
    engineSteps.put(K8sCanaryDeleteStep.STEP_TYPE, K8sCanaryDeleteStep.class);
    engineSteps.put(K8sBlueGreenStep.STEP_TYPE, K8sBlueGreenStep.class);
    engineSteps.put(K8sBGSwapServicesStep.STEP_TYPE, K8sBGSwapServicesStep.class);
    engineSteps.put(K8sApplyStep.STEP_TYPE, K8sApplyStep.class);
    engineSteps.put(TerraformApplyStep.STEP_TYPE, TerraformApplyStep.class);
    engineSteps.put(TerraformPlanStep.STEP_TYPE, TerraformPlanStep.class);
    engineSteps.put(TerraformDestroyStep.STEP_TYPE, TerraformDestroyStep.class);
    engineSteps.put(TerraformRollbackStep.STEP_TYPE, TerraformRollbackStep.class);
    engineSteps.put(InfrastructureDefinitionStep.STEP_TYPE, InfrastructureDefinitionStep.class);
    engineSteps.put(InfrastructureProvisionerStep.STEP_TYPE, InfrastructureProvisionerStep.class);
    engineSteps.put(RollbackStepsStep.STEP_TYPE, RollbackStepsStep.class);
    engineSteps.put(EnvironmentStep.STEP_TYPE, EnvironmentStep.class);
    engineSteps.put(HelmDeployStep.STEP_TYPE, HelmDeployStep.class);
    engineSteps.put(HelmRollbackStep.STEP_TYPE, HelmRollbackStep.class);
    engineSteps.put(CloudformationDeleteStackStep.STEP_TYPE, CloudformationDeleteStackStep.class);
    engineSteps.put(CloudformationCreateStackStep.STEP_TYPE, CloudformationCreateStackStep.class);
    engineSteps.put(CloudformationRollbackStep.STEP_TYPE, CloudformationRollbackStep.class);
    engineSteps.put(ServerlessAwsLambdaDeployStep.STEP_TYPE, ServerlessAwsLambdaDeployStep.class);
    engineSteps.put(ServerlessAwsLambdaRollbackStep.STEP_TYPE, ServerlessAwsLambdaRollbackStep.class);
    engineSteps.put(IndividualConfigFileStep.STEP_TYPE, IndividualConfigFileStep.class);
    engineSteps.put(ConfigFilesStep.STEP_TYPE, ConfigFilesStep.class);
    engineSteps.put(ConfigFilesStepV2.STEP_TYPE, ConfigFilesStepV2.class);
    engineSteps.put(CommandStep.STEP_TYPE, CommandStep.class);
    engineSteps.put(AzureWebAppSlotDeploymentStep.STEP_TYPE, AzureWebAppSlotDeploymentStep.class);
    engineSteps.put(AzureWebAppTrafficShiftStep.STEP_TYPE, AzureWebAppTrafficShiftStep.class);
    engineSteps.put(AzureWebAppSwapSlotStep.STEP_TYPE, AzureWebAppSwapSlotStep.class);
    engineSteps.put(AzureWebAppRollbackStep.STEP_TYPE, AzureWebAppRollbackStep.class);
    engineSteps.put(StartupCommandStep.STEP_TYPE, StartupCommandStep.class);
    engineSteps.put(AzureServiceSettingsStep.STEP_TYPE, AzureServiceSettingsStep.class);
    engineSteps.put(ElastigroupServiceSettingsStep.STEP_TYPE, ElastigroupServiceSettingsStep.class);
    engineSteps.put(ApplicationSettingsStep.STEP_TYPE, ApplicationSettingsStep.class);
    engineSteps.put(ConnectionStringsStep.STEP_TYPE, ConnectionStringsStep.class);
    engineSteps.putAll(NGCommonUtilStepsRegistrar.getEngineSteps());
    engineSteps.put(GitopsClustersStep.STEP_TYPE, GitopsClustersStep.class);
    engineSteps.put(JenkinsBuildStep.STEP_TYPE, JenkinsBuildStep.class);
    // ECS
    engineSteps.put(EcsRollingDeployStep.STEP_TYPE, EcsRollingDeployStep.class);
    engineSteps.put(EcsRollingRollbackStep.STEP_TYPE, EcsRollingRollbackStep.class);
    engineSteps.put(EcsCanaryDeployStep.STEP_TYPE, EcsCanaryDeployStep.class);
    engineSteps.put(EcsCanaryDeleteStep.STEP_TYPE, EcsCanaryDeleteStep.class);
    engineSteps.put(EcsBlueGreenCreateServiceStep.STEP_TYPE, EcsBlueGreenCreateServiceStep.class);
    engineSteps.put(EcsBlueGreenSwapTargetGroupsStep.STEP_TYPE, EcsBlueGreenSwapTargetGroupsStep.class);
    engineSteps.put(EcsBlueGreenRollbackStep.STEP_TYPE, EcsBlueGreenRollbackStep.class);
    engineSteps.put(EcsRunTaskStep.STEP_TYPE, EcsRunTaskStep.class);

    engineSteps.put(AzureCreateARMResourceStep.STEP_TYPE, AzureCreateARMResourceStep.class);
    engineSteps.put(MultiDeploymentSpawnerStep.STEP_TYPE, MultiDeploymentSpawnerStep.class);
    engineSteps.put(AzureCreateBPStep.STEP_TYPE, AzureCreateBPStep.class);
    engineSteps.put(AzureARMRollbackStep.STEP_TYPE, AzureARMRollbackStep.class);
    engineSteps.put(FetchInstanceScriptStep.STEP_TYPE, FetchInstanceScriptStep.class);
    engineSteps.put(ShellScriptProvisionStep.STEP_TYPE, ShellScriptProvisionStep.class);

    // Chaos
    // TODO : Enable this for UI
    engineSteps.put(ChaosStep.STEP_TYPE, ChaosStep.class);

    engineSteps.put(ElastigroupDeployStep.STEP_TYPE, ElastigroupDeployStep.class);
    engineSteps.put(ElastigroupRollbackStep.STEP_TYPE, ElastigroupRollbackStep.class);
    engineSteps.put(ElastigroupSetupStep.STEP_TYPE, ElastigroupSetupStep.class);
    engineSteps.put(TerragruntPlanStep.STEP_TYPE, TerragruntPlanStep.class);
    engineSteps.put(TerragruntApplyStep.STEP_TYPE, TerragruntApplyStep.class);
    engineSteps.put(TerragruntDestroyStep.STEP_TYPE, TerragruntDestroyStep.class);
    engineSteps.put(TerragruntRollbackStep.STEP_TYPE, TerragruntRollbackStep.class);

    return engineSteps;
  }
}
