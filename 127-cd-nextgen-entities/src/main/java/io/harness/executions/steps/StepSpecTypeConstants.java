/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.executions.steps;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;

@OwnedBy(CDP)
public interface StepSpecTypeConstants {
  String GITOPS_CREATE_PR = "CreatePR";
  String GITOPS_MERGE_PR = "MergePR";
  String GITOPS_UPDATE_RELEASE_REPO = "GitOpsUpdateReleaseRepo";
  String CLOUDFORMATION_CREATE_STACK = "CreateStack";
  String CLOUDFORMATION_DELETE_STACK = "DeleteStack";
  String CLOUDFORMATION_ROLLBACK_STACK = "RollbackStack";

  String K8S_ROLLING_DEPLOY = "K8sRollingDeploy";
  String K8S_ROLLING_ROLLBACK = "K8sRollingRollback";
  String K8S_BLUE_GREEN_DEPLOY = "K8sBlueGreenDeploy";
  String K8S_APPLY = "K8sApply";
  String K8S_SCALE = "K8sScale";
  String K8S_BG_SWAP_SERVICES = "K8sBGSwapServices";
  String K8S_CANARY_DELETE = "K8sCanaryDelete";
  String K8S_CANARY_DEPLOY = "K8sCanaryDeploy";
  String K8S_DELETE = "K8sDelete";

  String TERRAFORM_APPLY = "TerraformApply";
  String TERRAFORM_PLAN = "TerraformPlan";
  String TERRAFORM_DESTROY = "TerraformDestroy";
  String TERRAFORM_ROLLBACK = "TerraformRollback";

  String TERRAGRUNT_PLAN = "TerragruntPlan";
  String TERRAGRUNT_APPLY = "TerragruntApply";
  String TERRAGRUNT_DESTROY = "TerragruntDestroy";
  String TERRAGRUNT_ROLLBACK = "TerragruntRollback";

  String AZURE_CREATE_ARM_RESOURCE = "AzureCreateARMResource";
  String AZURE_CREATE_BP_RESOURCE = "AzureCreateBPResource";
  String AZURE_ROLLBACK_ARM_RESOURCE = "AzureARMRollback";

  String PLACEHOLDER = "Placeholder";

  String HELM_DEPLOY = "HelmDeploy";
  String HELM_ROLLBACK = "HelmRollback";

  String SERVERLESS_AWS_LAMBDA_DEPLOY = "ServerlessAwsLambdaDeploy";
  String SERVERLESS_AWS_LAMBDA_ROLLBACK = "ServerlessAwsLambdaRollback";
  String COMMAND = "Command";
  String ELASTIGROUP_DEPLOY = "ElastigroupDeploy";
  String ELASTIGROUP_ROLLBACK = "ElastigroupRollback";

  String AZURE_SLOT_DEPLOYMENT = "AzureSlotDeployment";
  String AZURE_TRAFFIC_SHIFT = "AzureTrafficShift";
  String AZURE_SWAP_SLOT = "AzureSwapSlot";
  String AZURE_WEBAPP_ROLLBACK = "AzureWebAppRollback";
  String DEPLOYMENT_STAGE = "Deployment";
  String JENKINS_BUILD = "JenkinsBuild";

  String DEPLOYMENT_TYPE_CUSTOM_DEPLOYMENT = "CustomDeployment";
  String CUSTOM_DEPLOYMENT_FETCH_INSTANCE_SCRIPT = "FetchInstanceScript";

  String ECS_ROLLING_DEPLOY = "EcsRollingDeploy";
  String ECS_ROLLING_ROLLBACK = "EcsRollingRollback";
  String ECS_CANARY_DEPLOY = "EcsCanaryDeploy";
  String ECS_CANARY_DELETE = "EcsCanaryDelete";
  String ECS_RUN_TASK = "EcsRunTask";
  String ECS_BLUE_GREEN_CREATE_SERVICE = "EcsBlueGreenCreateService";
  String ECS_BLUE_GREEN_SWAP_TARGET_GROUPS = "EcsBlueGreenSwapTargetGroups";
  String ECS_BLUE_GREEN_ROLLBACK = "EcsBlueGreenRollback";

  String SHELL_SCRIPT_PROVISION = "ShellScriptProvision";

  String CHAOS_STEP = "Chaos";

  String ELASTIGROUP_SETUP = "ElastigroupSetup";
  String GITOPS_FETCH_LINKED_APPS = "GitOpsFetchLinkedApps";
}
