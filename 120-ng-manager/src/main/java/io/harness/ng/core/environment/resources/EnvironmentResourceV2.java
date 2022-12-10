/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.environment.resources;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.ng.accesscontrol.PlatformPermissions.VIEW_PROJECT_PERMISSION;
import static io.harness.ng.accesscontrol.PlatformResourceTypes.PROJECT;
import static io.harness.ng.core.environment.mappers.EnvironmentMapper.checkDuplicateConfigFilesIdentifiersWithIn;
import static io.harness.ng.core.environment.mappers.EnvironmentMapper.checkDuplicateManifestIdentifiersWithIn;
import static io.harness.ng.core.environment.mappers.EnvironmentMapper.toNGEnvironmentConfig;
import static io.harness.ng.core.serviceoverride.mapper.NGServiceOverrideEntityConfigMapper.toNGServiceOverrideConfig;
import static io.harness.pms.rbac.NGResourceType.ENVIRONMENT;
import static io.harness.pms.rbac.NGResourceType.SERVICE;
import static io.harness.rbac.CDNGRbacPermissions.ENVIRONMENT_CREATE_PERMISSION;
import static io.harness.rbac.CDNGRbacPermissions.ENVIRONMENT_UPDATE_PERMISSION;
import static io.harness.rbac.CDNGRbacPermissions.ENVIRONMENT_VIEW_PERMISSION;
import static io.harness.rbac.CDNGRbacPermissions.SERVICE_UPDATE_PERMISSION;
import static io.harness.utils.PageUtils.getNGPageResponse;

import static java.lang.Long.parseLong;
import static javax.ws.rs.core.HttpHeaders.IF_MATCH;
import static org.apache.commons.lang3.StringUtils.isNumeric;

import io.harness.NGCommonEntityConstants;
import io.harness.NGResourceFilterConstants;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.accesscontrol.OrgIdentifier;
import io.harness.accesscontrol.ProjectIdentifier;
import io.harness.accesscontrol.ResourceIdentifier;
import io.harness.accesscontrol.acl.api.AccessCheckResponseDTO;
import io.harness.accesscontrol.acl.api.AccessControlDTO;
import io.harness.accesscontrol.acl.api.PermissionCheckDTO;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.cdng.envGroup.beans.EnvironmentGroupEntity;
import io.harness.cdng.envGroup.services.EnvironmentGroupService;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.EnvironmentValidationHelper;
import io.harness.ng.core.OrgAndProjectValidationHelper;
import io.harness.ng.core.beans.NGEntityTemplateResponseDTO;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.beans.Environment.EnvironmentKeys;
import io.harness.ng.core.environment.beans.EnvironmentFilterPropertiesDTO;
import io.harness.ng.core.environment.beans.EnvironmentInputSetYamlAndServiceOverridesMetadataDTO;
import io.harness.ng.core.environment.beans.EnvironmentInputsMergedResponseDto;
import io.harness.ng.core.environment.beans.EnvironmentInputsetYamlAndServiceOverridesMetadataInput;
import io.harness.ng.core.environment.beans.EnvironmentType;
import io.harness.ng.core.environment.dto.EnvironmentRequestDTO;
import io.harness.ng.core.environment.dto.EnvironmentResponse;
import io.harness.ng.core.environment.mappers.EnvironmentFilterHelper;
import io.harness.ng.core.environment.mappers.EnvironmentMapper;
import io.harness.ng.core.environment.services.EnvironmentService;
import io.harness.ng.core.environment.yaml.NGEnvironmentConfig;
import io.harness.ng.core.service.ServiceEntityValidationHelper;
import io.harness.ng.core.serviceoverride.beans.NGServiceOverridesEntity;
import io.harness.ng.core.serviceoverride.beans.NGServiceOverridesEntity.NGServiceOverridesEntityKeys;
import io.harness.ng.core.serviceoverride.beans.ServiceOverrideRequestDTO;
import io.harness.ng.core.serviceoverride.beans.ServiceOverrideResponseDTO;
import io.harness.ng.core.serviceoverride.mapper.ServiceOverridesMapper;
import io.harness.ng.core.serviceoverride.services.ServiceOverrideService;
import io.harness.ng.core.serviceoverride.yaml.NGServiceOverrideConfig;
import io.harness.ng.core.serviceoverride.yaml.NGServiceOverrideInfoConfig;
import io.harness.ng.core.utils.CoreCriteriaUtils;
import io.harness.ng.overview.dto.InstanceGroupedByServiceList;
import io.harness.ng.overview.service.CDOverviewDashboardService;
import io.harness.rbac.CDNGRbacUtility;
import io.harness.repositories.UpsertOptions;
import io.harness.security.annotations.InternalApi;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.utils.NGFeatureFlagHelperService;
import io.harness.utils.PageUtils;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;

