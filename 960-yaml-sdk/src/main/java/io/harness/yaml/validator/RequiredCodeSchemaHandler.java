/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.yaml.validator;

import io.harness.yaml.utils.SchemaValidationUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.ValidationMessage;
import java.util.ArrayList;
import java.util.List;

public class RequiredCodeSchemaHandler {
  private static final String TYPE_SCHEMA_NODE = "type";
  private static final String TEMPLATE_SCHEMA_NODE = "template";
  public List<ValidationMessage> handle(List<ValidationMessage> validationMessages, JsonNode jsonNode) {
    return handleTemplateField(validationMessages, jsonNode);
  }
  private List<ValidationMessage> handleTemplateField(List<ValidationMessage> validationMessages, JsonNode jsonNode) {
    List<ValidationMessage> filteredValidationMessages = new ArrayList<>();

    // If schema is complaining that template field is required and not provided But yaml contains type field. Then
    // it's not a template node but a simple node. Then skip the validation message.
    for (ValidationMessage validationMessage : validationMessages) {
      if (validationMessage.getArguments().length == 1
          && validationMessage.getArguments()[0].equals(TEMPLATE_SCHEMA_NODE)) {
        JsonNode problematicNode = SchemaValidationUtils.parseJsonNodeByPath(validationMessage, jsonNode);
        if (problematicNode.get(TYPE_SCHEMA_NODE) != null) {
          continue;
        }
      }
      filteredValidationMessages.add(validationMessage);
    }
    return filteredValidationMessages;
  }
}
