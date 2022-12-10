/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.lock.redis;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.redis.RedisReadMode.SLAVE;
import static io.harness.rule.OwnerRule.RAMA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.PersistenceTestBase;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.lock.AcquiredLock;
import io.harness.redis.RedisConfig;
import io.harness.redis.RedissonClientFactory;
import io.harness.rule.Owner;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

/**
 * @author rktummala on 01/07/2020
 */
@OwnedBy(PL)
public class RedisPersistentLockerTest extends PersistenceTestBase {
  private RedisPersistentLocker redisPersistentLocker;
  private RedissonClient client;

  @Before
  public void setup() {
    initMocks(this);
    mockStatic(RedissonClientFactory.class);
    RedisConfig config = mock(RedisConfig.class);
    when(config.isSentinel()).thenReturn(true);
    when(config.getReadMode()).thenReturn(SLAVE);
    client = mock(RedissonClient.class);
    when(RedissonClientFactory.getClient(any())).thenReturn(client);
    redisPersistentLocker = new RedisPersistentLocker(config);
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void testAcquireLockDoLock() throws InterruptedException {
    RLock rLock = mock(RLock.class);
    when(client.getLock(anyString())).thenReturn(rLock);
    when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);

    try (AcquiredLock lock = redisPersistentLocker.acquireLock(AcquiredLock.class, "cba", Duration.ofMinutes(1))) {
    }

    verify(rLock, times(1)).tryLock(anyLong(), anyLong(), any(TimeUnit.class));
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void testAcquireLockDoNotRunTheBody() throws InterruptedException {
    RLock rLock = mock(RLock.class);
    when(client.getLock(anyString())).thenReturn(rLock);
    when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(false);

    boolean body = false;
    try (AcquiredLock lock = redisPersistentLocker.acquireLock(AcquiredLock.class, "cba", Duration.ofMinutes(1))) {
      body = true;
    } catch (RuntimeException ex) {
      assertThat(ex.getMessage())
          .isEqualTo("Failed to acquire distributed lock for locks:io.harness.lock.AcquiredLock-cba");
    }

    assertThat(body).isFalse();
    verify(rLock, times(1)).tryLock(anyLong(), anyLong(), any(TimeUnit.class));
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void testTryAcquireLockDoNotThrowException() throws InterruptedException {
    RLock rLock = mock(RLock.class);
    when(client.getLock(anyString())).thenReturn(rLock);
    when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(false);

    try (AcquiredLock lock = redisPersistentLocker.tryToAcquireLock(AcquiredLock.class, "cba", Duration.ofMinutes(1))) {
      assertThat(lock).isNull();
    }

    verify(rLock, times(1)).tryLock(anyLong(), anyLong(), any(TimeUnit.class));
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void testAcquireLockNonLockedAtRelease() throws InterruptedException {
    RLock rLock = mock(RLock.class);
    when(client.getLock(anyString())).thenReturn(rLock);
    when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);

    try (AcquiredLock lock = redisPersistentLocker.acquireLock(AcquiredLock.class, "cba", Duration.ofMinutes(1))) {
      assertThat(lock).isNotNull();
    }

    verify(rLock, times(1)).tryLock(anyLong(), anyLong(), any(TimeUnit.class));
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void testWaitToAcquireLock() throws InterruptedException {
    RLock rLock = mock(RLock.class);
    when(client.getLock(anyString())).thenReturn(rLock);
    when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);

    try (AcquiredLock lock = redisPersistentLocker.waitToAcquireLock(
             AcquiredLock.class, "cba", Duration.ofMinutes(1), Duration.ofMinutes(2))) {
      assertThat(lock).isNotNull();
    }

    verify(rLock, times(1)).tryLock(anyLong(), anyLong(), any(TimeUnit.class));
  }
}
