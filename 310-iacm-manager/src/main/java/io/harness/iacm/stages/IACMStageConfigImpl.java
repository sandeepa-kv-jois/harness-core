/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.iacm.stages;

import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.string;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SwaggerConstants;
import io.harness.beans.dependencies.DependencyElement;
import io.harness.beans.yaml.extended.cache.Caching;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.beans.yaml.extended.infrastrucutre.UseFromStageInfraYaml;
import io.harness.beans.yaml.extended.platform.Platform;
import io.harness.beans.yaml.extended.runtime.CloudRuntime;
import io.harness.beans.yaml.extended.runtime.Runtime;
import io.harness.cimanager.stages.IntegrationStageConfig;
import io.harness.iacm.beans.steps.StepSpecTypeConstants;
import io.harness.plancreator.execution.ExecutionElementConfig;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlNode;
import io.harness.yaml.YamlSchemaTypes;
import io.harness.yaml.schema.beans.SupportedPossibleFieldTypes;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(HarnessTeam.IACM)
@Data
@Builder
@AllArgsConstructor
@JsonTypeName(StepSpecTypeConstants.IACM_STAGE)
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@TypeAlias("iacmStage")
public class IACMStageConfigImpl implements IntegrationStageConfig {
  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  String uuid;

  @YamlSchemaTypes(value = {string})
  @ApiModelProperty(dataType = SwaggerConstants.STRING_LIST_CLASSPATH)
  ParameterField<List<String>> sharedPaths;

  ExecutionElementConfig execution;

  // TODO: I Can't comment this values
  @JsonTypeInfo(
      use = JsonTypeInfo.Id.NAME, property = "type", visible = true, defaultImpl = UseFromStageInfraYaml.class)
  Infrastructure infrastructure;

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", visible = true, defaultImpl = CloudRuntime.class)
  Runtime runtime;

  @YamlSchemaTypes(value = {SupportedPossibleFieldTypes.runtime})
  @ApiModelProperty(dataType = "io.harness.beans.yaml.extended.platform.Platform")
  ParameterField<Platform> platform;

  @YamlSchemaTypes(value = {SupportedPossibleFieldTypes.runtime})
  @ApiModelProperty(dataType = "[Lio.harness.beans.dependencies.DependencyElement;")
  ParameterField<List<DependencyElement>> serviceDependencies;
  // <==================== =which i think are unnecesary for our steps
  @YamlSchemaTypes(value = {SupportedPossibleFieldTypes.runtime})
  @ApiModelProperty(dataType = SwaggerConstants.BOOLEAN_CLASSPATH)
  private ParameterField<Boolean> cloneCodebase;

  @YamlSchemaTypes(value = {SupportedPossibleFieldTypes.runtime})
  @ApiModelProperty(dataType = "io.harness.beans.yaml.extended.cache.Caching")
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  private Caching caching;
}
