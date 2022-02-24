/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.yaml.utils;

import static io.harness.rule.OwnerRule.BRIJESH;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.yaml.validator.EnumCodeSchemaHandler;

import com.networknt.schema.ValidationMessage;
import com.networknt.schema.ValidatorTypeCode;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class EnumCodeSchemaHandlerTest extends CategoryTest {
  private final EnumCodeSchemaHandler enumCodeSchemaHandler = new EnumCodeSchemaHandler();

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testHandle() {
    String path1 = "path1";
    String path2 = "path2";
    List<ValidationMessage> validationMessages = new ArrayList<>();
    validationMessages.add(ValidationMessage.of("type", ValidatorTypeCode.ENUM, path1, "[Http]"));
    validationMessages.add(ValidationMessage.of("type", ValidatorTypeCode.ENUM, path1, "[ShellScript]"));
    List<ValidationMessage> processValidationMessages = enumCodeSchemaHandler.handle(validationMessages);
    assertEquals(processValidationMessages.size(), 1);
    assertTrue(processValidationMessages.get(0).getArguments()[0].contains("Http"));
    assertTrue(processValidationMessages.get(0).getArguments()[0].contains("ShellScript"));
    // Arguments should not have Barrier.
    assertFalse(processValidationMessages.get(0).getArguments()[0].contains("Barrier"));
    validationMessages.add(ValidationMessage.of("type", ValidatorTypeCode.ENUM, path1, "[Barrier]"));
    processValidationMessages = enumCodeSchemaHandler.handle(validationMessages);
    assertEquals(processValidationMessages.size(), 1);
    assertTrue(processValidationMessages.get(0).getArguments()[0].contains("Http"));
    assertTrue(processValidationMessages.get(0).getArguments()[0].contains("ShellScript"));
    // Arguments should have Barrier now.
    assertTrue(processValidationMessages.get(0).getArguments()[0].contains("Barrier"));
    validationMessages.add(ValidationMessage.of("type", ValidatorTypeCode.ENUM, path2, "[Barrier]"));
    processValidationMessages = enumCodeSchemaHandler.handle(validationMessages);
    assertEquals(processValidationMessages.size(), 2);
  }
}
