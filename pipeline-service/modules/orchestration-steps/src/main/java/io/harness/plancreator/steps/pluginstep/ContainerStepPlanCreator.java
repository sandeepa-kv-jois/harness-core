/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator.steps.pluginstep;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.plancreator.steps.internal.PMSStepPlanCreatorV2;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.yaml.PipelineVersion;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.plugin.ContainerStepNode;

import com.google.common.collect.Sets;
import java.util.Set;

@OwnedBy(HarnessTeam.PIPELINE)
public class ContainerStepPlanCreator extends PMSStepPlanCreatorV2<ContainerStepNode> {
  @Override
  public Set<String> getSupportedStepTypes() {
    return Sets.newHashSet(StepSpecTypeConstants.CONTAINER_STEP);
  }

  @Override
  public Class<ContainerStepNode> getFieldClass() {
    return ContainerStepNode.class;
  }

  @Override
  public PlanCreationResponse createPlanForField(PlanCreationContext ctx, ContainerStepNode field) {
    field.getContainerStepInfo().setIdentifier(field.getIdentifier());
    field.getContainerStepInfo().setName(field.getName());
    return super.createPlanForField(ctx, field);
  }

  @Override
  public Set<String> getSupportedYamlVersions() {
    return Set.of(PipelineVersion.V0);
  }
}
