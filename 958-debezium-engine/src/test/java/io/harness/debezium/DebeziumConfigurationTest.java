package io.harness.debezium;

import static io.harness.rule.OwnerRule.SHALINI;

import static junit.framework.TestCase.assertEquals;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import java.util.Optional;
import java.util.Properties;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class DebeziumConfigurationTest {
  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testGetDebeziumProperties() {
    DebeziumConfig debeziumConfig = new DebeziumConfig(false, "testConnector", "offset_file", "false", "false", "6000",
        "MongoDbConnectorClass", "rs0/host1", "shop", "", "", "false", "products", "");
    Properties expected_props = new Properties();
    Properties props = new DebeziumConfiguration().getDebeziumProperties(debeziumConfig);
    expected_props.setProperty(DebeziumConfiguration.CONNECTOR_NAME, debeziumConfig.getConnectorName());
    expected_props.setProperty(DebeziumConfiguration.OFFSET_STORAGE, RedisOffsetBackingStore.class.getName());
    expected_props.setProperty(
        DebeziumConfiguration.OFFSET_STORAGE_FILE_FILENAME, debeziumConfig.getOffsetStorageFileName());
    expected_props.setProperty(DebeziumConfiguration.OFFSET_STORAGE_KEY, DebeziumConfiguration.REDIS_OFFSETS_KEY);
    expected_props.setProperty(
        DebeziumConfiguration.KEY_CONVERTER_SCHEMAS_ENABLE, debeziumConfig.getKeyConverterSchemasEnable());
    expected_props.setProperty(
        DebeziumConfiguration.VALUE_CONVERTER_SCHEMAS_ENABLE, debeziumConfig.getValueConverterSchemasEnable());
    expected_props.setProperty(
        DebeziumConfiguration.OFFSET_FLUSH_INTERVAL_MS, debeziumConfig.getOffsetFlushIntervalMillis());

    /* begin connector properties */
    expected_props.setProperty(DebeziumConfiguration.CONNECTOR_CLASS, DebeziumConfiguration.MONGO_DB_CONNECTOR);
    expected_props.setProperty(DebeziumConfiguration.MONGODB_HOSTS, debeziumConfig.getMongodbHosts());
    expected_props.setProperty(DebeziumConfiguration.MONGODB_NAME, debeziumConfig.getMongodbName());
    Optional.ofNullable(debeziumConfig.getMongodbUser())
        .filter(x -> !x.isEmpty())
        .ifPresent(x -> props.setProperty(DebeziumConfiguration.MONGODB_USER, x));
    Optional.ofNullable(debeziumConfig.getMongodbPassword())
        .filter(x -> !x.isEmpty())
        .ifPresent(x -> props.setProperty(DebeziumConfiguration.MONGODB_PASSWORD, x));
    expected_props.setProperty(DebeziumConfiguration.MONGODB_SSL_ENABLED, debeziumConfig.getSslEnabled());
    expected_props.setProperty(DebeziumConfiguration.DATABASE_INCLUDE_LIST, debeziumConfig.getDatabaseIncludeList());
    expected_props.setProperty(
        DebeziumConfiguration.COLLECTION_INCLUDE_LIST, debeziumConfig.getCollectionIncludeList());
    expected_props.setProperty(DebeziumConfiguration.TRANSFORMS, "unwrap");
    expected_props.setProperty(DebeziumConfiguration.TRANSFORMS_UNWRAP_TYPE,
        DebeziumConfiguration.DEBEZIUM_CONNECTOR_MONGODB_TRANSFORMS_EXTRACT_NEW_DOCUMENT_STATE);
    expected_props.setProperty(DebeziumConfiguration.TRANSFORMS_UNWRAP_DROP_TOMBSTONES, "false");
    expected_props.setProperty(DebeziumConfiguration.TRANSFORMS_UNWRAP_ADD_HEADERS, "op");
    assertEquals(expected_props, props);
  }
}