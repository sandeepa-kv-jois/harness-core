package io.harness.resourcegroup.v2.remote.dto;

import static io.harness.annotations.dev.HarnessTeam.PL;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.validator.EntityIdentifier;
import io.harness.data.validator.NGEntityName;
import io.harness.resourcegroup.v2.model.ResourceFilter;
import io.harness.resourcegroup.v2.model.ScopeSelector;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.hibernate.validator.constraints.NotEmpty;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(NON_NULL)
@OwnedBy(PL)
@Schema(name = "ResourceGroupV2", description = "Contains information of Resource Group")
public class ResourceGroupDTO {
  @ApiModelProperty(required = true) @NotNull @NotEmpty String accountIdentifier;
  @EntityIdentifier(allowBlank = true) String orgIdentifier;
  @EntityIdentifier(allowBlank = true) String projectIdentifier;
  @EntityIdentifier @ApiModelProperty(required = true) @NotNull @Size(max = 128) @NotEmpty String identifier;
  @NGEntityName @ApiModelProperty(required = true) @NotNull @Size(max = 128) @NotEmpty String name;
  String color;
  @Size(max = 128) Map<String, String> tags;
  @Size(max = 1024) String description;
  Set<String> allowedScopeLevels;

  @NotNull @NotEmpty @Valid List<ScopeSelector> includedScopes;

  List<ResourceFilter> resourceFilter;
}
