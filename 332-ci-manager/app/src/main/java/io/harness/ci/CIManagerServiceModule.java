/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.app;

import static io.harness.authorization.AuthorizationServiceHeader.CI_MANAGER;
import static io.harness.authorization.AuthorizationServiceHeader.MANAGER;
import static io.harness.eventsframework.EventsFrameworkConstants.DEFAULT_MAX_PROCESSING_TIME;
import static io.harness.eventsframework.EventsFrameworkConstants.DEFAULT_READ_BATCH_SIZE;
import static io.harness.eventsframework.EventsFrameworkConstants.OBSERVER_EVENT_CHANNEL;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.DELEGATE_ENTITY;
import static io.harness.lock.DistributedLockImplementation.MONGO;
import static io.harness.pms.listener.NgOrchestrationNotifyEventListener.NG_ORCHESTRATION;

import io.harness.AccessControlClientModule;
import io.harness.account.AccountClientModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.app.impl.CIYamlSchemaServiceImpl;
import io.harness.aws.AwsClient;
import io.harness.aws.AwsClientImpl;
import io.harness.callback.DelegateCallback;
import io.harness.callback.DelegateCallbackToken;
import io.harness.callback.MongoDatabase;
import io.harness.ci.CIExecutionServiceModule;
import io.harness.ci.app.intfc.CIYamlSchemaService;
import io.harness.ci.buildstate.SecretDecryptorViaNg;
import io.harness.ci.enforcement.CIBuildEnforcer;
import io.harness.ci.enforcement.CIBuildEnforcerImpl;
import io.harness.ci.execution.DelegateTaskEventListener;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.ci.ff.impl.CIFeatureFlagServiceImpl;
import io.harness.ci.license.CILicenseService;
import io.harness.ci.license.impl.CILicenseServiceImpl;
import io.harness.ci.logserviceclient.CILogServiceClientModule;
import io.harness.ci.tiserviceclient.TIServiceClientModule;
import io.harness.ci.validation.CIYAMLSanitizationService;
import io.harness.ci.validation.CIYAMLSanitizationServiceImpl;
import io.harness.cistatus.service.GithubService;
import io.harness.cistatus.service.GithubServiceImpl;
import io.harness.cistatus.service.azurerepo.AzureRepoService;
import io.harness.cistatus.service.azurerepo.AzureRepoServiceImpl;
import io.harness.cistatus.service.bitbucket.BitbucketService;
import io.harness.cistatus.service.bitbucket.BitbucketServiceImpl;
import io.harness.cistatus.service.gitlab.GitlabService;
import io.harness.cistatus.service.gitlab.GitlabServiceImpl;
import io.harness.concurrent.HTimeLimiter;
import io.harness.connector.ConnectorResourceClientModule;
import io.harness.core.ci.services.BuildNumberService;
import io.harness.core.ci.services.BuildNumberServiceImpl;
import io.harness.core.ci.services.CIOverviewDashboardService;
import io.harness.core.ci.services.CIOverviewDashboardServiceImpl;
import io.harness.enforcement.client.EnforcementClientModule;
import io.harness.entitysetupusageclient.EntitySetupUsageClientModule;
import io.harness.eventsframework.EventsFrameworkConstants;
import io.harness.eventsframework.api.Consumer;
import io.harness.eventsframework.impl.noop.NoOpConsumer;
import io.harness.eventsframework.impl.redis.RedisConsumer;
import io.harness.grpc.DelegateServiceDriverGrpcClientModule;
import io.harness.grpc.DelegateServiceGrpcClient;
import io.harness.grpc.client.AbstractManagerGrpcClientModule;
import io.harness.grpc.client.ManagerGrpcClientModule;
import io.harness.impl.scm.ScmServiceClientImpl;
import io.harness.licensing.remote.NgLicenseHttpClientModule;
import io.harness.lock.DistributedLockImplementation;
import io.harness.lock.PersistentLockModule;
import io.harness.manage.ManagedScheduledExecutorService;
import io.harness.mongo.MongoPersistence;
import io.harness.ng.core.event.MessageListener;
import io.harness.persistence.HPersistence;
import io.harness.pms.sdk.core.waiter.AsyncWaitEngine;
import io.harness.redis.RedisConfig;
import io.harness.redis.RedissonClientFactory;
import io.harness.reflection.HarnessReflections;
import io.harness.remote.client.ClientMode;
import io.harness.secrets.SecretDecryptor;
import io.harness.secrets.SecretNGManagerClientModule;
import io.harness.service.DelegateServiceDriverModule;
import io.harness.service.ScmServiceClient;
import io.harness.stoserviceclient.STOServiceClientModule;
import io.harness.telemetry.AbstractTelemetryModule;
import io.harness.telemetry.TelemetryConfiguration;
import io.harness.threading.ThreadPool;
import io.harness.timescaledb.TimeScaleDBConfig;
import io.harness.timescaledb.TimeScaleDBService;
import io.harness.timescaledb.TimeScaleDBServiceImpl;
import io.harness.token.TokenClientModule;
import io.harness.user.UserClientModule;
import io.harness.waiter.AsyncWaitEngineImpl;
import io.harness.waiter.WaitNotifyEngine;
import io.harness.yaml.core.StepSpecType;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.common.util.concurrent.TimeLimiter;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import io.dropwizard.jackson.Jackson;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;

