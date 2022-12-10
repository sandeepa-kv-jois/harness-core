/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.config;

import io.harness.annotation.RecasterAlias;
import io.harness.execution.ExecutionServiceConfig;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TypeAlias("ciExecutionServiceConfig")
@RecasterAlias("io.harness.ci.config.CIExecutionServiceConfig")
public class CIExecutionServiceConfig extends ExecutionServiceConfig {
  String ciImageTag;
  CIStepConfig stepConfig;
  CICacheIntelligenceConfig cacheIntelligenceConfig;
  ExecutionLimits executionLimits;

  @Builder
  public CIExecutionServiceConfig(String addonImageTag, String liteEngineImageTag, String defaultInternalImageConnector,
      String delegateServiceEndpointVariableValue, Integer defaultMemoryLimit, Integer defaultCPULimit,
      Integer pvcDefaultStorageSize, String addonImage, String liteEngineImage, boolean isLocal, String ciImageTag,
      CIStepConfig stepConfig, CICacheIntelligenceConfig cacheIntelligenceConfig, ExecutionLimits executionLimits) {
    super(addonImageTag, liteEngineImageTag, defaultInternalImageConnector, delegateServiceEndpointVariableValue,
        defaultMemoryLimit, defaultCPULimit, pvcDefaultStorageSize, addonImage, liteEngineImage, isLocal);
    this.ciImageTag = ciImageTag;
    this.stepConfig = stepConfig;
    this.cacheIntelligenceConfig = cacheIntelligenceConfig;
    this.executionLimits = executionLimits;
  }
}
