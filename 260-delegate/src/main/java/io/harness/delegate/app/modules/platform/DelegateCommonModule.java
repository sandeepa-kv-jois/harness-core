/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.app.modules.platform;

import io.harness.delegate.configuration.DelegateConfiguration;
import io.harness.delegate.service.DelegateAgentService;
import io.harness.delegate.service.platform.DelegatePlatformService;
import io.harness.exception.KeyManagerBuilderException;
import io.harness.exception.TrustManagerBuilderException;
import io.harness.exception.exceptionmanager.exceptionhandler.ExceptionHandler;
import io.harness.security.X509KeyManagerBuilder;
import io.harness.security.X509TrustManagerBuilder;
import io.harness.time.TimeModule;
import io.harness.version.VersionModule;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.MapBinder;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import java.time.Clock;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.DefaultAsyncHttpClient;
import org.asynchttpclient.DefaultAsyncHttpClientConfig;

@RequiredArgsConstructor
@Slf4j
public class DelegateCommonModule extends AbstractModule {
  private final DelegateConfiguration configuration;

  @Provides
  @Singleton
  public DefaultAsyncHttpClient defaultAsyncHttpClient()
      throws TrustManagerBuilderException, KeyManagerBuilderException, SSLException {
    final TrustManager trustManager = new X509TrustManagerBuilder().trustAllCertificates().build();
    final SslContextBuilder sslContextBuilder = SslContextBuilder.forClient().trustManager(trustManager);

    if (StringUtils.isNotEmpty(this.configuration.getClientCertificateFilePath())
        && StringUtils.isNotEmpty(this.configuration.getClientCertificateKeyFilePath())) {
      KeyManager keyManager = new X509KeyManagerBuilder()
                                  .withClientCertificateFromFile(this.configuration.getClientCertificateFilePath(),
                                      this.configuration.getClientCertificateKeyFilePath())
                                  .build();
      sslContextBuilder.keyManager(keyManager);
    }

    final SslContext sslContext = sslContextBuilder.build();

    return new DefaultAsyncHttpClient(
        new DefaultAsyncHttpClientConfig.Builder().setUseProxyProperties(true).setSslContext(sslContext).build());
  }

  @Override
  protected void configure() {
    install(VersionModule.getInstance());
    install(TimeModule.getInstance());

    // FixMe: Might be needed for CI tasks
    //  install(ExceptionModule.getInstance());
    bind(Clock.class).toInstance(Clock.systemUTC());
    bind(DelegateConfiguration.class).toInstance(configuration);
    bind(DelegateAgentService.class).to(DelegatePlatformService.class);
    // FixMe: Might be needed when we support back secrets
    //        bind(SecretsDelegateCacheHelperService.class).to(SecretsDelegateCacheHelperServiceImpl.class);
    //        bind(DelegatePropertyService.class).to(DelegatePropertyServiceImpl.class);
    //        bind(DelegatePropertiesServiceProvider.class).to(DelegatePropertiesServiceProviderImpl.class);
    //        bind(DelegateConfigurationServiceProvider.class).to(DelegateConfigurationServiceProviderImpl.class);

    // DefaultAsyncHttpClient is being bound using a separate function (as this function can't throw)
    bind(AsyncHttpClient.class).to(DefaultAsyncHttpClient.class);

    bindExceptionHandlers();
  }

  private void bindExceptionHandlers() {
    MapBinder<Class<? extends Exception>, ExceptionHandler> exceptionHandlerMapBinder = MapBinder.newMapBinder(
        binder(), new TypeLiteral<Class<? extends Exception>>() {}, new TypeLiteral<ExceptionHandler>() {});
  }
}
