/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.infra.steps;

import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.yaml.ParameterField;

import java.util.Map;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class InfrastructureTaskExecutableStepV2Params implements StepParameters {
  @NotNull private ParameterField<String> envRef;
  @NotNull private ParameterField<String> infraRef;
  private ServiceDefinitionType deploymentType;
  private ParameterField<Map<String, Object>> infraInputs;
  private ParameterField<Boolean> skipInstances;
}
