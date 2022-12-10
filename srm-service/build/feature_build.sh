#!/usr/bin/env bash
# Copyright 2021 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

set -ex

# GCP_KEY, HARNESS_WILD_CERT, KEYSTORE_PASS, JDK, VERSION, PURPOSE, BUILD are externally provided
# to this script through environment variables.

echo $GCP_KEY | base64 -d > /tmp/storage_secret.json
export GCP_KEY="/tmp/storage_secret.json"

echo $HARNESS_WILD_CERT | base64 -d > /harness/harness_wild.p12
export KEY_STORE="/harness/harness_wild.p12"
export KEY_STORE_PASSWORD=$KEYSTORE_PASS

export BUILD_NAME=$(git rev-parse --abbrev-ref HEAD)

export IMAGE_TAG=$VERSION-$BUILD_NAME

export GIT_BRANCH=$(git rev-parse --abbrev-ref HEAD)
export GIT_COMMIT=$(git rev-parse HEAD)

echo "--------------------------------------"
echo $JDK $BUILD $VERSION $PURPOSE $GIT_BRANCH $GIT_COMMIT $(date)
echo "--------------------------------------"

scripts/bazel/generate_credentials.sh

echo \ >> bazelrc.remote
echo build --google_credentials=/tmp/storage_secret.json >> bazelrc.remote
cat bazelrc.remote

yum install zlib-devel -y

chmod +x scripts/bazel/UpdateVersionInfoyaml.sh
chmod +x srm-service/build/build_jar.sh
chmod +x srm-service/build/build_dist.sh

scripts/bazel/UpdateVersionInfoyaml.sh $BUILD srm-service/build.properties
srm-service/build/build_jar.sh
srm-service/build/build_dist.sh || true

echo "INFO: list the jars built"
find . -name "*.jar"
