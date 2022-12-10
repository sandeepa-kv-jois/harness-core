/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.graphql.dto.common;

import io.harness.ccm.views.businessMapping.entities.BusinessMapping;
import io.harness.ccm.views.businessMapping.entities.SharedCost;

import com.google.cloud.Timestamp;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class SharedCostParameters {
  double totalCost;
  double numberOfEntities;
  Map<String, Double> costPerEntity;
  Map<String, Reference> entityReference;
  Map<String, Map<Timestamp, Double>> sharedCostFromGroupBy;
  BusinessMapping businessMappingFromGroupBy;
  Map<String, Map<Timestamp, Double>> sharedCostFromFilters;
  List<SharedCost> sharedCostBucketsFromFilters;
}
