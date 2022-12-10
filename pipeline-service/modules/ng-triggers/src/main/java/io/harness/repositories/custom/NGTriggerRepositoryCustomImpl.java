/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.repositories.custom;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity.NGTriggerEntityKeys;
import io.harness.ngtriggers.mapper.TriggerFilterHelper;
import io.harness.springdata.PersistenceUtils;

import com.google.inject.Inject;
import com.mongodb.client.result.DeleteResult;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.repository.support.PageableExecutionUtils;

@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(PIPELINE)
public class NGTriggerRepositoryCustomImpl implements NGTriggerRepositoryCustom {
  private final MongoTemplate mongoTemplate;

  @Override
  public Page<NGTriggerEntity> findAll(Criteria criteria, Pageable pageable) {
    Query query = new Query(criteria).with(pageable);
    List<NGTriggerEntity> triggers = mongoTemplate.find(query, NGTriggerEntity.class);
    return PageableExecutionUtils.getPage(
        triggers, pageable, () -> mongoTemplate.count(Query.of(query).limit(-1).skip(-1), NGTriggerEntity.class));
  }

  @Override
  public NGTriggerEntity update(Criteria criteria, NGTriggerEntity ngTriggerEntity) {
    Query query = new Query(criteria);
    Update update = TriggerFilterHelper.getUpdateOperations(ngTriggerEntity);
    RetryPolicy<Object> retryPolicy = getRetryPolicy(
        "[Retrying]: Failed updating Trigger; attempt: {}", "[Failed]: Failed updating Trigger; attempt: {}");
    return Failsafe.with(retryPolicy)
        .get(()
                 -> mongoTemplate.findAndModify(
                     query, update, new FindAndModifyOptions().returnNew(true), NGTriggerEntity.class));
  }

  @Override
  public NGTriggerEntity updateValidationStatus(Criteria criteria, NGTriggerEntity ngTriggerEntity) {
    Query query = new Query(criteria);
    Update update = new Update();
    update.set(NGTriggerEntityKeys.triggerStatus, ngTriggerEntity.getTriggerStatus());
    update.set(NGTriggerEntityKeys.enabled, ngTriggerEntity.getEnabled());
    RetryPolicy<Object> retryPolicy = getRetryPolicy(
        "[Retrying]: Failed updating Trigger; attempt: {}", "[Failed]: Failed updating Trigger; attempt: {}");
    return Failsafe.with(retryPolicy)
        .get(()
                 -> mongoTemplate.findAndModify(
                     query, update, new FindAndModifyOptions().returnNew(true), NGTriggerEntity.class));
  }

  @Override
  public NGTriggerEntity updateValidationStatusAndMetadata(Criteria criteria, NGTriggerEntity ngTriggerEntity) {
    Query query = new Query(criteria);
    Update update = new Update();
    update.set(NGTriggerEntityKeys.triggerStatus, ngTriggerEntity.getTriggerStatus());
    update.set(NGTriggerEntityKeys.metadata, ngTriggerEntity.getMetadata());
    RetryPolicy<Object> retryPolicy = getRetryPolicy(
        "[Retrying]: Failed updating Trigger; attempt: {}", "[Failed]: Failed updating Trigger; attempt: {}");
    return Failsafe.with(retryPolicy)
        .get(()
                 -> mongoTemplate.findAndModify(
                     query, update, new FindAndModifyOptions().returnNew(true), NGTriggerEntity.class));
  }

  @Override
  public DeleteResult hardDelete(Criteria criteria) {
    Query query = new Query(criteria);
    RetryPolicy<Object> retryPolicy = getRetryPolicy(
        "[Retrying]: Failed hard deleting Trigger; attempt: {}", "[Failed]: Failed deleting Trigger; attempt: {}");
    return Failsafe.with(retryPolicy).get(() -> mongoTemplate.remove(query, NGTriggerEntity.class));
  }

  private RetryPolicy<Object> getRetryPolicy(String failedAttemptMessage, String failureMessage) {
    return PersistenceUtils.getRetryPolicy(failedAttemptMessage, failureMessage);
  }
}
