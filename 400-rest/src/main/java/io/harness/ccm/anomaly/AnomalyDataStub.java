/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.anomaly;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.ccm.anomaly.entities.AnomalyEntity;
import io.harness.ccm.anomaly.entities.TimeGranularity;
import io.harness.ccm.commons.entities.anomaly.AnomalyData;
import io.harness.ccm.commons.entities.anomaly.AnomalyFeedback;
import io.harness.ccm.commons.entities.anomaly.EntityInfo;

import software.wings.graphql.schema.type.aggregation.QLTimeFilter;
import software.wings.graphql.schema.type.aggregation.QLTimeOperator;
import software.wings.graphql.schema.type.aggregation.billing.QLBillingDataFilter;
import software.wings.graphql.schema.type.aggregation.billing.QLCCMEntityGroupBy;
import software.wings.graphql.schema.type.aggregation.billing.QLCCMGroupBy;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@TargetModule(HarnessModule._375_CE_GRAPHQL)
@OwnedBy(CE)
public class AnomalyDataStub {
  public static String accountId = "ACCOUNT_ID";
  public static Instant anomalyTime = Instant.ofEpochMilli(0);

  public static AnomalyEntity getClusterAnomaly() {
    return AnomalyEntity.builder()
        .id("ANOMALY_ID1")
        .accountId("ACCOUNT_ID")
        .actualCost(10.1)
        .expectedCost(12.3)
        .anomalyTime(anomalyTime)
        .note("K8S_Anomaly")
        .anomalyScore(12.34)
        .clusterId("CLUSTER_ID")
        .clusterName("CLUSTER_NAME")
        .timeGranularity(TimeGranularity.DAILY)
        .build();
  }

  public static AnomalyEntity getNamespaceAnomaly() {
    return AnomalyEntity.builder()
        .id("ANOMALY_ID2")
        .accountId("ACCOUNT_ID")
        .actualCost(10.1)
        .expectedCost(12.3)
        .anomalyTime(anomalyTime)
        .note("K8S_Anomaly")
        .anomalyScore(12.34)
        .clusterId("CLUSTER_ID")
        .clusterName("CLUSTER_NAME")
        .namespace("NAMESPACE")
        .timeGranularity(TimeGranularity.DAILY)
        .build();
  }

  public static AnomalyEntity getGcpProjectAnomaly() {
    return AnomalyEntity.builder()
        .id("ANOMALY_ID2")
        .accountId("ACCOUNT_ID")
        .actualCost(10.1)
        .expectedCost(12.3)
        .anomalyTime(anomalyTime)
        .note("GCP_Project_Anomaly")
        .anomalyScore(12.34)
        .gcpProject("GCP_PROJECT")
        .timeGranularity(TimeGranularity.DAILY)
        .build();
  }
  public static AnomalyEntity getAwsAccountAnomaly() {
    return AnomalyEntity.builder()
        .id("ANOMALY_ID2")
        .accountId("ACCOUNT_ID")
        .actualCost(10.1)
        .expectedCost(12.3)
        .anomalyTime(anomalyTime)
        .note("Aws_Account_Anomaly")
        .anomalyScore(12.34)
        .awsAccount("AWS_ACCOUNT")
        .timeGranularity(TimeGranularity.DAILY)
        .build();
  }

  public static AnomalyData getAnomalyData() {
    return AnomalyData.builder()
        .id("5e974f98f369a6ed115bd046989a4ab08ed81148afbb6bad1f0986e73d7a0889")
        .time(1659484800000L)
        .anomalyRelativeTime("15 days ago")
        .actualAmount(355.2)
        .expectedAmount(115.01)
        .anomalousSpend(240.19)
        .anomalousSpendPercentage(208.83)
        .resourceInfo("gcpProjectId/gcpProduct/gcpSkuDescription")
        .resourceName(
            "pr10406a87045145c3/MongoDB Inc. MongoDB Atlas/MongoDB Atlas - Elastic Billing Subscription Overage Atlas Credits")
        .entity(EntityInfo.builder()
                    .field("gcpSkuDescription")
                    .clusterName("clusterName")
                    .clusterId("cid")
                    .namespace("namespace")
                    .workloadName("workloadName")
                    .workloadType("workloadType")
                    .gcpProjectId("pr10406a87045145c3")
                    .gcpProduct("MongoDB Inc. MongoDB Atlas")
                    .gcpSKUId("B78C-2F71-A88E")
                    .gcpSKUDescription("MongoDB Atlas - Elastic Billing Subscription Overage Atlas Credits")
                    .awsUsageAccountId("awsUsageAccountId")
                    .awsServiceCode("awsServiceCode")
                    .awsInstancetype("awsInstanceType")
                    .awsUsageType("awsUsageType")
                    .azureSubscriptionGuid("azureSubscriptionGuid")
                    .azureResourceGroup("azureResourceGroup")
                    .azureMeterCategory("azureMeterCategory")
                    .azureServiceName("azureServiceName")
                    .azureInstanceId("azureInstanceId")
                    .build())
        .details("details")
        .status("Open")
        .statusRelativeTime("statusRelativeTime")
        .comment("comment")
        .cloudProvider("GCP")
        .anomalyScore(1.1)
        .userFeedback(AnomalyFeedback.TRUE_ANOMALY)
        .build();
  }

  public static QLBillingDataFilter getBeforeTimeFilter() {
    return QLBillingDataFilter.builder()
        .startTime(QLTimeFilter.builder().operator(QLTimeOperator.BEFORE).value(anomalyTime.toEpochMilli()).build())
        .build();
  }

  public static QLBillingDataFilter getAfterTimeFilter() {
    return QLBillingDataFilter.builder()
        .startTime(QLTimeFilter.builder()
                       .operator(QLTimeOperator.AFTER)
                       .value(anomalyTime.minus(15, ChronoUnit.DAYS).toEpochMilli())
                       .build())
        .build();
  }

  public static QLCCMGroupBy getClusterGroupBy() {
    return QLCCMGroupBy.builder().entityGroupBy(QLCCMEntityGroupBy.Cluster).build();
  }
}
