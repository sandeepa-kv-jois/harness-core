package io.harness.ng.core.envGroup.beans;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.validator.EntityIdentifier;
import io.harness.data.validator.EntityName;
import io.harness.ng.core.common.beans.NGTag;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@OwnedBy(PIPELINE)
@Data
@Builder
@RecasterAlias("io.harness.ng.core.envGroup.beans.EnvironmentGroupConfig")
public class EnvironmentGroupConfig {
  @EntityName String name;
  @EntityIdentifier String identifier;

  String orgIdentifier;
  String projectIdentifier;

  String description;
  String color;
  List<NGTag> tags;

  private List<String> envIdentifiers;
}
