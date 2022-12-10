/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.ci.vm;

import io.harness.delegate.beans.ci.CICleanupTaskParams;
import io.harness.delegate.beans.ci.CIInitializeTaskParams;
import io.harness.delegate.beans.executioncapability.CIVmConnectionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.expression.ExpressionEvaluator;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Collections;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@JsonIgnoreProperties(ignoreUnknown = true)
public class CIVmCleanupTaskParams implements CICleanupTaskParams, ExecutionCapabilityDemander {
  @NotNull private String stageRuntimeId;
  @NotNull private String poolId;
  //@Getter private InfraInfo infraInfo;
  @Getter private CIInitializeTaskParams.Type infraInfo;

  @Builder.Default private static final CICleanupTaskParams.Type type = CICleanupTaskParams.Type.VM;

  @Override
  public CICleanupTaskParams.Type getType() {
    return type;
  }

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    return Collections.singletonList(
        CIVmConnectionCapability.builder().poolId(poolId).stageRuntimeId(stageRuntimeId).infraInfo(infraInfo).build());
  }
}
