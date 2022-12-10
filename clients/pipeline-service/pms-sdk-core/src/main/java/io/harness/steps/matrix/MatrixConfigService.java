/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.matrix;

import io.harness.exception.InvalidRequestException;
import io.harness.exception.InvalidYamlException;
import io.harness.plancreator.strategy.AxisConfig;
import io.harness.plancreator.strategy.ExcludeConfig;
import io.harness.plancreator.strategy.ExpressionAxisConfig;
import io.harness.plancreator.strategy.MatrixConfig;
import io.harness.plancreator.strategy.StrategyConfig;
import io.harness.plancreator.strategy.StrategyUtils;
import io.harness.pms.contracts.execution.ChildrenExecutableResponse;
import io.harness.pms.contracts.execution.MatrixMetadata;
import io.harness.pms.contracts.execution.StrategyMetadata;
import io.harness.pms.yaml.ParameterField;
import io.harness.serializer.JsonUtils;
import io.harness.yaml.utils.JsonPipelineUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Singleton
public class MatrixConfigService implements StrategyConfigService {
  public List<ChildrenExecutableResponse.Child> fetchChildren(StrategyConfig strategyConfig, String childNodeId) {
    MatrixConfig matrixConfig = (MatrixConfig) strategyConfig.getMatrixConfig();
    List<Map<String, String>> combinations = new ArrayList<>();
    List<List<Integer>> matrixMetadata = new ArrayList<>();
    List<String> keys = getKeys(matrixConfig);

    fetchCombinations(new LinkedHashMap<>(), matrixConfig.getAxes(), matrixConfig.getExpressionAxes(), combinations,
        ParameterField.isBlank(matrixConfig.getExclude()) ? null : matrixConfig.getExclude().getValue(), matrixMetadata,
        keys, 0, new LinkedList<>());
    List<ChildrenExecutableResponse.Child> children = new ArrayList<>();
    int currentIteration = 0;
    int totalCount = combinations.size();
    if (totalCount == 0) {
      throw new InvalidRequestException(
          "Total number of iterations found to be 0 for this strategy. Please check pipeline yaml");
    }
    for (Map<String, String> combination : combinations) {
      children.add(ChildrenExecutableResponse.Child.newBuilder()
                       .setChildNodeId(childNodeId)
                       .setStrategyMetadata(
                           StrategyMetadata.newBuilder()
                               .setCurrentIteration(currentIteration)
                               .setTotalIterations(totalCount)
                               .setMatrixMetadata(MatrixMetadata.newBuilder()
                                                      .addAllMatrixCombination(matrixMetadata.get(currentIteration))
                                                      .putAllMatrixValues(combination)
                                                      .build())
                               .build())
                       .build());
      currentIteration++;
    }
    return children;
  }

  public StrategyInfo expandJsonNode(
      StrategyConfig strategyConfig, JsonNode jsonNode, Optional<Integer> maxExpansionLimit) {
    MatrixConfig matrixConfig = (MatrixConfig) strategyConfig.getMatrixConfig();
    List<Map<String, String>> combinations = new ArrayList<>();
    List<List<Integer>> matrixMetadata = new ArrayList<>();
    List<String> keys = getKeys(matrixConfig);
    fetchCombinations(new LinkedHashMap<>(), matrixConfig.getAxes(), matrixConfig.getExpressionAxes(), combinations,
        ParameterField.isBlank(matrixConfig.getExclude()) ? null : matrixConfig.getExclude().getValue(), matrixMetadata,
        keys, 0, new LinkedList<>());
    int totalCount = combinations.size();
    if (totalCount == 0) {
      throw new InvalidRequestException(
          "Total number of iterations found to be 0 for this strategy. Please check pipeline yaml");
    }

    if (maxExpansionLimit.isPresent()) {
      if (totalCount > maxExpansionLimit.get()) {
        throw new InvalidYamlException("Iteration count is beyond the supported limit of " + maxExpansionLimit.get());
      }
    }

    List<JsonNode> jsonNodes = new ArrayList<>();
    int currentIteration = 0;
    for (List<Integer> matrixData : matrixMetadata) {
      JsonNode clonedNode = JsonPipelineUtils.asTree(JsonUtils.asMap(StrategyUtils.replaceExpressions(
          jsonNode.deepCopy().toString(), combinations.get(currentIteration), currentIteration, totalCount, null)));
      StrategyUtils.modifyJsonNode(clonedNode, matrixData.stream().map(String::valueOf).collect(Collectors.toList()));
      jsonNodes.add(clonedNode);
      currentIteration++;
    }
    int maxConcurrency = jsonNodes.size();
    if (!ParameterField.isBlank(matrixConfig.getMaxConcurrency())) {
      maxConcurrency = matrixConfig.getMaxConcurrency().getValue();
    }
    return StrategyInfo.builder().expandedJsonNodes(jsonNodes).maxConcurrency(maxConcurrency).build();
  }
  /**
   *
   * This function is used to recursively calculate the number of combinations that can be there for the
   * given matrix configuration in the yaml.
   * It takes care of excluding a given combination.
   *
   * @param currentCombinationRef - Reference variable to store the current combination
   * @param axes - The axes defined in the yaml
   * @param expressionAxes - The axes defined in the yaml
   * @param combinationsRef - The number of total combinations that are there till now
   * @param exclude - exclude as mentioned in the yaml
   * @param matrixMetadataRef - The metadata related to the combination number of values
   * @param keys - The list of keys as mentioned in the yaml under axes
   * @param index -  the current index of the key we are on
   * @param indexPath - the path till the current iteration for the indexes like [0,2,1] i.e the matrix combination
   *     index
   */
  private void fetchCombinations(Map<String, String> currentCombinationRef, Map<String, AxisConfig> axes,
      Map<String, ExpressionAxisConfig> expressionAxes, List<Map<String, String>> combinationsRef,
      List<ExcludeConfig> exclude, List<List<Integer>> matrixMetadataRef, List<String> keys, int index,
      List<Integer> indexPath) {
    if (shouldExclude(exclude, currentCombinationRef)) {
      return;
    }
    // This means we have traversed till the end therefore add it as part of matrix combination
    if (keys.size() == index) {
      combinationsRef.add(new HashMap<>(currentCombinationRef));
      // Add the path we chose to compute the current combination.
      matrixMetadataRef.add(new ArrayList<>(indexPath));
      return;
    }

    String key = keys.get(index);
    /*
     * There are 3 cases which can happen over here:
     * 1. If a key is present in axes then we will treat value for this key as primitive
     * 2.If a key is present in expressionAxes then it can be either the string or the object that is stored as value
     */
    if (axes.containsKey(key)) {
      handleAxes(key, currentCombinationRef, axes, expressionAxes, combinationsRef, exclude, matrixMetadataRef, keys,
          index, indexPath);
    } else if (expressionAxes.containsKey(key)) {
      handleExpression(key, currentCombinationRef, axes, expressionAxes, combinationsRef, exclude, matrixMetadataRef,
          keys, index, indexPath);
    }
  }

