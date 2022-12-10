/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.stepsdependency.constants;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;

import lombok.experimental.UtilityClass;

@OwnedBy(CDP)
@UtilityClass
public class OutcomeExpressionConstants {
  public final String CREATE_PR_OUTCOME = "createPROutcome";
  public final String MERGE_PR_OUTCOME = "mergePROutcome";
  public final String SERVICE = "service";
  public final String ARTIFACTS = "artifacts";
  public final String MANIFESTS = "manifests";
  public final String INFRASTRUCTURE_OUTCOME = "stage.spec.infrastructure.output";
  public final String INFRASTRUCTURE_GROUP = "infrastructureGroup";
  public final String K8S_ROLL_OUT = "rollingOutcome";
  public final String K8S_BLUE_GREEN_OUTCOME = "k8sBlueGreenOutcome";
  public final String K8S_APPLY_OUTCOME = "k8sApplyOutcome";
  public final String K8S_CANARY_OUTCOME = "k8sCanaryOutcome";
  public final String K8S_CANARY_DELETE_OUTCOME = "k8sCanaryDeleteOutcome";
  public final String K8S_BG_SWAP_SERVICES_OUTCOME = "k8sBGSwapServicesOutcome";
  public final String OUTPUT = "output";
  public final String INFRA_TASK_EXECUTABLE_STEP_OUTPUT = "InfrastructureStepOutput";
  public final String TERRAFORM_CONFIG = "terraformConfig";
  public final String DEPLOYMENT_INFO_OUTCOME = "deploymentInfoOutcome";
  public final String HELM_DEPLOY_OUTCOME = "helmDeployOutcome";
  public final String HELM_ROLLBACK_OUTCOME = "helmRollbackOutcome";
  public final String SERVERLESS_AWS_LAMBDA_ROLLBACK_DATA_OUTCOME = "serverlessAwsLambdaRollbackDataOutcome";
  public final String SERVERLESS_FETCH_FILE_OUTCOME = "serverlessFetchFileOutcome";
  public final String SERVERLESS_AWS_LAMBDA_ROLLBACK_OUTCOME = "serverlessAwsLambdaRollbackOutcome";
  public final String CONFIG_FILES = "configFiles";
  public final String STARTUP_COMMAND = "startupCommand";
  public final String STARTUP_SCRIPT = "startupScript";
  public final String APPLICATION_SETTINGS = "applicationSettings";
  public final String CONNECTION_STRINGS = "connectionStrings";
  public final String ECS_ROLLING_ROLLBACK_OUTCOME = "ecsRollingRollbackOutcome";
  public final String ECS_CANARY_DELETE_DATA_OUTCOME = "ecsCanaryDeleteDataOutcome";
  public final String ECS_CANARY_DELETE_OUTCOME = "ecsCanaryDeleteOutcome";
  public final String ECS_CANARY_DEPLOY_OUTCOME = "ecsCanaryDeployOutcome";
  public final String ROLLBACK_ARTIFACT = "rollbackArtifact";
  public final String ECS_BLUE_GREEN_PREPARE_ROLLBACK_DATA_OUTCOME = "ecsBlueGreenPrepareRollbackOutcome";
  public final String ECS_BLUE_GREEN_CREATE_SERVICE_OUTCOME = "ecsBlueGreenCreateServiceOutcome";
  public final String ECS_BLUE_GREEN_SWAP_TARGET_GROUPS_OUTCOME = "ecsBlueGreenSwapTargetGroupsOutcome";
  public final String ECS_BLUE_GREEN_SWAP_TARGET_GROUPS_START_OUTCOME = "ecsBlueGreenSwapTargetGroupsStartOutcome";
  public final String ECS_BLUE_GREEN_ROLLBACK_OUTCOME = "ecsBlueGreenRollbackOutcome";
  public final String UPDATE_RELEASE_REPO_OUTCOME = "updateReleaseRepoOutcome";
  public final String INSTANCES = "instances";
  public final String ELASTIGROUP_CONFIGURATION_OUTPUT = "elastigroupConfigurationOutput";
  public final String FREEZE_OUTCOME = "freezeOutcome";
  public final String ELASTIGROUP_SETUP_OUTCOME = "elastgroupSetupOutcome";
}
