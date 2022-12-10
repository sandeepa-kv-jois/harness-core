/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.merger.helpers;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.expression.common.ExpressionConstants.EXPR_END_ESC;
import static io.harness.expression.common.ExpressionConstants.EXPR_START;

import io.harness.annotations.dev.OwnedBy;
import io.harness.common.NGExpressionUtils;
import io.harness.data.structure.HarnessStringUtils;
import io.harness.jackson.JsonNodeUtils;
import io.harness.pms.merger.YamlConfig;
import io.harness.pms.merger.fqn.FQN;
import io.harness.pms.merger.fqn.FQNNode;
import io.harness.pms.yaml.YAMLFieldNameConstants;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.experimental.UtilityClass;

@OwnedBy(PIPELINE)
@UtilityClass
public class RuntimeInputFormHelper {
  private String EXECUTION_FQN_FROM_STAGE =
      YAMLFieldNameConstants.STAGE + "." + YAMLFieldNameConstants.SPEC + "." + YAMLFieldNameConstants.EXECUTION;
  public String createTemplateFromYaml(String templateYaml) {
    return createRuntimeInputForm(templateYaml, true);
  }

  public String createRuntimeInputForm(String yaml, boolean keepInput) {
    YamlConfig runtimeInputFormYamlConfig = createRuntimeInputFormYamlConfig(yaml, keepInput);
    return runtimeInputFormYamlConfig.getYaml();
  }

  public String removeRuntimeInputsFromYaml(String pipelineYaml, String runtimeInputsYaml, boolean keepInput) {
    YamlConfig runtimeInputFormYamlConfig =
        createRuntimeInputFormYamlConfig(new YamlConfig(pipelineYaml), new YamlConfig(runtimeInputsYaml), keepInput);
    return runtimeInputFormYamlConfig.getYaml();
  }

  private YamlConfig createRuntimeInputFormYamlConfig(String yaml, boolean keepInput) {
    YamlConfig yamlConfig = new YamlConfig(yaml);
    return createRuntimeInputFormYamlConfig(yamlConfig, keepInput);
  }

  public YamlConfig createRuntimeInputFormYamlConfig(YamlConfig yamlConfig, boolean keepInput) {
    Map<FQN, Object> fullMap = yamlConfig.getFqnToValueMap();
    Map<FQN, Object> templateMap = new LinkedHashMap<>();
    fullMap.keySet().forEach(key -> {
      String value = HarnessStringUtils.removeLeadingAndTrailingQuotesBothOrNone(fullMap.get(key).toString());
      // keepInput can be considered always true if value matches executionInputPattern. As the input will be provided
      // at execution time.
      if (NGExpressionUtils.matchesExecutionInputPattern(value)
          || (keepInput && NGExpressionUtils.matchesInputSetPattern(value))
          || (!keepInput && !NGExpressionUtils.matchesInputSetPattern(value) && !key.isIdentifierOrVariableName()
              && !key.isType())) {
        templateMap.put(key, fullMap.get(key));
      }
    });

    return new YamlConfig(templateMap, yamlConfig.getYamlMap());
  }

  public YamlConfig createRuntimeInputFormYamlConfig(YamlConfig pipeline, YamlConfig inputsConfig, boolean keepInput) {
    Map<FQN, Object> inputsFqnToValueMap = inputsConfig.getFqnToValueMap();
    Map<FQN, Object> pipelineFqnToValueMap = pipeline.getFqnToValueMap();
    Map<FQN, Object> templateMap = new LinkedHashMap<>();
    Set<FQN> fqnsWithSomeParentAsRuntimeOrExpression =
        leafNodesWithParentsAsRuntimeInput(pipelineFqnToValueMap, inputsFqnToValueMap);
    inputsFqnToValueMap.keySet().forEach(key -> {
      String value =
          HarnessStringUtils.removeLeadingAndTrailingQuotesBothOrNone(inputsFqnToValueMap.get(key).toString());
      // keepInput can be considered always true if value matches executionInputPattern. As the input will be provided
      // at execution time.
      if (NGExpressionUtils.matchesExecutionInputPattern(value)
          || (keepInput && NGExpressionUtils.matchesInputSetPattern(value))
          || (!keepInput && !NGExpressionUtils.matchesInputSetPattern(value) && !key.isIdentifierOrVariableName()
              && !key.isType())
          || (!keepInput && fqnsWithSomeParentAsRuntimeOrExpression.contains(key))) {
        templateMap.put(key, inputsFqnToValueMap.get(key));
      }
    });

    return new YamlConfig(templateMap, inputsConfig.getYamlMap());
  }

