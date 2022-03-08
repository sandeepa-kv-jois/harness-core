package software.wings.service.impl.newrelic;

import software.wings.metrics.RiskLevel;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class NewRelicMetricAnalysisValue {
  private String name;
  private String type;
  private String alertType;
  private RiskLevel riskLevel;
  private double testValue;
  private double controlValue;
  private List<NewRelicMetricHostAnalysisValue> hostAnalysisValues;
}
