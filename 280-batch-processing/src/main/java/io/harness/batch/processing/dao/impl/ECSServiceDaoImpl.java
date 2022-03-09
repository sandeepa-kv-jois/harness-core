/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.dao.impl;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.inject.Inject;
import io.harness.batch.processing.dao.intfc.ECSServiceDao;
import io.harness.ccm.commons.entities.ecs.ECSService;
import io.harness.persistence.HPersistence;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.time.Duration;

@Slf4j
@Repository
public class ECSServiceDaoImpl implements ECSServiceDao {
  @Autowired @Inject private HPersistence hPersistence;
  private final Cache<CacheKey, Boolean> saved = Caffeine.newBuilder().expireAfterWrite(Duration.ofHours(6)).build();

  @Value
  private static class CacheKey {
    String clusterId;
    String serviceArn;
  }

  @Override
  public void create(ECSService ecsService) {
    final CacheKey cacheKey = new CacheKey(ecsService.getClusterId(), ecsService.getServiceArn());
    saved.get(cacheKey, key -> hPersistence.save(ecsService) != null);
  }
}
