/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.merger.helpers;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.pms.yaml.YamlNode.UUID_FIELD_NAME;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.merger.fqn.FQN;
import io.harness.pms.merger.fqn.FQNNode;
import io.harness.pms.yaml.YAMLFieldNameConstants;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import lombok.experimental.UtilityClass;

@OwnedBy(PIPELINE)
@UtilityClass
public class FQNMapGenerator {
  public Map<FQN, Object> generateFQNMap(JsonNode yamlMap) {
    return generateFQNMap(yamlMap, false);
  }

  public Map<FQN, Object> generateFQNMap(JsonNode yamlMap, boolean keepUuidFields) {
    HashSet<String> expressions = new HashSet<>();
    Set<String> fieldNames = new LinkedHashSet<>();
    yamlMap.fieldNames().forEachRemaining(fieldNames::add);
    String topKey = fieldNames.iterator().next();

    if (keepUuidFields && topKey.equals(UUID_FIELD_NAME) && fieldNames.size() > 1) {
      topKey = fieldNames.stream().filter(o -> !o.equals(UUID_FIELD_NAME)).findAny().get();
    }
    FQNNode startNode = FQNNode.builder().nodeType(FQNNode.NodeType.KEY).key(topKey).build();
    FQN currentFQN = FQN.builder().fqnList(Collections.singletonList(startNode)).build();

    Map<FQN, Object> res = new LinkedHashMap<>();

    generateFQNMap(yamlMap.get(topKey), currentFQN, res, expressions, keepUuidFields);
    return res;
  }

  public void generateFQNMap(
      JsonNode map, FQN baseFQN, Map<FQN, Object> res, HashSet<String> expressions, boolean keepUuidFields) {
    Set<String> fieldNames = new LinkedHashSet<>();
    map.fieldNames().forEachRemaining(fieldNames::add);
    for (String key : fieldNames) {
      JsonNode value = map.get(key);
      FQN currFQN = FQN.duplicateAndAddNode(baseFQN, FQNNode.builder().nodeType(FQNNode.NodeType.KEY).key(key).build());
      if (value.getNodeType() == JsonNodeType.ARRAY) {
        if (value.size() == 0) {
          FQNHelper.validateUniqueFqn(currFQN, value, res, expressions);
          continue;
        }
        ArrayNode arrayNode = (ArrayNode) value;
        generateFQNMapFromList(arrayNode, currFQN, res, expressions, keepUuidFields);
      } else if (value.getNodeType() == JsonNodeType.OBJECT) {
        if (value.size() == 0) {
          FQNHelper.validateUniqueFqn(currFQN, value, res, expressions);
          continue;
        }
        generateFQNMap(value, currFQN, res, expressions, keepUuidFields);
      } else {
        FQNHelper.validateUniqueFqn(currFQN, value, res, expressions);
      }
    }
  }

  public void generateFQNMapFromList(
      ArrayNode list, FQN baseFQN, Map<FQN, Object> res, HashSet<String> expressions, boolean keepUuidFields) {
    if (list == null || list.get(0) == null) {
      FQNHelper.validateUniqueFqn(baseFQN, list, res, expressions);
      return;
    }
    JsonNode firstNode = list.get(0);
    if (firstNode.getNodeType() != JsonNodeType.OBJECT) {
      FQNHelper.validateUniqueFqn(baseFQN, list, res, expressions);
      return;
    }

    // Remove __uuid key if it contains in the json object
    if (!keepUuidFields && firstNode.isObject() && firstNode.get(UUID_FIELD_NAME) != null) {
      ObjectNode objectNode = (ObjectNode) firstNode;
      objectNode.remove(UUID_FIELD_NAME);
      firstNode = objectNode;
    }
    int noOfKeys = firstNode.size();
    // Taking decision based on noOfKeys assumes that if there is only one key in element then it will always have only
    // one. And current node does not have any meaningful information and all information is inside the child of current
    // element. That is not true anymore.
    // TODO: Simplify this logic.
    if (noOfKeys == 1 && EmptyPredicate.isEmpty(FQNHelper.getUuidKey(list))) {
      generateFQNMapFromListOfSingleKeyMaps(list, baseFQN, res, expressions, keepUuidFields);
    } else {
      generateFQNMapFromListOfMultipleKeyMaps(list, baseFQN, res, expressions, keepUuidFields);
    }
  }

  public void generateFQNMapFromListOfSingleKeyMaps(
      ArrayNode list, FQN baseFQN, Map<FQN, Object> res, HashSet<String> expressions, boolean keepUuidFields) {
    if (FQNHelper.checkIfListHasNoIdentifier(list)) {
      FQNHelper.validateUniqueFqn(baseFQN, list, res, expressions);
      return;
    }
    list.forEach(element -> {
      if (element.has(YAMLFieldNameConstants.PARALLEL)) {
        FQN currFQN = FQN.duplicateAndAddNode(baseFQN, FQNNode.builder().nodeType(FQNNode.NodeType.PARALLEL).build());
        ArrayNode listOfMaps = (ArrayNode) element.get(YAMLFieldNameConstants.PARALLEL);
        generateFQNMapFromList(listOfMaps, currFQN, res, expressions, keepUuidFields);
      } else {
        Set<String> fieldNames = new LinkedHashSet<>();
        element.fieldNames().forEachRemaining(fieldNames::add);
        String topKey = fieldNames.iterator().next();
        JsonNode innerMap = element.get(topKey);
        String identifier = innerMap.get(YAMLFieldNameConstants.IDENTIFIER).asText();
        FQN currFQN = FQN.duplicateAndAddNode(baseFQN,
            FQNNode.builder()
                .nodeType(FQNNode.NodeType.KEY_WITH_UUID)
                .key(topKey)
                .uuidKey(YAMLFieldNameConstants.IDENTIFIER)
                .uuidValue(identifier)
                .build());
        generateFQNMap(innerMap, currFQN, res, expressions, keepUuidFields);
      }
    });
  }

  public void generateFQNMapFromListOfMultipleKeyMaps(
      ArrayNode list, FQN baseFQN, Map<FQN, Object> res, HashSet<String> expressions, boolean keepUuidFields) {
    String uuidKey = FQNHelper.getUuidKey(list);
    if (EmptyPredicate.isEmpty(uuidKey)) {
      FQNHelper.validateUniqueFqn(baseFQN, list, res, expressions);
      return;
    }

    list.forEach(element -> {
      JsonNode jsonNode = element.get(uuidKey);
      if (jsonNode == null) {
        throw new InvalidRequestException("Invalid Yaml found");
      }
      FQN currFQN = FQN.duplicateAndAddNode(baseFQN,
          FQNNode.builder().nodeType(FQNNode.NodeType.UUID).uuidKey(uuidKey).uuidValue(jsonNode.asText()).build());
      if (FQNHelper.isKeyInsideUUIdsToIdentityElementInList(uuidKey)) {
        generateFQNMap(element, currFQN, res, expressions, keepUuidFields);
      } else {
        Set<String> fieldNames = new LinkedHashSet<>();
        element.fieldNames().forEachRemaining(fieldNames::add);
        for (String key : fieldNames) {
          FQN finalFQN =
              FQN.duplicateAndAddNode(currFQN, FQNNode.builder().nodeType(FQNNode.NodeType.KEY).key(key).build());
          FQNHelper.validateUniqueFqn(finalFQN, element.get(key), res, expressions);
        }
      }
    });
  }
}
