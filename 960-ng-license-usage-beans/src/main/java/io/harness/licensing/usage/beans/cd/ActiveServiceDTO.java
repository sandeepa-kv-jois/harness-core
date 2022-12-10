/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.licensing.usage.beans.cd;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.licensing.usage.beans.LicenseUsageDTO;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

@OwnedBy(CDP)
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "ActiveService", description = "This is details of the Active Service entity defined in Harness.")
public class ActiveServiceDTO extends LicenseUsageDTO {
  @Schema(description = "Active Service identifier.") @NotNull String identifier;
  @Schema(description = "Active Service name.") String name;
  @Schema(description = "Organization name.") String orgName;
  @Schema(description = "Project name.") String projectName;
  @Schema(description = "Active Service instance count.") long instanceCount;
  @Schema(description = "Last deployed timestamp.") long lastDeployed;
  @Schema(description = "Licenses consumed.") long licensesConsumed;
}
