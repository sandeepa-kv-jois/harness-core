/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.debezium;

import io.smallrye.mutiny.Uni;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.connect.runtime.WorkerConfig;
import org.apache.kafka.connect.storage.MemoryOffsetBackingStore;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.exceptions.JedisConnectionException;

@Slf4j
public class RedisOffsetBackingStore extends MemoryOffsetBackingStore {
  public String redisKey;
  public static final Integer RETRY_INITIAL_DELAY = 300;
  public static final Integer RETRY_MAX_DELAY = 10000;

  private String address;

  private Jedis client;

  public RedisOffsetBackingStore() {}

  public void connect() {
    HostAndPort address = HostAndPort.from(this.address);
    client = new Jedis(address);
    // make sure that client is connected
    client.ping();
  }

  @Override
  public void configure(WorkerConfig config) {
    super.configure(config);
    this.address = config.getString("offset.storage.file.filename");
    this.redisKey = config.getString("offset.storage.topic");
  }

  @Override
  public synchronized void start() {
    super.start();
    log.info("Starting RedisOffsetBackingStore");
    this.connect();
    this.load();
  }

  @Override
  public synchronized void stop() {
    super.stop();
    log.info("Stopped RedisOffsetBackingStore");
  }

  /**
   * Load offsets from redis keys
   */
  private void load() {
    this.data = new HashMap<>();
    // fetch offsets from Redis
    Map<String, String> offsets =
        Uni.createFrom()
            .item(() -> { return (Map<String, String>) client.hgetAll(this.redisKey); })
            // handle failures and retry
            .onFailure()
            .invoke(f -> {
              log.warn("Reading from offset store failed with " + f);
              log.warn("Will retry");
            })
            .onFailure(JedisConnectionException.class)
            .invoke(f -> {
              log.warn("Attempting to reconnect to redis ");
              this.connect();
            })
            // retry on failure with backoff
            .onFailure()
            .retry()
            .withBackOff(Duration.ofMillis(RETRY_INITIAL_DELAY), Duration.ofMillis(RETRY_MAX_DELAY))
            .indefinitely()
            // write success trace message
            .invoke(item -> { log.trace("Offsets fetched from redis: " + item); })
            .await()
            .indefinitely();
    if (offsets != null) {
      for (Map.Entry<String, String> mapEntry : offsets.entrySet()) {
        ByteBuffer key = (mapEntry.getKey() != null) ? ByteBuffer.wrap(mapEntry.getKey().getBytes()) : null;
        ByteBuffer value = (mapEntry.getValue() != null) ? ByteBuffer.wrap(mapEntry.getValue().getBytes()) : null;
        data.put(key, value);
      }
    } else {
      log.info("No offset found in the database, will start a full sync.");
    }
  }

  /**
   * Save offsets to redis keys
   */
  @Override
  protected void save() {
    for (Map.Entry<ByteBuffer, ByteBuffer> mapEntry : data.entrySet()) {
      byte[] key = (mapEntry.getKey() != null) ? mapEntry.getKey().array() : null;
      byte[] value = (mapEntry.getValue() != null) ? mapEntry.getValue().array() : null;
      // set the value in Redis
      Uni.createFrom()
          .item(() -> { return (Long) client.hset(redisKey.getBytes(), key, value); })
          // handle failures and retry
          .onFailure()
          .invoke(f -> {
            log.warn("Writing to offset store failed with " + f);
            log.warn("Will retry");
          })
          .onFailure(JedisConnectionException.class)
          .invoke(f -> {
            log.warn("Attempting to reconnect to redis ");
            this.connect();
          })
          // retry on failure with backoff
          .onFailure()
          .retry()
          .withBackOff(Duration.ofSeconds(1), Duration.ofSeconds(2))
          .indefinitely()
          // write success trace message
          .invoke(item -> { log.trace("Record written to offset store in redis: " + value); })
          .await()
          .indefinitely();
    }
  }
}