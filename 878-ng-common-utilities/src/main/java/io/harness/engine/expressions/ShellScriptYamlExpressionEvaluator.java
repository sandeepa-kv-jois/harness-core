/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.expressions;
import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.expression.EngineExpressionEvaluator;
import io.harness.expression.ExpressionEvaluatorUtils;
import io.harness.expression.ResolveObjectResponse;
import io.harness.expression.common.ExpressionMode;
import io.harness.ng.core.template.TemplateEntityConstants;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;

import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.List;
import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PL)
@Value
@Builder
@Slf4j
public class ShellScriptYamlExpressionEvaluator extends EngineExpressionEvaluator {
  protected final String yaml;

  private int functorToken;
  private static final String YAML_EXPRESSION_PREFIX = "__yamlExpression";
  private static final String SECRETS_PREFIX = "secrets";
  private static final String YAML_EXPRESSION_CONNECTOR_PREFIX = "__yamlExpression.connector";
  private static final String CONNECTOR_ROOT_FIELD = "connector";
  private static final String SPEC_FIELD = "spec";

  public ShellScriptYamlExpressionEvaluator(String yaml, int functorToken) {
    super(null);
    this.yaml = yaml;
    this.functorToken = functorToken;
  }

  @Override
  protected void initialize() {
    super.initialize();
    addStaticAlias(TemplateEntityConstants.SECRET_MANAGER_ROOT_FIELD, SPEC_FIELD);
    // Add Shell Script Yaml Expression Functor
    addToContext(YAML_EXPRESSION_PREFIX,
        ShellScriptYamlExpressionFunctor.builder().rootYamlField(getShellScriptYamlField()).build());
    // Add secret functor
    addToContext(SECRETS_PREFIX, new SecretFunctor(functorToken));
  }

  @Override
  protected List<String> fetchPrefixes() {
    ImmutableList.Builder<String> listBuilder = ImmutableList.builder();
    return listBuilder.add(YAML_EXPRESSION_PREFIX)
        .add(YAML_EXPRESSION_CONNECTOR_PREFIX)
        .addAll(super.fetchPrefixes())
        .build();
  }

  private YamlField getShellScriptYamlField() {
    try {
      YamlField yamlField = YamlUtils.readTree(yaml);
      return yamlField.getNode().getField(CONNECTOR_ROOT_FIELD);
    } catch (IOException e) {
      log.error("Connector root field is not present in " + yaml + "\n Exception is " + e);
      throw new InvalidRequestException("Not valid yaml passed. Root field should be " + CONNECTOR_ROOT_FIELD);
    }
  }

  @Override
  public Object resolve(Object o, boolean skipUnresolvedExpressionsCheck) {
    ExpressionMode expressionMode = skipUnresolvedExpressionsCheck ? ExpressionMode.RETURN_NULL_IF_UNRESOLVED
                                                                   : ExpressionMode.THROW_EXCEPTION_IF_UNRESOLVED;
    return ExpressionEvaluatorUtils.updateExpressions(o, new ShellScriptFunctorImpl(this, expressionMode));
  }

  public static class ShellScriptFunctorImpl extends ResolveFunctorImpl {
    public ShellScriptFunctorImpl(EngineExpressionEvaluator expressionEvaluator, ExpressionMode expressionMode) {
      super(expressionEvaluator, expressionMode);
    }

    @Override
    public ResolveObjectResponse processObject(Object o) {
      if (!(o instanceof ParameterField)) {
        return new ResolveObjectResponse(false, null);
      }

      ParameterField<?> parameterField = (ParameterField<?>) o;
      if (!parameterField.isExpression()) {
        return new ResolveObjectResponse(false, null);
      }

      String processedExpressionValue = processString(parameterField.getExpressionValue());
      parameterField.updateWithValue(processedExpressionValue);

      return new ResolveObjectResponse(true, parameterField);
    }
  }
}