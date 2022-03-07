package io.harness.resourcegroup.v2.model;
import static io.harness.annotations.dev.HarnessTeam.PL;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldNameConstants;

@OwnedBy(PL)
@Value
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(NON_NULL)
@FieldNameConstants(innerTypeName = "ResourceFilterKeys")
public class ResourceFilter {
  @NotNull String resourceType;
  List<String> identifiers;
}
