/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.yaml.utils;

import static io.harness.rule.OwnerRule.HINGER;
import static io.harness.rule.OwnerRule.PRASHANTSHARMA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.encryption.SecretRefData;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.yaml.core.variables.NGVariable;
import io.harness.yaml.core.variables.SecretNGVariable;
import io.harness.yaml.core.variables.StringNGVariable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PIPELINE)
public class NGVariableUtilsTest extends CategoryTest {
  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testFetchSecretExpression() {
    String secretValueInExpression = "<+pipeline.name>";
    String expectedSecretExpression = NGVariablesUtils.fetchSecretExpression(secretValueInExpression);
    assertThat(expectedSecretExpression).isEqualTo("<+secrets.getValue(" + secretValueInExpression + ")>");

    String secretValueConstant = "secretValue";
    expectedSecretExpression = NGVariablesUtils.fetchSecretExpression(secretValueConstant);
    assertThat(expectedSecretExpression).isEqualTo("<+secrets.getValue(\"" + secretValueConstant + "\")>");
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void fetchSecretExpressionWithExpressionToken() {
    Long expressionToken = 1234L;
    String secretValueInExpression = "<+pipeline.name>";
    String expectedSecretExpression =
        NGVariablesUtils.fetchSecretExpressionWithExpressionToken(secretValueInExpression, expressionToken);
    assertThat(expectedSecretExpression).isEqualTo("${ngSecretManager.obtain(<+pipeline.name>, 1234)}");

    String secretValueConstant = "secretValue";
    expectedSecretExpression =
        NGVariablesUtils.fetchSecretExpressionWithExpressionToken(secretValueConstant, expressionToken);
    assertThat(expectedSecretExpression)
        .isEqualTo("${ngSecretManager.obtain(\"" + secretValueConstant + "\", " + expressionToken + ")}");
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testGetSetOfSecretVars() {
    Set<String> secretVars = NGVariablesUtils.getSetOfSecretVars(null);
    assertThat(secretVars).isNotNull();

    List<NGVariable> variableList = new ArrayList<>();
    secretVars = NGVariablesUtils.getSetOfSecretVars(variableList);
    assertThat(secretVars).isNotNull();

    variableList.add(StringNGVariable.builder().name("var1").value(ParameterField.createValueField("val1")).build());
    secretVars = NGVariablesUtils.getSetOfSecretVars(variableList);
    assertThat(secretVars).isNotNull();
    assertThat(secretVars.size()).isEqualTo(0);

    variableList.add(SecretNGVariable.builder()
                         .name("var2")
                         .value(ParameterField.createValueField(SecretRefData.builder().identifier("val1").build()))
                         .build());
    secretVars = NGVariablesUtils.getSetOfSecretVars(variableList);
    assertThat(secretVars).isNotNull();
    assertThat(secretVars).contains("var2");
  }
}
