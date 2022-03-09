/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.envGroup.resource;


import io.harness.NGCommonEntityConstants;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.OrgIdentifier;
import io.harness.accesscontrol.ProjectIdentifier;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.envGroup.beans.EnvironmentGroupEntity;
import io.harness.ng.core.envGroup.dto.EnvironmentGroupResponse;
import io.harness.ng.core.envGroup.mappers.EnvironmentGroupMapper;
import io.harness.ng.core.envGroup.services.EnvironmentGroupService;
import io.harness.security.annotations.NextGenManagerAuth;

import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Optional;
import javax.validation.constraints.NotNull;
import javax.ws.rs.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;

@NextGenManagerAuth
@Api("/environmentGroup")
@Path("/environmentGroup")
@Produces({"application/json", "application/yaml"})
@Consumes({"application/json", "application/yaml"})
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@Tag(name = "EnvironmentGroup", description = "This contains APIs related to EnvironmentGroup")
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = NGCommonEntityConstants.BAD_REQUEST_CODE,
    description = NGCommonEntityConstants.BAD_REQUEST_PARAM_MESSAGE,
    content =
    {
      @Content(mediaType = NGCommonEntityConstants.APPLICATION_JSON_MEDIA_TYPE,
          schema = @Schema(implementation = FailureDTO.class))
      ,
          @Content(mediaType = NGCommonEntityConstants.APPLICATION_YAML_MEDIA_TYPE,
              schema = @Schema(implementation = FailureDTO.class))
    })
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = NGCommonEntityConstants.INTERNAL_SERVER_ERROR_CODE,
    description = NGCommonEntityConstants.INTERNAL_SERVER_ERROR_MESSAGE,
    content =
    {
      @Content(mediaType = NGCommonEntityConstants.APPLICATION_JSON_MEDIA_TYPE,
          schema = @Schema(implementation = ErrorDTO.class))
      ,
          @Content(mediaType = NGCommonEntityConstants.APPLICATION_YAML_MEDIA_TYPE,
              schema = @Schema(implementation = ErrorDTO.class))
    })
@OwnedBy(HarnessTeam.PIPELINE)
public class EnvironmentGroupResource {
  private final EnvironmentGroupService environmentGroupService;
  public static final String ENVIRONMENT_GROUP_PARAM_MESSAGE = "Environment Group Identifier for the entity";

  @GET
  @Path("{envGroupIdentifier}")
  @ApiOperation(value = "Gets a Environment Group by identifier", nickname = "getEnvironmentGroup")
  @Operation(operationId = "getEnvironmentGroup", summary = "Gets an Environment Group by identifier",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "The saved Environment Group")
      })
  public ResponseDTO<EnvironmentGroupResponse>
  get(@Parameter(description = ENVIRONMENT_GROUP_PARAM_MESSAGE) @PathParam(
          NGCommonEntityConstants.ENVIRONMENT_GROUP_KEY) String envGroupId,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = "Specify whether Environment is deleted or not") @QueryParam(
          NGCommonEntityConstants.DELETED_KEY) @DefaultValue("false") boolean deleted) {

      //TODO: NEED TO ADD RBAC AND ENABLE GITSYNC
    Optional<EnvironmentGroupEntity> environmentGroupEntity =
        environmentGroupService.get(accountId, orgIdentifier, projectIdentifier, envGroupId, deleted);
    return ResponseDTO.newResponse(environmentGroupEntity.map(EnvironmentGroupMapper::toResponseWrapper).orElse(null));
  }
}
