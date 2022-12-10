/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.servicenow.create;

import io.harness.exception.InvalidRequestException;
import io.harness.plancreator.steps.internal.PMSStepPlanCreatorV2;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.steps.StepSpecTypeConstants;

import com.google.common.collect.Sets;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;

public class ServiceNowCreateStepPlanCreator extends PMSStepPlanCreatorV2<ServiceNowCreateStepNode> {
  @Override
  public Set<String> getSupportedStepTypes() {
    return Sets.newHashSet(StepSpecTypeConstants.SERVICENOW_CREATE);
  }

  @Override
  public Class<ServiceNowCreateStepNode> getFieldClass() {
    return ServiceNowCreateStepNode.class;
  }

  @Override
  public PlanCreationResponse createPlanForField(PlanCreationContext ctx, ServiceNowCreateStepNode stepElement) {
    validateServiceNowTemplate(stepElement.getServiceNowCreateStepInfo());
    return super.createPlanForField(ctx, stepElement);
  }

  protected void validateServiceNowTemplate(ServiceNowCreateStepInfo serviceNowCreateStepInfo) {
    ServiceNowCreateSpecParameters specParameters =
        (ServiceNowCreateSpecParameters) serviceNowCreateStepInfo.getSpecParameters();
    if (specParameters.getUseServiceNowTemplate().getValue()) {
      if (ParameterField.isNull(specParameters.getTemplateName())
          || StringUtils.isBlank(specParameters.getTemplateName().getValue())) {
        throw new InvalidRequestException(
            String.format("Missing template name when creating ticket from ServiceNow template in %s step",
                serviceNowCreateStepInfo.getStepType().getType()));
      }
    }
  }
}
