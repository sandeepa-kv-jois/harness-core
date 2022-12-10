/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cvng.beans.pagerduty;

import static io.harness.annotations.dev.HarnessTeam.CV;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cvng.beans.DataCollectionRequestType;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.HashMap;
import java.util.Map;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@JsonTypeName("PAGERDUTY_SERVICES")
@SuperBuilder
@NoArgsConstructor
@OwnedBy(CV)
public class PagerDutyServicesRequest extends PagerDutyDataCollectionRequest {
  String query;

  public static final String DSL =
      PagerDutyDataCollectionRequest.readDSL("pagerduty-services.datacollection", PagerDutyServicesRequest.class);

  @Override
  public String getDSL() {
    return DSL;
  }

  @Override
  public DataCollectionRequestType getType() {
    return DataCollectionRequestType.PAGERDUTY_SERVICES;
  }

  @Override
  public Map<String, Object> fetchDslEnvVariables() {
    Map<String, Object> dslEnvVariables = new HashMap<>();
    dslEnvVariables.put("query", query);
    return dslEnvVariables;
  }
}
