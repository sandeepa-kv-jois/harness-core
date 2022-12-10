/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.provision.terraform.outcome;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.sdk.core.data.Outcome;

import lombok.Builder;
import lombok.Data;

@OwnedBy(HarnessTeam.CDP)
@Data
@Builder
@RecasterAlias("io.harness.cdng.provision.terraform.outcome.TerraformPlanOutcome")
public class TerraformPlanOutcome implements Outcome {
  public static final String OUTCOME_NAME = "plan";

  Integer detailedExitCode;
  String jsonFilePath;
  String humanReadableFilePath;
}
