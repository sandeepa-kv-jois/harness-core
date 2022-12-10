/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import io.harness.cvng.core.services.api.FeatureFlagService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AlwaysFalseFeatureFlagServiceImpl implements FeatureFlagService {
  @Override
  public boolean isFeatureFlagEnabled(String accountId, String name) {
    return false;
  }

  @Override
  public boolean isGlobalFlagEnabled(String name) {
    return false;
  }
}
