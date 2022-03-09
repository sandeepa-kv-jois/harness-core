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


KEYS=`git log --pretty=oneline --abbrev-commit |\
      awk "/${PREVIOUS_CUT_COMMIT_MESSAGE}/ {exit} {print}" |\
      grep -o -iE '('$PROJECTS')-[0-9]+' | sort | uniq`

LABLE="HOTFIX"

if [ "${PURPOSE}" = "saas" ]
then
    LABLE="SAAS_HOTFIX"
    FIELD_ID="customfield_10644"
    HOTFIX_FIELD="customfield_10773"
elif [ "${PURPOSE}" = "on-prem" ]
then
    LABLE="ONPREM_HOTFIX"
    FIELD_ID="customfield_10646"
    HOTFIX_FIELD="customfield_10775"
else
   echo "Unknown purpose ${PURPOSE}"
   exit 1
fi

echo $KEYS

echo $LABLE


for KEY in ${KEYS}
do
    echo $KEY
    curl \
       -X PUT \
       --data "{ \"update\": { \"labels\": [ {\"add\": \"${LABLE}\"} ] },\"fields\" : { \"${FIELD_ID}\" : \"${VERSION}\", \"${HOTFIX_FIELD}\" : \"${VERSION}\" }}" \
       -H "Content-Type: application/json" \
       https://harness.atlassian.net/rest/api/2/issue/${KEY} \
       --user $JIRA_USERNAME:$JIRA_PASSWORD
done
