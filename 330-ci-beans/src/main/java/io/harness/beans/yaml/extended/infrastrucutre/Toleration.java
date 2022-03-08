package io.harness.beans.yaml.extended.infrastrucutre;

import static io.harness.beans.SwaggerConstants.INTEGER_CLASSPATH;
import static io.harness.beans.SwaggerConstants.STRING_CLASSPATH;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.string;

import io.harness.pms.yaml.ParameterField;
import io.harness.yaml.YamlSchemaTypes;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Toleration {
  @ApiModelProperty(dataType = STRING_CLASSPATH) private ParameterField<String> effect;
  @ApiModelProperty(dataType = STRING_CLASSPATH) private ParameterField<String> key;
  @ApiModelProperty(dataType = STRING_CLASSPATH) private ParameterField<String> operator;
  @YamlSchemaTypes(value = {string})
  @ApiModelProperty(dataType = INTEGER_CLASSPATH)
  private ParameterField<Integer> tolerationSeconds;
  @ApiModelProperty(dataType = STRING_CLASSPATH) private ParameterField<String> value;
}