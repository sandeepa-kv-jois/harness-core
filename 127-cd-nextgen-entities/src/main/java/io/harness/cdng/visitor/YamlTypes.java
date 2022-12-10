/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.visitor;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.manifest.ManifestType;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.pms.yaml.YamlNode;
import io.harness.walktree.visitor.utilities.VisitorParentPathUtils;

@OwnedBy(CDC)
public interface YamlTypes {
  String GITOPS_CREATE_PR = StepSpecTypeConstants.GITOPS_CREATE_PR;
  String GITOPS_MERGE_PR = StepSpecTypeConstants.GITOPS_MERGE_PR;
  String UPDATE_RELEASE_REPO = StepSpecTypeConstants.GITOPS_UPDATE_RELEASE_REPO;

  String PRIMARY_ARTIFACT = "primary";
  String PRIMARY_ARTIFACT_REF = "primaryArtifactRef";
  String ARTIFACT_SOURCES = "sources";
  String IDENTIFIER = "identifier";
  String ARTIFACT_LIST_CONFIG = "artifacts";
  String SIDECAR_ARTIFACT_CONFIG = "sidecar";
  String SIDECARS_ARTIFACT_CONFIG = "sidecars";
  String ENVIRONMENT_YAML = "environment";
  String ENVIRONMENT_GROUP_YAML = "environmentGroup";
  String GITOPS_CLUSTERS = "gitopsClusters";
  String ENVIRONMENT_REF = "environmentRef";
  String ENVIRONMENT_GROUP_REF = "envGroupRef";
  String INFRASTRUCTURE_DEF = "infrastructureDefinition";
  String INFRASTRUCTURE_DEFS = "infrastructureDefinitions";
  String INFRASTRUCTURE_STEP_PARAMETERS = "infrastructureStepParameters";
  String ENVIRONMENT_NODE_ID = "environmentNodeId";

  String K8S_ROLLING_ROLLBACK = StepSpecTypeConstants.K8S_ROLLING_ROLLBACK;
  String K8S_ROLLING_DEPLOY = StepSpecTypeConstants.K8S_ROLLING_DEPLOY;
  String K8S_BLUE_GREEN_DEPLOY = StepSpecTypeConstants.K8S_BLUE_GREEN_DEPLOY;
  String K8S_APPLY = StepSpecTypeConstants.K8S_APPLY;
  String K8S_SCALE = StepSpecTypeConstants.K8S_SCALE;
  String K8S_CANARY_DEPLOY = StepSpecTypeConstants.K8S_CANARY_DEPLOY;
  String K8S_BG_SWAP_SERVICES = StepSpecTypeConstants.K8S_BG_SWAP_SERVICES;
  String K8S_DELETE = StepSpecTypeConstants.K8S_DELETE;
  String K8S_CANARY_DELETE = StepSpecTypeConstants.K8S_CANARY_DELETE;

  String HELM_DEPLOY = StepSpecTypeConstants.HELM_DEPLOY;
  String HELM_ROLLBACK = StepSpecTypeConstants.HELM_ROLLBACK;

  String SERVERLESS_AWS_LAMBDA_DEPLOY = StepSpecTypeConstants.SERVERLESS_AWS_LAMBDA_DEPLOY;
  String SERVERLESS_AWS_LAMBDA_ROLLBACK = StepSpecTypeConstants.SERVERLESS_AWS_LAMBDA_ROLLBACK;
  String COMMAND = StepSpecTypeConstants.COMMAND;

  String ELASTIGROUP_DEPLOY = StepSpecTypeConstants.ELASTIGROUP_DEPLOY;
  String ELASTIGROUP_ROLLBACK = StepSpecTypeConstants.ELASTIGROUP_ROLLBACK;

  String ECS_ROLLING_DEPLOY = StepSpecTypeConstants.ECS_ROLLING_DEPLOY;
  String ECS_ROLLING_ROLLBACK = StepSpecTypeConstants.ECS_ROLLING_ROLLBACK;
  String ECS_CANARY_DEPLOY = StepSpecTypeConstants.ECS_CANARY_DEPLOY;
  String ECS_CANARY_DELETE = StepSpecTypeConstants.ECS_CANARY_DELETE;
  String ECS_RUN_TASK = StepSpecTypeConstants.ECS_RUN_TASK;
  String ECS_BLUE_GREEN_CREATE_SERVICE = StepSpecTypeConstants.ECS_BLUE_GREEN_CREATE_SERVICE;
  String ECS_BLUE_GREEN_SWAP_TARGET_GROUPS = StepSpecTypeConstants.ECS_BLUE_GREEN_SWAP_TARGET_GROUPS;
  String ECS_BLUE_GREEN_ROLLBACK = StepSpecTypeConstants.ECS_BLUE_GREEN_ROLLBACK;

