#!/usr/bin/env bash
# Copyright 2021 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

CONFIG_FILE=/opt/harness/config.yml
REDISSON_CACHE_FILE=/opt/harness/redisson-jcache.yaml

replace_key_value () {
  CONFIG_KEY="$1";
  CONFIG_VALUE="$2";
  if [[ "" != "$CONFIG_VALUE" ]]; then
    export CONFIG_VALUE; export CONFIG_KEY; export CONFIG_KEY=.$CONFIG_KEY; yq -i 'eval(strenv(CONFIG_KEY))=env(CONFIG_VALUE)' $CONFIG_FILE
  fi
}

yq -i '.server.adminConnectors=[]' $CONFIG_FILE

if [[ "" != "$LOGGING_LEVEL" ]]; then
    export LOGGING_LEVEL; yq -i '.logging.level=env(LOGGING_LEVEL)' $CONFIG_FILE
fi

if [[ "" != "$LOGGERS" ]]; then
  IFS=',' read -ra LOGGER_ITEMS <<< "$LOGGERS"
  for ITEM in "${LOGGER_ITEMS[@]}"; do
    LOGGER=`echo $ITEM | awk -F= '{print $1}'`
    LOGGER_LEVEL=`echo $ITEM | awk -F= '{print $2}'`
    export LOGGER_LEVEL; export LOGGER; yq -i '.logging.loggers.[env(LOGGER)]=env(LOGGER_LEVEL)' $CONFIG_FILE
  done
fi

if [[ "$STACK_DRIVER_LOGGING_ENABLED" == "true" ]]; then
  yq -i 'del(.logging.appenders.[] | select(.type == "console"))' $CONFIG_FILE
  yq -i '(.logging.appenders.[] | select(.type == "gke-console") | .stackdriverLogEnabled) = true' $CONFIG_FILE
else
  yq -i 'del(.logging.appenders.[] | select(.type == "gke-console"))' $CONFIG_FILE
fi

if [[ "" != "$EVENTS_FRAMEWORK_REDIS_SENTINELS" ]]; then
  IFS=',' read -ra SENTINEL_URLS <<< "$EVENTS_FRAMEWORK_REDIS_SENTINELS"
  INDEX=0
  for REDIS_SENTINEL_URL in "${SENTINEL_URLS[@]}"; do
    export REDIS_SENTINEL_URL; export INDEX; yq -i '.eventsFramework.redis.sentinelUrls.[env(INDEX)]=env(REDIS_SENTINEL_URL)' $CONFIG_FILE
    INDEX=$(expr $INDEX + 1)
  done
fi

yq -i 'del(.codec)' $REDISSON_CACHE_FILE

if [[ "$REDIS_SCRIPT_CACHE" == "false" ]]; then
  yq -i '.useScriptCache=false' $REDISSON_CACHE_FILE
fi


if [[ "" != "$CACHE_CONFIG_REDIS_URL" ]]; then
  export CACHE_CONFIG_REDIS_URL; yq -i '.singleServerConfig.address=env(CACHE_CONFIG_REDIS_URL)' $REDISSON_CACHE_FILE
fi

if [[ "$CACHE_CONFIG_USE_SENTINEL" == "true" ]]; then
  yq -i 'del(.singleServerConfig)' $REDISSON_CACHE_FILE
fi

if [[ "" != "$CACHE_CONFIG_SENTINEL_MASTER_NAME" ]]; then
  export CACHE_CONFIG_SENTINEL_MASTER_NAME; yq -i '.sentinelServersConfig.masterName=env(CACHE_CONFIG_SENTINEL_MASTER_NAME)' $REDISSON_CACHE_FILE
fi

if [[ "" != "$CACHE_CONFIG_REDIS_SENTINELS" ]]; then
  IFS=',' read -ra SENTINEL_URLS <<< "$CACHE_CONFIG_REDIS_SENTINELS"
  INDEX=0
  for REDIS_SENTINEL_URL in "${SENTINEL_URLS[@]}"; do
    export REDIS_SENTINEL_URL; export INDEX; yq -i '.sentinelServersConfig.sentinelAddresses.[env(INDEX)]=env(REDIS_SENTINEL_URL)' $REDISSON_CACHE_FILE
    INDEX=$(expr $INDEX + 1)
  done
fi

if [[ "" != "$REDIS_NETTY_THREADS" ]]; then
  export REDIS_NETTY_THREADS; yq -i '.nettyThreads=env(REDIS_NETTY_THREADS)' $REDISSON_CACHE_FILE
fi

if [[ "" != "$REDIS_CONNECTION_POOL_SIZE" ]]; then
  export REDIS_CONNECTION_POOL_SIZE; yq -i '.singleServerConfig.connectionPoolSize=env(REDIS_CONNECTION_POOL_SIZE)' $REDISSON_CACHE_FILE
fi

