/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.pipeline;

import io.harness.advisers.rollback.OnFailRollbackParameters;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.azure.webapp.AzureWebAppRollbackStepInfo;
import io.harness.cdng.azure.webapp.AzureWebAppSlotDeploymentStepInfo;
import io.harness.cdng.azure.webapp.AzureWebAppSwapSlotStepInfo;
import io.harness.cdng.azure.webapp.AzureWebAppTrafficShiftStepInfo;
import io.harness.cdng.customDeployment.FetchInstanceScriptStepInfo;
import io.harness.cdng.ecs.EcsBlueGreenCreateServiceStepInfo;
import io.harness.cdng.ecs.EcsBlueGreenRollbackStepInfo;
import io.harness.cdng.ecs.EcsBlueGreenSwapTargetGroupsStepInfo;
import io.harness.cdng.ecs.EcsCanaryDeleteStepInfo;
import io.harness.cdng.ecs.EcsCanaryDeployStepInfo;
import io.harness.cdng.ecs.EcsRollingDeployStepInfo;
import io.harness.cdng.ecs.EcsRollingRollbackStepInfo;
import io.harness.cdng.ecs.EcsRunTaskStepInfo;
import io.harness.cdng.elastigroup.ElastigroupSetupStepInfo;
import io.harness.cdng.gitops.CreatePRStepInfo;
import io.harness.cdng.gitops.MergePRStepInfo;
import io.harness.cdng.gitops.UpdateReleaseRepoStepInfo;
import io.harness.cdng.helm.HelmDeployStepInfo;
import io.harness.cdng.helm.rollback.HelmRollbackStepInfo;
import io.harness.cdng.jenkins.jenkinsstep.JenkinsBuildStepInfo;
import io.harness.cdng.k8s.K8sApplyStepInfo;
import io.harness.cdng.k8s.K8sBGSwapServicesStepInfo;
import io.harness.cdng.k8s.K8sBlueGreenStepInfo;
import io.harness.cdng.k8s.K8sCanaryDeleteStepInfo;
import io.harness.cdng.k8s.K8sCanaryStepInfo;
import io.harness.cdng.k8s.K8sDeleteStepInfo;
import io.harness.cdng.k8s.K8sRollingRollbackStepInfo;
import io.harness.cdng.k8s.K8sRollingStepInfo;
import io.harness.cdng.k8s.K8sScaleStepInfo;
import io.harness.cdng.pipeline.steps.CdStepParametersUtils;
import io.harness.cdng.provision.azure.AzureARMRollbackStepInfo;
import io.harness.cdng.provision.azure.AzureCreateARMResourceStepInfo;
import io.harness.cdng.provision.azure.AzureCreateBPStepInfo;
import io.harness.cdng.provision.cloudformation.CloudformationCreateStackStepInfo;
import io.harness.cdng.provision.cloudformation.CloudformationDeleteStackStepInfo;
import io.harness.cdng.provision.cloudformation.CloudformationRollbackStepInfo;
import io.harness.cdng.provision.shellscript.ShellScriptProvisionStepInfo;
import io.harness.cdng.provision.terraform.TerraformApplyStepInfo;
import io.harness.cdng.provision.terraform.TerraformDestroyStepInfo;
import io.harness.cdng.provision.terraform.TerraformPlanStepInfo;
import io.harness.cdng.provision.terraform.steps.rolllback.TerraformRollbackStepInfo;
import io.harness.cdng.provision.terragrunt.TerragruntApplyStepInfo;
import io.harness.cdng.provision.terragrunt.TerragruntDestroyStepInfo;
import io.harness.cdng.provision.terragrunt.TerragruntPlanStepInfo;
import io.harness.cdng.provision.terragrunt.TerragruntRollbackStepInfo;
import io.harness.cdng.serverless.ServerlessAwsLambdaDeployStepInfo;
import io.harness.cdng.serverless.ServerlessAwsLambdaRollbackStepInfo;
import io.harness.cdng.spot.elastigroup.deploy.ElastigroupDeployStepInfo;
import io.harness.cdng.spot.elastigroup.rollback.ElastigroupRollbackStepInfo;
import io.harness.cdng.ssh.CommandStepInfo;
import io.harness.plancreator.steps.common.StepElementParameters.StepElementParametersBuilder;
import io.harness.plancreator.steps.common.WithDelegateSelector;
import io.harness.plancreator.steps.common.WithStepElementParameters;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.steps.StepUtils;
import io.harness.yaml.core.StepSpecType;

import io.swagger.annotations.ApiModel;

@ApiModel(
    subTypes = {CreatePRStepInfo.class, MergePRStepInfo.class, K8sApplyStepInfo.class, K8sBlueGreenStepInfo.class,
        K8sCanaryStepInfo.class, K8sRollingStepInfo.class, K8sRollingRollbackStepInfo.class, K8sScaleStepInfo.class,
        K8sDeleteStepInfo.class, K8sBGSwapServicesStepInfo.class, K8sCanaryDeleteStepInfo.class,
        TerraformApplyStepInfo.class, TerraformPlanStepInfo.class, TerraformDestroyStepInfo.class,
        TerraformRollbackStepInfo.class, HelmDeployStepInfo.class, HelmRollbackStepInfo.class,
        CloudformationCreateStackStepInfo.class, CloudformationDeleteStackStepInfo.class,
        ServerlessAwsLambdaDeployStepInfo.class, ServerlessAwsLambdaRollbackStepInfo.class,
        CloudformationRollbackStepInfo.class, CommandStepInfo.class, AzureWebAppSlotDeploymentStepInfo.class,
        AzureWebAppTrafficShiftStepInfo.class, AzureWebAppSwapSlotStepInfo.class, AzureWebAppRollbackStepInfo.class,
        JenkinsBuildStepInfo.class, AzureCreateARMResourceStepInfo.class, AzureCreateBPStepInfo.class,
        AzureARMRollbackStepInfo.class, EcsRollingDeployStepInfo.class, EcsRollingRollbackStepInfo.class,
        EcsCanaryDeployStepInfo.class, EcsCanaryDeleteStepInfo.class, EcsBlueGreenCreateServiceStepInfo.class,
        EcsBlueGreenSwapTargetGroupsStepInfo.class, EcsBlueGreenRollbackStepInfo.class,
        FetchInstanceScriptStepInfo.class, ShellScriptProvisionStepInfo.class, UpdateReleaseRepoStepInfo.class,
        EcsRunTaskStepInfo.class, ElastigroupDeployStepInfo.class, ElastigroupRollbackStepInfo.class,
        ElastigroupSetupStepInfo.class, TerragruntPlanStepInfo.class, TerragruntApplyStepInfo.class,
        TerragruntDestroyStepInfo.class, TerragruntRollbackStepInfo.class})

@OwnedBy(HarnessTeam.CDC)
public interface CDStepInfo extends StepSpecType, WithStepElementParameters, WithDelegateSelector {
  default StepParameters getStepParameters(
      CdAbstractStepNode stepElementConfig, OnFailRollbackParameters failRollbackParameters, PlanCreationContext ctx) {
    StepElementParametersBuilder stepParametersBuilder =
        CdStepParametersUtils.getStepParameters(stepElementConfig, failRollbackParameters);
    StepUtils.appendDelegateSelectorsToSpecParameters(stepElementConfig.getStepSpecType(), ctx);
    stepParametersBuilder.spec(getSpecParameters());
    return stepParametersBuilder.build();
  }
}
