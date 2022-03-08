package io.harness.delegate.beans.ci.pod;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class PodToleration {
  private String effect;
  private String key;
  private String operator;
  private String value;
  private Integer tolerationSeconds;
}
