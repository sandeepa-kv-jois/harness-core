/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.cloudevents.aws.ecs.service.tasklet.support.response;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class EcsUtilizationData {
  private String clusterArn;
  private String clusterName;
  private String serviceArn;
  private String serviceName;
  private String clusterId;
  private String settingId;
  private List<MetricValue> metricValues;
}
