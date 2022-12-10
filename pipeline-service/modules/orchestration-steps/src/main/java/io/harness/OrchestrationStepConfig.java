/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.steps.container.execution.ContainerExecutionConfig;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@OwnedBy(HarnessTeam.CF)
@Data
@Builder
public class OrchestrationStepConfig {
  @JsonProperty("ffServerBaseUrl") private String ffServerBaseUrl;
  @JsonProperty("ffServerApiKey") private String ffServerApiKey;
  @JsonProperty("ffServerSSLVerify") private Boolean ffServerSSLVerify;
  @JsonProperty("containerStepConfig") private ContainerExecutionConfig containerStepConfig;
}
