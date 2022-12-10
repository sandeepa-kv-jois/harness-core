/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.iacmserviceclient;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.iacm.beans.entities.IACMServiceConfig;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Scopes;

@OwnedBy(HarnessTeam.IACM)
public class IACMServiceClientModule extends AbstractModule {
  IACMServiceConfig iacmServiceConfig;

  @Inject
  public IACMServiceClientModule(IACMServiceConfig iacmServiceConfig) {
    this.iacmServiceConfig = iacmServiceConfig;
  }

  @Override
  protected void configure() {
    this.bind(IACMServiceConfig.class).toInstance(this.iacmServiceConfig);
    this.bind(IACMServiceClient.class).toProvider(IACMServiceClientFactory.class).in(Scopes.SINGLETON);
  }
}
