/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.template;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;

@OwnedBy(CDC)
public interface TemplateEntityConstants {
  String STEP = "Step";
  String STAGE = "Stage";
  String PIPELINE = "Pipeline";
  String CUSTOM_DEPLOYMENT = "CustomDeployment";
  String MONITORED_SERVICE = "MonitoredService";
  String SECRET_MANAGER = "SecretManager";
  String STABLE_TEMPLATE = "Stable";
  String LAST_UPDATES_TEMPLATE = "LastUpdated";
  String ALL = "All";
  String TEMPLATE_STABLE_TRUE_WITH_YAML_CHANGE = "TemplateStableTrueWithYamlChange";
  String TEMPLATE_STABLE_TRUE = "TemplateStableTrue";
  String TEMPLATE_STABLE_FALSE = "TemplateStableFalse";
  String TEMPLATE_LAST_UPDATED_FALSE = "TemplateLastUpdatedFalse";
  String TEMPLATE_LAST_UPDATED_TRUE = "TemplateLastUpdatedTrue";
  String TEMPLATE_CHANGE_SCOPE = "TemplateChangeScope";
  String TEMPLATE_CREATE = "TemplateCreate";
  String OTHERS = "Others";
  String STEP_ROOT_FIELD = "step";
  String STAGE_ROOT_FIELD = "stage";
  String PIPELINE_ROOT_FIELD = "pipeline";
  String CUSTOM_DEPLOYMENT_ROOT_FIELD = "customDeployment";
  String MONITORED_SERVICE_ROOT_FIELD = "monitoredService";
  String SECRET_MANAGER_ROOT_FIELD = "secretManager";
  String ARTIFACT_SOURCE_ROOT_FIELD = "artifactSource";
  String ARTIFACT_SOURCE = "ArtifactSource";
  String STEP_GROUP = "StepGroup";
  String STEP_GROUP_ROOT_FIELD = "stepGroup";
}
