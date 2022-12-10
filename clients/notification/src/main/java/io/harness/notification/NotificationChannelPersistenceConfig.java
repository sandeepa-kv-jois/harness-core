/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.notification;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotation.HarnessRepo;
import io.harness.annotations.dev.OwnedBy;
import io.harness.mongo.MongoConfig;
import io.harness.mongo.metrics.HarnessConnectionPoolListener;
import io.harness.springdata.HMongoTemplate;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientOptions;
import com.mongodb.MongoClientURI;
import com.mongodb.ReadPreference;
import java.util.Collection;
import java.util.Collections;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDbFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.config.AbstractMongoConfiguration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoDbFactory;
import org.springframework.data.mongodb.core.convert.DbRefResolver;
import org.springframework.data.mongodb.core.convert.DefaultDbRefResolver;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@OwnedBy(PL)
@Configuration
@EnableMongoRepositories(basePackages = {"io.harness.notification"},
    includeFilters = @ComponentScan.Filter(HarnessRepo.class), mongoTemplateRef = "notification-channel")
public class NotificationChannelPersistenceConfig extends AbstractMongoConfiguration {
  private final MongoBackendConfiguration mongoBackendConfiguration;
  private final HarnessConnectionPoolListener harnessConnectionPoolListener;
  private final MongoConfig mongoConfig;

  @Inject
  public NotificationChannelPersistenceConfig(Injector injector) {
    this.mongoBackendConfiguration =
        (MongoBackendConfiguration) injector.getInstance(Key.get(NotificationClientConfiguration.class))
            .getNotificationClientBackendConfiguration();
    this.harnessConnectionPoolListener = injector.getInstance(HarnessConnectionPoolListener.class);
    this.mongoConfig = injector.getInstance(MongoConfig.class);
  }

  @Override
  public MongoClient mongoClient() {
    MongoClientOptions primaryMongoClientOptions =
        MongoClientOptions.builder()
            .retryWrites(true)
            .connectTimeout(mongoBackendConfiguration.getConnectTimeout())
            .serverSelectionTimeout(mongoBackendConfiguration.getServerSelectionTimeout())
            .socketTimeout(mongoBackendConfiguration.getSocketTimeout())
            .maxConnectionIdleTime(mongoBackendConfiguration.getMaxConnectionIdleTime())
            .connectionsPerHost(mongoBackendConfiguration.getConnectionsPerHost())
            .readPreference(ReadPreference.primary())
            .addConnectionPoolListener(harnessConnectionPoolListener)
            .applicationName("ng_notification_channel_client")
            .description("ng_notification_channel_client")
            .build();
    MongoClientURI uri =
        new MongoClientURI(mongoBackendConfiguration.getUri(), MongoClientOptions.builder(primaryMongoClientOptions));
    return new MongoClient(uri);
  }

  @Override
  protected String getDatabaseName() {
    return new MongoClientURI(mongoBackendConfiguration.getUri()).getDatabase();
  }

  @Bean
  MongoTransactionManager transactionManager(MongoDbFactory dbFactory) {
    return new MongoTransactionManager(dbFactory);
  }

  @Override
  protected Collection<String> getMappingBasePackages() {
    return Collections.singleton("io.harness");
  }

  @Bean(name = "notification-channel")
  public MongoTemplate mongoTemplate() throws Exception {
    MongoDbFactory mongoDbFactory = new SimpleMongoDbFactory(mongoClient(), getDatabaseName());
    DbRefResolver dbRefResolver = new DefaultDbRefResolver(mongoDbFactory);
    MongoMappingContext mappingContext = this.mongoMappingContext();
    mappingContext.setAutoIndexCreation(false);
    MappingMongoConverter converter = new MappingMongoConverter(dbRefResolver, mappingContext);
    converter.setCodecRegistryProvider(mongoDbFactory);
    converter.afterPropertiesSet();
    return new HMongoTemplate(mongoDbFactory, mappingMongoConverter(), mongoConfig);
  }
}