  String AZURE_SLOT_DEPLOYMENT = StepSpecTypeConstants.AZURE_SLOT_DEPLOYMENT;
  String AZURE_TRAFFIC_SHIFT = StepSpecTypeConstants.AZURE_TRAFFIC_SHIFT;
  String AZURE_SWAP_SLOT = StepSpecTypeConstants.AZURE_SWAP_SLOT;
  String AZURE_WEBAPP_ROLLBACK = StepSpecTypeConstants.AZURE_WEBAPP_ROLLBACK;
  String FETCH_INSTANCE_SCRIPT = StepSpecTypeConstants.CUSTOM_DEPLOYMENT_FETCH_INSTANCE_SCRIPT;

  String MANIFEST_LIST_CONFIG = "manifests";
  String MANIFEST_CONFIG = "manifest";
  String K8S_MANIFEST = ManifestType.K8Manifest;
  String HELM_CHART_MANIFEST = ManifestType.HelmChart;
  String KUSTOMIZE_MANIFEST = ManifestType.Kustomize;
  String OPENSHIFT_MANIFEST = ManifestType.OpenshiftTemplate;
  String SPEC = "spec";
  String PIPELINE_INFRASTRUCTURE = "infrastructure";
  String SERVICE_CONFIG = "serviceConfig";
  String SERVICE_SECTION = "serviceSection";
  String SERVICE_ENTITY = "service";
  String SERVICE_REF = "serviceRef";
  String SERVICE_DEFINITION = "serviceDefinition";
  String SERVICE_SPEC = "spec";
  String SERVICE_OVERRIDE = "serviceOverrides";
  String SERVICE_INPUTS = "serviceInputs";
  String STAGE_OVERRIDES_CONFIG = "stageOverrides";
  String PATH_CONNECTOR = VisitorParentPathUtils.PATH_CONNECTOR;
  String CONNECTOR_REF = "connectorRef";
  String CONFIGURATION = "configuration";
  String TAG = "tag";
  String TAG_REGEX = "tagRegex";
  String IMAGE_PATH = "imagePath";
  String BRANCH = "branch";
  String COMMIT_ID = "commitId";
  String NAMESPACE = "namespace";
  String RELEASE_NAME = "releaseName";
  String CLUSTER = "cluster";
  String STORE_CONFIG_WRAPPER = "store";
  String CONFIG_FILES = "configFiles";
  String CONFIG_FILE = "configFile";

  String SKIP_DRY_RUN = "skipDryRun";
  String OUTPUT = "output";
  String TIMEOUT = "timeout";
  String UUID = YamlNode.UUID_FIELD_NAME;
  String HEADERS = "headers";
  String DELEGATE_SELECTORS = "delegateSelectors";

  String COMMAND_FLAGS_WRAPPER = "commandFlags";
  String REGION = "region";
  String STAGE = "stage";
  String TEMPLATE = "template";
  String CREDENTIALS_REF = "credentialsRef";
  String HOSTS = "hosts";
  String HOST_FILTER = "hostFilter";
  String SIDECARS = "sidecars";
  String SIDECAR = "sidecar";
  String ARTIFACTS = "artifacts";
  String ROLLBACK_STEPS = "rollbackSteps";
  String STEPS = "steps";
  String STRATEGY = "strategy";
  String STEP_GROUP = "stepGroup";
  String PRIMARY = "primary";

  String SUBSCRIPTION = "subscription";
  String RESOURCE_GROUP = "resourceGroup";

  // METADATA for Service and Environment Plan Creator
  String SERVICE_SPEC_UUID = "service_spec_uuid";
  String POST_SERVICE_SPEC_UUID = "service_spec_uuid";
  String INFRA_SECTION_UUID = "infra_section_uuid";
  String NEXT_UUID = "nextUuid";

  String CLOUD_PROVIDER = "cloudProvider";
  String LOAD_BALANCER = "loadBalancer";
  String HOST_NAME_CONVENTION = "hostNameConvention";

  String APP_SERVICE = "appService";
  String DEPLOYMENT_SLOT = "deploymentSlot";

  String ENVIRONMENT_INPUTS = "environmentInputs";
  String SERVICE_OVERRIDE_INPUTS = "serviceOverrideInputs";
  String INPUTS = "inputs";
  String REF = "ref";
  String JENKINS_BUILD = StepSpecTypeConstants.JENKINS_BUILD;
  String STARTUP_COMMAND = "startupCommand";

  String ELASTIGROUP_SERVICE_SETTINGS_STEP = "Elastigroup_Service_Settings";
  String AZURE_SERVICE_SETTINGS_STEP = "Azure_Service_Settings";

  String APPLICATION_SETTINGS = "applicationSettings";
  String CONNECTION_STRINGS = "connectionStrings";
  String DEPLOYMENT_TYPE = "deploymentType";

  String ENV_PRODUCTION = "Production";
  String ENV_PRE_PRODUCTION = "PreProduction";

  String SERVICE_ENTITIES = "services";

  String PIPELINE = "pipeline";
  String PARALLEL_STAGE = "parallel";
  String STAGES = "stages";
  String ELASTIGROUP_SETUP = StepSpecTypeConstants.ELASTIGROUP_SETUP;

  String ORG = "organization";
  String SPACE = "space";
}
