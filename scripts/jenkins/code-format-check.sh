#!/usr/bin/env bash
# Copyright 2021 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

BASEDIR=$(pwd -L)

retryCount=0
timestamp=$(date +%d-%m-%Y_%H-%M-%S)
logPath="/var/log/format_${timestamp}.log"
#logPath="check.log"
#repoPath="~/.m2/"
grepStr="Could not resolve dependencies for project"
echo "Starting Format Checks" > "$logPath"

executeWithRetry() {
  command=$1
  set +e
  #echo "mvn $MAVEN_ARGS $command -Dmaven.repo.local=$repoPath >> $logPath"
  #mvn $MAVEN_ARGS $1 -Dmaven.repo.local=$repoPath >> $logPath
  result="$?"
  set -e
  if [ $result -ne 0 ]; then
    if grep -q "$grepStr" "$logPath"; then
      if [ $retryCount -lt 1 ]; then
        grep "$grepStr" "$logPath"
        printf "Installing modules and Retrying once......................"
        #mvn install -DskipTests -Dmaven.repo.local=$repoPath
        echo "$command"
        retryCount=$((retryCount+1))
        executeWithRetry "$command"
      else
        cat "$logPath"
        printf "Cannot fix dependency after retrying"
        exit 1
      fi
    else
      cat "$logPath"
      printf "No dependency Error Reported. But script exited in error"
      exit 1
    fi
  fi
}

validate_proto() {
  file=$1

  if ! grep 'syntax = "proto3";' ${file} > /dev/null
  then
    echo ${file} needs to use: syntax = \"proto3\";
    exit 1;
  fi

  if ! grep "option java_multiple_files = true;" ${file} > /dev/null
  then
    echo ${file} needs to use: option java_multiple_files = true;
    exit 1;
  fi
}

#echo "Running Sort Pom"
#executeWithRetry 'sortpom:sort'
#echo "Sort Pom Completed"

find . -iname "*.graphql" | xargs -L 1 prettier --write --print-width=120

find . \( -iname "*.java" -o -iname "*.proto" \) | xargs clang-format -i

bazel run //:buildifier

git diff --exit-code

if [ -n "$(git status --porcelain --untracked-files=no)" ]; then
  echo "There are changes identified due to code format issue"
  echo "Please rerun code-formatter and update the PR to fix it"
  exit 1
fi

find . \( -iname "*.proto" -a -not -regex ".*/target/.*" \) |\
    grep -v src/main/proto/log_analysis_record.proto |\
    grep -v src/main/proto/time_series_record.proto |\
    while read file; do validate_proto "$file"; done

ISSUES=`buf check lint`

if [ ! -z "${ISSUES}" ]
then
  echo $ISSUES
  exit 1
fi
