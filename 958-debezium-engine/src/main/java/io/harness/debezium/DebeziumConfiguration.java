/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.debezium;

import java.io.File;
import java.io.IOException;
import java.util.Optional;
import java.util.Properties;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DebeziumConfiguration {
  private static final String MONGO_DB_CONNECTOR = "io.debezium.connector.mongodb.MongoDbConnector";
  private static final String CONNECTOR_NAME = "name";
  private static final String OFFSET_STORAGE = "offset.storage";
  private static final String OFFSET_STORAGE_FILE_FILENAME = "offset.storage.file.filename";
  private static final String OFFSET_STORAGE_COLLECTION = "offset.storage.topic";
  private static final String KEY_CONVERTER_SCHEMAS_ENABLE = "key.converter.schemas.enable";
  private static final String VALUE_CONVERTER_SCHEMAS_ENABLE = "value.converter.schemas.enable";
  private static final String OFFSET_FLUSH_INTERVAL_MS = "offset.flush.interval.ms";
  private static final String CONNECTOR_CLASS = "connector.class";
  private static final String MONGODB_HOSTS = "mongodb.hosts";
  private static final String MONGODB_NAME = "mongodb.name";
  private static final String MONGODB_USER = "mongodb.user";
  private static final String MONGODB_PASSWORD = "mongodb.password";
  private static final String MONGODB_SSL_ENABLED = "mongodb.ssl.enabled";
  private static final String DATABASE_INCLUDE_LIST = "database.include.list";
  private static final String COLLECTION_INCLUDE_LIST = "collection.include.list";
  private static final String TRANSFORMS = "transforms";
  private static final String TRANSFORMS_UNWRAP_TYPE = "transforms.unwrap.type";
  private static final String TRANSFORMS_UNWRAP_DROP_TOMBSTONES = "transforms.unwrap.drop.tombstones";
  private static final String TRANSFORMS_UNWRAP_ADD_HEADERS = "transforms.unwrap.add.headers";
  private static final String DEBEZIUM_CONNECTOR_MONGODB_TRANSFORMS_EXTRACT_NEW_DOCUMENT_STATE =
      "io.debezium.connector.mongodb.transforms.ExtractNewDocumentState";

  public Properties getDebeziumProperties(DebeziumConfig debeziumConfig) {
    File offsetStorageTempFile = null;
    try {
      offsetStorageTempFile = File.createTempFile("offsets_", ".dat");
    } catch (IOException e) {
      log.error("Error creating file");
    }
    Properties props = new Properties();
    String offsetCollection = "offset_collection";
    props.setProperty(CONNECTOR_NAME, debeziumConfig.getConnectorName());
    // TODO: replacing local offset storage with Mongo or Redis
    props.setProperty(OFFSET_STORAGE, "org.apache.kafka.connect.storage.FileOffsetBackingStore");
    props.setProperty(OFFSET_STORAGE_FILE_FILENAME, offsetStorageTempFile.getAbsolutePath());
    props.setProperty(OFFSET_STORAGE_COLLECTION, offsetCollection);
    props.setProperty(KEY_CONVERTER_SCHEMAS_ENABLE, debeziumConfig.getKeyConverterSchemasEnable());
    props.setProperty(VALUE_CONVERTER_SCHEMAS_ENABLE, debeziumConfig.getValueConverterSchemasEnable());
    props.setProperty(OFFSET_FLUSH_INTERVAL_MS, debeziumConfig.getOffsetFlushIntervalMillis());

    /* begin connector properties */
    props.setProperty(CONNECTOR_CLASS, MONGO_DB_CONNECTOR);
    props.setProperty(MONGODB_HOSTS, debeziumConfig.getMongodbHosts());
    props.setProperty(MONGODB_NAME, debeziumConfig.getMongodbName());
    Optional.ofNullable(debeziumConfig.getMongodbUser())
        .filter(x -> !x.isEmpty())
        .ifPresent(x -> props.setProperty(MONGODB_USER, x));
    Optional.ofNullable(debeziumConfig.getMongodbPassword())
        .filter(x -> !x.isEmpty())
        .ifPresent(x -> props.setProperty(MONGODB_PASSWORD, x));
    props.setProperty(MONGODB_SSL_ENABLED, debeziumConfig.getSslEnabled());
    props.setProperty(DATABASE_INCLUDE_LIST, debeziumConfig.getDatabaseIncludeList());
    props.setProperty(COLLECTION_INCLUDE_LIST, debeziumConfig.getCollectionIncludeList());
    props.setProperty(TRANSFORMS, "unwrap");
    props.setProperty(TRANSFORMS_UNWRAP_TYPE, DEBEZIUM_CONNECTOR_MONGODB_TRANSFORMS_EXTRACT_NEW_DOCUMENT_STATE);
    props.setProperty(TRANSFORMS_UNWRAP_DROP_TOMBSTONES, "false");
    props.setProperty(TRANSFORMS_UNWRAP_ADD_HEADERS, "op");
    return props;
  }
}
