/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.expressions;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.pms.data.PmsEngineExpressionService;
import io.harness.plan.ExpressionModeMapper;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.ExpressionMode;
import io.harness.pms.expression.EngineExpressionService;
import io.harness.pms.serializer.recaster.RecastOrchestrationUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;

@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
public class EngineExpressionServiceImpl implements EngineExpressionService {
  @Inject PmsEngineExpressionService pmsEngineExpressionService;

  @Override
  public String renderExpression(Ambiance ambiance, String expression, boolean skipUnresolvedExpressionsCheck) {
    return pmsEngineExpressionService.renderExpression(ambiance, expression, skipUnresolvedExpressionsCheck);
  }

  @Override
  public String renderExpression(Ambiance ambiance, String expression, ExpressionMode mode) {
    return pmsEngineExpressionService.renderExpression(
        ambiance, expression, ExpressionModeMapper.fromExpressionModeProto(mode));
  }

  @Override
  public Object evaluateExpression(Ambiance ambiance, String expression) {
    String json = pmsEngineExpressionService.evaluateExpression(ambiance, expression);
    Object result;
    try {
      result = RecastOrchestrationUtils.fromJson(json, Object.class);
    } catch (Exception e) {
      result = json;
    }
    return result;
  }
}
