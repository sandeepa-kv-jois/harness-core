/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.serializer;

import static java.lang.String.format;

import io.harness.beans.FeatureName;
import io.harness.beans.serializer.RunTimeInputHandler;
import io.harness.beans.yaml.extended.CIShellType;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class SerializerUtils {
  public static List<String> getEntrypoint(ParameterField<CIShellType> parametrizedShellType) {
    List<String> entrypoint;
    CIShellType shellType = RunTimeInputHandler.resolveShellType(parametrizedShellType);
    if (shellType == CIShellType.SH) {
      entrypoint = Arrays.asList("sh", "-c");
    } else if (shellType == CIShellType.BASH) {
      entrypoint = Arrays.asList("bash", "-c");
    } else if (shellType == CIShellType.POWERSHELL) {
      entrypoint = Arrays.asList("powershell", "-Command");
    } else if (shellType == CIShellType.PWSH) {
      entrypoint = Arrays.asList("pwsh", "-Command");
    } else {
      throw new CIStageExecutionException(format("Invalid shell type: %s", shellType));
    }
    return entrypoint;
  }

  public static String getEarlyExitCommand(ParameterField<CIShellType> parametrizedShellType) {
    String cmd;
    CIShellType shellType = RunTimeInputHandler.resolveShellType(parametrizedShellType);
    if (shellType == CIShellType.SH || shellType == CIShellType.BASH) {
      cmd = "set -xe; ";
    } else if (shellType == CIShellType.POWERSHELL || shellType == CIShellType.PWSH) {
      cmd = "$ErrorActionPreference = 'Stop' \n";
    } else {
      throw new CIStageExecutionException(format("Invalid shell type: %s", shellType));
    }
    return cmd;
  }

  public static String convertJsonNodeToString(String key, JsonNode jsonNode) {
    try {
      YamlUtils.removeUuid(jsonNode);
      if (jsonNode.isValueNode()) {
        return jsonNode.asText("");
      } else if (jsonNode.isArray() && isPrimitiveArray(jsonNode)) {
        ArrayNode arrayNode = (ArrayNode) jsonNode;
        List<String> strValues = new ArrayList<>();
        for (JsonNode node : arrayNode) {
          strValues.add(node.asText(""));
        }

        return String.join(",", strValues);
      } else {
        ObjectWriter ow = new ObjectMapper().writer().withDefaultPrettyPrinter();
        return ow.writeValueAsString(jsonNode);
      }
    } catch (Exception ex) {
      throw new CIStageExecutionException(String.format("Invalid setting attribute %s value", key));
    }
  }

  public static String convertMapToJsonString(Map<String, String> m) {
    try {
      ObjectWriter ow = new ObjectMapper().writer().withDefaultPrettyPrinter();
      return ow.writeValueAsString(m);
    } catch (Exception ex) {
      throw new CIStageExecutionException(String.format("Invalid setting %s", m));
    }
  }

  // Return whether array contains only value node or not.
  private static boolean isPrimitiveArray(JsonNode jsonNode) {
    ArrayNode arrayNode = (ArrayNode) jsonNode;
    for (JsonNode e : arrayNode) {
      if (!e.isValueNode()) {
        return false;
      }
    }
    return true;
  }

  public static String getSafeGitDirectoryCmd(
      CIShellType shellType, String accountId, CIFeatureFlagService featureFlagService) {
    // This adds the safe directory to the end of .gitconfig file based on FF
    if (featureFlagService.isEnabled(FeatureName.CI_DISABLE_GIT_SAFEDIR, accountId)) {
      return "";
    } else {
      String safeDirScript;
      if (shellType == CIShellType.SH || shellType == CIShellType.BASH) {
        safeDirScript = "set +x\n"
            + "if [ -x \"$(command -v git)\" ]; then\n"
            + "  git config --global --add safe.directory '*' || true \n"
            + "fi\n"
            + "set -x\n";
      } else {
        safeDirScript = "try\n"
            + "{\n"
            + "    git config --global --add safe.directory '*' | Out-Null\n"
            + "}\n"
            + "catch [System.Management.Automation.CommandNotFoundException]\n"
            + "{\n }\n";
      }
      return safeDirScript;
    }
  }

  public static String getTestSplitStrategy(String splitStrategy) {
    switch (splitStrategy) {
      case "TestCount":
        return "test_count";
      case "ClassTiming":
        return "class_timing";
      default:
        return "";
    }
  }
}