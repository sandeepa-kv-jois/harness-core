/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.pipeline.executions.beans;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.environment.beans.EnvironmentType;
import io.harness.pms.sdk.execution.beans.PipelineModuleInfo;

import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;

@Data
@Builder
@RecasterAlias("io.harness.cdng.pipeline.executions.beans.CDPipelineModuleInfo")
@OwnedBy(HarnessTeam.CDP)
public class CDPipelineModuleInfo implements PipelineModuleInfo {
  @Singular private List<String> serviceIdentifiers;
  @Singular private List<String> envIdentifiers;
  @Singular private List<String> envGroupIdentifiers;
  @Singular private List<String> serviceDefinitionTypes;
  @Singular private List<EnvironmentType> environmentTypes;
  @Singular private List<String> infrastructureTypes;
  @Singular private List<String> infrastructureIdentifiers;
  @Singular private List<String> infrastructureNames;
  @Singular private List<String> freezeIdentifiers;
}