@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class CIManagerServiceModule extends AbstractModule {
  private final CIManagerConfiguration ciManagerConfiguration;

  public CIManagerServiceModule(CIManagerConfiguration ciManagerConfiguration) {
    this.ciManagerConfiguration = ciManagerConfiguration;
  }

  @Provides
  @Singleton
  Supplier<DelegateCallbackToken> getDelegateCallbackTokenSupplier(
      DelegateServiceGrpcClient delegateServiceGrpcClient) {
    return (Supplier<DelegateCallbackToken>) Suppliers.memoize(
        () -> getDelegateCallbackToken(delegateServiceGrpcClient, ciManagerConfiguration));
  }

  // Final url returned from this fn would be: https://pr.harness.io/ci-delegate-upgrade/ng/#
  @Provides
  @Singleton
  @Named("ngBaseUrl")
  String getNgBaseUrl() {
    String apiUrl = ciManagerConfiguration.getApiUrl();
    if (apiUrl.endsWith("/")) {
      return apiUrl.substring(0, apiUrl.length() - 1);
    }
    return apiUrl;
  }

  private DelegateCallbackToken getDelegateCallbackToken(
      DelegateServiceGrpcClient delegateServiceClient, CIManagerConfiguration appConfig) {
    log.info("Generating Delegate callback token");
    final DelegateCallbackToken delegateCallbackToken = delegateServiceClient.registerCallback(
        DelegateCallback.newBuilder()
            .setMongoDatabase(MongoDatabase.newBuilder()
                                  .setCollectionNamePrefix("ciManager")
                                  .setConnection(appConfig.getHarnessCIMongo().getUri())
                                  .build())
            .build());
    log.info("Delegate callback token generated =[{}]", delegateCallbackToken.getToken());
    return delegateCallbackToken;
  }

  @Provides
  @Named("yaml-schema-mapper")
  @Singleton
  public ObjectMapper getYamlSchemaObjectMapper() {
    ObjectMapper objectMapper = Jackson.newObjectMapper();
    CIManagerApplication.configureObjectMapper(objectMapper);
    return objectMapper;
  }

  @Provides
  @Named("yaml-schema-subtypes")
  @Singleton
  public Map<Class<?>, Set<Class<?>>> yamlSchemaSubtypes() {
    Set<Class<? extends StepSpecType>> subTypesOfStepSpecType =
        HarnessReflections.get().getSubTypesOf(StepSpecType.class);
    Set<Class<?>> set = new HashSet<>(subTypesOfStepSpecType);

    return ImmutableMap.of(StepSpecType.class, set);
  }

  @Provides
  @Singleton
  public AsyncWaitEngine asyncWaitEngine(WaitNotifyEngine waitNotifyEngine) {
    return new AsyncWaitEngineImpl(waitNotifyEngine, NG_ORCHESTRATION);
  }

  @Provides
  @Singleton
  public TimeLimiter timeLimiter(ExecutorService executorService) {
    return HTimeLimiter.create(executorService);
  }

  @Provides
  @Singleton
  DistributedLockImplementation distributedLockImplementation() {
    return ciManagerConfiguration.getDistributedLockImplementation() == null
        ? MONGO
        : ciManagerConfiguration.getDistributedLockImplementation();
  }

  @Provides
  @Named("lock")
  @Singleton
  RedisConfig redisConfig() {
    return ciManagerConfiguration.getRedisLockConfig();
  }

  @Override
  protected void configure() {
    install(PrimaryVersionManagerModule.getInstance());
    bind(CIManagerConfiguration.class).toInstance(ciManagerConfiguration);
    bind(HPersistence.class).to(MongoPersistence.class).in(Singleton.class);
    bind(BuildNumberService.class).to(BuildNumberServiceImpl.class);
    bind(CIYamlSchemaService.class).to(CIYamlSchemaServiceImpl.class).in(Singleton.class);
    bind(CIFeatureFlagService.class).to(CIFeatureFlagServiceImpl.class).in(Singleton.class);
    bind(CILicenseService.class).to(CILicenseServiceImpl.class).in(Singleton.class);
    bind(CIOverviewDashboardService.class).to(CIOverviewDashboardServiceImpl.class);
    bind(ScmServiceClient.class).to(ScmServiceClientImpl.class);
    bind(GithubService.class).to(GithubServiceImpl.class);
    bind(GitlabService.class).to(GitlabServiceImpl.class);
    bind(BitbucketService.class).to(BitbucketServiceImpl.class);
    bind(AzureRepoService.class).to(AzureRepoServiceImpl.class);
    bind(SecretDecryptor.class).to(SecretDecryptorViaNg.class);
    bind(CIBuildEnforcer.class).to(CIBuildEnforcerImpl.class);
    bind(CIYAMLSanitizationService.class).to(CIYAMLSanitizationServiceImpl.class).in(Singleton.class);
    install(NgLicenseHttpClientModule.getInstance(ciManagerConfiguration.getNgManagerClientConfig(),
        ciManagerConfiguration.getNgManagerServiceSecret(), CI_MANAGER.getServiceId()));

    bind(ScheduledExecutorService.class)
        .annotatedWith(Names.named("ciTelemetryPublisherExecutor"))
        .toInstance(new ScheduledThreadPoolExecutor(1,
            new ThreadFactoryBuilder()
                .setNameFormat("ci-telemetry-publisher-Thread-%d")
                .setPriority(Thread.NORM_PRIORITY)
                .build()));
    bind(ScheduledExecutorService.class)
        .annotatedWith(Names.named("pluginMetadataPublishExecutor"))
        .toInstance(new ScheduledThreadPoolExecutor(1,
            new ThreadFactoryBuilder()
                .setNameFormat("plugin-metadata-publisher-Thread-%d")
                .setPriority(Thread.NORM_PRIORITY)
                .build()));
    bind(AwsClient.class).to(AwsClientImpl.class);
    registerEventListeners();
    try {
      bind(TimeScaleDBService.class)
          .toConstructor(TimeScaleDBServiceImpl.class.getConstructor(TimeScaleDBConfig.class));
    } catch (NoSuchMethodException e) {
      log.error("TimeScaleDbServiceImpl Initialization Failed in due to missing constructor", e);
    }
    if (ciManagerConfiguration.getEnableDashboardTimescale() != null
        && ciManagerConfiguration.getEnableDashboardTimescale()) {
      bind(TimeScaleDBConfig.class)
          .annotatedWith(Names.named("TimeScaleDBConfig"))
          .toInstance(ciManagerConfiguration.getTimeScaleDBConfig() != null
                  ? ciManagerConfiguration.getTimeScaleDBConfig()
                  : TimeScaleDBConfig.builder().build());
    } else {
      bind(TimeScaleDBConfig.class)
          .annotatedWith(Names.named("TimeScaleDBConfig"))
          .toInstance(TimeScaleDBConfig.builder().build());
    }

    // Keeping it to 1 thread to start with. Assuming executor service is used only to
    // serve health checks. If it's being used for other tasks also, max pool size should be increased.
    bind(ExecutorService.class)
        .toInstance(ThreadPool.create(1, 2, 5, TimeUnit.SECONDS,
            new ThreadFactoryBuilder()
                .setNameFormat("default-ci-executor-%d")
                .setPriority(Thread.MIN_PRIORITY)
                .build()));

    bind(ScheduledExecutorService.class)
        .annotatedWith(Names.named("async-taskPollExecutor"))
        .toInstance(new ScheduledThreadPoolExecutor(
            ciManagerConfiguration.getAsyncDelegateResponseConsumption().getCorePoolSize(),
            new ThreadFactoryBuilder()
                .setNameFormat("async-taskPollExecutor-Thread-%d")
                .setPriority(Thread.NORM_PRIORITY)
                .build()));

    bind(ScheduledExecutorService.class)
        .annotatedWith(Names.named("taskPollExecutor"))
        .toInstance(new ManagedScheduledExecutorService("TaskPoll-Thread"));

    install(new CIExecutionServiceModule(
        ciManagerConfiguration.getCiExecutionServiceConfig(), ciManagerConfiguration.getShouldConfigureWithPMS()));
    install(DelegateServiceDriverModule.getInstance(false, true));
    install(new DelegateServiceDriverGrpcClientModule(ciManagerConfiguration.getManagerServiceSecret(),
        ciManagerConfiguration.getManagerTarget(), ciManagerConfiguration.getManagerAuthority(), true));

    install(new TokenClientModule(ciManagerConfiguration.getNgManagerClientConfig(),
        ciManagerConfiguration.getNgManagerServiceSecret(), CI_MANAGER.getServiceId()));
    install(PersistentLockModule.getInstance());

    install(new AbstractManagerGrpcClientModule() {
      @Override
      public ManagerGrpcClientModule.Config config() {
        return ManagerGrpcClientModule.Config.builder()
            .target(ciManagerConfiguration.getManagerTarget())
            .authority(ciManagerConfiguration.getManagerAuthority())
            .build();
      }

      @Override
      public String application() {
        return "CIManager";
      }
    });

    install(AccessControlClientModule.getInstance(
        ciManagerConfiguration.getAccessControlClientConfiguration(), CI_MANAGER.getServiceId()));
    install(new EntitySetupUsageClientModule(ciManagerConfiguration.getNgManagerClientConfig(),
        ciManagerConfiguration.getNgManagerServiceSecret(), "CIManager"));
    install(new ConnectorResourceClientModule(ciManagerConfiguration.getNgManagerClientConfig(),
        ciManagerConfiguration.getNgManagerServiceSecret(), "CIManager", ClientMode.PRIVILEGED));
    install(new SecretNGManagerClientModule(ciManagerConfiguration.getNgManagerClientConfig(),
        ciManagerConfiguration.getNgManagerServiceSecret(), "CIManager"));
    install(new CILogServiceClientModule(ciManagerConfiguration.getLogServiceConfig()));
    install(UserClientModule.getInstance(ciManagerConfiguration.getManagerClientConfig(),
        ciManagerConfiguration.getManagerServiceSecret(), CI_MANAGER.getServiceId()));
    install(new TIServiceClientModule(ciManagerConfiguration.getTiServiceConfig()));
    install(new STOServiceClientModule(ciManagerConfiguration.getStoServiceConfig()));
    install(new AccountClientModule(ciManagerConfiguration.getManagerClientConfig(),
        ciManagerConfiguration.getNgManagerServiceSecret(), CI_MANAGER.toString()));
    install(EnforcementClientModule.getInstance(ciManagerConfiguration.getNgManagerClientConfig(),
        ciManagerConfiguration.getNgManagerServiceSecret(), CI_MANAGER.getServiceId(),
        ciManagerConfiguration.getEnforcementClientConfiguration()));
    install(new AbstractTelemetryModule() {
      @Override
      public TelemetryConfiguration telemetryConfiguration() {
        return ciManagerConfiguration.getSegmentConfiguration();
      }
    });
    install(new CICacheRegistrar());
  }

  private void registerEventListeners() {
    final RedisConfig redisConfig = ciManagerConfiguration.getEventsFrameworkConfiguration().getRedisConfig();
    String authorizationServiceHeader = MANAGER.getServiceId();

    if (redisConfig.getRedisUrl().equals("dummyRedisUrl")) {
      bind(Consumer.class)
          .annotatedWith(Names.named(OBSERVER_EVENT_CHANNEL))
          .toInstance(
              NoOpConsumer.of(EventsFrameworkConstants.DUMMY_TOPIC_NAME, EventsFrameworkConstants.DUMMY_GROUP_NAME));

    } else {
      RedissonClient redissonClient = RedissonClientFactory.getClient(redisConfig);
      bind(Consumer.class)
          .annotatedWith(Names.named(OBSERVER_EVENT_CHANNEL))
          .toInstance(RedisConsumer.of(OBSERVER_EVENT_CHANNEL, authorizationServiceHeader, redissonClient,
              DEFAULT_MAX_PROCESSING_TIME, DEFAULT_READ_BATCH_SIZE, redisConfig.getEnvNamespace()));

      bind(MessageListener.class)
          .annotatedWith(Names.named(DELEGATE_ENTITY + OBSERVER_EVENT_CHANNEL))
          .to(DelegateTaskEventListener.class);
    }
  }
}
