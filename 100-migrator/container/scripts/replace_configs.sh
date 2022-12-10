#!/usr/bin/env bash
# Copyright 2021 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

CONFIG_FILE=/opt/harness/config.yml
NEWRELIC_FILE=/opt/harness/newrelic.yml
REDISSON_CACHE_FILE=/opt/harness/redisson-jcache.yaml

replace_key_value () {
  CONFIG_KEY="$1";
  CONFIG_VALUE="$2";
  if [[ "" != "$CONFIG_VALUE" ]]; then
    export CONFIG_VALUE; export CONFIG_KEY; export CONFIG_KEY=.$CONFIG_KEY; yq -i 'eval(strenv(CONFIG_KEY))=env(CONFIG_VALUE)' $CONFIG_FILE
  fi
}

yq -i 'del(.server.applicationConnectors[0])' $CONFIG_FILE
yq -i 'del(.grpcServerConfig.connectors[0])' $CONFIG_FILE

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

if [[ "" != "$SERVER_PORT" ]]; then
  export SERVER_PORT; yq -i '.server.applicationConnectors[0].port=env(SERVER_PORT)' $CONFIG_FILE
else
  yq -i '.server.applicationConnectors[0].port=9090' $CONFIG_FILE
fi

if [[ "" != "$GRPC_SERVER_PORT" ]]; then
  export GRPC_SERVER_PORT; yq -i '.grpcServerConfig.connectors[0].port=env(GRPC_SERVER_PORT)' $CONFIG_FILE
fi

if [[ "" != "$SERVER_MAX_THREADS" ]]; then
  export SERVER_MAX_THREADS; yq -i '.server.maxThreads=env(SERVER_MAX_THREADS)' $CONFIG_FILE
fi

if [[ "" != "$UI_SERVER_URL" ]]; then
  export UI_SERVER_URL; yq -i '.portal.url=env(UI_SERVER_URL)' $CONFIG_FILE
fi

if [[ "" != "$AUTHTOKENEXPIRYINMILLIS" ]]; then
  export AUTHTOKENEXPIRYINMILLIS; yq -i '.portal.authTokenExpiryInMillis=env(AUTHTOKENEXPIRYINMILLIS)' $CONFIG_FILE
fi

if [[ "" != "$EXTERNAL_GRAPHQL_RATE_LIMIT" ]]; then
  export EXTERNAL_GRAPHQL_RATE_LIMIT; yq -i '.portal.externalGraphQLRateLimitPerMinute=env(EXTERNAL_GRAPHQL_RATE_LIMIT)' $CONFIG_FILE
fi

if [[ "" != "$CUSTOM_DASH_GRAPHQL_RATE_LIMIT" ]]; then
  export CUSTOM_DASH_GRAPHQL_RATE_LIMIT; yq -i '.portal.customDashGraphQLRateLimitPerMinute=env(CUSTOM_DASH_GRAPHQL_RATE_LIMIT)' $CONFIG_FILE
fi

if [[ "" != "$ALLOWED_ORIGINS" ]]; then
  export ALLOWED_ORIGINS; yq -i '.portal.allowedOrigins=env(ALLOWED_ORIGINS)' $CONFIG_FILE
fi

if [[ "" != "$STORE_REQUEST_PAYLOAD" ]]; then
  export STORE_REQUEST_PAYLOAD; yq -i '.auditConfig.storeRequestPayload=env(STORE_REQUEST_PAYLOAD)' $CONFIG_FILE
fi

if [[ "" != "$STORE_RESPONSE_PAYLOAD" ]]; then
  export STORE_RESPONSE_PAYLOAD; yq -i '.auditConfig.storeResponsePayload=env(STORE_RESPONSE_PAYLOAD)' $CONFIG_FILE
fi