  private boolean shouldExclude(List<ExcludeConfig> exclude, Map<String, String> currentCombinationRef) {
    if (exclude == null) {
      return false;
    }
    for (ExcludeConfig excludeConfig : exclude) {
      Set<String> excludeKeySet = excludeConfig.getExclude().keySet();
      int count = 0;
      for (String key : excludeKeySet) {
        if (!currentCombinationRef.containsKey(key)) {
          return false;
        }
        if (currentCombinationRef.get(key).equals(excludeConfig.getExclude().get(key))) {
          count++;
        }
      }
      if (count == excludeKeySet.size()) {
        return true;
      }
    }
    return false;
  }

  private void handleAxes(String key, Map<String, String> currentCombinationRef, Map<String, AxisConfig> primitiveAxes,
      Map<String, ExpressionAxisConfig> expressionAxes, List<Map<String, String>> combinationsRef,
      List<ExcludeConfig> exclude, List<List<Integer>> matrixMetadataRef, List<String> keys, int index,
      List<Integer> indexPath) {
    AxisConfig axisValues = primitiveAxes.get(key);
    int i = 0;
    // If value is null in one of the axis, then there are two cases:
    // Either null was provided at the start in the pipeline or an expression was present which we were not able to
    // resolve.
    if (axisValues.getAxisValue().getValue() == null) {
      throw new InvalidYamlException("Expected List but found null value in one of the axis with key:" + key);
    }
    for (String value : axisValues.getAxisValue().getValue()) {
      currentCombinationRef.put(key, value);
      indexPath.add(i);
      fetchCombinations(currentCombinationRef, primitiveAxes, expressionAxes, combinationsRef, exclude,
          matrixMetadataRef, keys, index + 1, indexPath);
      currentCombinationRef.remove(key);
      indexPath.remove(indexPath.size() - 1);
      i++;
    }
  }

  private void handleExpression(String key, Map<String, String> currentCombinationRef, Map<String, AxisConfig> axes,
      Map<String, ExpressionAxisConfig> expressionAxisConfigMap, List<Map<String, String>> combinationsRef,
      List<ExcludeConfig> exclude, List<List<Integer>> matrixMetadataRef, List<String> keys, int index,
      List<Integer> indexPath) {
    ExpressionAxisConfig axisValues = expressionAxisConfigMap.get(key);
    if (axisValues.getExpression().getValue() == null) {
      throw new InvalidYamlException(
          "Unable to resolve expression defined as value in matrix axis. Please ensure that the expression is correct");
    }
    Object value = axisValues.getExpression().getValue();
    if (!(value instanceof List)) {
      throw new InvalidYamlException(
          "Expression provided did not resolve into a list of string/objects. Please ensure that expression is correct");
    }

    int i = 0;
    for (Object val : (List<Object>) value) {
      if (val instanceof Map) {
        currentCombinationRef.put(key, JsonUtils.asJson(val));
      } else if (val instanceof String) {
        currentCombinationRef.put(key, (String) val);
      } else {
        throw new InvalidRequestException("Either Map or String expected. Found unknown");
      }
      indexPath.add(i);
      fetchCombinations(currentCombinationRef, axes, expressionAxisConfigMap, combinationsRef, exclude,
          matrixMetadataRef, keys, index + 1, indexPath);
      currentCombinationRef.remove(key);
      indexPath.remove(indexPath.size() - 1);
      i++;
    }
  }

  private List<String> getKeys(MatrixConfig matrixConfig) {
    List<String> keys = new LinkedList<>(matrixConfig.getAxes().keySet());
    keys.addAll(matrixConfig.getExpressionAxes().keySet());
    return keys;
  }
}