  private Set<FQN> leafNodesWithParentsAsRuntimeInput(Map<FQN, Object> pipelineFqns, Map<FQN, Object> inputsFqns) {
    final Set<FQN> result = new HashSet<>();
    for (FQN fqn : inputsFqns.keySet()) {
      if (isEmpty(fqn.getFqnList()) || fqn.getFqnList().size() <= 1) {
        continue;
      }
      List<FQNNode> fqnList = new ArrayList<>(fqn.getFqnList());
      fqnList.remove(fqnList.size() - 1);
      while (fqnList.size() > 0) {
        // find first fqn from inputsFqn containing "pipeline" since this can have pipeline as embedded object like
        // triggers, input set
        FQN parentFqn = normalizeAgainstKey(fqnList, "pipeline");
        if (pipelineFqns.containsKey(parentFqn)) {
          if (pipelineFqns.get(parentFqn) instanceof TextNode) {
            TextNode text = (TextNode) pipelineFqns.get(parentFqn);
            if (NGExpressionUtils.isRuntimeField(text.asText())) {
              result.add(fqn);
              break;
            }
          }
        }
        fqnList.remove(fqnList.size() - 1);
      }
    }
    return result;
  }

  private FQN normalizeAgainstKey(List<FQNNode> fqnList, String key) {
    int indexWithKey = -1;
    for (int i = 0; i < fqnList.size(); i++) {
      FQNNode fqnNode = fqnList.get(i);
      if (key.equals(fqnNode.getKey()) && fqnNode.getNodeType() == FQNNode.NodeType.KEY) {
        indexWithKey = i;
        break;
      }
    }
    return indexWithKey != -1 ? FQN.builder().fqnList(fqnList.subList(indexWithKey, fqnList.size())).build()
                              : FQN.builder().fqnList(fqnList).build();
  }

  public String createExecutionInputFormAndUpdateYamlField(JsonNode jsonNode) {
    YamlConfig yamlConfig = new YamlConfig(jsonNode, true);
    Map<FQN, Object> fullMap = yamlConfig.getFqnToValueMap();
    Map<FQN, Object> templateMap = new LinkedHashMap<>();
    fullMap.keySet().forEach(key -> {
      String value = fullMap.get(key).toString().replace("\\\"", "").replace("\"", "");
      if (NGExpressionUtils.matchesExecutionInputPattern(value)) {
        templateMap.put(key, fullMap.get(key));
        fullMap.put(key,
            EXPR_START + NGExpressionUtils.EXPRESSION_INPUT_CONSTANT + "." + key.getExpressionFqnWithoutIgnoring()
                + EXPR_END_ESC);
      } else if (NGExpressionUtils.matchesUpdatedExecutionInputPattern(value)) {
        templateMap.put(key, fullMap.get(key));
      }
    });
    // Updating the executionInput field to expression in jsonNode.
    JsonNodeUtils.merge(jsonNode, (new YamlConfig(fullMap, yamlConfig.getYamlMap(), false, true)).getYamlMap());
    return (new YamlConfig(templateMap, yamlConfig.getYamlMap(), false, true)).getYaml();
  }

  public String createExecutionInputFormAndUpdateYamlFieldForStage(JsonNode jsonNode) {
    YamlConfig yamlConfig = new YamlConfig(jsonNode, true);

    Map<FQN, Object> fullMap = yamlConfig.getFqnToValueMap();
    Map<FQN, Object> templateMap = new LinkedHashMap<>();

    fullMap.keySet().forEach(key -> {
      if (!key.getExpressionFqn().startsWith(EXECUTION_FQN_FROM_STAGE)) {
        String value = fullMap.get(key).toString().replace("\\\"", "").replace("\"", "");
        if (NGExpressionUtils.matchesExecutionInputPattern(value)) {
          templateMap.put(key, fullMap.get(key));
          fullMap.put(key,
              EXPR_START + NGExpressionUtils.EXPRESSION_INPUT_CONSTANT + "." + key.getExpressionFqnWithoutIgnoring()
                  + EXPR_END_ESC);
        } else if (NGExpressionUtils.matchesUpdatedExecutionInputPattern(value)) {
          templateMap.put(key, fullMap.get(key));
        }
      }
    });

    // Updating the executionInput field to expression in jsonNode.
    JsonNodeUtils.merge(jsonNode, (new YamlConfig(fullMap, yamlConfig.getYamlMap(), false, true)).getYamlMap());
    return (new YamlConfig(templateMap, yamlConfig.getYamlMap(), false, true)).getYaml();
  }
}
