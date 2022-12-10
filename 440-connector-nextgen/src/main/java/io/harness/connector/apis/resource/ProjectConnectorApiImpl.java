/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.apis.resource;

import static io.harness.NGCommonEntityConstants.DIFFERENT_ORG_IN_PAYLOAD_AND_PARAM;
import static io.harness.NGCommonEntityConstants.DIFFERENT_PROJECT_IN_PAYLOAD_AND_PARAM;
import static io.harness.NGCommonEntityConstants.DIFFERENT_SLUG_IN_PAYLOAD_AND_PARAM;
import static io.harness.NGConstants.HARNESS_SECRET_MANAGER_IDENTIFIER;
import static io.harness.connector.accesscontrol.ConnectorsAccessControlPermissions.DELETE_CONNECTOR_PERMISSION;
import static io.harness.connector.accesscontrol.ConnectorsAccessControlPermissions.EDIT_CONNECTOR_PERMISSION;
import static io.harness.connector.accesscontrol.ConnectorsAccessControlPermissions.VIEW_CONNECTOR_PERMISSION;
import static io.harness.exception.WingsException.USER;
import static io.harness.utils.PageUtils.getNGPageResponse;

import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.accesscontrol.OrgIdentifier;
import io.harness.accesscontrol.ProjectIdentifier;
import io.harness.accesscontrol.ResourceIdentifier;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorRegistryFactory;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.ConnectorValidationResult;
import io.harness.connector.accesscontrol.ResourceTypes;
import io.harness.connector.helper.ConnectorRbacHelper;
import io.harness.connector.services.ConnectorService;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.exception.ConnectorNotFoundException;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.beans.PageResponse;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.spec.server.connector.v1.ProjectConnectorApi;
import io.harness.spec.server.connector.v1.model.ConnectorRequest;
import io.harness.spec.server.connector.v1.model.ConnectorResponse;
import io.harness.spec.server.connector.v1.model.ConnectorTestConnectionResponse;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;

@NextGenManagerAuth
public class ProjectConnectorApiImpl implements ProjectConnectorApi {
  private final AccessControlClient accessControlClient;
  private final ConnectorService connectorService;
  private final ConnectorApiUtils connectorApiUtils;
  private final ConnectorRbacHelper connectorRbacHelper;

  @Inject
  public ProjectConnectorApiImpl(AccessControlClient accessControlClient,
      @Named("connectorDecoratorService") ConnectorService connectorService, ConnectorApiUtils connectorApiUtils,
      ConnectorRbacHelper connectorRbacHelper) {
    this.accessControlClient = accessControlClient;
    this.connectorService = connectorService;
    this.connectorApiUtils = connectorApiUtils;
    this.connectorRbacHelper = connectorRbacHelper;
  }

  @NGAccessControlCheck(resourceType = ResourceTypes.CONNECTOR, permission = EDIT_CONNECTOR_PERMISSION)
  @Override
  public Response createProjectScopedConnector(ConnectorRequest request, @OrgIdentifier String org,
      @ProjectIdentifier String project, @AccountIdentifier String account) {
    return createConnector(request, account, org, project);
  }

  @NGAccessControlCheck(resourceType = ResourceTypes.CONNECTOR, permission = DELETE_CONNECTOR_PERMISSION)
  @Override
  public Response deleteProjectScopedConnector(@OrgIdentifier String org, @ProjectIdentifier String project,
      @ResourceIdentifier String connector, @AccountIdentifier String account) {
    if (HARNESS_SECRET_MANAGER_IDENTIFIER.equals(connector)) {
      throw new InvalidRequestException("Delete operation not supported for Harness Secret Manager");
    }
    Optional<ConnectorResponseDTO> connectorResponseDTO = connectorService.get(account, org, project, connector);
    if (!connectorResponseDTO.isPresent()) {
      throw new NotFoundException(String.format("Connector with identifier [%s] not found", connector));
    }
    boolean deleted = connectorService.delete(account, org, project, connector, false);

    if (!deleted) {
      throw new InvalidRequestException(String.format("Connector with slug [%s] could not be deleted", connector));
    }
    ConnectorResponseDTO responseDTO = connectorResponseDTO.get();
    ConnectorResponse connectorResponse = connectorApiUtils.toConnectorResponse(responseDTO);

    return Response.ok().entity(connectorResponse).build();
  }

  @NGAccessControlCheck(resourceType = ResourceTypes.CONNECTOR, permission = VIEW_CONNECTOR_PERMISSION)
  @Override
  public Response getProjectScopedConnector(@OrgIdentifier String org, @ProjectIdentifier String project,
      @ResourceIdentifier String connector, @AccountIdentifier String account) {
    Optional<ConnectorResponseDTO> connectorResponseDTO = connectorService.get(account, org, project, connector);
    if (!connectorResponseDTO.isPresent()) {
      throw new NotFoundException(String.format("Connector with identifier [%s] not found", connector));
    }
    ConnectorResponseDTO responseDTO = connectorResponseDTO.get();
    ConnectorResponse connectorResponse = connectorApiUtils.toConnectorResponse(responseDTO);

    return Response.ok().entity(connectorResponse).build();
  }

