/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.schema;

import static io.harness.EntityType.PIPELINES;
import static io.harness.EntityType.TRIGGERS;
import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.EntityType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.encryption.Scope;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ngtriggers.service.NGTriggerYamlSchemaService;
import io.harness.plancreator.pipeline.PipelineConfig;
import io.harness.pms.annotations.PipelineServiceAuth;
import io.harness.pms.pipeline.service.PMSYamlSchemaService;
import io.harness.pms.yaml.SchemaErrorResponse;
import io.harness.pms.yaml.YamlSchemaResponse;
import io.harness.yaml.schema.YamlSchemaResource;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import javax.validation.constraints.NotNull;
import javax.ws.rs.NotSupportedException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@PipelineServiceAuth
@Slf4j
@OwnedBy(PIPELINE)
public class PmsYamlSchemaResourceImpl implements YamlSchemaResource, PmsYamlSchemaResource {
  private final PMSYamlSchemaService pmsYamlSchemaService;
  private final NGTriggerYamlSchemaService ngTriggerYamlSchemaService;

  public ResponseDTO<JsonNode> getYamlSchema(@NotNull EntityType entityType, String projectIdentifier,
      String orgIdentifier, Scope scope, String identifier, @NotNull String accountIdentifier) {
    JsonNode schema = null;
    if (entityType == PIPELINES) {
      schema = pmsYamlSchemaService.getPipelineYamlSchema(accountIdentifier, projectIdentifier, orgIdentifier, scope);
    } else if (entityType == TRIGGERS) {
      schema = ngTriggerYamlSchemaService.getTriggerYamlSchema(projectIdentifier, orgIdentifier, identifier, scope);
    } else {
      throw new NotSupportedException(String.format("Entity type %s is not supported", entityType.getYamlName()));
    }

    return ResponseDTO.newResponse(schema);
  }

  public ResponseDTO<Boolean> invalidateYamlSchemaCache() {
    pmsYamlSchemaService.invalidateAllCache();
    return ResponseDTO.newResponse(true);
  }

  public ResponseDTO<io.harness.pms.yaml.YamlSchemaResponse> getIndividualYamlSchema(@NotNull String accountIdentifier,
      String orgIdentifier, String projectIdentifier, String yamlGroup, EntityType stepEntityType, Scope scope) {
    // TODO(Brijesh): write logic to handle empty schema when ff or feature restriction is off.
    JsonNode schema = pmsYamlSchemaService.getIndividualYamlSchema(
        accountIdentifier, orgIdentifier, projectIdentifier, scope, stepEntityType, yamlGroup);
    return ResponseDTO.newResponse(
        YamlSchemaResponse.builder().schema(schema).schemaErrorResponse(SchemaErrorResponse.builder().build()).build());
  }

  // DO NOT DELETE THIS WITHOUT CONFIRMING WITH UI
  public ResponseDTO<PipelineConfig> dummyApiForSwaggerSchemaCheck() {
    log.info("Get pipeline");
    return ResponseDTO.newResponse(PipelineConfig.builder().build());
  }
}
