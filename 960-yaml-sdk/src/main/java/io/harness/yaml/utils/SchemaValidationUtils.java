/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.yaml.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.ValidationMessage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;

@UtilityClass
public class SchemaValidationUtils {
  public String[] removeParenthesisFromArguments(String[] arguments) {
    List<String> cleanArguments = new ArrayList<>();
    int length = arguments.length;
    for (int index = 0; index < length; index++) {
      if (!arguments[index].equals("[]")) {
        cleanArguments.add(arguments[index].substring(1, arguments[index].length() - 1));
      }
    }
    return cleanArguments.toArray(new String[0]);
  }

  public Map<String, List<ValidationMessage>> getValidationMessageCodeMap(
      Collection<ValidationMessage> validationMessages) {
    Map<String, List<ValidationMessage>> codes = new HashMap<>();
    for (ValidationMessage validationMessage : validationMessages) {
      if (codes.containsKey(validationMessage.getCode())) {
        codes.get(validationMessage.getCode()).add(validationMessage);
      } else {
        List<ValidationMessage> validationMessageList = new ArrayList<>();
        validationMessageList.add(validationMessage);
        codes.put(validationMessage.getCode(), validationMessageList);
      }
    }
    return codes;
  }

  public Map<String, List<ValidationMessage>> getValidationPathMap(Collection<ValidationMessage> validationMessages) {
    Map<String, List<ValidationMessage>> pathMap = new HashMap<>();
    for (ValidationMessage validationMessage : validationMessages) {
      if (pathMap.containsKey(validationMessage.getPath())) {
        pathMap.get(validationMessage.getPath()).add(validationMessage);
      } else {
        List<ValidationMessage> validationMessageList = new ArrayList<>();
        validationMessageList.add(validationMessage);
        pathMap.put(validationMessage.getPath(), validationMessageList);
      }
    }
    return pathMap;
  }

  public JsonNode parseJsonNodeByPath(ValidationMessage validationMessage, JsonNode jsonNode) {
    JsonNode currentNode = jsonNode.deepCopy();
    String[] pathList = validationMessage.getPath().split("\\.");
    for (String path : pathList) {
      if (path.equals("$")) {
        continue;
      }
      char[] charSet = path.toCharArray();
      int index = 0;
      while (index < charSet.length) {
        if (charSet[index] == ']') {
          index++;
        } else if (charSet[index] == '[') {
          int endIndex = path.indexOf(']', index);
          // Selecting element in ArrayNode.
          currentNode = currentNode.get(Integer.parseInt(path.substring(index + 1, endIndex)));
          index = endIndex + 1;
        } else {
          int nextIndex = path.indexOf('[', index);
          if (nextIndex == -1) {
            nextIndex = path.length();
          }
          currentNode = currentNode.get(path.substring(index, nextIndex));
          index = nextIndex;
        }
      }
    }
    return currentNode;
  }
}
