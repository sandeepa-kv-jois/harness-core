/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.awsconnector;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

@OwnedBy(HarnessTeam.CDP)
public enum AwsTaskType {
  VALIDATE,
  LIST_S3_BUCKETS,
  GET_BUILDS,
  GET_BUILD,
  LAST_SUCCESSFUL_BUILD,
  LIST_IAM_ROLES,
  CF_LIST_PARAMS,
  LIST_EC2_INSTANCES,
  LIST_ASG_INSTANCES,
  LIST_ASG_NAMES,
  LIST_VPC,
  LIST_TAGS,
  LIST_LOAD_BALANCERS,
  LIST_ECS_CLUSTERS,
  LIST_ELASTIC_LOAD_BALANCERS,
  LIST_ELASTIC_LOAD_BALANCER_LISTENERS,
  LIST_ELASTIC_LOAD_BALANCER_LISTENER_RULE,
  PUT_AUDIT_BATCH_TO_BUCKET
}
