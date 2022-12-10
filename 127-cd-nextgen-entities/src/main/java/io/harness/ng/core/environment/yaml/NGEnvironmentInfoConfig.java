/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.environment.yaml;

import static io.harness.data.structure.CollectionUtils.emptyIfNull;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static java.util.stream.Collectors.groupingBy;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SwaggerConstants;
import io.harness.data.validator.EntityIdentifier;
import io.harness.data.validator.EntityName;
import io.harness.ng.core.environment.beans.EnvironmentType;
import io.harness.ng.core.environment.beans.NGEnvironmentGlobalOverride;
import io.harness.validator.NGRegexValidatorConstants;
import io.harness.yaml.core.variables.NGVariable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModelProperty;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.Valid;
import javax.validation.constraints.AssertTrue;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(HarnessTeam.CDC)
@Data
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@TypeAlias("ngEnvironmentInfoConfig")
public class NGEnvironmentInfoConfig {
  @JsonProperty("__uuid")
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  String uuid;
  String orgIdentifier;
  String projectIdentifier;
  @NotNull @EntityIdentifier @Pattern(regexp = NGRegexValidatorConstants.IDENTIFIER_PATTERN) String identifier;
  Map<String, String> tags;
  @NotNull @EntityName @Pattern(regexp = NGRegexValidatorConstants.NAME_PATTERN) String name;
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) String description;
  @ApiModelProperty(required = true) EnvironmentType type;
  @Valid List<NGVariable> variables;
  @JsonProperty("overrides") NGEnvironmentGlobalOverride ngEnvironmentGlobalOverride;
  @AssertTrue(message = "duplicate variables are present. Please remove them and retry")
  private boolean isValid() {
    try {
      Set<String> duplicateVariables = emptyIfNull(variables)
                                           .stream()
                                           .collect(groupingBy(NGVariable::getName, Collectors.counting()))
                                           .entrySet()
                                           .stream()
                                           .filter(entry -> entry.getValue() > 1)
                                           .map(Map.Entry::getKey)
                                           .collect(Collectors.toSet());
      return isEmpty(duplicateVariables);
    } catch (Exception ex) {
      //
    }
    return true;
  }
}