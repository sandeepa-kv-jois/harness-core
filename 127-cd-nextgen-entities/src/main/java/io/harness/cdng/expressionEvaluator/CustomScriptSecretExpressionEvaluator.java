/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.expressionEvaluator;

import io.harness.expression.EngineExpressionEvaluator;

public class CustomScriptSecretExpressionEvaluator extends EngineExpressionEvaluator {
  private final String script;
  private final int secretFunctor;

  public CustomScriptSecretExpressionEvaluator(String script, int secretFunctor) {
    super(null);
    this.script = script;
    this.secretFunctor = secretFunctor;
  }

  @Override
  protected void initialize() {
    super.initialize();
    addToContext("secrets", new CustomSecretFunctor(secretFunctor));
  }
}
