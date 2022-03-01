package io.harness.accesscontrol.aggregator.api;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
@ApiModel(value = "AggregatorSecondarySyncState")
@Schema(name = "AggregatorSecondarySyncState")
@OwnedBy(HarnessTeam.PL)
public class AggregatorSecondarySyncStateDTO {
  private String identifier;
  private SecondarySyncStatus secondarySyncStatus;
}