  @NGAccessControlCheck(resourceType = ResourceTypes.CONNECTOR, permission = VIEW_CONNECTOR_PERMISSION)
  @Override
  public Response getProjectScopedConnectors(@OrgIdentifier String org, @ProjectIdentifier String project,
      Boolean recursive, String searchTerm, Integer page, Integer limit, @AccountIdentifier String account) {
    PageResponse<ConnectorResponseDTO> pageResponse = getNGPageResponse(
        connectorService.list(page, limit, account, null, org, project, null, searchTerm, recursive, null));

    List<ConnectorResponseDTO> connectorResponseDTOS = pageResponse.getContent();

    List<ConnectorResponse> connectorResponses = connectorApiUtils.toConnectorResponses(connectorResponseDTOS);

    ResponseBuilder responseBuilder = Response.ok();
    ResponseBuilder responseBuilderWithLinks = connectorApiUtils.addLinksHeader(responseBuilder,
        String.format("/v1/orgs/%s/projects/%s/connectors", org, project), connectorResponses.size(), page, limit);

    return responseBuilderWithLinks.entity(connectorResponses).build();
  }

  @Override
  public Response testProjectScopedConnector(String org, String project, String connector, String account) {
    connectorService.get(account, org, project, connector)
        .map(connectorResponseDTO
            -> connectorRbacHelper.checkSecretRuntimeAccessWithConnectorDTO(
                connectorResponseDTO.getConnector(), account))
        .orElseThrow(()
                         -> new ConnectorNotFoundException(
                             String.format("No connector found with identifier %s", connector), USER));

    ConnectorValidationResult connectorValidationResult =
        connectorService.testConnection(account, org, project, connector);
    ConnectorTestConnectionResponse connectorTestConnectionResponse =
        connectorApiUtils.toConnectorTestConnectionResponse(connectorValidationResult);

    return Response.ok().entity(connectorTestConnectionResponse).build();
  }

  @NGAccessControlCheck(resourceType = ResourceTypes.CONNECTOR, permission = EDIT_CONNECTOR_PERMISSION)
  @Override
  public Response updateProjectScopedConnector(ConnectorRequest connectorRequest, @OrgIdentifier String org,
      @ProjectIdentifier String project, @ResourceIdentifier String connector, @AccountIdentifier String account) {
    if (!Objects.equals(connectorRequest.getConnector().getSlug(), connector)) {
      throw new InvalidRequestException(DIFFERENT_SLUG_IN_PAYLOAD_AND_PARAM, USER);
    }
    if (!Objects.equals(connectorRequest.getConnector().getOrg(), org)) {
      throw new InvalidRequestException(DIFFERENT_ORG_IN_PAYLOAD_AND_PARAM, USER);
    }
    if (!Objects.equals(connectorRequest.getConnector().getProject(), project)) {
      throw new InvalidRequestException(DIFFERENT_PROJECT_IN_PAYLOAD_AND_PARAM, USER);
    }

    if (HARNESS_SECRET_MANAGER_IDENTIFIER.equals(connectorRequest.getConnector().getSlug())) {
      throw new InvalidRequestException("Update operation not supported for Harness Secret Manager");
    }
    ConnectorDTO connectorDTO = connectorApiUtils.toConnectorDTO(connectorRequest);
    ConnectorResponseDTO connectorResponseDTO = connectorService.update(connectorDTO, account);
    ConnectorResponse connectorResponse = connectorApiUtils.toConnectorResponse(connectorResponseDTO);

    return Response.ok().entity(connectorResponse).build();
  }

  private Response createConnector(ConnectorRequest request, String account, String org, String project) {
    if (!Objects.equals(request.getConnector().getOrg(), org)) {
      throw new InvalidRequestException(DIFFERENT_ORG_IN_PAYLOAD_AND_PARAM, USER);
    }
    if (!Objects.equals(request.getConnector().getProject(), project)) {
      throw new InvalidRequestException(DIFFERENT_PROJECT_IN_PAYLOAD_AND_PARAM, USER);
    }
    if (HARNESS_SECRET_MANAGER_IDENTIFIER.equals(request.getConnector().getSlug())) {
      throw new InvalidRequestException(
          String.format("%s cannot be used as connector identifier", HARNESS_SECRET_MANAGER_IDENTIFIER), USER);
    }
    if (request.getConnector().getSpec().getType().value() == ConnectorType.LOCAL.getDisplayName()) {
      throw new InvalidRequestException("Local Secret Manager creation not supported", USER);
    }
    Map<String, String> connectorAttributes = new HashMap<>();
    connectorAttributes.put("category",
        ConnectorRegistryFactory.getConnectorCategory(connectorApiUtils.getConnectorType(request.getConnector()))
            .toString());
    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(account, request.getConnector().getOrg(), request.getConnector().getProject()),
        Resource.of(ResourceTypes.CONNECTOR, null, connectorAttributes), EDIT_CONNECTOR_PERMISSION);
    ConnectorResponseDTO connectorResponseDTO =
        connectorService.create(connectorApiUtils.toConnectorDTO(request), account);
    ConnectorResponse connectorResponse = connectorApiUtils.toConnectorResponse(connectorResponseDTO);

    return Response.status(Response.Status.CREATED).entity(connectorResponse).build();
  }
}
