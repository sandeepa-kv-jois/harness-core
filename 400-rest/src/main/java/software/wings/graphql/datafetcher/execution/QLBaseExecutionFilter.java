/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.execution;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;

import software.wings.graphql.schema.type.aggregation.EntityFilter;
import software.wings.graphql.schema.type.aggregation.QLIdFilter;
import software.wings.graphql.schema.type.aggregation.QLNumberFilter;
import software.wings.graphql.schema.type.aggregation.QLTimeFilter;
import software.wings.graphql.schema.type.aggregation.deployment.QLDeploymentTagFilter;
import software.wings.graphql.schema.type.aggregation.environment.QLEnvironmentTypeFilter;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@OwnedBy(CDC)
@Data
@NoArgsConstructor
@AllArgsConstructor
@TargetModule(HarnessModule._380_CG_GRAPHQL)
public class QLBaseExecutionFilter implements EntityFilter {
  private QLIdFilter execution;
  private QLIdFilter application;
  private QLIdFilter service;
  private QLIdFilter cloudProvider;
  private QLIdFilter environment;
  private QLEnvironmentTypeFilter environmentType;
  private QLIdFilter status;
  private QLTimeFilter endTime;
  private QLTimeFilter startTime;
  private QLNumberFilter duration;
  private QLIdFilter triggeredBy;
  private QLIdFilter trigger;
  private QLIdFilter workflow;
  private QLIdFilter pipeline;
  private QLTimeFilter creationTime;
  private QLDeploymentTagFilter tag;
  private String applicationId;
  private QLIdFilter artifactBuildNo;
  private QLIdFilter helmChartVersion;
}
