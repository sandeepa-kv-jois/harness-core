/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.environment;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Stores information for setting up environment for running  CI job
 */

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", defaultImpl = K8BuildJobEnvInfo.class)
@JsonSubTypes({
  @JsonSubTypes.Type(value = K8BuildJobEnvInfo.class, name = "K8")
  , @JsonSubTypes.Type(value = VmBuildJobInfo.class, name = "VM"),
      @JsonSubTypes.Type(value = VmBuildJobInfo.class, name = "DOCKER")
})
public interface BuildJobEnvInfo {
  enum Type {
    @JsonProperty("K8") K8("K8"),
    @JsonProperty("VM") VM("VM"),
    @JsonProperty("DOCKER") DOCKER("DOCKER");

    private final String yamlName;
    Type(String yamlName) {
      this.yamlName = yamlName;
    }

    @JsonValue
    public String getYamlName() {
      return yamlName;
    }
  }

  Type getType();
}
