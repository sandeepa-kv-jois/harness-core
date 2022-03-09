/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.mongo;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import org.hibernate.validator.constraints.NotEmpty;

@OwnedBy(CDC)
@Value
@Builder
public class AnalyticNodeMongoConfig {
  @JsonProperty(defaultValue = "none") @Builder.Default @NotEmpty String mongoTagKey = "none";
  @JsonProperty(defaultValue = "none") @Builder.Default @NotEmpty String mongoTagValue = "none";
}
