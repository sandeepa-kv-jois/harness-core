/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.event.usagemetrics;

import static io.harness.beans.PageRequest.PageRequestBuilder.aPageRequest;
import static io.harness.exception.WingsException.USER;
import static io.harness.validation.Validator.notNullCheck;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;

import static org.mongodb.morphia.aggregation.Accumulator.accumulator;
import static org.mongodb.morphia.aggregation.Group.grouping;
import static org.mongodb.morphia.aggregation.Group.id;
import static org.mongodb.morphia.aggregation.Projection.projection;

import io.harness.beans.PageRequest;
import io.harness.beans.SearchFilter.Operator;

import software.wings.beans.Account;
import software.wings.beans.Account.AccountKeys;
import software.wings.beans.Application;
import software.wings.beans.Application.ApplicationKeys;
import software.wings.beans.Environment;
import software.wings.beans.Environment.EnvironmentKeys;
import software.wings.beans.Service;
import software.wings.beans.Service.ServiceKeys;
import software.wings.beans.Workflow;
import software.wings.beans.infrastructure.instance.Instance;
import software.wings.dl.WingsPersistence;
import software.wings.verification.CVConfiguration;
import software.wings.verification.CVConfiguration.CVConfigurationKeys;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mongodb.AggregationOptions;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.mongodb.morphia.annotations.Id;
import org.mongodb.morphia.query.Query;

/**
 * Created by Pranjal on 01/10/2019
 */
@Singleton
public class UsageMetricsHelper {
  @Inject private WingsPersistence wingsPersistence;

  public Application getApplication(String appId) {
    Application application = wingsPersistence.createQuery(Application.class)
                                  .project(ApplicationKeys.accountId, true)
                                  .project(ApplicationKeys.name, true)
                                  .filter(Application.ID_KEY2, appId)
                                  .get();
    notNullCheck("Application does not exist", application, USER);
    return application;
  }

  public String getServiceName(String appId, String serviceId) {
    Service service = wingsPersistence.createQuery(Service.class)
                          .project(Workflow.NAME_KEY, true)
                          .filter(ServiceKeys.appId, appId)
                          .filter(Service.ID, serviceId)
                          .get();
    notNullCheck("Service does not exist", service, USER);
    return service.getName();
  }

  public String getEnvironmentName(String appId, String environmentId) {
    Environment environment = wingsPersistence.createQuery(Environment.class)
                                  .project(EnvironmentKeys.name, true)
                                  .filter(EnvironmentKeys.appId, appId)
                                  .filter(Environment.ID_KEY2, environmentId)
                                  .get();
    notNullCheck("Environment does not exist", environment, USER);
    return environment.getName();
  }

  public List<Account> listAllAccountsWithDefaults() {
    PageRequest<Account> pageRequest = aPageRequest()
                                           .addFieldsIncluded(Account.ID_KEY2, AccountKeys.accountName)
                                           .addFilter(EnvironmentKeys.appId, Operator.EQ, GLOBAL_APP_ID)
                                           .build();
    return wingsPersistence.getAllEntities(pageRequest, () -> wingsPersistence.query(Account.class, pageRequest));
  }

  public Map<String, Integer> getAllValidInstanceCounts() {
    Map<String, Integer> instanceCountMap = new HashMap<>();
    Query<Instance> query = wingsPersistence.createQuery(Instance.class);
    query.criteria("isDeleted").equal(false);
    int maxOperationTimeInMillis = wingsPersistence.getMaxTimeMs(Instance.class);
    wingsPersistence.getDatastore(Instance.class)
        .createAggregation(Instance.class)
        .match(query)
        .project(projection("accountId"))
        .group(id(grouping("accountId")), grouping("count", accumulator("$sum", 1)))
        .aggregate(InstanceCount.class,
            AggregationOptions.builder()
                .maxTime(wingsPersistence.getMaxTimeMs(Instance.class), TimeUnit.MILLISECONDS)
                .build())
        .forEachRemaining(
            instanceCount -> instanceCountMap.put(instanceCount.getId().getAccountId(), instanceCount.getCount()));
    return instanceCountMap;
  }

  public CVConfiguration getCVConfig(String cvConfigId) {
    CVConfiguration cvConfiguration = wingsPersistence.createQuery(CVConfiguration.class)
                                          .project(CVConfigurationKeys.name, true)
                                          .project(CVConfigurationKeys.serviceId, true)
                                          .filter(CVConfiguration.ID_KEY2, cvConfigId)
                                          .get();
    notNullCheck("CV Config does not exist", cvConfiguration, USER);
    return cvConfiguration;
  }

  @Data
  @NoArgsConstructor
  public static class InstanceCount {
    @Id ID id;
    int count;
  }

  @Data
  @NoArgsConstructor
  public static class ID {
    String accountId;
  }
}
