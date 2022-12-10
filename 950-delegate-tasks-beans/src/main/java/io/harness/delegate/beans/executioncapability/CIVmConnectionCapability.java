/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.executioncapability;

import io.harness.delegate.beans.ci.CIInitializeTaskParams;

import java.time.Duration;
import lombok.Builder;
import lombok.Getter;
import lombok.Value;

@Value
@Builder
public class CIVmConnectionCapability implements ExecutionCapability {
  private final CapabilityType capabilityType = CapabilityType.CI_VM;

  private String poolId;
  private String stageRuntimeId;
  //@Getter private InfraInfo infraInfo;
  @Getter private CIInitializeTaskParams.Type infraInfo;

  @Override
  public EvaluationMode evaluationMode() {
    return EvaluationMode.AGENT;
  }

  @Override
  public String fetchCapabilityBasis() {
    //    return infraInfo.fetchCapabilityBasis();
    if (infraInfo == CIInitializeTaskParams.Type.VM) {
      return String.format("%s-%s", poolId, stageRuntimeId);
    } else {
      return String.format("%s", stageRuntimeId);
    }
  }

  @Override
  public Duration getMaxValidityPeriod() {
    return Duration.ofHours(6);
  }

  @Override
  public Duration getPeriodUntilNextValidation() {
    return Duration.ofHours(4);
  }
}
