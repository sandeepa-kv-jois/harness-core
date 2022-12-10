/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.yaml.extended.infrastrucutre.k8;

import static io.harness.beans.SwaggerConstants.INTEGER_CLASSPATH;
import static io.harness.beans.SwaggerConstants.STRING_CLASSPATH;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.string;

import io.harness.annotation.RecasterAlias;
import io.harness.pms.yaml.ParameterField;
import io.harness.yaml.YamlSchemaTypes;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@TypeAlias("toleration")
@RecasterAlias("io.harness.beans.yaml.extended.infrastrucutre.k8.Toleration")
public class Toleration {
  @ApiModelProperty(dataType = STRING_CLASSPATH) private ParameterField<String> effect;
  @ApiModelProperty(dataType = STRING_CLASSPATH) private ParameterField<String> key;
  @ApiModelProperty(dataType = STRING_CLASSPATH) private ParameterField<String> operator;
  @YamlSchemaTypes(value = {string})
  @ApiModelProperty(dataType = INTEGER_CLASSPATH)
  private ParameterField<Integer> tolerationSeconds;
  @ApiModelProperty(dataType = STRING_CLASSPATH) private ParameterField<String> value;
}
