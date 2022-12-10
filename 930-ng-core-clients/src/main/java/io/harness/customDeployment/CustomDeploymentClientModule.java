/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.customDeployment;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.customDeployment.remote.CustomDeploymentClientHttpFactory;
import io.harness.customDeployment.remote.CustomDeploymentResourceClient;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.security.ServiceTokenGenerator;
import io.harness.serializer.kryo.KryoConverterFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;

@OwnedBy(CDP)
public class CustomDeploymentClientModule extends AbstractModule {
  private final ServiceHttpClientConfig customDeploymentClientConfig;
  private final String serviceSecret;
  private final String clientId;

  @Inject
  public CustomDeploymentClientModule(
      ServiceHttpClientConfig customDeploymentClientConfig, String serviceSecret, String clientId) {
    this.customDeploymentClientConfig = customDeploymentClientConfig;
    this.serviceSecret = serviceSecret;
    this.clientId = clientId;
  }

  @Provides
  @Singleton
  private CustomDeploymentClientHttpFactory customDeploymentClientHttpFactory(
      KryoConverterFactory kryoConverterFactory) {
    return new CustomDeploymentClientHttpFactory(this.customDeploymentClientConfig, this.serviceSecret,
        new ServiceTokenGenerator(), kryoConverterFactory, clientId);
  }

  @Override
  protected void configure() {
    this.bind(CustomDeploymentResourceClient.class)
        .toProvider(CustomDeploymentClientHttpFactory.class)
        .in(Scopes.SINGLETON);
  }
}
