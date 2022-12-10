/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.artifact.bean.yaml.nexusartifact;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.yaml.schema.beans.SupportedPossibleFieldTypes.integer;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SwaggerConstants;
import io.harness.pms.yaml.ParameterField;
import io.harness.validation.OneOfField;
import io.harness.yaml.YamlSchemaTypes;

import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.Wither;

@OwnedBy(CDC)
@Data
@Builder
@OneOfField(fields = {"repositoryPort", "repositoryUrl"})
@Schema(name = "NexusRegistryDockerConfig",
    description = "This entity contains the details of the Nexus config for docker repository")
public class NexusRegistryDockerConfig implements NexusRegistryConfigSpec {
  /**
   * Artifacts in repos need to be referenced via a path.
   */
  @NotNull @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) @Wither ParameterField<String> artifactPath;

  /**
   * Repo port.
   */
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH)
  @YamlSchemaTypes(value = {integer})
  @Wither
  ParameterField<String> repositoryPort;

  /**
   * repo server hostname.
   */
  @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) @Wither ParameterField<String> repositoryUrl;
}
