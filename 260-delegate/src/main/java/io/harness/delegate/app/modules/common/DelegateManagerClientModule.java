/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.app.modules.common;

import io.harness.managerclient.DelegateAgentManagerClient;
import io.harness.managerclient.DelegateAgentManagerClientFactory;

import com.google.inject.AbstractModule;

public class DelegateManagerClientModule extends AbstractModule {
  @Override
  protected void configure() {
    bind(DelegateAgentManagerClient.class).toProvider(DelegateAgentManagerClientFactory.class);
  }
}
