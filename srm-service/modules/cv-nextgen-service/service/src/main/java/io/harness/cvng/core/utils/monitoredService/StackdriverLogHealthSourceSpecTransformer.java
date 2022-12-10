/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.utils.monitoredService;

import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.StackdriverLogHealthSourceSpec;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.StackdriverLogHealthSourceSpec.QueryDTO;
import io.harness.cvng.core.entities.StackdriverLogCVConfig;

import com.google.common.base.Preconditions;
import java.util.List;
import java.util.stream.Collectors;

public class StackdriverLogHealthSourceSpecTransformer
    implements CVConfigToHealthSourceTransformer<StackdriverLogCVConfig, StackdriverLogHealthSourceSpec> {
  @Override
  public StackdriverLogHealthSourceSpec transformToHealthSourceConfig(List<StackdriverLogCVConfig> cvConfigs) {
    Preconditions.checkArgument(
        cvConfigs.stream().map(StackdriverLogCVConfig::getConnectorIdentifier).distinct().count() == 1,
        "ConnectorRef should be same for List of all configs.");
    Preconditions.checkArgument(cvConfigs.stream().map(StackdriverLogCVConfig::getProductName).distinct().count() == 1,
        "Application feature name should be same for List of all configs.");

    return StackdriverLogHealthSourceSpec.builder()
        .connectorRef(cvConfigs.get(0).getConnectorIdentifier())
        .feature(cvConfigs.get(0).getProductName())
        .queries(cvConfigs.stream()
                     .map(cv
                         -> QueryDTO.builder()
                                .name(cv.getQueryName())
                                .query(cv.getQuery())
                                .messageIdentifier(cv.getMessageIdentifier())
                                .serviceInstanceIdentifier(cv.getServiceInstanceIdentifier())
                                .build())
                     .collect(Collectors.toList()))
        .build();
  }
}
