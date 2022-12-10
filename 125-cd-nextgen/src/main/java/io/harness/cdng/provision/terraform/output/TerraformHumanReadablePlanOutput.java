/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.provision.terraform.output;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.sdk.core.data.ExecutionSweepingOutput;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
@OwnedBy(HarnessTeam.CDP)
public class TerraformHumanReadablePlanOutput implements ExecutionSweepingOutput {
  private static final String OUTPUT_NAME_FORMAT = "%s_planHumanReadable";

  private String provisionerIdentifier;
  private String tfPlanFileId;
  private String tfPlanFileBucket;

  public static String getOutputName(String provisionerIdentifier) {
    return String.format(OUTPUT_NAME_FORMAT, provisionerIdentifier);
  }

  public static String getOutputName(String stepFqn, String provisionerIdentifier) {
    return stepFqn + "." + getOutputName(provisionerIdentifier);
  }
}
