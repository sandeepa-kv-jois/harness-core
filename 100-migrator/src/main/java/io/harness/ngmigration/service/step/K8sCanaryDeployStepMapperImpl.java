/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.step;

import io.harness.cdng.k8s.CountInstanceSelection;
import io.harness.cdng.k8s.InstanceSelectionBase;
import io.harness.cdng.k8s.InstanceSelectionWrapper;
import io.harness.cdng.k8s.K8sCanaryStepInfo;
import io.harness.cdng.k8s.K8sCanaryStepNode;
import io.harness.cdng.k8s.K8sInstanceUnitType;
import io.harness.cdng.k8s.PercentageInstanceSelection;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.ngmigration.service.MigratorUtility;
import io.harness.plancreator.steps.AbstractStepNode;
import io.harness.pms.yaml.ParameterField;

import software.wings.beans.InstanceUnitType;
import software.wings.sm.State;
import software.wings.sm.states.k8s.K8sCanaryDeploy;
import software.wings.yaml.workflow.StepYaml;

import java.util.Map;

public class K8sCanaryDeployStepMapperImpl implements StepMapper {
  @Override
  public String getStepType(StepYaml stepYaml) {
    return StepSpecTypeConstants.K8S_CANARY_DEPLOY;
  }

  @Override
  public State getState(StepYaml stepYaml) {
    Map<String, Object> properties = StepMapper.super.getProperties(stepYaml);
    K8sCanaryDeploy state = new K8sCanaryDeploy(stepYaml.getName());
    state.parseProperties(properties);
    return state;
  }

  @Override
  public AbstractStepNode getSpec(StepYaml stepYaml) {
    K8sCanaryDeploy state = (K8sCanaryDeploy) getState(stepYaml);
    K8sCanaryStepNode k8sCanaryStepNode = new K8sCanaryStepNode();
    baseSetup(state, k8sCanaryStepNode);
    InstanceSelectionBase spec;
    if (state.getInstanceUnitType().equals(InstanceUnitType.COUNT)) {
      spec = new CountInstanceSelection();
      ((CountInstanceSelection) spec).setCount(ParameterField.createValueField(state.getInstances()));
    } else {
      spec = new PercentageInstanceSelection();
      ((PercentageInstanceSelection) spec).setPercentage(ParameterField.createValueField(state.getInstances()));
    }

    k8sCanaryStepNode.setK8sCanaryStepInfo(
        K8sCanaryStepInfo.infoBuilder()
            .skipDryRun(ParameterField.createValueField(state.isSkipDryRun()))
            .instanceSelection(
                InstanceSelectionWrapper.builder()
                    .type(InstanceUnitType.COUNT.equals(state.getInstanceUnitType()) ? K8sInstanceUnitType.Count
                                                                                     : K8sInstanceUnitType.Percentage)
                    .spec(spec)
                    .build())
            .delegateSelectors(MigratorUtility.getDelegateSelectors(state.getDelegateSelectors()))
            .build());
    return k8sCanaryStepNode;
  }

  @Override
  public boolean areSimilar(StepYaml stepYaml1, StepYaml stepYaml2) {
    K8sCanaryDeploy state1 = (K8sCanaryDeploy) getState(stepYaml1);
    K8sCanaryDeploy state2 = (K8sCanaryDeploy) getState(stepYaml2);
    if (!state2.getInstanceUnitType().equals(state1.getInstanceUnitType())) {
      return false;
    }
    return state1.getInstances().equals(state2.getInstances());
  }
}
