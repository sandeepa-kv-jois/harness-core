/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(
    name = "StepCategory", description = "This contains step category name, step data and internal step categories.")
public class StepCategory {
  String name;
  @Builder.Default List<StepData> stepsData = new ArrayList<>();
  @Builder.Default List<StepCategory> stepCategories = new ArrayList<>();

  public void addStepData(StepData stepData) {
    stepsData.add(stepData);
  }

  public void addStepCategory(StepCategory stepCategory) {
    stepCategories.add(stepCategory);
  }

  public StepCategory getOrCreateChildStepCategory(String name) {
    Optional<StepCategory> stepCategoryOptional =
        stepCategories.stream().filter(category -> category.getName().equalsIgnoreCase(name)).findFirst();
    if (stepCategoryOptional.isPresent()) {
      return stepCategoryOptional.get();
    }
    StepCategory stepCategory = StepCategory.builder().name(name).build();
    addStepCategory(stepCategory);
    return stepCategory;
  }
}
