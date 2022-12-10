/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.workflow;

import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.data.structure.EmptyPredicate;
import io.harness.ngmigration.service.step.StepMapperFactory;

import software.wings.beans.GraphNode;
import software.wings.beans.RollingOrchestrationWorkflow;
import software.wings.beans.Workflow;
import software.wings.beans.WorkflowPhase.Yaml;
import software.wings.service.impl.yaml.handler.workflow.RollingWorkflowYamlHandler;
import software.wings.yaml.workflow.RollingWorkflowYaml;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import java.util.Collections;
import java.util.List;

public class RollingWorkflowHandlerImpl extends WorkflowHandler {
  @Inject RollingWorkflowYamlHandler rollingWorkflowYamlHandler;
  @Inject private StepMapperFactory stepMapperFactory;

  @Override
  public List<Yaml> getRollbackPhases(Workflow workflow) {
    RollingWorkflowYaml rollingWorkflowYaml = rollingWorkflowYamlHandler.toYaml(workflow, workflow.getAppId());
    return EmptyPredicate.isNotEmpty(rollingWorkflowYaml.getRollbackPhases()) ? rollingWorkflowYaml.getRollbackPhases()
                                                                              : Collections.emptyList();
  }

  @Override
  public List<Yaml> getPhases(Workflow workflow) {
    RollingWorkflowYaml rollingWorkflowYaml = rollingWorkflowYamlHandler.toYaml(workflow, workflow.getAppId());
    return EmptyPredicate.isNotEmpty(rollingWorkflowYaml.getPhases()) ? rollingWorkflowYaml.getPhases()
                                                                      : Collections.emptyList();
  }

  @Override
  public List<GraphNode> getSteps(Workflow workflow) {
    RollingOrchestrationWorkflow orchestrationWorkflow =
        (RollingOrchestrationWorkflow) workflow.getOrchestrationWorkflow();
    return getSteps(orchestrationWorkflow.getWorkflowPhases(), orchestrationWorkflow.getPreDeploymentSteps(),
        orchestrationWorkflow.getPostDeploymentSteps());
  }

  //  .failureStrategies(Collections.singletonList(
  //      FailureStrategyConfig.builder()
  //                        .onFailure(OnFailureConfig.builder()
  //                                       .errors(Collections.singletonList(NGFailureType.ALL_ERRORS))
  //      .action(StageRollbackFailureActionConfig.builder().build())
  //      .build())
  //      .build()))

  @Override
  public boolean areSimilar(Workflow workflow1, Workflow workflow2) {
    return areSimilar(stepMapperFactory, workflow1, workflow2);
  }

  public JsonNode getTemplateSpec(Workflow workflow) {
    return getDeploymentStageTemplateSpec(workflow, stepMapperFactory);
  }

  @Override
  public ServiceDefinitionType inferServiceDefinitionType(Workflow workflow) {
    // We can infer the type based on the service, infra & sometimes based on the steps used.
    // TODO: Deepak Puthraya
    return ServiceDefinitionType.KUBERNETES;
  }
}
