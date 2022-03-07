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

set -e

SHDIR=$(dirname "$0")
PROJFILE="$SHDIR/jira-projects.txt"
check_file_present $PROJFILE
PROJECTS=$(<$PROJFILE)

for line in `git branch -r | grep "release/on-prem" |grep ".xx$"| tail -${PREV_BRANCHES_COUNT}`;
        do
               echo "Onprem branch is $line"
         git log --remotes=$line* --pretty=oneline --abbrev-commit | grep -iE "\[(${PROJECTS})-[0-9]+]:" -o | sort | uniq | tr '[:lower:]' '[:upper:]' >> release_onprem_temp.txt
done

cat release_onprem_temp.txt | sort | uniq >release_onprem.txt


echo "Saas branch is ${SAAS_BRANCH}"

git log --remotes=origin/${SAAS_BRANCH}* --pretty=oneline --abbrev-commit | grep -iE "\[(${PROJECTS})-[0-9]+]:" -o | sort | uniq | tr '[:lower:]' '[:upper:]' > release_saas.txt

cat release_onprem.txt | wc -l
cat release_saas.txt | wc -l


NOT_MERGED=`comm -23 release_onprem.txt release_saas.txt | tr '\n' ' '`



if [ -z "$NOT_MERGED" ]
then
      echo "All onprem hotfixes are exist in ${SAAS_BRANCH} saas branch " > envvars
else
      echo NOT_MERGED="${NOT_MERGED}" > envvars
fi
