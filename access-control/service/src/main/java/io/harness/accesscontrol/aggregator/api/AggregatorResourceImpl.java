/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.aggregator.api;

import static io.harness.accesscontrol.aggregator.api.AggregatorMapper.toDTO;

import io.harness.aggregator.AggregatorService;
import io.harness.aggregator.models.AggregatorSecondarySyncState;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.dto.ResponseDTO;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import javax.validation.executable.ValidateOnExecution;
import lombok.extern.slf4j.Slf4j;

@ValidateOnExecution
@Singleton
@Slf4j
@OwnedBy(HarnessTeam.PL)
public class AggregatorResourceImpl implements AggregatorResource {
  private final AggregatorService aggregatorService;

  @Inject
  public AggregatorResourceImpl(AggregatorService aggregatorResource) {
    this.aggregatorService = aggregatorResource;
  }

  @Override
  public ResponseDTO<AggregatorSecondarySyncStateDTO> triggerSecondarySync() {
    AggregatorSecondarySyncState aggregatorSecondarySyncState = aggregatorService.requestSecondarySync();
    return ResponseDTO.newResponse(toDTO(aggregatorSecondarySyncState));
  }

  @Override
  public ResponseDTO<AggregatorSecondarySyncStateDTO> switchToPrimary() {
    AggregatorSecondarySyncState aggregatorSecondarySyncState = aggregatorService.requestSwitchToPrimary();
    return ResponseDTO.newResponse(toDTO(aggregatorSecondarySyncState));
  }
}
