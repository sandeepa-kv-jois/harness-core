/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.policy.step;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.pms.sdk.core.steps.io.StepResponse.StepOutcome;
import static io.harness.pms.sdk.core.steps.io.StepResponse.builder;

import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.ErrorCode;
import io.harness.eraro.Level;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureData;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;

import java.io.IOException;
import java.util.List;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PIPELINE)
@UtilityClass
@Slf4j
public class PolicyStepHelper {
  public String getPolicySetsStringForQueryParam(List<String> policySets) {
    return policySets.toString().replace("[", "").replace("]", "").replace(" ", "");
  }

  public boolean isInvalidPayload(String payload) {
    try {
      YamlField yamlField = YamlUtils.readTree(payload);
      // Policy manager does not support primitive values like strings or numbers. Arrays are also not supported
      return !yamlField.getNode().isObject();
    } catch (IOException e) {
      return true;
    }
  }

  public StepResponse buildPolicyEvaluationErrorStepResponse(String errorResponseString) {
    try {
      PolicyEvaluationErrorResponse policyEvaluationErrorResponse =
          YamlUtils.read(errorResponseString, PolicyEvaluationErrorResponse.class);
      String errorMessage = policyEvaluationErrorResponse.getMessage().replace("policy set", "Policy Set");
      return buildFailureStepResponse(ErrorCode.POLICY_SET_ERROR, errorMessage, FailureType.UNKNOWN_FAILURE);

    } catch (IOException e) {
      log.error("Unable to parse error response from Policy Manager. Error response:\n" + errorResponseString, e);
      return PolicyStepHelper.buildFailureStepResponse(ErrorCode.HTTP_RESPONSE_EXCEPTION,
          "Unexpected error occurred while evaluating Policies.", FailureType.APPLICATION_FAILURE);
    }
  }

  public StepResponse buildFailureStepResponse(ErrorCode errorCode, String message, FailureType failureType) {
    FailureData failureData = FailureData.newBuilder()
                                  .setCode(errorCode.name())
                                  .setLevel(Level.ERROR.name())
                                  .setMessage(message)
                                  .addFailureTypes(failureType)
                                  .build();
    FailureInfo failureInfo = FailureInfo.newBuilder().addFailureData(failureData).build();
    return builder().status(Status.FAILED).failureInfo(failureInfo).build();
  }

  public StepResponse buildFailureStepResponse(
      ErrorCode errorCode, String message, FailureType failureType, StepOutcome stepOutcome) {
    FailureData failureData = FailureData.newBuilder()
                                  .setCode(errorCode.name())
                                  .setLevel(Level.ERROR.name())
                                  .setMessage(message)
                                  .addFailureTypes(failureType)
                                  .build();
    FailureInfo failureInfo = FailureInfo.newBuilder().addFailureData(failureData).build();
    return builder().status(Status.FAILED).failureInfo(failureInfo).stepOutcome(stepOutcome).build();
  }
}
