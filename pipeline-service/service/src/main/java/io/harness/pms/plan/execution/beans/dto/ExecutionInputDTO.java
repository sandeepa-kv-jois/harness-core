/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.beans.dto;

import io.harness.pms.plan.execution.PlanExecutionResourceConstants;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@ApiModel("ExecutionInputDTO")
@Schema(name = "ExecutionInputDTO", description = "Contains the template for Execution time inputs.")
public class ExecutionInputDTO {
  @Schema(description = PlanExecutionResourceConstants.NODE_EXECUTION_ID_PARAM_MESSAGE) String nodeExecutionId;
  @Schema(description = PlanExecutionResourceConstants.INPUT_INSTANCE_ID_PARAM_MESSAGE) String inputInstanceId;
  @Schema(description = "template for Execution time inputs.") String inputTemplate;
  @Schema(description = "submitted user input for Execution time input") String userInput;
  @Schema(description = "Yaml of the field/node for provided nodeExecutionId") String fieldYaml;
}
