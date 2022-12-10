/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.aws;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.ConnectorValidationResult;
import io.harness.connector.task.aws.AwsValidationHandler;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.connector.awsconnector.AwsCFTaskParamsRequest;
import io.harness.delegate.beans.connector.awsconnector.AwsListASGInstancesTaskParamsRequest;
import io.harness.delegate.beans.connector.awsconnector.AwsListEC2InstancesTaskParamsRequest;
import io.harness.delegate.beans.connector.awsconnector.AwsListTagsTaskParamsRequest;
import io.harness.delegate.beans.connector.awsconnector.AwsPutAuditBatchToBucketTaskParamsRequest;
import io.harness.delegate.beans.connector.awsconnector.AwsTaskParams;
import io.harness.delegate.beans.connector.awsconnector.AwsTaskType;
import io.harness.delegate.beans.connector.awsconnector.AwsValidateTaskResponse;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.common.AbstractDelegateRunnableTask;
import io.harness.exception.InvalidRequestException;
import io.harness.security.encryption.EncryptedDataDetail;

import com.google.inject.Inject;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;

@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class AwsDelegateTask extends AbstractDelegateRunnableTask {
  @Inject private AwsValidationHandler awsValidationHandler;
  @Inject private AwsS3DelegateTaskHelper awsS3DelegateTaskHelper;
  @Inject private AwsIAMDelegateTaskHelper awsIAMDelegateTaskHelper;
  @Inject private AwsCFDelegateTaskHelper awsCFDelegateTaskHelper;
  @Inject private AwsListEC2InstancesDelegateTaskHelper awsListEC2InstancesDelegateTaskHelper;
  @Inject private AwsASGDelegateTaskHelper awsASGDelegateTaskHelper;
  @Inject private AwsListVpcDelegateTaskHelper awsListVpcDelegateTaskHelper;
  @Inject private AwsListTagsDelegateTaskHelper awsListTagsDelegateTaskHelper;
  @Inject private AwsListLoadBalancersDelegateTaskHelper awsListLoadBalancersDelegateTaskHelper;
  @Inject private AwsECSDelegateTaskHelper awsECSDelegateTaskHelper;
  @Inject private AwsElasticLoadBalancersDelegateTaskHelper awsElasticLoadBalancersDelegateTaskHelper;

  public AwsDelegateTask(DelegateTaskPackage delegateTaskPackage, ILogStreamingTaskClient logStreamingTaskClient,
      Consumer<DelegateTaskResponse> consumer, BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  public DelegateResponseData run(Object[] parameters) {
    throw new NotImplementedException("Not implemented");
  }

  @Override
  public boolean isSupportingErrorFramework() {
    return true;
  }

  @Override
  public DelegateResponseData run(TaskParameters parameters) {
    final AwsTaskParams awsTaskParams = (AwsTaskParams) parameters;
    final AwsTaskType awsTaskType = awsTaskParams.getAwsTaskType();
    if (Objects.isNull(awsTaskType)) {
      throw new InvalidRequestException("Task type not provided");
    }

    final List<EncryptedDataDetail> encryptionDetails = awsTaskParams.getEncryptionDetails();
    switch (awsTaskType) {
      // TODO: we can move this to factory method using guice mapbinder later
      case VALIDATE:
        return handleValidateTask(awsTaskParams, encryptionDetails);
      case LIST_S3_BUCKETS:
        return awsS3DelegateTaskHelper.getS3Buckets(awsTaskParams);
      case GET_BUILD:
        return awsS3DelegateTaskHelper.getBuild(awsTaskParams);
      case GET_BUILDS:
        return awsS3DelegateTaskHelper.getBuilds(awsTaskParams);
      case LAST_SUCCESSFUL_BUILD:
        return awsS3DelegateTaskHelper.getLastSuccessfulBuild(awsTaskParams);
      case LIST_IAM_ROLES:
        return awsIAMDelegateTaskHelper.getIAMRoleList(awsTaskParams);
      case CF_LIST_PARAMS:
        return awsCFDelegateTaskHelper.getCFParamsList((AwsCFTaskParamsRequest) parameters);
      case LIST_EC2_INSTANCES:
        return awsListEC2InstancesDelegateTaskHelper.getInstances((AwsListEC2InstancesTaskParamsRequest) parameters);
      case LIST_ASG_INSTANCES:
        return awsASGDelegateTaskHelper.getInstances((AwsListASGInstancesTaskParamsRequest) parameters);
      case LIST_ASG_NAMES:
        return awsASGDelegateTaskHelper.getASGNames(awsTaskParams);
      case LIST_VPC:
        return awsListVpcDelegateTaskHelper.getVpcList(awsTaskParams);
      case LIST_TAGS:
        return awsListTagsDelegateTaskHelper.getTagList((AwsListTagsTaskParamsRequest) awsTaskParams);
      case LIST_LOAD_BALANCERS:
        return awsListLoadBalancersDelegateTaskHelper.getLoadBalancerList(awsTaskParams);
      case LIST_ECS_CLUSTERS:
        return awsECSDelegateTaskHelper.getEcsClustersList(awsTaskParams);
      case LIST_ELASTIC_LOAD_BALANCERS:
        return awsElasticLoadBalancersDelegateTaskHelper.getElbList(awsTaskParams);
      case LIST_ELASTIC_LOAD_BALANCER_LISTENERS:
        return awsElasticLoadBalancersDelegateTaskHelper.getElbListenerList(awsTaskParams);
      case LIST_ELASTIC_LOAD_BALANCER_LISTENER_RULE:
        return awsElasticLoadBalancersDelegateTaskHelper.getElbListenerRulesList(awsTaskParams);
      case PUT_AUDIT_BATCH_TO_BUCKET:
        return awsS3DelegateTaskHelper.putAuditBatchToBucket((AwsPutAuditBatchToBucketTaskParamsRequest) awsTaskParams);
      default:
        throw new InvalidRequestException("Task type not identified");
    }
  }

  public DelegateResponseData handleValidateTask(
      AwsTaskParams awsTaskParams, List<EncryptedDataDetail> encryptionDetails) {
    ConnectorValidationResult connectorValidationResult =
        awsValidationHandler.validate(awsTaskParams, encryptionDetails);
    connectorValidationResult.setDelegateId(getDelegateId());
    return AwsValidateTaskResponse.builder().connectorValidationResult(connectorValidationResult).build();
  }
}
