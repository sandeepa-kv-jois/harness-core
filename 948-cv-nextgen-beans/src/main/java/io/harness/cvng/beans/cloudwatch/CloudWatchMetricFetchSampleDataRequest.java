/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cvng.beans.cloudwatch;

import static io.harness.annotations.dev.HarnessTeam.CV;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cvng.beans.DataCollectionRequest;
import io.harness.cvng.utils.CloudWatchUtils;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@JsonTypeName("CLOUDWATCH_METRIC_SAMPLE_DATA_REQUEST")
@Data
@SuperBuilder
@NoArgsConstructor
@OwnedBy(CV)
public class CloudWatchMetricFetchSampleDataRequest extends DataCollectionRequest<AwsConnectorDTO> {
  private static final String SERVICE = "monitoring";
  public static final String DSL = DataCollectionRequest.readDSL(
      "cloudwatch-metrics-sample-fetch.datacollection", CloudWatchMetricFetchSampleDataRequest.class);

  String expression;
  String region;
  String group;
  String metricName;
  String metricIdentifier;

  @Override
  public String getDSL() {
    return DSL;
  }

  @Override
  public String getBaseUrl() {
    return CloudWatchUtils.getBaseUrl(region, SERVICE);
  }

  @Override
  public Map<String, String> collectionHeaders() {
    return new HashMap<>();
  }

  @Override
  public Map<String, Object> fetchDslEnvVariables() {
    return CloudWatchUtils.getDslEnvVariables(
        region, group, expression, metricName, metricIdentifier, SERVICE, getConnectorConfigDTO(), false);
  }

  @Override
  public Instant getEndTime(Instant currentTime) {
    return currentTime;
  }

  @Override
  public Instant getStartTime(Instant currentTime) {
    return currentTime.minus(Duration.ofHours(2));
  }
}
