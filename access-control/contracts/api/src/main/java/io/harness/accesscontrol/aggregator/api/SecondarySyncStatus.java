package io.harness.accesscontrol.aggregator.api;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

@OwnedBy(HarnessTeam.PL)
public enum SecondarySyncStatus {
  SECONDARY_SYNC_REQUESTED,
  SECONDARY_SYNC_RUNNING,
  SWITCH_TO_PRIMARY_REQUESTED
}
