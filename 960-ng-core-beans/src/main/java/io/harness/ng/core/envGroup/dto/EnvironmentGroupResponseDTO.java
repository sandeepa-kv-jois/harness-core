/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.envGroup.dto;

import io.harness.ng.core.environment.beans.EnvironmentType;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(name = "EnvironmentGroupResponse", description = "This is the Environment Group Entity defined in Harness")
public class EnvironmentGroupResponseDTO {
  String accountId;
  String orgIdentifier;
  String projectIdentifier;
  String identifier;
  String name;
  String description;
  String color;
  boolean deleted;
  Map<String, String> tags;
  @JsonIgnore Long version;
}
