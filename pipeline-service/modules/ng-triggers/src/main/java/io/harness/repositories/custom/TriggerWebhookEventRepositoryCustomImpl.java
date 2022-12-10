/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.repositories.custom;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ngtriggers.beans.entity.TriggerWebhookEvent;
import io.harness.ngtriggers.mapper.TriggerFilterHelper;
import io.harness.springdata.PersistenceUtils;

import com.google.inject.Inject;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(PIPELINE)
public class TriggerWebhookEventRepositoryCustomImpl implements TriggerWebhookEventRepositoryCustom {
  private final MongoTemplate mongoTemplate;

  @Override
  public TriggerWebhookEvent update(Criteria criteria, TriggerWebhookEvent triggerWebhookEvent) {
    Query query = new Query(criteria);
    Update update = TriggerFilterHelper.getUpdateOperations(triggerWebhookEvent);
    RetryPolicy<Object> retryPolicy = getRetryPolicy(
        "[Retrying]: Failed updating Trigger; attempt: {}", "[Failed]: Failed updating Trigger; attempt: {}");
    return Failsafe.with(retryPolicy)
        .get(()
                 -> mongoTemplate.findAndModify(
                     query, update, new FindAndModifyOptions().returnNew(true), TriggerWebhookEvent.class));
  }

  private RetryPolicy<Object> getRetryPolicy(String failedAttemptMessage, String failureMessage) {
    return PersistenceUtils.getRetryPolicy(failedAttemptMessage, failureMessage);
  }
}
