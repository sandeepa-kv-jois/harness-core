package io.harness.event;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OrchestrationLogConfiguration {
  @Builder.Default boolean shouldUseBatching = true;
  @Builder.Default int orchestrationLogBatchSize = 5;
}
