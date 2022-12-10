/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.repositories.custom;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.ngtriggers.beans.entity.TriggerEventHistory;
import io.harness.ngtriggers.beans.entity.TriggerEventHistory.TriggerEventHistoryKeys;

import com.google.inject.Inject;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.repository.support.PageableExecutionUtils;

@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(PIPELINE)
public class TriggerEventHistoryRepositoryCustomImpl implements TriggerEventHistoryRepositoryCustom {
  private final MongoTemplate mongoTemplate;

  private final TriggerEventHistoryReadHelper triggerEventHistoryReadHelper;

  @Override
  public List<TriggerEventHistory> findAll(Criteria criteria) {
    Query query = new Query(criteria);
    return mongoTemplate.find(query, TriggerEventHistory.class);
  }

  @Override
  public Page<TriggerEventHistory> findAll(Criteria criteria, Pageable pageable) {
    try {
      Query query = new Query(criteria).with(pageable);
      long count = triggerEventHistoryReadHelper.findCount(query);
      List<TriggerEventHistory> eventHistoryList = triggerEventHistoryReadHelper.find(query);

      return PageableExecutionUtils.getPage(eventHistoryList, pageable, () -> count);
    } catch (IllegalArgumentException ex) {
      log.error(ex.getMessage(), ex);
      throw new InvalidRequestException("Trigger event history not found", ex);
    }
  }

  @Override
  public List<TriggerEventHistory> findAllActivationTimestampsInRange(Criteria criteria) {
    Query query = new Query(criteria);
    query.fields()
        .include(TriggerEventHistoryKeys.uuid)
        .include(TriggerEventHistoryKeys.createdAt)
        .include(TriggerEventHistoryKeys.exceptionOccurred);
    return mongoTemplate.find(query, TriggerEventHistory.class);
  }
}
