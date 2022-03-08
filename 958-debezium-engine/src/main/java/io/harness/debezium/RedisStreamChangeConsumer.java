/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.debezium;

import io.debezium.DebeziumException;
import io.debezium.engine.ChangeEvent;
import io.debezium.engine.DebeziumEngine;
import io.debezium.engine.DebeziumEngine.RecordCommitter;
import io.debezium.util.DelayStrategy;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Transaction;
import redis.clients.jedis.exceptions.JedisConnectionException;
import redis.clients.jedis.exceptions.JedisDataException;

@Slf4j
public class RedisStreamChangeConsumer implements DebeziumEngine.ChangeConsumer<ChangeEvent<String, String>> {
  private HostAndPort address;
  String redisAddress;

  int batchSize = 500;
  int initialRetryDelay = 300;
  int maxRetryDelay = 10000;
  String nullKey = "default";
  String nullValue = "default";

  private Jedis client = null;

  public RedisStreamChangeConsumer(String redisAddress) {
    this.redisAddress = redisAddress;
  }

  @PostConstruct
  void connect() {
    address = HostAndPort.from("127.0.0.1:6379");

    client = new Jedis(address);

    // make sure that client is connected
    client.ping();

    log.info("Using Jedis '{}'", client);
  }

  @PreDestroy
  void close() {
    try {
      client.close();
    } catch (Exception e) {
      log.warn("Exception while closing Jedis: {}", client, e);
    } finally {
      client = null;
    }
  }

  /**
   * Split collection to batches by batch size using a stream
   */
  private <T> Stream<List<T>> batches(List<T> source, int length) {
    if (source.isEmpty()) {
      return Stream.empty();
    }

    int size = source.size();
    int fullChunks = (size - 1) / length;

    return IntStream.range(0, fullChunks + 1)
        .mapToObj(n -> source.subList(n * length, n == fullChunks ? size : (n + 1) * length));
  }

  @Override
  public void handleBatch(List<ChangeEvent<String, String>> records,
      RecordCommitter<ChangeEvent<String, String>> committer) throws InterruptedException {
    DelayStrategy delayStrategy = DelayStrategy.exponential(initialRetryDelay, maxRetryDelay);

    log.trace("Handling a batch of {} records", records.size());
    batches(records, batchSize).forEach(batch -> {
      boolean completedSuccessfully = false;

      // As long as we failed to execute the current batch to the stream, we should retry if the reason was either a
      // connection error or OOM in Redis.
      while (!completedSuccessfully) {
        if (client == null) {
          // Try to reconnect
          try {
            connect();
            continue; // Managed to establish a new connection to Redis, avoid a redundant retry
          } catch (Exception e) {
            close();
            log.error("Can't connect to Redis", e);
          }
        } else {
          Transaction transaction;
          try {
            log.trace("Preparing a Redis Transaction of {} records", batch.size());
            transaction = client.multi();

            // Add the batch records to the stream(s) via Transaction
            for (ChangeEvent<String, String> record : batch) {
              String destination = record.destination();
              String key = (record.key() != null) ? (record.key()) : nullKey;
              String value = (record.value() != null) ? (record.value()) : nullValue;

              // Add the record to the destination stream
              transaction.xadd(destination, null, Collections.singletonMap(key, value));
            }

            // Execute the transaction in Redis
            transaction.exec();

            // Mark all the batch records as processed only when the transaction succeeds
            for (ChangeEvent<String, String> record : batch) {
              committer.markProcessed(record);
            }
            completedSuccessfully = true;
          } catch (JedisConnectionException jce) {
            close();
          } catch (JedisDataException jde) {
            // When Redis reaches its max memory limitation, a JedisDataException will be thrown with one of the
            // messages listed below. In this case, we will retry execute the batch, assuming some memory will be freed
            // eventually as result of evicting elements from the stream by the target DB.
            if (jde.getMessage().equals(
                    "EXECABORT Transaction discarded because of: OOM command not allowed when used memory > 'maxmemory'.")) {
              log.error("Redis runs OOM", jde);
            } else if (jde.getMessage().startsWith("EXECABORT")) {
              log.error("Redis transaction error", jde);
            }
            // When Redis is starting, a JedisDataException will be thrown with this message.
            // We will retry communicating with the target DB as once of the Redis is available, this message will be
            // gone.
            else if (jde.getMessage().equals("LOADING Redis is loading the dataset in memory")) {
              log.error("Redis is starting", jde);
            } else {
              log.error("Unexpected JedisDataException", jde);
              throw new DebeziumException(jde);
            }
          } catch (Exception e) {
            log.error("Unexpected Exception", e);
            throw new DebeziumException(e);
          }
        }

        // Failed to execute the transaction, retry...
        delayStrategy.sleepWhen(!completedSuccessfully);
      }
    });

    // Mark the whole batch as finished once the sub batches completed
    committer.markBatchFinished();
  }
}
