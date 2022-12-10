/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.yaml.extended.runtime.V1;

import static io.harness.annotations.dev.HarnessTeam.CI;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@Value
@Builder
@AllArgsConstructor
@JsonTypeName("cloud")
@TypeAlias("CloudRuntimeV1")
@OwnedBy(CI)
public class CloudRuntimeV1 implements RuntimeV1 {
  @Builder.Default @NotNull @ApiModelProperty(allowableValues = "cloud") Type type = Type.CLOUD;
  @NotNull CloudRuntimeSpec spec;

  @Value
  @Builder
  public static class CloudRuntimeSpec {}
}