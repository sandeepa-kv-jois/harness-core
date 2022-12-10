/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.governance;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.EXTERNAL_PROPERTY;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.infra.beans.InfraUseFromStage;
import io.harness.cdng.infra.yaml.Infrastructure;
import io.harness.cdng.visitor.YamlTypes;
import io.harness.governance.ExpansionKeysConstants;
import io.harness.ng.core.environment.beans.EnvironmentBasicInfo;
import io.harness.ng.core.infrastructure.InfrastructureType;
import io.harness.pms.merger.YamlConfig;
import io.harness.pms.sdk.core.governance.ExpandedValue;
import io.harness.yaml.utils.JsonPipelineUtils;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.HashMap;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.SneakyThrows;
import lombok.experimental.FieldNameConstants;

@OwnedBy(HarnessTeam.CDC)
@Builder
@FieldNameConstants(innerTypeName = "keys")
public class InfrastructureExpandedValue implements ExpandedValue {
  private EnvironmentBasicInfo environment;
  private InfrastructureValue infrastructureDefinition;
  private InfraUseFromStage useFromStage;
  private boolean allowSimultaneousDeployments;

  private JsonNode infraSpecConnectorNode;

  @Override
  public String getKey() {
    return ExpansionKeysConstants.INFRA_EXPANSION_KEY;
  }

  @SneakyThrows
  @Override
  public String toJson() {
    final HashMap<Object, Object> props = new HashMap<>();
    putIfNonNull(props, "environment", environment);
    putIfNonNull(props, "infrastructureDefinition", infrastructureDefinition);
    putIfNonNull(props, "useFromStage", useFromStage);
    putIfNonNull(props, "allowSimultaneousDeployments", allowSimultaneousDeployments);
    String json = JsonPipelineUtils.writeJsonString(props);
    if (infraSpecConnectorNode != null) {
      YamlConfig yamlConfig = new YamlConfig(json);
      ObjectNode parentNode = (ObjectNode) yamlConfig.getYamlMap();
      ObjectNode infraNode = (ObjectNode) parentNode.get(keys.infrastructureDefinition);
      ObjectNode spec = (ObjectNode) infraNode.get("spec");
      if (spec.get(YamlTypes.CONNECTOR_REF) != null) {
        spec.set("connector", infraSpecConnectorNode);
        spec.remove(YamlTypes.CONNECTOR_REF);
      }
      return parentNode.toPrettyString();
    }

    return json;
  }

  private void putIfNonNull(Map<Object, Object> props, String key, Object val) {
    if (val != null) {
      props.put(key, val);
    }
  }

  @Data
  @Builder
  public static class InfrastructureValue {
    InfrastructureType type;
    @JsonProperty("spec")
    @JsonTypeInfo(use = NAME, property = "type", include = EXTERNAL_PROPERTY, visible = true)
    Infrastructure spec;
  }
}
