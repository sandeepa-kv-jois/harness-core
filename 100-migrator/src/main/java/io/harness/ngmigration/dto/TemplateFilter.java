/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.dto;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ngmigration.beans.ImportMechanism;

import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.v3.oas.annotations.Parameter;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

@OwnedBy(HarnessTeam.CDC)
@Data
@EqualsAndHashCode(callSuper = true)
@JsonTypeName("TEMPLATE")
public class TemplateFilter extends Filter {
  @Parameter(description = "All templates from Application to import") private String appId;

  @Parameter(
      description =
          "APP: To migrate templates in a specific app template library. ACCOUNT: To migrate templates in account template library.")
  TemplateScope scope;
  @Parameter(
      description =
          "ALL: To migrate all templates. ID: TO migrate only specific templates. Specific type is currently not supported")
  @NotNull
  private ImportMechanism importType;

  @Parameter(description = "To be provided if mechanism is ID") private List<String> ids;
}