if [[ "" != "$MONGO_URI" ]]; then
  export MONGO_URI=${MONGO_URI//\\&/&}; yq -i '.mongo.uri=env(MONGO_URI)' $CONFIG_FILE
fi

if [[ "" != "$MONGO_TRACE_MODE" ]]; then
  export MONGO_TRACE_MODE; yq -i '.mongo.traceMode=env(MONGO_TRACE_MODE)' $CONFIG_FILE
fi

if [[ "" != "$MONGO_SSL_CONFIG" ]]; then
  export MONGO_SSL_CONFIG; yq -i '.mongo.mongoSSLConfig.mongoSSLEnabled=env(MONGO_SSL_CONFIG)' $CONFIG_FILE
fi

if [[ "" != "$MONGO_SSL_CA_TRUST_STORE_PATH" ]]; then
  export MONGO_SSL_CA_TRUST_STORE_PATH; yq -i '.mongo.mongoSSLConfig.mongoTrustStorePath=env(MONGO_SSL_CA_TRUST_STORE_PATH)' $CONFIG_FILE
fi

if [[ "" != "$MONGO_SSL_CA_TRUST_STORE_PASSWORD" ]]; then
  export MONGO_SSL_CA_TRUST_STORE_PASSWORD; yq -i '.mongo.mongoSSLConfig.mongoTrustStorePassword=env(MONGO_SSL_CA_TRUST_STORE_PASSWORD)' $CONFIG_FILE
fi

if [[ "" != "$MONGO_CONNECT_TIMEOUT" ]]; then
  export MONGO_CONNECT_TIMEOUT; yq -i '.mongo.connectTimeout=env(MONGO_CONNECT_TIMEOUT)' $CONFIG_FILE
fi

if [[ "" != "$MONGO_SERVER_SELECTION_TIMEOUT" ]]; then
  export MONGO_SERVER_SELECTION_TIMEOUT; yq -i '.mongo.serverSelectionTimeout=env(MONGO_SERVER_SELECTION_TIMEOUT)' $CONFIG_FILE
fi

if [[ "" != "$MONGO_SOCKET_TIMEOUT" ]]; then
  export MONGO_SOCKET_TIMEOUT; yq -i '.mongo.socketTimeout=env(MONGO_SOCKET_TIMEOUT)' $CONFIG_FILE
fi

if [[ "" != "$MAX_CONNECTION_IDLE_TIME" ]]; then
  export MAX_CONNECTION_IDLE_TIME; yq -i '.mongo.maxConnectionIdleTime=env(MAX_CONNECTION_IDLE_TIME)' $CONFIG_FILE
fi

if [[ "" != "$MONGO_CONNECTIONS_PER_HOST" ]]; then
  export MONGO_CONNECTIONS_PER_HOST; yq -i '.mongo.connectionsPerHost=env(MONGO_CONNECTIONS_PER_HOST)' $CONFIG_FILE
fi

if [[ "" != "$MONGO_INDEX_MANAGER_MODE" ]]; then
  export MONGO_INDEX_MANAGER_MODE; yq -i '.mongo.indexManagerMode=env(MONGO_INDEX_MANAGER_MODE)' $CONFIG_FILE
fi

if [[ "" != "$EVEMTS_MONGO_INDEX_MANAGER_MODE" ]]; then
  export EVEMTS_MONGO_INDEX_MANAGER_MODE; yq -i '.events-mongo.indexManagerMode=env(EVEMTS_MONGO_INDEX_MANAGER_MODE)' $CONFIG_FILE
fi

if [[ "" != "$EVENTS_MONGO_URI" ]]; then
  export EVENTS_MONGO_URI; yq -i '.events-mongo.uri=env(EVENTS_MONGO_URI)' $CONFIG_FILE
else
  yq -i 'del(.events-mongo)' $CONFIG_FILE
fi

if [[ "" != "$CF_CLIENT_API_KEY" ]]; then
  export CF_CLIENT_API_KEY; yq -i '.cfClientConfig.apiKey=env(CF_CLIENT_API_KEY)' $CONFIG_FILE
fi

if [[ "" != "$CF_CLIENT_CONFIG_URL" ]]; then
  export CF_CLIENT_CONFIG_URL; yq -i '.cfClientConfig.configUrl=env(CF_CLIENT_CONFIG_URL)' $CONFIG_FILE
fi

if [[ "" != "$CF_CLIENT_BUFFER_SIZE" ]]; then
  export CF_CLIENT_BUFFER_SIZE; yq -i '.cfClientConfig.bufferSize=env(CF_CLIENT_BUFFER_SIZE)' $CONFIG_FILE
fi

if [[ "" != "$CF_CLIENT_EVENT_URL" ]]; then
  export CF_CLIENT_EVENT_URL; yq -i '.cfClientConfig.eventUrl=env(CF_CLIENT_EVENT_URL)' $CONFIG_FILE
fi

if [[ "" != "$CF_CLIENT_ANALYTICS_ENABLED" ]]; then
  export CF_CLIENT_ANALYTICS_ENABLED; yq -i '.cfClientConfig.analyticsEnabled=env(CF_CLIENT_ANALYTICS_ENABLED)' $CONFIG_FILE
fi

if [[ "" != "$CF_CLIENT_CONNECTION_TIMEOUT" ]]; then
  export CF_CLIENT_CONNECTION_TIMEOUT; yq -i '.cfClientConfig.connectionTimeout=env(CF_CLIENT_CONNECTION_TIMEOUT)' $CONFIG_FILE
fi

if [[ "" != "$CF_CLIENT_READ_TIMEOUT" ]]; then
  export CF_CLIENT_READ_TIMEOUT; yq -i '.cfClientConfig.readTimeout=env(CF_CLIENT_READ_TIMEOUT)' $CONFIG_FILE
fi

if [[ "" != "$CF_MIGRATION_ENABLED" ]]; then
  export CF_MIGRATION_ENABLED; yq -i '.cfMigrationConfig.enabled=env(CF_MIGRATION_ENABLED)' $CONFIG_FILE
fi

if [[ "" != "$CF_MIGRATION_ADMIN_URL" ]]; then
  export CF_MIGRATION_ADMIN_URL; yq -i '.cfMigrationConfig.adminUrl=env(CF_MIGRATION_ADMIN_URL)' $CONFIG_FILE
fi

if [[ "" != "$CF_MIGRATION_API_KEY" ]]; then
  export CF_MIGRATION_API_KEY; yq -i '.cfMigrationConfig.apiKey=env(CF_MIGRATION_API_KEY)' $CONFIG_FILE
fi

if [[ "" != "$CF_MIGRATION_ACCOUNT" ]]; then
  export CF_MIGRATION_ACCOUNT; yq -i '.cfMigrationConfig.account=env(CF_MIGRATION_ACCOUNT)' $CONFIG_FILE
fi

if [[ "" != "$CF_MIGRATION_ORG" ]]; then
  export CF_MIGRATION_ORG; yq -i '.cfMigrationConfig.org=env(CF_MIGRATION_ORG)' $CONFIG_FILE
fi

if [[ "" != "$CF_MIGRATION_PROJECT" ]]; then
  export CF_MIGRATION_PROJECT; yq -i '.cfMigrationConfig.project=env(CF_MIGRATION_PROJECT)' $CONFIG_FILE
fi

if [[ "" != "$CF_MIGRATION_ENVIRONMENT" ]]; then
  export CF_MIGRATION_ENVIRONMENT; yq -i '.cfMigrationConfig.environment=env(CF_MIGRATION_ENVIRONMENT)' $CONFIG_FILE
fi

replace_key_value featureFlagConfig.featureFlagSystem "$FEATURE_FLAG_SYSTEM"
replace_key_value featureFlagConfig.syncFeaturesToCF "$SYNC_FEATURES_TO_CF"


if [[ "" != "$ELASTICSEARCH_URI" ]]; then
  export ELASTICSEARCH_URI; yq -i '.elasticsearch.uri=env(ELASTICSEARCH_URI)' $CONFIG_FILE
fi

if [[ "" != "$ELASTICSEARCH_INDEX_SUFFIX" ]]; then
  export ELASTICSEARCH_INDEX_SUFFIX; yq -i '.elasticsearch.indexSuffix=env(ELASTICSEARCH_INDEX_SUFFIX)' $CONFIG_FILE
fi

if [[ "" != "$ELASTICSEARCH_MONGO_TAG_NAME" ]]; then
 export ELASTICSEARCH_MONGO_TAG_NAME; yq -i '.elasticsearch.mongoTagKey=env(ELASTICSEARCH_MONGO_TAG_NAME)' $CONFIG_FILE
fi

if [[ "" != "$ELASTICSEARCH_MONGO_TAG_VALUE" ]]; then
 export ELASTICSEARCH_MONGO_TAG_VALUE; yq -i '.elasticsearch.mongoTagValue=env(ELASTICSEARCH_MONGO_TAG_VALUE)' $CONFIG_FILE
fi

if [[ "" != "$MONGO_LOCK_URI" ]]; then
  export MONGO_LOCK_URI=${MONGO_LOCK_URI//\\&/&}; yq -i '.mongo.locksUri=env(MONGO_LOCK_URI)' $CONFIG_FILE
fi

yq -i '.server.requestLog.appenders[0].threshold="TRACE"' $CONFIG_FILE

if [[ "$STACK_DRIVER_LOGGING_ENABLED" == "true" ]]; then
  yq -i 'del(.logging.appenders[2])' $CONFIG_FILE
  yq -i 'del(.logging.appenders[0])' $CONFIG_FILE
  yq -i '.logging.appenders[0].stackdriverLogEnabled=true' $CONFIG_FILE
else
  if [[ "$ROLLING_FILE_LOGGING_ENABLED" == "true" ]]; then
    yq -i 'del(.logging.appenders[1])' $CONFIG_FILE
    yq -i '.logging.appenders[1].currentLogFilename="/opt/harness/logs/portal.log"' $CONFIG_FILE
    yq -i '.logging.appenders[1].archivedLogFilenamePattern="/opt/harness/logs/portal.%d.%i.log"' $CONFIG_FILE
  else
    yq -i 'del(.logging.appenders[2])' $CONFIG_FILE
    yq -i 'del(.logging.appenders[1])' $CONFIG_FILE
  fi
fi

if [[ "" != "$WATCHER_METADATA_URL" ]]; then
  export WATCHER_METADATA_URL; yq -i '.watcherMetadataUrl=env(WATCHER_METADATA_URL)' $CONFIG_FILE
fi

if [[ "" != "$DELEGATE_METADATA_URL" ]]; then
  export DELEGATE_METADATA_URL; yq -i '.delegateMetadataUrl=env(DELEGATE_METADATA_URL)' $CONFIG_FILE
fi

if [[ "" != "$API_URL" ]]; then
  export API_URL; yq -i '.apiUrl=env(API_URL)' $CONFIG_FILE
fi

if [[ "" != "$ENV_PATH" ]]; then
  export ENV_PATH; yq -i '.envPath=env(ENV_PATH)' $CONFIG_FILE
fi

if [[ "" != "$DEPLOY_MODE" ]]; then
  export DEPLOY_MODE; yq -i '.deployMode=env(DEPLOY_MODE)' $CONFIG_FILE
fi

if [[ "" != "$KUBECTL_VERSION" ]]; then
  export KUBECTL_VERSION; yq -i '.kubectlVersion=env(KUBECTL_VERSION)' $CONFIG_FILE
fi

if [[ "" != "$NEWRELIC_LICENSE_KEY" ]]; then
  export NEWRELIC_LICENSE_KEY; yq -i '.common.license_key=env(NEWRELIC_LICENSE_KEY)' $NEWRELIC_FILE
fi

if [[ "$DISABLE_NEW_RELIC" == "true" ]]; then
  yq -i '.common.agent_enabled=false' $NEWRELIC_FILE
fi

if [[ "" != "$jwtPasswordSecret" ]]; then
  export jwtPasswordSecret; yq -i '.portal.jwtPasswordSecret=env(jwtPasswordSecret)' $CONFIG_FILE
fi

if [[ "" != "$jwtExternalServiceSecret" ]]; then
  export jwtExternalServiceSecret; yq -i '.portal.jwtExternalServiceSecret=env(jwtExternalServiceSecret)' $CONFIG_FILE
fi

if [[ "" != "$jwtZendeskSecret" ]]; then
  export jwtZendeskSecret; yq -i '.portal.jwtZendeskSecret=env(jwtZendeskSecret)' $CONFIG_FILE
fi

if [[ "" != "$jwtMultiAuthSecret" ]]; then
  export jwtMultiAuthSecret; yq -i '.portal.jwtMultiAuthSecret=env(jwtMultiAuthSecret)' $CONFIG_FILE
fi

if [[ "" != "$jwtSsoRedirectSecret" ]]; then
  export jwtSsoRedirectSecret; yq -i '.portal.jwtSsoRedirectSecret=env(jwtSsoRedirectSecret)' $CONFIG_FILE
fi

if [[ "" != "$jwtAuthSecret" ]]; then
  export jwtAuthSecret; yq -i '.portal.jwtAuthSecret=env(jwtAuthSecret)' $CONFIG_FILE
fi

if [[ "" != "$jwtMarketPlaceSecret" ]]; then
  export jwtMarketPlaceSecret; yq -i '.portal.jwtMarketPlaceSecret=env(jwtMarketPlaceSecret)' $CONFIG_FILE
fi

if [[ "" != "$jwtIdentityServiceSecret" ]]; then
  export jwtIdentityServiceSecret; yq -i '.portal.jwtIdentityServiceSecret=env(jwtIdentityServiceSecret)' $CONFIG_FILE
fi

if [[ "" != "$jwtDataHandlerSecret" ]]; then
  export jwtDataHandlerSecret; yq -i '.portal.jwtDataHandlerSecret=env(jwtDataHandlerSecret)' $CONFIG_FILE
fi

if [[ "" != "$jwtNextGenManagerSecret" ]]; then
  export jwtNextGenManagerSecret; yq -i '.portal.jwtNextGenManagerSecret=env(jwtNextGenManagerSecret)' $CONFIG_FILE
fi

if [[ "" != "$FEATURES" ]]; then
  export FEATURES; yq -i '.featuresEnabled=env(FEATURES)' $CONFIG_FILE
fi

if [[ "" != "$SAMPLE_TARGET_ENV" ]]; then
  export SAMPLE_TARGET_ENV; yq -i '.sampleTargetEnv=env(SAMPLE_TARGET_ENV)' $CONFIG_FILE
fi

if [[ "" != "$SAMPLE_TARGET_STATUS_HOST" ]]; then
  export SAMPLE_TARGET_STATUS_HOST; yq -i '.sampleTargetStatusHost=env(SAMPLE_TARGET_STATUS_HOST)' $CONFIG_FILE
fi

if [[ "" != "$GLOBAL_WHITELIST" ]]; then
  export GLOBAL_WHITELIST; yq -i '.globalWhitelistConfig.filters=env(GLOBAL_WHITELIST)' $CONFIG_FILE
fi

if [[ "" != "$SMTP_HOST" ]]; then
  export SMTP_HOST; yq -i '.smtp.host=env(SMTP_HOST)' $CONFIG_FILE
fi

if [[ "" != "$SMTP_USERNAME" ]]; then
  export SMTP_USERNAME; yq -i '.smtp.username=env(SMTP_USERNAME)' $CONFIG_FILE
fi

if [[ "" != "$SMTP_PASSWORD" ]]; then
  export SMTP_PASSWORD; yq -i '.smtp.password=env(SMTP_PASSWORD)' $CONFIG_FILE
fi

if [[ "" != "$SMTP_USE_SSL" ]]; then
  export SMTP_USE_SSL; yq -i '.smtp.useSSL=env(SMTP_USE_SSL)' $CONFIG_FILE
fi

if [[ "" != "$MARKETO_ENABLED" ]]; then
  export MARKETO_ENABLED; yq -i '.marketoConfig.enabled=env(MARKETO_ENABLED)' $CONFIG_FILE
fi

if [[ "" != "$MARKETO_URL" ]]; then
  export MARKETO_URL; yq -i '.marketoConfig.url=env(MARKETO_URL)' $CONFIG_FILE
fi

if [[ "" != "$MARKETO_CLIENT_ID" ]]; then
  export MARKETO_CLIENT_ID; yq -i '.marketoConfig.clientId=env(MARKETO_CLIENT_ID)' $CONFIG_FILE
fi

if [[ "" != "$MARKETO_CLIENT_SECRET" ]]; then
  export MARKETO_CLIENT_SECRET; yq -i '.marketoConfig.clientSecret=env(MARKETO_CLIENT_SECRET)' $CONFIG_FILE
fi

if [[ "" != "$SEGMENT_ENABLED" ]]; then
  export SEGMENT_ENABLED; yq -i '.segmentConfig.enabled=env(SEGMENT_ENABLED)' $CONFIG_FILE
fi

if [[ "" != "$SEGMENT_URL" ]]; then
  export SEGMENT_URL; yq -i '.segmentConfig.url=env(SEGMENT_URL)' $CONFIG_FILE
fi

if [[ "" != "$SEGMENT_APIKEY" ]]; then
  export SEGMENT_APIKEY; yq -i '.segmentConfig.apiKey=env(SEGMENT_APIKEY)' $CONFIG_FILE
fi

if [[ "" != "$SALESFORCE_USERNAME" ]]; then
  export SALESFORCE_USERNAME; yq -i '.salesforceConfig.userName=env(SALESFORCE_USERNAME)' $CONFIG_FILE
fi

if [[ "" != "$SALESFORCE_PASSWORD" ]]; then
  export SALESFORCE_PASSWORD; yq -i '.salesforceConfig.password=env(SALESFORCE_PASSWORD)' $CONFIG_FILE
fi

if [[ "" != "$SALESFORCE_CONSUMER_KEY" ]]; then
  export SALESFORCE_CONSUMER_KEY; yq -i '.salesforceConfig.consumerKey=env(SALESFORCE_CONSUMER_KEY)' $CONFIG_FILE
fi

if [[ "" != "$SALESFORCE_CONSUMER_SECRET" ]]; then
  export SALESFORCE_CONSUMER_SECRET; yq -i '.salesforceConfig.consumerSecret=env(SALESFORCE_CONSUMER_SECRET)' $CONFIG_FILE
fi

if [[ "" != "$SALESFORCE_GRANT_TYPE" ]]; then
  export SALESFORCE_GRANT_TYPE; yq -i '.salesforceConfig.grantType=env(SALESFORCE_GRANT_TYPE)' $CONFIG_FILE
fi

if [[ "" != "$SALESFORCE_LOGIN_INSTANCE_DOMAIN" ]]; then
  export SALESFORCE_LOGIN_INSTANCE_DOMAIN; yq -i '.salesforceConfig.loginInstanceDomain=env(SALESFORCE_LOGIN_INSTANCE_DOMAIN)' $CONFIG_FILE
fi

if [[ "" != "$SALESFORCE_API_VERSION" ]]; then
  export SALESFORCE_API_VERSION; yq -i '.salesforceConfig.apiVersion=env(SALESFORCE_API_VERSION)' $CONFIG_FILE
fi

if [[ "" != "$SALESFORCE_INTEGRATION_ENABLED" ]]; then
  export SALESFORCE_INTEGRATION_ENABLED; yq -i '.salesforceConfig.enabled=env(SALESFORCE_INTEGRATION_ENABLED)' $CONFIG_FILE
fi

if [[ "" != "$CE_SETUP_CONFIG_AWS_ACCOUNT_ID" ]]; then
  export CE_SETUP_CONFIG_AWS_ACCOUNT_ID; yq -i '.ceSetUpConfig.awsAccountId=env(CE_SETUP_CONFIG_AWS_ACCOUNT_ID)' $CONFIG_FILE
fi

if [[ "" != "$CE_SETUP_CONFIG_AWS_S3_BUCKET_NAME" ]]; then
  export CE_SETUP_CONFIG_AWS_S3_BUCKET_NAME; yq -i '.ceSetUpConfig.awsS3BucketName=env(CE_SETUP_CONFIG_AWS_S3_BUCKET_NAME)' $CONFIG_FILE
fi

if [[ "" != "$CE_SETUP_CONFIG_GCP_PROJECT_ID" ]]; then
  export CE_SETUP_CONFIG_GCP_PROJECT_ID; yq -i '.ceSetUpConfig.gcpProjectId=env(CE_SETUP_CONFIG_GCP_PROJECT_ID)' $CONFIG_FILE
fi

if [[ "" != "$CE_SETUP_CONFIG_AWS_ROLE_NAME" ]]; then
  export CE_SETUP_CONFIG_AWS_ROLE_NAME; yq -i '.ceSetUpConfig.awsRoleName=env(CE_SETUP_CONFIG_AWS_ROLE_NAME)' $CONFIG_FILE
fi

if [[ "" != "$CE_SETUP_CONFIG_SAMPLE_ACCOUNT_ID" ]]; then
  export CE_SETUP_CONFIG_SAMPLE_ACCOUNT_ID; yq -i '.ceSetUpConfig.sampleAccountId=env(CE_SETUP_CONFIG_SAMPLE_ACCOUNT_ID)' $CONFIG_FILE
fi

if [[ "" != "$CE_SETUP_CONFIG_AWS_ACCESS_KEY" ]]; then
  export CE_SETUP_CONFIG_AWS_ACCESS_KEY; yq -i '.ceSetUpConfig.awsAccessKey=env(CE_SETUP_CONFIG_AWS_ACCESS_KEY)' $CONFIG_FILE
fi

if [[ "" != "$CE_SETUP_CONFIG_AWS_SECRET_KEY" ]]; then
  export CE_SETUP_CONFIG_AWS_SECRET_KEY; yq -i '.ceSetUpConfig.awsSecretKey=env(CE_SETUP_CONFIG_AWS_SECRET_KEY)' $CONFIG_FILE
fi

if [[ "" != "$CE_SETUP_CONFIG_MASTER_CLOUD_FORMATION" ]]; then
  export CE_SETUP_CONFIG_MASTER_CLOUD_FORMATION; yq -i '.ceSetUpConfig.masterAccountCloudFormationTemplateLink=env(CE_SETUP_CONFIG_MASTER_CLOUD_FORMATION)' $CONFIG_FILE
fi

if [[ "" != "$CE_SETUP_CONFIG_LINKED_CLOUD_FORMATION" ]]; then
  export CE_SETUP_CONFIG_LINKED_CLOUD_FORMATION; yq -i '.ceSetUpConfig.linkedAccountCloudFormationTemplateLink=env(CE_SETUP_CONFIG_LINKED_CLOUD_FORMATION)' $CONFIG_FILE
fi

if [[ "" != "$CE_SETUP_CONFIG_AZURE_CLIENTSECRET" ]]; then
  export CE_SETUP_CONFIG_AZURE_CLIENTSECRET; yq -i '.ceSetUpConfig.azureAppClientSecret=env(CE_SETUP_CONFIG_AZURE_CLIENTSECRET)' $CONFIG_FILE
fi

if [[ "" != "$CE_SETUP_CONFIG_AZURE_CLIENTID" ]]; then
  export CE_SETUP_CONFIG_AZURE_CLIENTID; yq -i '.ceSetUpConfig.azureAppClientId=env(CE_SETUP_CONFIG_AZURE_CLIENTID)' $CONFIG_FILE
fi

if [[ "" != "$DATADOG_ENABLED" ]]; then
  export DATADOG_ENABLED; yq -i '.datadogConfig.enabled=env(DATADOG_ENABLED)' $CONFIG_FILE
fi

if [[ "" != "$DATADOG_APIKEY" ]]; then
  export DATADOG_APIKEY; yq -i '.datadogConfig.apiKey=env(DATADOG_APIKEY)' $CONFIG_FILE
fi

if [[ "" != "$DELEGATE_DOCKER_IMAGE" ]]; then
  export DELEGATE_DOCKER_IMAGE; yq -i '.portal.delegateDockerImage=env(DELEGATE_DOCKER_IMAGE)' $CONFIG_FILE
fi

if [[ "" != "$OPTIONAL_DELEGATE_TASK_REJECT_AT_LIMIT" ]]; then
  export OPTIONAL_DELEGATE_TASK_REJECT_AT_LIMIT; yq -i '.portal.optionalDelegateTaskRejectAtLimit=env(OPTIONAL_DELEGATE_TASK_REJECT_AT_LIMIT)' $CONFIG_FILE
fi

if [[ "" != "$EXECUTION_LOG_DATA_STORE" ]]; then
  export EXECUTION_LOG_DATA_STORE; yq -i '.executionLogStorageMode=env(EXECUTION_LOG_DATA_STORE)' $CONFIG_FILE
fi

if [[ "" != "$FILE_STORAGE" ]]; then
  export FILE_STORAGE; yq -i '.fileStorageMode=env(FILE_STORAGE)' $CONFIG_FILE
fi

if [[ "" != "$CLUSTER_NAME" ]]; then
  export CLUSTER_NAME; yq -i '.clusterName=env(CLUSTER_NAME)' $CONFIG_FILE
fi

if [[ "" != "$DEPLOYMENT_CLUSTER_NAME" ]]; then
  export DEPLOYMENT_CLUSTER_NAME; yq -i '.deploymentClusterName=env(DEPLOYMENT_CLUSTER_NAME)' $CONFIG_FILE
fi

if [[ "" != "$BACKGROUND_SCHEDULER_CLUSTERED" ]]; then
  export BACKGROUND_SCHEDULER_CLUSTERED; yq -i '.backgroundScheduler.clustered=env(BACKGROUND_SCHEDULER_CLUSTERED)' $CONFIG_FILE
fi

if [[ "" != "$ENABLE_CRONS" ]]; then
  export ENABLE_CRONS; yq -i '.enableIterators=env(ENABLE_CRONS)' $CONFIG_FILE
  export ENABLE_CRONS; yq -i '.backgroundScheduler.enabled=env(ENABLE_CRONS)' $CONFIG_FILE
  export ENABLE_CRONS; yq -i '.serviceScheduler.enabled=env(ENABLE_CRONS)' $CONFIG_FILE
fi

if [[ "" != "$ALLOW_TRIAL_REGISTRATION" ]]; then
  export ALLOW_TRIAL_REGISTRATION; yq -i '.trialRegistrationAllowed=env(ALLOW_TRIAL_REGISTRATION)' $CONFIG_FILE
fi

if [[ "" != "$EVENTS_FRAMEWORK_AVAILABLE_IN_ONPREM" ]]; then
  export EVENTS_FRAMEWORK_AVAILABLE_IN_ONPREM; yq -i '.eventsFrameworkAvailableInOnPrem=env(EVENTS_FRAMEWORK_AVAILABLE_IN_ONPREM)' $CONFIG_FILE
else
  yq -i '.eventsFrameworkAvailableInOnPrem=false' $CONFIG_FILE
fi

if [[ "" != "$ALLOW_TRIAL_REGISTRATION_FOR_BUGATHON" ]]; then
  export ALLOW_TRIAL_REGISTRATION_FOR_BUGATHON; yq -i '.trialRegistrationAllowedForBugathon=env(ALLOW_TRIAL_REGISTRATION_FOR_BUGATHON)' $CONFIG_FILE
fi

if [[ "" != "$GITHUB_OAUTH_CLIENT" ]]; then
  export GITHUB_OAUTH_CLIENT; yq -i '.githubConfig.clientId=env(GITHUB_OAUTH_CLIENT)' $CONFIG_FILE
fi

if [[ "" != "$GITHUB_OAUTH_SECRET" ]]; then
  export GITHUB_OAUTH_SECRET; yq -i '.githubConfig.clientSecret=env(GITHUB_OAUTH_SECRET)' $CONFIG_FILE
fi

if [[ "" != "$GITHUB_OAUTH_CALLBACK_URL" ]]; then
  export GITHUB_OAUTH_CALLBACK_URL; yq -i '.githubConfig.callbackUrl=env(GITHUB_OAUTH_CALLBACK_URL)' $CONFIG_FILE
fi

if [[ "" != "$AZURE_OAUTH_CLIENT" ]]; then
  export AZURE_OAUTH_CLIENT; yq -i '.azureConfig.clientId=env(AZURE_OAUTH_CLIENT)' $CONFIG_FILE
fi

if [[ "" != "$AZURE_OAUTH_SECRET" ]]; then
  export AZURE_OAUTH_SECRET; yq -i '.azureConfig.clientSecret=env(AZURE_OAUTH_SECRET)' $CONFIG_FILE
fi

if [[ "" != "$AZURE_OAUTH_CALLBACK_URL" ]]; then
  export AZURE_OAUTH_CALLBACK_URL; yq -i '.azureConfig.callbackUrl=env(AZURE_OAUTH_CALLBACK_URL)' $CONFIG_FILE
fi

if [[ "" != "$GOOGLE_OAUTH_CLIENT" ]]; then
  export GOOGLE_OAUTH_CLIENT; yq -i '.googleConfig.clientId=env(GOOGLE_OAUTH_CLIENT)' $CONFIG_FILE
fi

if [[ "" != "$GOOGLE_OAUTH_SECRET" ]]; then
  export GOOGLE_OAUTH_SECRET; yq -i '.googleConfig.clientSecret=env(GOOGLE_OAUTH_SECRET)' $CONFIG_FILE
fi

if [[ "" != "$GOOGLE_OAUTH_CALLBACK_URL" ]]; then
  export GOOGLE_OAUTH_CALLBACK_URL; yq -i '.googleConfig.callbackUrl=env(GOOGLE_OAUTH_CALLBACK_URL)' $CONFIG_FILE
fi

if [[ "" != "$BITBUCKET_OAUTH_CLIENT" ]]; then
  export BITBUCKET_OAUTH_CLIENT; yq -i '.bitbucketConfig.clientId=env(BITBUCKET_OAUTH_CLIENT)' $CONFIG_FILE
fi

if [[ "" != "$BITBUCKET_OAUTH_SECRET" ]]; then
  export BITBUCKET_OAUTH_SECRET; yq -i '.bitbucketConfig.clientSecret=env(BITBUCKET_OAUTH_SECRET)' $CONFIG_FILE
fi

if [[ "" != "$BITBUCKET_OAUTH_CALLBACK_URL" ]]; then
  export BITBUCKET_OAUTH_CALLBACK_URL; yq -i '.bitbucketConfig.callbackUrl=env(BITBUCKET_OAUTH_CALLBACK_URL)' $CONFIG_FILE
fi

if [[ "" != "$GITLAB_OAUTH_CLIENT" ]]; then
  export GITLAB_OAUTH_CLIENT; yq -i '.gitlabConfig.clientId=env(GITLAB_OAUTH_CLIENT)' $CONFIG_FILE
fi

if [[ "" != "$GITLAB_OAUTH_SECRET" ]]; then
  export GITLAB_OAUTH_SECRET; yq -i '.gitlabConfig.clientSecret=env(GITLAB_OAUTH_SECRET)' $CONFIG_FILE
fi

if [[ "" != "$GITLAB_OAUTH_CALLBACK_URL" ]]; then
  export GITLAB_OAUTH_CALLBACK_URL; yq -i '.gitlabConfig.callbackUrl=env(GITLAB_OAUTH_CALLBACK_URL)' $CONFIG_FILE
fi

if [[ "" != "$LINKEDIN_OAUTH_CLIENT" ]]; then
  export LINKEDIN_OAUTH_CLIENT; yq -i '.linkedinConfig.clientId=env(LINKEDIN_OAUTH_CLIENT)' $CONFIG_FILE
fi

if [[ "" != "$LINKEDIN_OAUTH_SECRET" ]]; then
  export LINKEDIN_OAUTH_SECRET; yq -i '.linkedinConfig.clientSecret=env(LINKEDIN_OAUTH_SECRET)' $CONFIG_FILE
fi

if [[ "" != "$LINKEDIN_OAUTH_CALLBACK_URL" ]]; then
  export LINKEDIN_OAUTH_CALLBACK_URL; yq -i '.linkedinConfig.callbackUrl=env(LINKEDIN_OAUTH_CALLBACK_URL)' $CONFIG_FILE
fi

if [[ "" != "$AWS_MARKETPLACE_ACCESSKEY" ]]; then
  export AWS_MARKETPLACE_ACCESSKEY; yq -i '.mktPlaceConfig.awsAccessKey=env(AWS_MARKETPLACE_ACCESSKEY)' $CONFIG_FILE
fi

if [[ "" != "$AWS_MARKETPLACE_SECRETKEY" ]]; then
  export AWS_MARKETPLACE_SECRETKEY; yq -i '.mktPlaceConfig.awsSecretKey=env(AWS_MARKETPLACE_SECRETKEY)' $CONFIG_FILE
fi

if [[ "" != "$AWS_MARKETPLACE_PRODUCTCODE" ]]; then
  export AWS_MARKETPLACE_PRODUCTCODE; yq -i '.mktPlaceConfig.awsMarketPlaceProductCode=env(AWS_MARKETPLACE_PRODUCTCODE)' $CONFIG_FILE
fi

if [[ "" != "$AWS_MARKETPLACE_CE_PRODUCTCODE" ]]; then
  export AWS_MARKETPLACE_CE_PRODUCTCODE; yq -i '.mktPlaceConfig.awsMarketPlaceCeProductCode=env(AWS_MARKETPLACE_CE_PRODUCTCODE)' $CONFIG_FILE
fi

if [[ "" != "$ALLOW_BLACKLISTED_EMAIL_DOMAINS" ]]; then
  export ALLOW_BLACKLISTED_EMAIL_DOMAINS; yq -i '.blacklistedEmailDomainsAllowed=env(ALLOW_BLACKLISTED_EMAIL_DOMAINS)' $CONFIG_FILE
fi

if [[ "" != "$ALLOW_PWNED_PASSWORDS" ]]; then
  export ALLOW_PWNED_PASSWORDS; yq -i '.pwnedPasswordsAllowed=env(ALLOW_PWNED_PASSWORDS)' $CONFIG_FILE
fi

if [[ "" != "$TIMESCALEDB_URI" ]]; then
  export TIMESCALEDB_URI; yq -i '.timescaledb.timescaledbUrl=env(TIMESCALEDB_URI)' $CONFIG_FILE
fi

if [[ "" != "$TIMESCALEDB_USERNAME" ]]; then
  export TIMESCALEDB_USERNAME; yq -i '.timescaledb.timescaledbUsername=env(TIMESCALEDB_USERNAME)' $CONFIG_FILE
fi

if [[ "" != "$TIMESCALEDB_PASSWORD" ]]; then
  export TIMESCALEDB_PASSWORD; yq -i '.timescaledb.timescaledbPassword=env(TIMESCALEDB_PASSWORD)' $CONFIG_FILE
fi

if [[ "" != "$TIMESCALEDB_CONNECT_TIMEOUT" ]]; then
  export TIMESCALEDB_CONNECT_TIMEOUT; yq -i '.timescaledb.connectTimeout=env(TIMESCALEDB_CONNECT_TIMEOUT)' $CONFIG_FILE
fi

if [[ "" != "$TIMESCALEDB_SOCKET_TIMEOUT" ]]; then
  export TIMESCALEDB_SOCKET_TIMEOUT; yq -i '.timescaledb.socketTimeout=env(TIMESCALEDB_SOCKET_TIMEOUT)' $CONFIG_FILE
fi

if [[ "" != "$TIMESCALEDB_LOGUNCLOSED" ]]; then
  export TIMESCALEDB_LOGUNCLOSED; yq -i '.timescaledb.logUnclosedConnections=env(TIMESCALEDB_LOGUNCLOSED)' $CONFIG_FILE
fi

if [[ "" != "$TIMESCALEDB_LOGGERLEVEL" ]]; then
  export TIMESCALEDB_LOGGERLEVEL; yq -i '.timescaledb.loggerLevel=env(TIMESCALEDB_LOGGERLEVEL)' $CONFIG_FILE
fi

if [[ "$TIMESCALEDB_HEALTH_CHECK_NEEDED" == "true" ]]; then
  export TIMESCALEDB_HEALTH_CHECK_NEEDED; yq -i '.timescaledb.isHealthCheckNeeded=env(TIMESCALEDB_HEALTH_CHECK_NEEDED)' $CONFIG_FILE
fi

if [[ "" != "$TIMESCALEDB_SSL_MODE" ]]; then
  export TIMESCALEDB_SSL_MODE; yq -i '.timescaledb.sslMode=env(TIMESCALEDB_SSL_MODE)' $CONFIG_FILE
fi

if [[ "" != "$TIMESCALEDB_SSL_ROOT_CERT" ]]; then
  export TIMESCALEDB_SSL_ROOT_CERT; yq -i '.timescaledb.sslRootCert=env(TIMESCALEDB_SSL_ROOT_CERT)' $CONFIG_FILE
fi

if [[ "$SEARCH_ENABLED" == "true" ]]; then
  yq -i '.searchEnabled=true' $CONFIG_FILE
fi

if [[ "$GRAPHQL_ENABLED" == "false" ]]; then
  yq -i '.graphQLEnabled=false' $CONFIG_FILE
fi

if [[ "$MONGO_DEBUGGING_ENABLED" == "true" ]]; then
  yq -i '.logging.loggers.["org.mongodb.morphia.query"]="TRACE"' $CONFIG_FILE
  yq -i '.logging.loggers.connection="TRACE"' $CONFIG_FILE
fi

if [[ "" != "$AZURE_MARKETPLACE_ACCESSKEY" ]]; then
  export AZURE_MARKETPLACE_ACCESSKEY; yq -i '.mktPlaceConfig.azureMarketplaceAccessKey=env(AZURE_MARKETPLACE_ACCESSKEY)' $CONFIG_FILE
fi

if [[ "" != "$AZURE_MARKETPLACE_SECRETKEY" ]]; then
  export AZURE_MARKETPLACE_SECRETKEY; yq -i '.mktPlaceConfig.azureMarketplaceSecretKey=env(AZURE_MARKETPLACE_SECRETKEY)' $CONFIG_FILE
fi

if [[ "" != "$WORKERS" ]]; then
  IFS=',' read -ra WORKER_ITEMS <<< "$WORKERS"
  for ITEM in "${WORKER_ITEMS[@]}"; do
    WORKER=`echo $ITEM | awk -F= '{print $1}'`
    WORKER_FLAG=`echo $ITEM | awk -F= '{print $2}'`
    export WORKER_FLAG; export WORKER; yq -i '.workers.active.env(WORKER)=env(WORKER_FLAG)' $CONFIG_FILE
  done
fi

if [[ "" != "$PUBLISHERS" ]]; then
  IFS=',' read -ra PUBLISHER_ITEMS <<< "$PUBLISHERS"
  for ITEM in "${PUBLISHER_ITEMS[@]}"; do
    PUBLISHER=`echo $ITEM | awk -F= '{print $1}'`
    PUBLISHER_FLAG=`echo $ITEM | awk -F= '{print $2}'`
    export PUBLISHER_FLAG; export PUBLISHER; yq -i '.publishers.active.env(PUBLISHER)=env(PUBLISHER_FLAG)' $CONFIG_FILE
  done
fi

if [[ "" != "$DISTRIBUTED_LOCK_IMPLEMENTATION" ]]; then
  export DISTRIBUTED_LOCK_IMPLEMENTATION; yq -i '.distributedLockImplementation=env(DISTRIBUTED_LOCK_IMPLEMENTATION)' $CONFIG_FILE
fi

if [[ "" != "$ATMOSPHERE_BACKEND" ]]; then
  export ATMOSPHERE_BACKEND; yq -i '.atmosphereBroadcaster=env(ATMOSPHERE_BACKEND)' $CONFIG_FILE
fi

yq -i 'del(.codec)' $REDISSON_CACHE_FILE

if [[ "" != "$REDIS_URL" ]]; then
  export REDIS_URL; yq -i '.redisLockConfig.redisUrl=env(REDIS_URL)' $CONFIG_FILE
  export REDIS_URL; yq -i '.redisAtmosphereConfig.redisUrl=env(REDIS_URL)' $CONFIG_FILE
  export REDIS_URL; yq -i '.singleServerConfig.address=env(REDIS_URL)' $REDISSON_CACHE_FILE
fi

if [[ "" != "$ATMOSPHERE_REDIS_URL" ]]; then
  export ATMOSPHERE_REDIS_URL; yq -i '.redisAtmosphereConfig.redisUrl=env(ATMOSPHERE_REDIS_URL)' $CONFIG_FILE
fi

if [[ "" != "$REDIS_CONNECTION_POOL_SIZE" ]]; then
  export REDIS_CONNECTION_POOL_SIZE; yq -i '.redisLockConfig.connectionPoolSize=env(REDIS_CONNECTION_POOL_SIZE)' $CONFIG_FILE
  export REDIS_CONNECTION_POOL_SIZE; yq -i '.redisAtmosphereConfig.connectionPoolSize=env(REDIS_CONNECTION_POOL_SIZE)' $CONFIG_FILE
  export REDIS_CONNECTION_POOL_SIZE; yq -i '.singleServerConfig.connectionPoolSize=env(REDIS_CONNECTION_POOL_SIZE)' $REDISSON_CACHE_FILE
fi

if [[ "" != "$REDIS_RETRY_INTERVAL" ]]; then
  export REDIS_RETRY_INTERVAL; yq -i '.redisLockConfig.retryInterval=env(REDIS_RETRY_INTERVAL)' $CONFIG_FILE
  export REDIS_RETRY_INTERVAL; yq -i '.redisAtmosphereConfig.retryInterval=env(REDIS_RETRY_INTERVAL)' $CONFIG_FILE
  export REDIS_RETRY_INTERVAL; yq -i '.singleServerConfig.retryInterval=env(REDIS_RETRY_INTERVAL)' $REDISSON_CACHE_FILE
fi

if [[ "" != "$REDIS_RETRY_ATTEMPTS" ]]; then
  export REDIS_RETRY_ATTEMPTS; yq -i '.redisLockConfig.retryAttempts=env(REDIS_RETRY_ATTEMPTS)' $CONFIG_FILE
  export REDIS_RETRY_ATTEMPTS; yq -i '.redisAtmosphereConfig.retryAttempts=env(REDIS_RETRY_ATTEMPTS)' $CONFIG_FILE
  export REDIS_RETRY_ATTEMPTS; yq -i '.singleServerConfig.retryAttempts=env(REDIS_RETRY_ATTEMPTS)' $REDISSON_CACHE_FILE
fi

if [[ "" != "$REDIS_TIMEOUT" ]]; then
  export REDIS_TIMEOUT; yq -i '.redisLockConfig.timeout=env(REDIS_TIMEOUT)' $CONFIG_FILE
  export REDIS_TIMEOUT; yq -i '.redisAtmosphereConfig.timeout=env(REDIS_TIMEOUT)' $CONFIG_FILE
  export REDIS_TIMEOUT; yq -i '.singleServerConfig.timeout=env(REDIS_TIMEOUT)' $REDISSON_CACHE_FILE
fi

if [[ "$REDIS_SENTINEL" == "true" ]]; then
  yq -i '.redisLockConfig.sentinel=true' $CONFIG_FILE
  yq -i '.redisAtmosphereConfig.sentinel=true' $CONFIG_FILE
  yq -i 'del(.singleServerConfig)' $REDISSON_CACHE_FILE
fi

if [[ "" != "$REDIS_MASTER_NAME" ]]; then
  export REDIS_MASTER_NAME; yq -i '.redisLockConfig.masterName=env(REDIS_MASTER_NAME)' $CONFIG_FILE
  export REDIS_MASTER_NAME; yq -i '.redisAtmosphereConfig.masterName=env(REDIS_MASTER_NAME)' $CONFIG_FILE
  export REDIS_MASTER_NAME; yq -i '.sentinelServersConfig.masterName=env(REDIS_MASTER_NAME)' $REDISSON_CACHE_FILE
fi

if [[ "" != "$REDIS_SENTINELS" ]]; then
  IFS=',' read -ra REDIS_SENTINEL_URLS <<< "$REDIS_SENTINELS"
  INDEX=0
  for REDIS_SENTINEL_URL in "${REDIS_SENTINEL_URLS[@]}"; do
    export REDIS_SENTINEL_URL; export INDEX; yq -i '.redisLockConfig.sentinelUrls.[env(INDEX)]=env(REDIS_SENTINEL_URL)' $CONFIG_FILE
    export REDIS_SENTINEL_URL; export INDEX; yq -i '.redisAtmosphereConfig.sentinelUrls.[env(INDEX)]=env(REDIS_SENTINEL_URL)' $CONFIG_FILE
    export REDIS_SENTINEL_URL; yq -i '.sentinelServersConfig.sentinelAddresses +=[env(REDIS_SENTINEL_URL)]' $REDISSON_CACHE_FILE
    INDEX=$(expr $INDEX + 1)
  done
fi

if [[ "" != "$REDIS_ENV_NAMESPACE" ]]; then
    export REDIS_ENV_NAMESPACE; yq -i '.redisLockConfig.envNamespace=env(REDIS_ENV_NAMESPACE)' $CONFIG_FILE
    export REDIS_ENV_NAMESPACE; yq -i '.redisAtmosphereConfig.envNamespace=env(REDIS_ENV_NAMESPACE)' $CONFIG_FILE
fi

if [[ "" != "$REDIS_NETTY_THREADS" ]]; then
  export REDIS_NETTY_THREADS; yq -i '.redisLockConfig.nettyThreads=env(REDIS_NETTY_THREADS)' $CONFIG_FILE
  export REDIS_NETTY_THREADS; yq -i '.redisAtmosphereConfig.nettyThreads=env(REDIS_NETTY_THREADS)' $CONFIG_FILE
  export REDIS_NETTY_THREADS; yq -i '.nettyThreads=env(REDIS_NETTY_THREADS)' $REDISSON_CACHE_FILE
fi

if [[ "$REDIS_SCRIPT_CACHE" == "false" ]]; then
  yq -i '.redisLockConfig.useScriptCache=false' $CONFIG_FILE
  yq -i '.redisAtmosphereConfig.useScriptCache=false' $CONFIG_FILE
  yq -i '.useScriptCache=false' $REDISSON_CACHE_FILE
fi

if [[ "" != "$CACHE_NAMESPACE" ]]; then
    export CACHE_NAMESPACE; yq -i '.cacheConfig.cacheNamespace=env(CACHE_NAMESPACE)' $CONFIG_FILE
fi

if [[ "" != "$CACHE_BACKEND" ]]; then
    export CACHE_BACKEND; yq -i '.cacheConfig.cacheBackend=env(CACHE_BACKEND)' $CONFIG_FILE
fi

if [[ "" != "$GCP_MARKETPLACE_ENABLED" ]]; then
    export GCP_MARKETPLACE_ENABLED; yq -i '.gcpMarketplaceConfig.enabled=env(GCP_MARKETPLACE_ENABLED)' $CONFIG_FILE
fi

if [[ "" != "$GCP_MARKETPLACE_SUBSCRIPTION_NAME" ]]; then
    export GCP_MARKETPLACE_SUBSCRIPTION_NAME; yq -i '.gcpMarketplaceConfig.subscriptionName=env(GCP_MARKETPLACE_SUBSCRIPTION_NAME)' $CONFIG_FILE
fi

if [[ "" != "$CURRENT_JRE" ]]; then
  export CURRENT_JRE; yq -i '.currentJre=env(CURRENT_JRE)' $CONFIG_FILE
fi

if [[ "" != "$MIGRATE_TO_JRE" ]]; then
  export MIGRATE_TO_JRE; yq -i '.migrateToJre=env(MIGRATE_TO_JRE)' $CONFIG_FILE
fi

if [[ "" != "$ORACLE_JRE_TAR_PATH" ]]; then
  export ORACLE_JRE_TAR_PATH; yq -i '.jreConfigs.oracle8u191.jreTarPath=env(ORACLE_JRE_TAR_PATH)' $CONFIG_FILE
fi

if [[ "" != "$OPENJDK_JRE_TAR_PATH" ]]; then
  export OPENJDK_JRE_TAR_PATH; yq -i '.jreConfigs.openjdk8u242.jreTarPath=env(OPENJDK_JRE_TAR_PATH)' $CONFIG_FILE
fi

if [[ "" != "$CDN_URL" ]]; then
  export CDN_URL; yq -i '.cdnConfig.url=env(CDN_URL)' $CONFIG_FILE
fi

if [[ "" != "$CDN_KEY" ]]; then
  export CDN_KEY; yq -i '.cdnConfig.keyName=env(CDN_KEY)' $CONFIG_FILE
fi

if [[ "" != "$CDN_KEY_SECRET" ]]; then
  export CDN_KEY_SECRET; yq -i '.cdnConfig.keySecret=env(CDN_KEY_SECRET)' $CONFIG_FILE
fi

if [[ "" != "$CDN_DELEGATE_JAR_PATH" ]]; then
  export CDN_DELEGATE_JAR_PATH; yq -i '.cdnConfig.delegateJarPath=env(CDN_DELEGATE_JAR_PATH)' $CONFIG_FILE
fi

if [[ "" != "$CDN_WATCHER_JAR_BASE_PATH" ]]; then
  export CDN_WATCHER_JAR_BASE_PATH; yq -i '.cdnConfig.watcherJarBasePath=env(CDN_WATCHER_JAR_BASE_PATH)' $CONFIG_FILE
fi

if [[ "" != "$CDN_WATCHER_JAR_PATH" ]]; then
  export CDN_WATCHER_JAR_PATH; yq -i '.cdnConfig.watcherJarPath=env(CDN_WATCHER_JAR_PATH)' $CONFIG_FILE
fi

if [[ "" != "$CDN_WATCHER_METADATA_FILE_PATH" ]]; then
  export CDN_WATCHER_METADATA_FILE_PATH; yq -i '.cdnConfig.watcherMetaDataFilePath=env(CDN_WATCHER_METADATA_FILE_PATH)' $CONFIG_FILE
fi

if [[ "" != "$CDN_ORACLE_JRE_TAR_PATH" ]]; then
  export CDN_ORACLE_JRE_TAR_PATH; yq -i '.cdnConfig.cdnJreTarPaths.oracle8u191=env(CDN_ORACLE_JRE_TAR_PATH)' $CONFIG_FILE
fi

if [[ "" != "$CDN_OPENJDK_JRE_TAR_PATH" ]]; then
  export CDN_OPENJDK_JRE_TAR_PATH; yq -i '.cdnConfig.cdnJreTarPaths.openjdk8u242=env(CDN_OPENJDK_JRE_TAR_PATH)' $CONFIG_FILE
fi

if [[ "" != "$COMMAND_LIBRARY_SERVICE_BASE_URL" ]]; then
  export COMMAND_LIBRARY_SERVICE_BASE_URL; yq -i '.commandLibraryServiceConfig.baseUrl=env(COMMAND_LIBRARY_SERVICE_BASE_URL)' $CONFIG_FILE
fi

if [[ "" != "$BUGSNAG_API_KEY" ]]; then
  export BUGSNAG_API_KEY; yq -i '.bugsnagApiKey=env(BUGSNAG_API_KEY)' $CONFIG_FILE
fi

if [[ "" != "$ACCOUNT_LICENSE_CHECK_JOB_FREQUENCY" ]]; then
  export ACCOUNT_LICENSE_CHECK_JOB_FREQUENCY; yq -i '.jobsFrequencyConfig.accountLicenseCheckJobFrequencyInMinutes=env(ACCOUNT_LICENSE_CHECK_JOB_FREQUENCY)' $CONFIG_FILE
fi

if [[ "" != "$ACCOUNT_DELETION_JOB_FREQUENCY" ]]; then
  export ACCOUNT_DELETION_JOB_FREQUENCY; yq -i '.jobsFrequencyConfig.accountDeletionJobFrequencyInMinutes=env(ACCOUNT_DELETION_JOB_FREQUENCY)' $CONFIG_FILE
fi

if [[ "" != "$MANAGER_TO_COMMAND_LIBRARY_SERVICE_SECRET" ]]; then
  export MANAGER_TO_COMMAND_LIBRARY_SERVICE_SECRET; yq -i '.commandLibraryServiceConfig.managerToCommandLibraryServiceSecret=env(MANAGER_TO_COMMAND_LIBRARY_SERVICE_SECRET)' $CONFIG_FILE
fi

if [[ "" != "$DELEGATE_SERVICE_TARGET" ]]; then
  export DELEGATE_SERVICE_TARGET; yq -i '.grpcDelegateServiceClientConfig.target=env(DELEGATE_SERVICE_TARGET)' $CONFIG_FILE
fi

if [[ "" != "$DELEGATE_SERVICE_AUTHORITY" ]]; then
  export DELEGATE_SERVICE_AUTHORITY; yq -i '.grpcDelegateServiceClientConfig.authority=env(DELEGATE_SERVICE_AUTHORITY)' $CONFIG_FILE
fi

if [[ "" != "$DELEGATE_SERVICE_MANAGEMENT_AUTHORITY" ]]; then
  export DELEGATE_SERVICE_MANAGEMENT_AUTHORITY; yq -i '.grpcDMSClientConfig.authority=env(DELEGATE_SERVICE_MANAGEMENT_AUTHORITY)' $CONFIG_FILE
fi

if [[ "" != "$DELEGATE_SERVICE_MANAGEMENT_TARGET" ]]; then
  export DELEGATE_SERVICE_MANAGEMENT_TARGET; yq -i '.grpcDMSClientConfig.target=env(DELEGATE_SERVICE_MANAGEMENT_TARGET)' $CONFIG_FILE
fi

if [[ "" != "$DELEGATE_SERVICE_MANAGEMENT_SECRET" ]]; then
  export DELEGATE_SERVICE_MANAGEMENT_SECRET; yq -i '.dmsSecret=env(DELEGATE_SERVICE_MANAGEMENT_SECRET)' $CONFIG_FILE
fi


if [[ "" != "$DELEGATE_GRPC_TARGET" ]]; then
  export DELEGATE_GRPC_TARGET; yq -i '.grpcOnpremDelegateClientConfig.target=env(DELEGATE_GRPC_TARGET)' $CONFIG_FILE
fi

if [[ "" != "$DELEGATE_GRPC_AUTHORITY" ]]; then
  export DELEGATE_GRPC_AUTHORITY; yq -i '.grpcOnpremDelegateClientConfig.authority=env(DELEGATE_GRPC_AUTHORITY)' $CONFIG_FILE
fi

if [[ "" != "$NG_MANAGER_AUTHORITY" ]]; then
  export NG_MANAGER_AUTHORITY; yq -i '.grpcClientConfig.authority=env(NG_MANAGER_AUTHORITY)' $CONFIG_FILE
fi

if [[ "" != "$NG_MANAGER_TARGET" ]]; then
  export NG_MANAGER_TARGET; yq -i '.grpcClientConfig.target=env(NG_MANAGER_TARGET)' $CONFIG_FILE
fi

if [[ "" != "$REMINDERS_BEFORE_ACCOUNT_DELETION" ]]; then
  export REMINDERS_BEFORE_ACCOUNT_DELETION; yq -i '.numberOfRemindersBeforeAccountDeletion=env(REMINDERS_BEFORE_ACCOUNT_DELETION)' $CONFIG_FILE
fi

if [[ "" != "$EXPORT_DATA_BATCH_SIZE" ]]; then
  export EXPORT_DATA_BATCH_SIZE; yq -i '.exportAccountDataBatchSize=env(EXPORT_DATA_BATCH_SIZE)' $CONFIG_FILE
fi

if [[ "" != "$COMMAND_LIBRARY_PUBLISHING_ALLOWED" ]]; then
  export COMMAND_LIBRARY_PUBLISHING_ALLOWED; yq -i '.commandLibraryServiceConfig.publishingAllowed=env(COMMAND_LIBRARY_PUBLISHING_ALLOWED)' $CONFIG_FILE
fi

if [[ "" != "$COMMAND_LIBRARY_PUBLISHING_SECRET" ]]; then
  export COMMAND_LIBRARY_PUBLISHING_SECRET; yq -i '.commandLibraryServiceConfig.publishingSecret=env(COMMAND_LIBRARY_PUBLISHING_SECRET)' $CONFIG_FILE
fi

if [[ "" != "$LOG_STREAMING_SERVICE_BASEURL" ]]; then
  export LOG_STREAMING_SERVICE_BASEURL; yq -i '.logStreamingServiceConfig.baseUrl=env(LOG_STREAMING_SERVICE_BASEURL)' $CONFIG_FILE
fi

if [[ "" != "$LOG_STREAMING_SERVICE_TOKEN" ]]; then
  export LOG_STREAMING_SERVICE_TOKEN; yq -i '.logStreamingServiceConfig.serviceToken=env(LOG_STREAMING_SERVICE_TOKEN)' $CONFIG_FILE
fi

if [[ "" != "$ACCESS_CONTROL_ENABLED" ]]; then
  export ACCESS_CONTROL_ENABLED; yq -i '.accessControlClient.enableAccessControl=env(ACCESS_CONTROL_ENABLED)' $CONFIG_FILE
fi

if [[ "" != "$ACCESS_CONTROL_BASE_URL" ]]; then
  export ACCESS_CONTROL_BASE_URL; yq -i '.accessControlClient.accessControlServiceConfig.baseUrl=env(ACCESS_CONTROL_BASE_URL)' $CONFIG_FILE
fi

if [[ "" != "$ACCESS_CONTROL_SECRET" ]]; then
  export ACCESS_CONTROL_SECRET; yq -i '.accessControlClient.accessControlServiceSecret=env(ACCESS_CONTROL_SECRET)' $CONFIG_FILE
fi

if [[ "" != "$ENABLE_AUDIT" ]]; then
  export ENABLE_AUDIT; yq -i '.enableAudit=env(ENABLE_AUDIT)' $CONFIG_FILE
fi

if [[ "" != "$EVENTS_FRAMEWORK_REDIS_SENTINELS" ]]; then
  IFS=',' read -ra SENTINEL_URLS <<< "$EVENTS_FRAMEWORK_REDIS_SENTINELS"
  INDEX=0
  for REDIS_SENTINEL_URL in "${SENTINEL_URLS[@]}"; do
    export REDIS_SENTINEL_URL; export INDEX; yq -i '.eventsFramework.redis.sentinelUrls.[env(INDEX)]=env(REDIS_SENTINEL_URL)' $CONFIG_FILE
    INDEX=$(expr $INDEX + 1)
  done
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
replace_key_value ngAuthUIEnabled "$HARNESS_ENABLE_NG_AUTH_UI_PLACEHOLDER"
replace_key_value portal.gatewayPathPrefix "$GATEWAY_PATH_PREFIX"
replace_key_value portal.zendeskBaseUrl "$ZENDESK_BASE_URL"

if [[ "" != "$NG_MANAGER_BASE_URL" ]]; then
  export NG_MANAGER_BASE_URL; yq -i '.ngManagerServiceHttpClientConfig.baseUrl=env(NG_MANAGER_BASE_URL)' $CONFIG_FILE
fi

if [[ "" != "$ENABLE_USER_CHANGESTREAM" ]]; then
  export ENABLE_USER_CHANGESTREAM; yq -i '.userChangeStreamEnabled=env(ENABLE_USER_CHANGESTREAM)' $CONFIG_FILE
fi

if [[ "" != "$DISABLE_DELEGATE_MGMT_IN_MANAGER" ]]; then
  export DISABLE_DELEGATE_MGMT_IN_MANAGER; yq -i '.disableDelegateMgmtInManager=env(DISABLE_DELEGATE_MGMT_IN_MANAGER)' $CONFIG_FILE
fi

if [[ "" != "$LDAP_GROUP_SYNC_INTERVAL" ]]; then
  export LDAP_GROUP_SYNC_INTERVAL; yq -i '.ldapSyncJobConfig.syncInterval=env(LDAP_GROUP_SYNC_INTERVAL)' $CONFIG_FILE
fi

if [[ "" != "$LDAP_GROUP_SYNC_POOL_SIZE" ]]; then
  export LDAP_GROUP_SYNC_POOL_SIZE; yq -i '.ldapSyncJobConfig.poolSize=env(LDAP_GROUP_SYNC_POOL_SIZE)' $CONFIG_FILE
fi

if [[ "" != "$LDAP_GROUP_SYNC_DEFAULT_CRON" ]]; then
  export LDAP_GROUP_SYNC_DEFAULT_CRON; yq -i '.ldapSyncJobConfig.defaultCronExpression=env(LDAP_GROUP_SYNC_DEFAULT_CRON)' $CONFIG_FILE
fi

replace_key_value cg.enableOpentelemetry "$ENABLE_OPENTELEMETRY"
