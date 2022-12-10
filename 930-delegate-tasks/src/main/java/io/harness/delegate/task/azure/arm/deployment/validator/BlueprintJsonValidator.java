/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.azure.arm.deployment.validator;

import static io.harness.azure.model.AzureConstants.BLUEPRINT_JSON_FILE_BLANK_VALIDATION_MSG;

import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.delegate.task.azure.common.validator.Validator;
import io.harness.exception.runtime.azure.AzureBPDeploymentException;
import io.harness.serializer.JsonUtils;

public class BlueprintJsonValidator implements Validator<String> {
  @Override
  public void validate(String blueprintJson) {
    isValidJson(blueprintJson);
  }

  private void isValidJson(final String blueprintJson) {
    if (isBlank(blueprintJson)) {
      throw new AzureBPDeploymentException(BLUEPRINT_JSON_FILE_BLANK_VALIDATION_MSG);
    }

    try {
      JsonUtils.readTree(blueprintJson);
    } catch (Exception e) {
      throw new AzureBPDeploymentException("Invalid Blueprint JSON file", e);
    }
  }
}
