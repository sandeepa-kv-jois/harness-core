/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.steps;

import static io.harness.annotations.dev.HarnessTeam.CI;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.steps.StepType;

import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

/**
 * Each stepInfo will bind to some step
 * Reason for binding While using execution framework we have to give step type from stepInfo beans
 */
@Value
@Builder
@TypeAlias("typeInfo")
@OwnedBy(CI)
@RecasterAlias("io.harness.beans.steps.TypeInfo")
public class TypeInfo implements NonYamlInfo {
  @NotNull CIStepInfoType stepInfoType;
  @NotNull StepType stepType;
}
