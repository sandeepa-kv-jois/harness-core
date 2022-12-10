/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.redis;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.secret.ConfigSecret;

import com.google.inject.Singleton;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.redisson.client.codec.Codec;

@OwnedBy(PL)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Singleton
public class RedisConfig {
  private boolean sentinel;
  private String masterName;
  private String redisUrl;
  private List<String> sentinelUrls;
  private int connectionMinimumIdleSize;
  private String envNamespace;
  private RedisReadMode readMode;
  private Class<? extends Codec> codec;
  private int nettyThreads;
  private boolean useScriptCache;
  @ConfigSecret private String password;
  @ConfigSecret private String userName;
  @ConfigSecret private RedisSSLConfig sslConfig;
  private int subscriptionsPerConnection;
  private int subscriptionConnectionPoolSize;
  private int connectionPoolSize;
  private int retryInterval;
  private int retryAttempts;
  private int timeout;
}
