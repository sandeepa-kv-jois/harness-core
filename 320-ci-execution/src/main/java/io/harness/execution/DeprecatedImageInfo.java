package io.harness.execution;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DeprecatedImageInfo {
  String tag;
  String version;
}
