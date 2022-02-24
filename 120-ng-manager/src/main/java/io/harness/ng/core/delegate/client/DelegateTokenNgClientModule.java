package io.harness.ng.core.delegate.client;

import static io.harness.annotations.dev.HarnessTeam.DEL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.security.ServiceTokenGenerator;
import io.harness.serializer.kryo.KryoConverterFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;

@OwnedBy(DEL)
public class DelegateTokenNgClientModule extends AbstractModule {
  private final ServiceHttpClientConfig serviceHttpClientConfig;
  private final String serviceSecret;
  private final String clientId;

  public DelegateTokenNgClientModule(
      ServiceHttpClientConfig serviceHttpClientConfig, String serviceSecret, String clientId) {
    this.serviceHttpClientConfig = serviceHttpClientConfig;
    this.serviceSecret = serviceSecret;
    this.clientId = clientId;
  }

  @Provides
  private DelegateTokenNgClientFactory delegateTokenNgClientFactory(KryoConverterFactory kryoConverterFactory) {
    return new DelegateTokenNgClientFactory(
        this.serviceHttpClientConfig, this.serviceSecret, new ServiceTokenGenerator(), kryoConverterFactory, clientId);
  }

  @Override
  protected void configure() {
    this.bind(DelegateTokenNgClient.class).toProvider(DelegateTokenNgClientFactory.class).in(Scopes.SINGLETON);
  }
}
