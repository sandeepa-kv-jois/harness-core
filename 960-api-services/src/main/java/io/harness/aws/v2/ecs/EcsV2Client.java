/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.aws.v2.ecs;

import io.harness.aws.beans.AwsInternalConfig;

import java.util.List;
import software.amazon.awssdk.core.waiters.WaiterResponse;
import software.amazon.awssdk.services.applicationautoscaling.model.DeleteScalingPolicyRequest;
import software.amazon.awssdk.services.applicationautoscaling.model.DeleteScalingPolicyResponse;
import software.amazon.awssdk.services.applicationautoscaling.model.DeregisterScalableTargetRequest;
import software.amazon.awssdk.services.applicationautoscaling.model.DeregisterScalableTargetResponse;
import software.amazon.awssdk.services.applicationautoscaling.model.DescribeScalableTargetsRequest;
import software.amazon.awssdk.services.applicationautoscaling.model.DescribeScalableTargetsResponse;
import software.amazon.awssdk.services.applicationautoscaling.model.DescribeScalingPoliciesRequest;
import software.amazon.awssdk.services.applicationautoscaling.model.DescribeScalingPoliciesResponse;
import software.amazon.awssdk.services.applicationautoscaling.model.PutScalingPolicyRequest;
import software.amazon.awssdk.services.applicationautoscaling.model.PutScalingPolicyResponse;
import software.amazon.awssdk.services.applicationautoscaling.model.RegisterScalableTargetRequest;
import software.amazon.awssdk.services.applicationautoscaling.model.RegisterScalableTargetResponse;
import software.amazon.awssdk.services.ecs.model.CreateServiceRequest;
import software.amazon.awssdk.services.ecs.model.CreateServiceResponse;
import software.amazon.awssdk.services.ecs.model.DeleteServiceRequest;
import software.amazon.awssdk.services.ecs.model.DeleteServiceResponse;
import software.amazon.awssdk.services.ecs.model.DescribeServicesRequest;
import software.amazon.awssdk.services.ecs.model.DescribeServicesResponse;
import software.amazon.awssdk.services.ecs.model.DescribeTasksResponse;
import software.amazon.awssdk.services.ecs.model.ListServicesRequest;
import software.amazon.awssdk.services.ecs.model.ListServicesResponse;
import software.amazon.awssdk.services.ecs.model.ListTasksRequest;
import software.amazon.awssdk.services.ecs.model.ListTasksResponse;
import software.amazon.awssdk.services.ecs.model.RegisterTaskDefinitionRequest;
import software.amazon.awssdk.services.ecs.model.RegisterTaskDefinitionResponse;
import software.amazon.awssdk.services.ecs.model.RunTaskRequest;
import software.amazon.awssdk.services.ecs.model.RunTaskResponse;
import software.amazon.awssdk.services.ecs.model.TagResourceRequest;
import software.amazon.awssdk.services.ecs.model.TagResourceResponse;
import software.amazon.awssdk.services.ecs.model.UntagResourceRequest;
import software.amazon.awssdk.services.ecs.model.UntagResourceResponse;
import software.amazon.awssdk.services.ecs.model.UpdateServiceRequest;
import software.amazon.awssdk.services.ecs.model.UpdateServiceResponse;

public interface EcsV2Client {
  CreateServiceResponse createService(
      AwsInternalConfig awsConfig, CreateServiceRequest createServiceRequest, String region);

  UpdateServiceResponse updateService(
      AwsInternalConfig awsConfig, UpdateServiceRequest updateServiceRequest, String region);

  DeleteServiceResponse deleteService(
      AwsInternalConfig awsConfig, DeleteServiceRequest deleteServiceRequest, String region);

  RegisterTaskDefinitionResponse createTask(
      AwsInternalConfig awsConfig, RegisterTaskDefinitionRequest registerTaskDefinitionRequest, String region);

  WaiterResponse<DescribeServicesResponse> ecsServiceInactiveStateCheck(AwsInternalConfig awsConfig,
      DescribeServicesRequest describeServicesRequest, String region, int serviceInactiveStateTimeout);

  RegisterScalableTargetResponse registerScalableTarget(
      AwsInternalConfig awsConfig, RegisterScalableTargetRequest registerScalableTargetRequest, String region);

  DeregisterScalableTargetResponse deregisterScalableTarget(
      AwsInternalConfig awsConfig, DeregisterScalableTargetRequest deregisterScalableTargetRequest, String region);

  PutScalingPolicyResponse attachScalingPolicy(
      AwsInternalConfig awsConfig, PutScalingPolicyRequest putScalingPolicyRequest, String region);

  DeleteScalingPolicyResponse deleteScalingPolicy(
      AwsInternalConfig awsConfig, DeleteScalingPolicyRequest deleteScalingPolicyRequest, String region);

  DescribeScalableTargetsResponse listScalableTargets(
      AwsInternalConfig awsConfig, DescribeScalableTargetsRequest describeScalableTargetsRequest, String region);

  DescribeScalingPoliciesResponse listScalingPolicies(
      AwsInternalConfig awsConfig, DescribeScalingPoliciesRequest describeScalingPoliciesRequest, String region);

  DescribeServicesResponse describeService(
      AwsInternalConfig awsConfig, String clusterName, String serviceName, String region);

  ListTasksResponse listTaskArns(AwsInternalConfig awsConfig, ListTasksRequest listTasksRequest, String region);

  DescribeTasksResponse getTasks(AwsInternalConfig awsConfig, String clusterName, List<String> taskArns, String region);

  RunTaskResponse runTask(AwsInternalConfig awsConfig, RunTaskRequest runTaskRequest, String region);

  ListServicesResponse listServices(
      AwsInternalConfig awsConfig, ListServicesRequest listServicesRequest, String region);

  DescribeServicesResponse describeServices(
      AwsInternalConfig awsConfig, DescribeServicesRequest describeServicesRequest, String region);

  UntagResourceResponse untagService(
      AwsInternalConfig awsInternalConfig, UntagResourceRequest untagResourceRequest, String region);

  TagResourceResponse tagService(
      AwsInternalConfig awsInternalConfig, TagResourceRequest tagResourceRequest, String region);
}
