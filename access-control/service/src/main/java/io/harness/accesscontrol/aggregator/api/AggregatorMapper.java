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
