#!/bin/bash
# Copyright 2021 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

function check_file_present(){
     local_file=$1
     if [ ! -f "$local_file" ]; then
        echo "ERROR: $LINENO: File $local_file not found. Exiting"
        exit 1
     fi
}

SHDIR=$(dirname "$0")
PROJFILE="$SHDIR/jira-projects.txt"
check_file_present $PROJFILE
PROJECTS=$(<$PROJFILE)

KEY=`git log --pretty=oneline --abbrev-commit -1 |\
  grep -o -iE "\[(${PROJECTS})-[0-9]+]:" | grep -o -iE "(${PROJECTS})-[0-9]+"`

if [ -z "$KEY" ]; then
  KEY=`git rev-parse --abbrev-ref HEAD | grep -o -iE "(${PROJECTS})-[0-9]+"`
fi

FIELD_ID="customfield_10769"
if [ ! -z "$KEY" ]
then
  echo $KEY
  curl \
    -X PUT \
    --data "{ \"fields\" : { \"${FIELD_ID}\" : \"${FEATURE_BUILD_VALUE}\" }}" \
    -H "Content-Type: application/json" \
    https://harness.atlassian.net/rest/api/2/issue/${KEY} \
    --user $JIRA_USERNAME:$JIRA_PASSWORD
fi
