/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.debezium;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.connect.runtime.WorkerConfig;
import org.apache.kafka.connect.storage.MemoryOffsetBackingStore;
import org.redisson.Redisson;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;

@Slf4j
public class RedisOffsetBackingStore extends MemoryOffsetBackingStore {
  private String redisKey;
  private String address;
  private RedissonClient redisson;

  public RedisOffsetBackingStore() {}

  public void connect() {
    Config config = new Config();
    config.useSingleServer().setAddress(address);
    redisson = Redisson.create(config);
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
    RMap<byte[], byte[]> offsets = redisson.getMap(this.redisKey);
    if (offsets.size() > 0) {
      for (Map.Entry<byte[], byte[]> mapEntry : offsets.entrySet()) {
        ByteBuffer key = (mapEntry.getKey() != null) ? ByteBuffer.wrap(mapEntry.getKey()) : null;
        ByteBuffer value = (mapEntry.getValue() != null) ? ByteBuffer.wrap(mapEntry.getValue()) : null;
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
      RMap<byte[], byte[]> offsets_map = redisson.getMap(this.redisKey);
      offsets_map.put(key, value);
    }
  }
}