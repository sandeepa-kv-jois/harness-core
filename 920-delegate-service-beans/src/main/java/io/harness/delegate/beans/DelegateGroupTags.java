package io.harness.delegate.beans;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import java.util.List;
import lombok.Value;

@OwnedBy(HarnessTeam.DEL)
@Value
public final class DelegateGroupTags {
  private final List<String> tags;
}
