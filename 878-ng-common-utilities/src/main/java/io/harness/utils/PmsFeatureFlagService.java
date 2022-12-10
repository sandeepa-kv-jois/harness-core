/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.utils;

import io.harness.beans.FeatureName;

import javax.validation.constraints.NotNull;

public interface PmsFeatureFlagService {
  boolean isEnabled(String accountId, @NotNull FeatureName featureName);

  boolean isEnabled(String accountId, @NotNull String featureName);
}
