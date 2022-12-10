/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.steps.cf;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SwaggerConstants;
import io.harness.pms.yaml.ParameterField;

import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(HarnessTeam.CF)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonTypeName("SetDefaultVariations")
@TypeAlias("SetDefaultVariationsYaml")
public class SetDefaultVariationsYaml implements PatchInstruction {
  @Builder.Default
  @NotNull
  @ApiModelProperty(allowableValues = "SetDefaultVariations")
  private Type type = Type.SET_DEFAULT_VARIATIONS;
  @NotNull private String identifier;
  @NotNull private SetDefaultVariationsYamlSpec spec;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class SetDefaultVariationsYamlSpec {
    @NotNull @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) private ParameterField<String> on;
    @NotNull @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) private ParameterField<String> off;
  }
}
