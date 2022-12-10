/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.step;

import io.harness.plancreator.steps.AbstractStepNode;
import io.harness.plancreator.steps.barrier.BarrierStepInfo;
import io.harness.plancreator.steps.barrier.BarrierStepNode;
import io.harness.steps.StepSpecTypeConstants;

import software.wings.sm.State;
import software.wings.sm.states.BarrierState;
import software.wings.yaml.workflow.StepYaml;

import java.util.Map;

public class BarrierStepMapperImpl implements StepMapper {
  @Override
  public String getStepType(StepYaml stepYaml) {
    return StepSpecTypeConstants.BARRIER;
  }

  @Override
  public State getState(StepYaml stepYaml) {
    Map<String, Object> properties = StepMapper.super.getProperties(stepYaml);
    BarrierState state = new BarrierState(stepYaml.getName());
    state.parseProperties(properties);
    return state;
  }

  @Override
  public AbstractStepNode getSpec(StepYaml stepYaml) {
    BarrierState state = (BarrierState) getState(stepYaml);
    BarrierStepNode barrierStepNode = new BarrierStepNode();
    baseSetup(stepYaml, barrierStepNode);
    BarrierStepInfo barrierStepInfo =
        BarrierStepInfo.builder().name(state.getName()).identifier(state.getIdentifier()).build();
    barrierStepNode.setBarrierStepInfo(barrierStepInfo);
    return barrierStepNode;
  }

  @Override
  public boolean areSimilar(StepYaml stepYaml1, StepYaml stepYaml2) {
    // Barrier steps are pretty much same across.
    return true;
  }
}
