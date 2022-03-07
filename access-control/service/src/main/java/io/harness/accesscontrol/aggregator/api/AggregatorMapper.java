/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.aggregator.api;

import io.harness.aggregator.models.AggregatorSecondarySyncState;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(HarnessTeam.PL)
public class AggregatorMapper {
  public static AggregatorSecondarySyncStateDTO toDTO(AggregatorSecondarySyncState aggregatorSecondarySyncState) {
    return AggregatorSecondarySyncStateDTO.builder()
        .identifier(aggregatorSecondarySyncState.getIdentifier())
        .secondarySyncStatus(aggregatorSecondarySyncState.getSecondarySyncStatus())
        .build();
  }
}
