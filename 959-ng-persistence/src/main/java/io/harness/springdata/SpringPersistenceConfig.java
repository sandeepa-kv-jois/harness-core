/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.springdata;

import static io.harness.mongo.MongoConfig.DOT_REPLACEMENT;
import static io.harness.springdata.PersistenceStoreUtils.getMatchingEntities;

import io.harness.annotation.HarnessRepo;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EmbeddedUser;
import io.harness.mongo.MongoConfig;
import io.harness.persistence.Store;
import io.harness.reflection.HarnessReflections;

import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.name.Names;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.convert.CustomConversions;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.mongodb.MongoDbFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.config.AbstractMongoConfiguration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.guice.annotation.GuiceModule;

@Configuration
@GuiceModule
@EnableMongoRepositories(basePackages = {"io.harness.repositories"},
    includeFilters = @ComponentScan.Filter(HarnessRepo.class), mongoTemplateRef = "primary")
@EnableMongoAuditing
@OwnedBy(HarnessTeam.PL)
public class SpringPersistenceConfig extends AbstractMongoConfiguration {
  protected final Injector injector;
  protected final List<Class<? extends Converter<?, ?>>> springConverters;
  protected final MongoConfig mongoConfig;
  protected final MongoClient mongoClient;

  public SpringPersistenceConfig(Injector injector, List<Class<? extends Converter<?, ?>>> springConverters) {
    this.injector = injector;
    this.springConverters = springConverters;
    this.mongoClient = injector.getInstance(Key.get(MongoClient.class, Names.named("primaryMongoClient")));
    this.mongoConfig = injector.getInstance(MongoConfig.class);
  }

  @Override
  public MongoClient mongoClient() {
    return mongoClient;
  }

  @Override
  protected String getDatabaseName() {
    return new MongoClientURI(mongoConfig.getUri()).getDatabase();
  }

  @Bean(name = "primary")
  @Primary
  public MongoTemplate mongoTemplate() throws Exception {
    MappingMongoConverter mappingMongoConverter = mappingMongoConverter();
    mappingMongoConverter.setMapKeyDotReplacement(DOT_REPLACEMENT);
    return new HMongoTemplate(mongoDbFactory(), mappingMongoConverter, mongoConfig);
  }

  @Bean
  MongoTransactionManager transactionManager(MongoDbFactory dbFactory) {
    return new MongoTransactionManager(dbFactory);
  }

  @Override
  protected Set<Class<?>> getInitialEntitySet() {
    Set<Class<?>> classes = HarnessReflections.get().getTypesAnnotatedWith(TypeAlias.class);
    Store store = null;
    if (Objects.nonNull(mongoConfig.getAliasDBName())) {
      store = Store.builder().name(mongoConfig.getAliasDBName()).build();
    }
    return getMatchingEntities(classes, store);
  }

  @Bean
  public CustomConversions customConversions() {
    List<?> converterInstances = springConverters.stream().map(injector::getInstance).collect(Collectors.toList());
    return new MongoCustomConversions(converterInstances);
  }

  @Bean
  public AuditorAware<EmbeddedUser> auditorAware() {
    return injector.getInstance(SpringSecurityAuditorAware.class);
  }

  @Override
  protected boolean autoIndexCreation() {
    return false;
  }
}
