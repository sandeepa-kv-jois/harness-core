/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.clients;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.remote.client.AbstractHttpClientFactory;
import io.harness.remote.client.ClientMode;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.security.ServiceTokenGenerator;
import io.harness.serializer.kryo.KryoConverterFactory;

import com.google.inject.Provider;
import com.google.inject.Singleton;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;

@OwnedBy(HarnessTeam.PL)
@FieldDefaults(level = AccessLevel.PRIVATE)
@Singleton
public class AccessControlHttpClientFactory
    extends AbstractHttpClientFactory implements Provider<AccessControlHttpClient> {
  public AccessControlHttpClientFactory(ServiceHttpClientConfig secretManagerConfig, String serviceSecret,
      ServiceTokenGenerator tokenGenerator, KryoConverterFactory kryoConverterFactory, String clientId,
      boolean enableCircuitBreaker, ClientMode clientMode) {
    super(secretManagerConfig, serviceSecret, tokenGenerator, kryoConverterFactory, clientId, enableCircuitBreaker,
        clientMode);
  }

  @Override
  public AccessControlHttpClient get() {
    return getRetrofit().create(AccessControlHttpClient.class);
  }
}
