#!/usr/bin/env bash
# Copyright 2021 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

CONFIG_FILE=/opt/harness/config.yml

if [[ "$STACK_DRIVER_LOGGING_ENABLED" == "true" ]]; then
  yq delete -i $CONFIG_FILE 'logging.appenders.(type==console)'
  yq write -i $CONFIG_FILE 'logging.appenders.(type==gke-console).stackdriverLogEnabled' "true"
else
  yq delete -i $CONFIG_FILE 'logging.appenders.(type==gke-console)'
fi

# Remove the TLS connector (as ingress terminates TLS)
yq delete -i $CONFIG_FILE connectors[0]

if [[ "" != "$MONGO_URI" ]]; then
  yq write -i $CONFIG_FILE harness-mongo.uri "$MONGO_URI"
fi

if [[ "" != "$MONGO_SERVER_SELECTION_TIMEOUT" ]]; then
  yq write -i $CONFIG_FILE harness-mongo.serverSelectionTimeout "$MONGO_SERVER_SELECTION_TIMEOUT"
fi

if [[ "" != "$MONGO_TAG_NAME" ]]; then
  yq write -i $CONFIG_FILE mongotags.tagKey "$MONGO_TAG_NAME"
fi

if [[ "" != "$MONGO_TAG_VALUE" ]]; then
  yq write -i $CONFIG_FILE mongotags.tagValue "$MONGO_TAG_VALUE"
fi

if [[ "" != "$MONGO_INDEX_MANAGER_MODE" ]]; then
  yq write -i $CONFIG_FILE harness-mongo.indexManagerMode $MONGO_INDEX_MANAGER_MODE
fi

if [[ "" != "$EVEMTS_MONGO_INDEX_MANAGER_MODE" ]]; then
  yq write -i $CONFIG_FILE events-mongo.indexManagerMode $EVEMTS_MONGO_INDEX_MANAGER_MODE
fi

if [[ "" != "$EVENTS_MONGO_URI" ]]; then
  yq write -i $CONFIG_FILE events-mongo.uri "$EVENTS_MONGO_URI"
fi

if [[ "" != "$PMS_MONGO_URI" ]]; then
  yq write -i $CONFIG_FILE pms-harness.uri "$PMS_MONGO_URI"
fi

if [[ "" != "$CDC_MONGO_URI" ]]; then
  yq write -i $CONFIG_FILE cdc-mongo.uri "$CDC_MONGO_URI"
fi

if [[ "" != "$TIMESCALEDB_URI" ]]; then
  yq write -i $CONFIG_FILE timescaledb.timescaledbUrl "$TIMESCALEDB_URI"
fi

if [[ "" != "$TIMESCALEDB_USERNAME" ]]; then
  yq write -i $CONFIG_FILE timescaledb.timescaledbUsername "$TIMESCALEDB_USERNAME"
fi

if [[ "" != "$TIMESCALEDB_PASSWORD" ]]; then
  yq write -i $CONFIG_FILE timescaledb.timescaledbPassword "$TIMESCALEDB_PASSWORD"
fi

if [[ "" != "$GCP_PROJECT_ID" ]]; then
  yq write -i $CONFIG_FILE gcp-project-id "$GCP_PROJECT_ID"
fi

if [[ "" != "$NG_HARNESS_MONGO_URI" ]]; then
  yq write -i $CONFIG_FILE ng-harness.uri  "$NG_HARNESS_MONGO_URI"
fi