if [[ "" != "$REDIS_RETRY_INTERVAL" ]]; then
  export REDIS_RETRY_INTERVAL; yq -i '.singleServerConfig.retryInterval=env(REDIS_RETRY_INTERVAL)' $REDISSON_CACHE_FILE
fi

if [[ "" != "$REDIS_RETRY_ATTEMPTS" ]]; then
  export REDIS_RETRY_ATTEMPTS; yq -i '.singleServerConfig.retryAttempts=env(REDIS_RETRY_ATTEMPTS)' $REDISSON_CACHE_FILE
fi

if [[ "" != "$REDIS_TIMEOUT" ]]; then
  export REDIS_TIMEOUT; yq -i '.singleServerConfig.timeout=env(REDIS_TIMEOUT)' $REDISSON_CACHE_FILE
fi

replace_key_value eventsFramework.redis.sentinel $EVENTS_FRAMEWORK_USE_SENTINEL
replace_key_value eventsFramework.redis.envNamespace $EVENTS_FRAMEWORK_ENV_NAMESPACE
replace_key_value eventsFramework.redis.redisUrl $EVENTS_FRAMEWORK_REDIS_URL
replace_key_value eventsFramework.redis.masterName $EVENTS_FRAMEWORK_SENTINEL_MASTER_NAME
replace_key_value eventsFramework.redis.userName $EVENTS_FRAMEWORK_REDIS_USERNAME
replace_key_value eventsFramework.redis.password $EVENTS_FRAMEWORK_REDIS_PASSWORD
replace_key_value eventsFramework.redis.sslConfig.enabled $EVENTS_FRAMEWORK_REDIS_SSL_ENABLED
replace_key_value eventsFramework.redis.sslConfig.CATrustStorePath $EVENTS_FRAMEWORK_REDIS_SSL_CA_TRUST_STORE_PATH
replace_key_value eventsFramework.redis.sslConfig.CATrustStorePassword $EVENTS_FRAMEWORK_REDIS_SSL_CA_TRUST_STORE_PASSWORD

replace_key_value redisLockConfig.sentinel $REDIS_LOCK_CONFIG_USE_SENTINEL
replace_key_value redisLockConfig.envNamespace $REDIS_LOCK_CONFIG_ENV_NAMESPACE
replace_key_value redisLockConfig.redisUrl $REDIS_LOCK_CONFIG_REDIS_URL
replace_key_value redisLockConfig.masterName $REDIS_LOCK_CONFIG_SENTINEL_MASTER_NAME
replace_key_value redisLockConfig.userName $REDIS_LOCK_CONFIG_REDIS_USERNAME
replace_key_value redisLockConfig.password $REDIS_LOCK_CONFIG_REDIS_PASSWORD
replace_key_value redisLockConfig.useScriptCache $REDIS_LOCK_CONFIG_REDIS_USE_SCRIPT_CACHE
replace_key_value redisLockConfig.connectionPoolSize $REDIS_CONNECTION_POOL_SIZE
replace_key_value redisLockConfig.retryInterval $REDIS_RETRY_INTERVAL
replace_key_value redisLockConfig.retryAttempts $REDIS_RETRY_ATTEMPTS
replace_key_value redisLockConfig.timeout $REDIS_TIMEOUT

replace_key_value cfClientConfig.apiKey "$CF_CLIENT_API_KEY"
replace_key_value cfClientConfig.configUrl "$CF_CLIENT_CONFIG_URL"
replace_key_value cfClientConfig.eventUrl "$CF_CLIENT_EVENT_URL"
replace_key_value cfClientConfig.analyticsEnabled "$CF_CLIENT_ANALYTICS_ENABLED"
replace_key_value cfClientConfig.connectionTimeout "$CF_CLIENT_CONNECTION_TIMEOUT"
replace_key_value cfClientConfig.readTimeout "$CF_CLIENT_READ_TIMEOUT"
replace_key_value cfClientConfig.bufferSize "$CF_CLIENT_BUFFER_SIZE"

replace_key_value featureFlagConfig.featureFlagSystem "$FEATURE_FLAG_SYSTEM"
replace_key_value featureFlagConfig.syncFeaturesToCF "$SYNC_FEATURES_TO_CF"

if [[ "" != "$REDIS_LOCK_CONFIG_REDIS_SENTINELS" ]]; then
  IFS=',' read -ra SENTINEL_URLS <<< "$REDIS_LOCK_CONFIG_REDIS_SENTINELS"
  INDEX=0
  for REDIS_SENTINEL_URL in "${SENTINEL_URLS[@]}"; do
    export REDIS_SENTINEL_URL; export INDEX; yq -i '.redisLockConfig.sentinelUrls.[env(INDEX)]=env(REDIS_SENTINEL_URL)' $CONFIG_FILE
    INDEX=$(expr $INDEX + 1)
  done
fi

replace_key_value distributedLockImplementation $DISTRIBUTED_LOCK_IMPLEMENTATION
