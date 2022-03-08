package software.wings.service.impl.newrelic;

import software.wings.metrics.RiskLevel;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class NewRelicMetricHostAnalysisValue {
  private RiskLevel riskLevel;
  private String testHostName;
  private String controlHostName;
  private List<Double> testValues;
  private List<Double> controlValues;
  private List<Double> upperThresholds;
  private List<Double> lowerThresholds;
  private List<Integer> anomalies;
  int testStartIndex;
  private String failFastCriteriaDescription;
}