@NextGenManagerAuth
@Api("/environmentsV2")
@Path("/environmentsV2")
@Produces({"application/json", "application/yaml"})
@Consumes({"application/json", "application/yaml"})
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@Tag(name = "Environments", description = "This contains APIs related to Environments")
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
@OwnedBy(HarnessTeam.CDC)
@Slf4j
public class EnvironmentResourceV2 {
  private final EnvironmentService environmentService;
  private final AccessControlClient accessControlClient;
  private final OrgAndProjectValidationHelper orgAndProjectValidationHelper;
  private final ServiceOverrideService serviceOverrideService;
  private final EnvironmentValidationHelper environmentValidationHelper;
  private final ServiceEntityValidationHelper serviceEntityValidationHelper;
  private final EnvironmentFilterHelper environmentFilterHelper;
  private final EnvironmentGroupService environmentGroupService;
  private final CDOverviewDashboardService cdOverviewDashboardService;
  private final NGFeatureFlagHelperService featureFlagHelperService;

  public static final String ENVIRONMENT_YAML_METADATA_INPUT_PARAM_MESSAGE =
      "Lists of Environment Identifiers and service identifiers for the entities";

  public static final String ENVIRONMENT_PARAM_MESSAGE = "Environment Identifier for the entity";

  @GET
  @Path("{environmentIdentifier}")
  @NGAccessControlCheck(resourceType = ENVIRONMENT, permission = "core_environment_view")
  @ApiOperation(value = "Gets a Environment by identifier", nickname = "getEnvironmentV2")
  @Operation(operationId = "getEnvironmentV2", summary = "Gets an Environment by identifier",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "The saved Environment")
      })
  public ResponseDTO<EnvironmentResponse>
  get(@Parameter(description = ENVIRONMENT_PARAM_MESSAGE) @PathParam(
          "environmentIdentifier") @ResourceIdentifier String environmentIdentifier,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = "Specify whether Environment is deleted or not") @QueryParam(
          NGCommonEntityConstants.DELETED_KEY) @DefaultValue("false") boolean deleted) {
    Optional<Environment> environment =
        environmentService.get(accountId, orgIdentifier, projectIdentifier, environmentIdentifier, deleted);
    String version = "0";
    if (environment.isPresent()) {
      version = environment.get().getVersion().toString();
      if (EmptyPredicate.isEmpty(environment.get().getYaml())) {
        NGEnvironmentConfig ngEnvironmentConfig = toNGEnvironmentConfig(environment.get());
        environment.get().setYaml(EnvironmentMapper.toYaml(ngEnvironmentConfig));
      }
    } else {
      throw new NotFoundException(String.format("Environment with identifier [%s] in project [%s], org [%s] not found",
          environmentIdentifier, projectIdentifier, orgIdentifier));
    }
    return ResponseDTO.newResponse(version, environment.map(EnvironmentMapper::toResponseWrapper).orElse(null));
  }

  @POST
  @ApiOperation(value = "Create an Environment", nickname = "createEnvironmentV2")
  @Operation(operationId = "createEnvironmentV2", summary = "Create an Environment",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the created Environment")
      })
  public ResponseDTO<EnvironmentResponse>
  create(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
             NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @Parameter(description = "Details of the Environment to be created")
      @Valid EnvironmentRequestDTO environmentRequestDTO) {
    throwExceptionForNoRequestDTO(environmentRequestDTO);
    validateEnvironmentScope(environmentRequestDTO, accountId);

    if (environmentRequestDTO.getType() == null) {
      throw new InvalidRequestException(
          "Type for an environment cannot be empty. Possible values: " + Arrays.toString(EnvironmentType.values()));
    }
    Map<String, String> environmentAttributes = new HashMap<>();
    environmentAttributes.put("type", environmentRequestDTO.getType().toString());
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, environmentRequestDTO.getOrgIdentifier(),
                                                  environmentRequestDTO.getProjectIdentifier()),
        Resource.of(ENVIRONMENT, null, environmentAttributes), ENVIRONMENT_CREATE_PERMISSION);
    Environment environmentEntity = EnvironmentMapper.toEnvironmentEntity(accountId, environmentRequestDTO);
    orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(environmentEntity.getOrgIdentifier(),
        environmentEntity.getProjectIdentifier(), environmentEntity.getAccountId());
    Environment createdEnvironment = environmentService.create(environmentEntity);
    return ResponseDTO.newResponse(
        createdEnvironment.getVersion().toString(), EnvironmentMapper.toResponseWrapper(createdEnvironment));
  }

  private boolean checkFeatureFlagForEnvOrgAccountLevel(String accountId) {
    return featureFlagHelperService.isEnabled(accountId, FeatureName.CDS_OrgAccountLevelServiceEnvEnvGroup);
  }

  @DELETE
  @Path("{environmentIdentifier}")
  @ApiOperation(value = "Delete en environment by identifier", nickname = "deleteEnvironmentV2")
  @NGAccessControlCheck(resourceType = ENVIRONMENT, permission = "core_environment_delete")
  @Operation(operationId = "deleteEnvironmentV2", summary = "Delete an Environment by identifier",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns true if the Environment is deleted")
      })
  public ResponseDTO<Boolean>
  delete(@HeaderParam(IF_MATCH) String ifMatch,
      @Parameter(description = ENVIRONMENT_PARAM_MESSAGE) @PathParam(
          "environmentIdentifier") @ResourceIdentifier String environmentIdentifier,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier) {
    return ResponseDTO.newResponse(environmentService.delete(accountId, orgIdentifier, projectIdentifier,
        environmentIdentifier, isNumeric(ifMatch) ? parseLong(ifMatch) : null));
  }

  @PUT
  @ApiOperation(value = "Update an environment by identifier", nickname = "updateEnvironmentV2")
  @Operation(operationId = "updateEnvironmentV2", summary = "Update an Environment by identifier",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the updated Environment")
      })
  public ResponseDTO<EnvironmentResponse>
  update(@HeaderParam(IF_MATCH) String ifMatch,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @Parameter(description = "Details of the Environment to be updated")
      @Valid EnvironmentRequestDTO environmentRequestDTO) {
    throwExceptionForNoRequestDTO(environmentRequestDTO);
    validateEnvironmentScope(environmentRequestDTO, accountId);

    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, environmentRequestDTO.getOrgIdentifier(),
                                                  environmentRequestDTO.getProjectIdentifier()),
        Resource.of(ENVIRONMENT, environmentRequestDTO.getIdentifier()), ENVIRONMENT_UPDATE_PERMISSION);

    Environment requestEnvironment = EnvironmentMapper.toEnvironmentEntity(accountId, environmentRequestDTO);
    requestEnvironment.setVersion(isNumeric(ifMatch) ? parseLong(ifMatch) : null);
    Environment updatedEnvironment = environmentService.update(requestEnvironment);
    return ResponseDTO.newResponse(
        updatedEnvironment.getVersion().toString(), EnvironmentMapper.toResponseWrapper(updatedEnvironment));
  }

  @PUT
  @Path("upsert")
  @ApiOperation(value = "Upsert an environment by identifier", nickname = "upsertEnvironmentV2")
  @Operation(operationId = "upsertEnvironmentV2", summary = "Upsert an Environment by identifier",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the updated Environment")
      })
  public ResponseDTO<EnvironmentResponse>
  upsert(@HeaderParam(IF_MATCH) String ifMatch,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @Parameter(description = "Details of the Environment to be updated")
      @Valid EnvironmentRequestDTO environmentRequestDTO) {
    throwExceptionForNoRequestDTO(environmentRequestDTO);
    validateEnvironmentScope(environmentRequestDTO, accountId);

    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, environmentRequestDTO.getOrgIdentifier(),
                                                  environmentRequestDTO.getProjectIdentifier()),
        Resource.of(ENVIRONMENT, environmentRequestDTO.getIdentifier()), ENVIRONMENT_UPDATE_PERMISSION);

    Environment requestEnvironment = EnvironmentMapper.toEnvironmentEntity(accountId, environmentRequestDTO);
    requestEnvironment.setVersion(isNumeric(ifMatch) ? parseLong(ifMatch) : null);
    orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(requestEnvironment.getOrgIdentifier(),
        requestEnvironment.getProjectIdentifier(), requestEnvironment.getAccountId());
    Environment upsertEnvironment = environmentService.upsert(requestEnvironment, UpsertOptions.DEFAULT);
    return ResponseDTO.newResponse(
        upsertEnvironment.getVersion().toString(), EnvironmentMapper.toResponseWrapper(upsertEnvironment));
  }

  @GET
  @ApiOperation(value = "Gets environment list", nickname = "getEnvironmentList")
  @Operation(operationId = "getEnvironmentList", summary = "Gets Environment list for a project",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns the list of Environments for a Project")
      })
  public ResponseDTO<PageResponse<EnvironmentResponse>>
  listEnvironment(@Parameter(description = NGCommonEntityConstants.PAGE_PARAM_MESSAGE) @QueryParam(
                      NGCommonEntityConstants.PAGE) @DefaultValue("0") int page,
      @Parameter(description = NGCommonEntityConstants.SIZE_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.SIZE) @DefaultValue("100") int size,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ResourceIdentifier String projectIdentifier,
      @Parameter(description = "The word to be searched and included in the list response") @QueryParam(
          NGResourceFilterConstants.SEARCH_TERM_KEY) String searchTerm,
      @Parameter(description = "List of EnvironmentIds") @QueryParam("envIdentifiers") List<String> envIdentifiers,
      @Parameter(
          description =
              "Specifies sorting criteria of the list. Like sorting based on the last updated entity, alphabetical sorting in an ascending or descending order")
      @QueryParam("sort") List<String> sort) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgIdentifier, projectIdentifier),
        Resource.of(ENVIRONMENT, null), ENVIRONMENT_VIEW_PERMISSION, "Unauthorized to list environments");
    Criteria criteria = environmentFilterHelper.createCriteriaForGetList(
        accountId, orgIdentifier, projectIdentifier, false, searchTerm);
    Pageable pageRequest;

    if (isNotEmpty(envIdentifiers)) {
      criteria.and(EnvironmentKeys.identifier).in(envIdentifiers);
    }
    if (isEmpty(sort)) {
      pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, EnvironmentKeys.createdAt));
    } else {
      pageRequest = PageUtils.getPageRequest(page, size, sort);
    }
    Page<Environment> environmentEntities = environmentService.list(criteria, pageRequest);
    environmentEntities.forEach(environment -> {
      if (EmptyPredicate.isEmpty(environment.getYaml())) {
        NGEnvironmentConfig ngEnvironmentConfig = toNGEnvironmentConfig(environment);
        environment.setYaml(EnvironmentMapper.toYaml(ngEnvironmentConfig));
      }
    });
    return ResponseDTO.newResponse(getNGPageResponse(environmentEntities.map(EnvironmentMapper::toResponseWrapper)));
  }

  @GET
  @Path("/getActiveServiceInstancesForEnvironment")
  @ApiOperation(value = "Get list of instances grouped by service for particular environment",
      nickname = "getActiveServiceInstancesForEnvironment")
  @Hidden
  public ResponseDTO<InstanceGroupedByServiceList>
  getActiveServiceInstancesForEnvironment(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ENVIRONMENT_IDENTIFIER_KEY) String environmentIdentifier,
      @QueryParam(NGCommonEntityConstants.SERVICE_IDENTIFIER_KEY) String serviceIdentifier,
      @QueryParam(NGCommonEntityConstants.BUILD_KEY) String buildId) {
    /*
    if (tag != null && serviceIdentifier == null) {

    }

     */
    return ResponseDTO.newResponse(cdOverviewDashboardService.getInstanceGroupedByServiceList(
        accountIdentifier, orgIdentifier, projectIdentifier, environmentIdentifier, serviceIdentifier, buildId));
  }

  @POST
  @Path("/listV2")
  @ApiOperation(value = "Gets environment list", nickname = "getEnvironmentListV2")
  @Operation(operationId = "getEnvironmentList", summary = "Gets Environment list for a project with filters",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns the list of Environments for a Project")
      },
      hidden = true)
  public ResponseDTO<PageResponse<EnvironmentResponse>>
  listEnvironmentsV2(@Parameter(description = NGCommonEntityConstants.PAGE_PARAM_MESSAGE) @QueryParam(
                         NGCommonEntityConstants.PAGE) @DefaultValue("0") int page,
      @Parameter(description = NGCommonEntityConstants.SIZE_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.SIZE) @DefaultValue("100") int size,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ResourceIdentifier String projectIdentifier,
      @Parameter(description = "The word to be searched and included in the list response") @QueryParam(
          NGResourceFilterConstants.SEARCH_TERM_KEY) String searchTerm,
      @Parameter(description = "List of EnvironmentIds") @QueryParam("envIdentifiers") List<String> envIdentifiers,
      @Parameter(
          description =
              "Specifies sorting criteria of the list. Like sorting based on the last updated entity, alphabetical sorting in an ascending or descending order")
      @QueryParam("sort") List<String> sort,
      @RequestBody(description = "This is the body for the filter properties for listing environments.")
      EnvironmentFilterPropertiesDTO filterProperties,
      @QueryParam(NGResourceFilterConstants.FILTER_KEY) String filterIdentifier) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgIdentifier, projectIdentifier),
        Resource.of(ENVIRONMENT, null), ENVIRONMENT_VIEW_PERMISSION, "Unauthorized to list environments");
    Criteria criteria = environmentFilterHelper.createCriteriaForGetList(
        accountId, orgIdentifier, projectIdentifier, false, searchTerm, filterIdentifier, filterProperties);
    Pageable pageRequest;

    if (isNotEmpty(envIdentifiers)) {
      criteria.and(EnvironmentKeys.identifier).in(envIdentifiers);
    }
    if (isEmpty(sort)) {
      pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, EnvironmentKeys.createdAt));
    } else {
      pageRequest = PageUtils.getPageRequest(page, size, sort);
    }
    Page<Environment> environmentEntities = environmentService.list(criteria, pageRequest);
    environmentEntities.forEach(environment -> {
      if (EmptyPredicate.isEmpty(environment.getYaml())) {
        NGEnvironmentConfig ngEnvironmentConfig = toNGEnvironmentConfig(environment);
        environment.setYaml(EnvironmentMapper.toYaml(ngEnvironmentConfig));
      }
    });
    return ResponseDTO.newResponse(getNGPageResponse(environmentEntities.map(EnvironmentMapper::toResponseWrapper)));
  }

  @GET
  @Path("/list/access")
  @ApiOperation(value = "Gets environment access list", nickname = "getEnvironmentAccessList")
  @Operation(operationId = "getEnvironmentAccessList", summary = "Gets Environment Access list",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Returns the list of Environments for a Project that are accessible")
      })
  public ResponseDTO<List<EnvironmentResponse>>
  listAccessEnvironment(@Parameter(description = NGCommonEntityConstants.PAGE) @QueryParam(
                            NGCommonEntityConstants.PAGE) @DefaultValue("0") int page,
      @Parameter(description = NGCommonEntityConstants.SIZE) @QueryParam(NGCommonEntityConstants.SIZE) @DefaultValue(
          "100") int size,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Parameter(description = "The word to be searched and included in the list response") @QueryParam(
          NGResourceFilterConstants.SEARCH_TERM_KEY) String searchTerm,
      @Parameter(description = "List of EnvironmentIds") @QueryParam("envIdentifiers") List<String> envIdentifiers,
      @Parameter(description = "Environment group identifier") @QueryParam(
          "envGroupIdentifier") String envGroupIdentifier,
      @Parameter(
          description =
              "Specifies sorting criteria of the list. Like sorting based on the last updated entity, alphabetical sorting in an ascending or descending order")
      @QueryParam("sort") List<String> sort) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgIdentifier, projectIdentifier),
        Resource.of(PROJECT, projectIdentifier), VIEW_PROJECT_PERMISSION, "Unauthorized to list environments");
    if (isEmpty(envIdentifiers) && isNotEmpty(envGroupIdentifier)) {
      Optional<EnvironmentGroupEntity> environmentGroupEntity =
          environmentGroupService.get(accountId, orgIdentifier, projectIdentifier, envGroupIdentifier, false);
      environmentGroupEntity.ifPresent(groupEntity -> envIdentifiers.addAll(groupEntity.getEnvIdentifiers()));
    }
    Criteria criteria = CoreCriteriaUtils.createCriteriaForGetList(accountId, orgIdentifier, projectIdentifier, false);

    if (isNotEmpty(envIdentifiers)) {
      criteria.and(EnvironmentKeys.identifier).in(envIdentifiers);
    }

    List<EnvironmentResponse> environmentList = environmentService.listAccess(criteria)
                                                    .stream()
                                                    .map(EnvironmentMapper::toResponseWrapper)
                                                    .collect(Collectors.toList());

    List<PermissionCheckDTO> permissionCheckDTOS = environmentList.stream()
                                                       .map(CDNGRbacUtility::environmentResponseToPermissionCheckDTO)
                                                       .collect(Collectors.toList());
    List<AccessControlDTO> accessControlList =
        accessControlClient.checkForAccess(permissionCheckDTOS).getAccessControlList();
    return ResponseDTO.newResponse(filterEnvironmentResponseByPermissionAndId(accessControlList, environmentList));
  }

  @GET
  @Path("/dummy-env-api")
  @ApiOperation(value = "This is dummy api to expose NGEnvironmentConfig", nickname = "dummyNGEnvironmentConfigApi")
  @Hidden
  // do not delete this.
  public ResponseDTO<NGEnvironmentConfig> getNGEnvironmentConfig() {
    return ResponseDTO.newResponse(NGEnvironmentConfig.builder().build());
  }

  @POST
  @Path("/mergeEnvironmentInputs/{environmentIdentifier}")
  @ApiOperation(value = "This api merges old and new environment inputs YAML", nickname = "mergeEnvironmentInputs")
  @Hidden
  public ResponseDTO<EnvironmentInputsMergedResponseDto> mergeEnvironmentInputs(
      @Parameter(description = ENVIRONMENT_PARAM_MESSAGE) @PathParam(
          NGCommonEntityConstants.ENVIRONMENT_IDENTIFIER_KEY) @ResourceIdentifier String environmentIdentifier,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      String oldEnvironmentInputsYaml) {
    return ResponseDTO.newResponse(environmentService.mergeEnvironmentInputs(
        accountId, orgIdentifier, projectIdentifier, environmentIdentifier, oldEnvironmentInputsYaml));
  }

  @POST
  @Path("/serviceOverrides")
  @ApiOperation(value = "upsert a Service Override for an Environment", nickname = "upsertServiceOverride")
  @Operation(operationId = "upsertServiceOverride", summary = "upsert a Service Override for an Environment",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Upsert ( Create/Update )  a Service Override in an Environment.")
      })
  public ResponseDTO<io.harness.ng.core.serviceoverride.beans.ServiceOverrideResponseDTO>
  upsertServiceOverride(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
                            NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @Parameter(description = "Details of the Service Override to be upserted")
      @Valid io.harness.ng.core.serviceoverride.beans.ServiceOverrideRequestDTO serviceOverrideRequestDTO) {
    throwExceptionForNoRequestDTO(serviceOverrideRequestDTO);
    validateServiceOverrideScope(serviceOverrideRequestDTO, accountId);

    NGServiceOverridesEntity serviceOverridesEntity =
        ServiceOverridesMapper.toServiceOverridesEntity(accountId, serviceOverrideRequestDTO);
    orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(serviceOverridesEntity.getOrgIdentifier(),
        serviceOverridesEntity.getProjectIdentifier(), serviceOverridesEntity.getAccountId());
    environmentValidationHelper.checkThatEnvExists(serviceOverridesEntity.getAccountId(),
        serviceOverridesEntity.getOrgIdentifier(), serviceOverridesEntity.getProjectIdentifier(),
        serviceOverridesEntity.getEnvironmentRef());
    serviceEntityValidationHelper.checkThatServiceExists(serviceOverridesEntity.getAccountId(),
        serviceOverridesEntity.getOrgIdentifier(), serviceOverridesEntity.getProjectIdentifier(),
        serviceOverridesEntity.getServiceRef());
    checkForServiceOverrideUpdateAccess(accountId, serviceOverridesEntity.getOrgIdentifier(),
        serviceOverridesEntity.getProjectIdentifier(), serviceOverridesEntity.getEnvironmentRef(),
        serviceOverridesEntity.getServiceRef());
    validateServiceOverrides(serviceOverridesEntity);

    NGServiceOverridesEntity createdServiceOverride = serviceOverrideService.upsert(serviceOverridesEntity);
    return ResponseDTO.newResponse(ServiceOverridesMapper.toResponseWrapper(createdServiceOverride));
  }

  @POST
  @Path("/environmentInputYamlAndServiceOverridesMetadata")
  @ApiOperation(value = "This api returns environments runtime input YAML and serviceOverrides Yaml",
      nickname = "getEnvironmentsInputYamlAndServiceOverrides")
  @Hidden
  public ResponseDTO<EnvironmentInputSetYamlAndServiceOverridesMetadataDTO>
  getEnvironmentsInputYamlAndServiceOverrides(
      @Parameter(description = ENVIRONMENT_YAML_METADATA_INPUT_PARAM_MESSAGE) @Valid
      @NotNull EnvironmentInputsetYamlAndServiceOverridesMetadataInput environmentYamlMetadata,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier) {
    List<String> envIdentifiers = new ArrayList<>();
    if (isNotEmpty(environmentYamlMetadata.getEnvIdentifiers())) {
      envIdentifiers.addAll(environmentYamlMetadata.getEnvIdentifiers());
    }
    if (isNotEmpty(environmentYamlMetadata.getEnvGroupIdentifier())) {
      Optional<EnvironmentGroupEntity> environmentGroupEntity = environmentGroupService.get(
          accountId, orgIdentifier, projectIdentifier, environmentYamlMetadata.getEnvGroupIdentifier(), false);
      environmentGroupEntity.ifPresent(groupEntity -> envIdentifiers.addAll(groupEntity.getEnvIdentifiers()));
    }
    EnvironmentInputSetYamlAndServiceOverridesMetadataDTO environmentInputsetYamlandServiceOverridesMetadataDTO =
        environmentService.getEnvironmentsInputYamlAndServiceOverridesMetadata(accountId, orgIdentifier,
            projectIdentifier, envIdentifiers, environmentYamlMetadata.getServiceIdentifiers());

    return ResponseDTO.newResponse(environmentInputsetYamlandServiceOverridesMetadataDTO);
  }

  private void validateServiceOverrides(NGServiceOverridesEntity serviceOverridesEntity) {
    final NGServiceOverrideConfig serviceOverrideConfig = toNGServiceOverrideConfig(serviceOverridesEntity);
    if (serviceOverrideConfig.getServiceOverrideInfoConfig() != null) {
      final NGServiceOverrideInfoConfig serviceOverrideInfoConfig =
          serviceOverrideConfig.getServiceOverrideInfoConfig();

      if (isEmpty(serviceOverrideInfoConfig.getManifests()) && isEmpty(serviceOverrideInfoConfig.getConfigFiles())
          && isEmpty(serviceOverrideInfoConfig.getVariables())
          && serviceOverrideInfoConfig.getApplicationSettings() == null
          && serviceOverrideInfoConfig.getConnectionStrings() == null) {
        final Optional<NGServiceOverridesEntity> optionalNGServiceOverrides =
            serviceOverrideService.get(serviceOverridesEntity.getAccountId(), serviceOverridesEntity.getOrgIdentifier(),
                serviceOverridesEntity.getProjectIdentifier(), serviceOverridesEntity.getEnvironmentRef(),
                serviceOverridesEntity.getServiceRef());
        if (optionalNGServiceOverrides.isEmpty()) {
          throw new InvalidRequestException("No overrides found in request");
        }
      }

      checkDuplicateManifestIdentifiersWithIn(serviceOverrideInfoConfig.getManifests());
      checkDuplicateConfigFilesIdentifiersWithIn(serviceOverrideInfoConfig.getConfigFiles());
    }
  }

  @DELETE
  @Path("/serviceOverrides")
  @ApiOperation(value = "Delete a Service Override entity", nickname = "deleteServiceOverride")
  @Operation(operationId = "deleteServiceOverride", summary = "Delete a ServiceOverride entity",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns true if the Service Override is deleted")
      })
  public ResponseDTO<Boolean>
  deleteServiceOverride(@Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
                            NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE, required = true) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE, required = true) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = NGCommonEntityConstants.ENV_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ENVIRONMENT_IDENTIFIER_KEY) @ResourceIdentifier String environmentIdentifier,
      @Parameter(description = NGCommonEntityConstants.SERVICE_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.SERVICE_IDENTIFIER_KEY) @ResourceIdentifier String serviceIdentifier) {
    orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(orgIdentifier, projectIdentifier, accountId);
    environmentValidationHelper.checkThatEnvExists(accountId, orgIdentifier, projectIdentifier, environmentIdentifier);
    serviceEntityValidationHelper.checkThatServiceExists(
        accountId, orgIdentifier, projectIdentifier, serviceIdentifier);
    // check access for service and env
    checkForServiceOverrideUpdateAccess(
        accountId, orgIdentifier, projectIdentifier, environmentIdentifier, serviceIdentifier);
    return ResponseDTO.newResponse(serviceOverrideService.delete(
        accountId, orgIdentifier, projectIdentifier, environmentIdentifier, serviceIdentifier));
  }

  @GET
  @Path("/dummy-api-for-exposing-objects")
  @ApiOperation(value = "This is dummy api to expose objects to swagger", nickname = "dummyNGServiceOverrideConfig")
  @Hidden
  // do not delete this.
  public ResponseDTO<EnvSwaggerObjectWrapper> exposeSwaggerObjects() {
    return ResponseDTO.newResponse(EnvSwaggerObjectWrapper.builder().build());
  }

  @GET
  @Path("/serviceOverrides")
  @ApiOperation(value = "Gets Service Overrides list ", nickname = "getServiceOverridesList")
  @Operation(operationId = "getServiceOverridesList", summary = "Gets Service Overrides list",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns the list of Service Overrides for an Environment."
                + "serviceIdentifier, if passed, can be used to get the overrides for that particular Service in the Environment")
      })
  public ResponseDTO<PageResponse<ServiceOverrideResponseDTO>>
  listServiceOverrides(@Parameter(description = NGCommonEntityConstants.PAGE_PARAM_MESSAGE) @QueryParam(
                           NGCommonEntityConstants.PAGE) @DefaultValue("0") int page,
      @Parameter(description = NGCommonEntityConstants.SIZE_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.SIZE) @DefaultValue("100") int size,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier @NotNull String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ResourceIdentifier @NotNull String projectIdentifier,
      @Parameter(description = NGCommonEntityConstants.ENV_PARAM_MESSAGE, required = true) @QueryParam(
          NGCommonEntityConstants.ENVIRONMENT_IDENTIFIER_KEY) @ResourceIdentifier @NotNull String environmentIdentifier,
      @Parameter(description = NGCommonEntityConstants.SERVICE_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.SERVICE_IDENTIFIER_KEY) @ResourceIdentifier String serviceIdentifier,
      @Parameter(
          description =
              "Specifies the sorting criteria of the list. Like sorting based on the last updated entity, alphabetical sorting in an ascending or descending order")
      @QueryParam("sort") List<String> sort) {
    orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(orgIdentifier, projectIdentifier, accountId);
    environmentValidationHelper.checkThatEnvExists(accountId, orgIdentifier, projectIdentifier, environmentIdentifier);

    if (isNotEmpty(serviceIdentifier)) {
      serviceEntityValidationHelper.checkThatServiceExists(
          accountId, orgIdentifier, projectIdentifier, serviceIdentifier);
    }
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgIdentifier, projectIdentifier),
        Resource.of(ENVIRONMENT, environmentIdentifier), ENVIRONMENT_VIEW_PERMISSION,
        "Unauthorized to view environment");

    Criteria criteria = environmentFilterHelper.createCriteriaForGetServiceOverrides(
        accountId, orgIdentifier, projectIdentifier, environmentIdentifier, serviceIdentifier);
    Pageable pageRequest;

    if (isEmpty(sort)) {
      pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, NGServiceOverridesEntityKeys.createdAt));
    } else {
      pageRequest = PageUtils.getPageRequest(page, size, sort);
    }
    Page<NGServiceOverridesEntity> serviceOverridesEntities = serviceOverrideService.list(criteria, pageRequest);

    return ResponseDTO.newResponse(
        getNGPageResponse(serviceOverridesEntities.map(ServiceOverridesMapper::toResponseWrapper)));
  }

  @GET
  @Path("/runtimeInputs")
  @ApiOperation(value = "This api returns Environment inputs YAML", nickname = "getEnvironmentInputs")
  @Hidden
  public ResponseDTO<NGEntityTemplateResponseDTO> getEnvironmentInputs(
      @Parameter(description = ENVIRONMENT_PARAM_MESSAGE) @NotNull @QueryParam(
          "environmentIdentifier") @ResourceIdentifier String environmentIdentifier,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier) {
    String environmentInputsYaml = environmentService.createEnvironmentInputsYaml(
        accountId, orgIdentifier, projectIdentifier, environmentIdentifier);

    return ResponseDTO.newResponse(
        NGEntityTemplateResponseDTO.builder().inputSetTemplateYaml(environmentInputsYaml).build());
  }

  @GET
  @Path("/serviceOverrides/runtimeInputs")
  @ApiOperation(value = "This api returns Service Override inputs YAML", nickname = "getServiceOverrideInputs")
  @Hidden
  public ResponseDTO<NGEntityTemplateResponseDTO> getServiceOverrideInputs(
      @Parameter(description = ENVIRONMENT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ENVIRONMENT_IDENTIFIER_KEY) @ResourceIdentifier String environmentIdentifier,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = NGCommonEntityConstants.SERVICE_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.SERVICE_IDENTIFIER_KEY) @ResourceIdentifier String serviceIdentifier) {
    String serviceOverrideInputsYaml = serviceOverrideService.createServiceOverrideInputsYaml(
        accountId, orgIdentifier, projectIdentifier, environmentIdentifier, serviceIdentifier);
    return ResponseDTO.newResponse(
        NGEntityTemplateResponseDTO.builder().inputSetTemplateYaml(serviceOverrideInputsYaml).build());
  }

  @GET
  @Hidden
  @Path("/attributes")
  @ApiOperation(hidden = true, value = "Get Environments Attributes", nickname = "getEnvironmentsAttributes")
  @InternalApi
  public ResponseDTO<List<Map<String, String>>> getEnvironmentsAttributes(
      @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @QueryParam("envIdentifiers") List<String> envIdentifiers) {
    return ResponseDTO.newResponse(
        environmentService.getAttributes(accountId, orgIdentifier, projectIdentifier, envIdentifiers));
  }

  private void checkForServiceOverrideUpdateAccess(
      String accountId, String orgIdentifier, String projectIdentifier, String environmentRef, String serviceRef) {
    final List<PermissionCheckDTO> permissionCheckDTOList = new ArrayList<>();
    permissionCheckDTOList.add(PermissionCheckDTO.builder()
                                   .permission(ENVIRONMENT_UPDATE_PERMISSION)
                                   .resourceIdentifier(environmentRef)
                                   .resourceType(ENVIRONMENT)
                                   .resourceScope(ResourceScope.of(accountId, orgIdentifier, projectIdentifier))
                                   .build());
    permissionCheckDTOList.add(PermissionCheckDTO.builder()
                                   .permission(SERVICE_UPDATE_PERMISSION)
                                   .resourceIdentifier(serviceRef)
                                   .resourceType(SERVICE)
                                   .resourceScope(ResourceScope.of(accountId, orgIdentifier, projectIdentifier))
                                   .build());

    final AccessCheckResponseDTO accessCheckResponse = accessControlClient.checkForAccess(permissionCheckDTOList);
    accessCheckResponse.getAccessControlList().forEach(accessControlDTO -> {
      if (!accessControlDTO.isPermitted()) {
        String errorMessage;
        errorMessage = String.format("Missing permission %s on %s", accessControlDTO.getPermission(),
            accessControlDTO.getResourceType().toLowerCase());
        if (!StringUtils.isEmpty(accessControlDTO.getResourceIdentifier())) {
          errorMessage =
              errorMessage.concat(String.format(" with identifier %s", accessControlDTO.getResourceIdentifier()));
        }
        throw new InvalidRequestException(errorMessage, WingsException.USER);
      }
    });
  }

  private List<EnvironmentResponse> filterEnvironmentResponseByPermissionAndId(
      List<AccessControlDTO> accessControlList, List<EnvironmentResponse> environmentList) {
    List<EnvironmentResponse> filteredAccessControlDtoList = new ArrayList<>();
    for (int i = 0; i < accessControlList.size(); i++) {
      AccessControlDTO accessControlDTO = accessControlList.get(i);
      EnvironmentResponse environmentResponse = environmentList.get(i);
      if (accessControlDTO.isPermitted()
          && environmentResponse.getEnvironment().getIdentifier().equals(accessControlDTO.getResourceIdentifier())) {
        filteredAccessControlDtoList.add(environmentResponse);
      }
    }
    return filteredAccessControlDtoList;
  }

  private void throwExceptionForNoRequestDTO(EnvironmentRequestDTO dto) {
    if (dto == null) {
      throw new InvalidRequestException(
          "No request body sent in the API. Following field is required: identifier, type. Other optional fields: name, orgIdentifier, projectIdentifier, tags, description, version");
    }
  }

  private void throwExceptionForNoRequestDTO(ServiceOverrideRequestDTO dto) {
    if (dto == null) {
      throw new InvalidRequestException("No request body for Service overrides sent in the API");
    }
  }

  private void validateEnvironmentScope(EnvironmentRequestDTO requestDTO, String accountId) {
    try {
      if (checkFeatureFlagForEnvOrgAccountLevel(accountId)) {
        if (isNotEmpty(requestDTO.getProjectIdentifier())) {
          Preconditions.checkArgument(isNotEmpty(requestDTO.getOrgIdentifier()),
              "org identifier must be specified when project identifier is specified. Environments can be created at Project/Org/Account scope");
        }
      } else {
        Preconditions.checkArgument(isNotEmpty(requestDTO.getOrgIdentifier()),
            "org identifier must be specified. Environments only be created at Project scope");
        Preconditions.checkArgument(isNotEmpty(requestDTO.getProjectIdentifier()),
            "project identifier must be specified. Environments can only be created at Project scope");
      }
    } catch (Exception ex) {
      throw new InvalidRequestException(ex.getMessage());
    }
  }

  private void validateServiceOverrideScope(ServiceOverrideRequestDTO requestDTO, String accountId) {
    try {
      if (checkFeatureFlagForEnvOrgAccountLevel(accountId)) {
        if (isNotEmpty(requestDTO.getProjectIdentifier())) {
          Preconditions.checkArgument(isNotEmpty(requestDTO.getOrgIdentifier()),
              "org identifier must be specified when project identifier is specified. Service Overrides can be created at Project/Org/Account scope");
        }
      } else {
        Preconditions.checkArgument(isNotEmpty(requestDTO.getOrgIdentifier()),
            "org identifier must be specified. Service overrides can only be created at Project scope");
        Preconditions.checkArgument(isNotEmpty(requestDTO.getProjectIdentifier()),
            "project identifier must be specified. Service overrides can only be created at Project scope");
      }
    } catch (Exception ex) {
      throw new InvalidRequestException(ex.getMessage());
    }
  }
}
