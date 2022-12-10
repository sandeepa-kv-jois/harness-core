/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.scim.resource;

import io.harness.scim.PatchRequest;
import io.harness.scim.ScimGroup;
import io.harness.scim.ScimListResponse;
import io.harness.scim.ScimResource;
import io.harness.scim.service.ScimGroupService;
import io.harness.security.annotations.ScimAPI;

import com.google.inject.Inject;
import io.dropwizard.jersey.PATCH;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;

@Api("scim")
@Path("/scim/account/{accountIdentifier}/")
@Consumes({"application/scim+json", "application/json"})
@Produces({"application/scim+json", "application/json"})
@Slf4j
@ScimAPI
public class NGScimGroupResource extends ScimResource {
  @Inject private ScimGroupService scimGroupService;

  @POST
  @Path("Groups")
  @ApiOperation(value = "Create a new group and return uuid in response", nickname = "createScimGroup")
  public Response createGroup(ScimGroup groupQuery, @PathParam("accountIdentifier") String accountIdentifier) {
    try {
      return Response.status(Response.Status.CREATED)
          .entity(scimGroupService.createGroup(groupQuery, accountIdentifier))
          .build();
    } catch (Exception ex) {
      log.error("NGSCIM: Failed to create the group, for accountId {}, Exception is", accountIdentifier, ex);
      return getExceptionResponse(ex, Response.Status.NOT_FOUND.getStatusCode(), Response.Status.CONFLICT);
    }
  }

  @GET
  @Path("Groups/{groupIdentifier}")
  @ApiOperation(value = "Fetch an existing user by uuid", nickname = "getScimGroup")
  public Response getGroup(
      @PathParam("accountIdentifier") String accountIdentifier, @PathParam("groupIdentifier") String groupIdentifier) {
    try {
      return Response.status(Response.Status.OK)
          .entity(scimGroupService.getGroup(groupIdentifier, accountIdentifier))
          .build();
    } catch (Exception ex) {
      log.error("NGSCIM: Failed to fetch the groups with id: {}, for accountId {}, Exception is", groupIdentifier,
          accountIdentifier, ex);
      return getExceptionResponse(ex, Response.Status.NOT_FOUND.getStatusCode(), Response.Status.NOT_FOUND);
    }
  }

  @DELETE
  @Path("Groups/{groupIdentifier}")
  @ApiOperation(value = "Delete an existing user by uuid", nickname = "deleteScimGroup")
  public Response deleteGroup(
      @PathParam("accountIdentifier") String accountIdentifier, @PathParam("groupIdentifier") String groupIdentifier) {
    scimGroupService.deleteGroup(groupIdentifier, accountIdentifier);
    return Response.status(Response.Status.NO_CONTENT).build();
  }

  @GET
  @Path("Groups")
  @ApiOperation(
      value =
          "Search groups by their name. Supports pagination. If nothing is passed in filter, all results will be returned.",
      nickname = "searchScimGroup")
  public Response
  searchGroup(@PathParam("accountIdentifier") String accountIdentifier, @QueryParam("filter") String filter,
      @QueryParam("count") Integer count, @QueryParam("startIndex") Integer startIndex) {
    try {
      ScimListResponse<ScimGroup> groupResources =
          scimGroupService.searchGroup(filter, accountIdentifier, count, startIndex);
      return Response.status(Response.Status.OK).entity(groupResources).build();
    } catch (Exception ex) {
      log.error("NGSCIM: Search group call failed. AccountId: {}, filter: {}, count: {}, startIndex{}",
          accountIdentifier, filter, count, startIndex, ex);
      return getExceptionResponse(ex, Response.Status.NOT_FOUND.getStatusCode(), Response.Status.NOT_FOUND);
    }
  }

  @PATCH
  @Path("Groups/{groupIdentifier}")
  @ApiOperation(value = "Update some fields of a groups by uuid. Can update members/name", nickname = "patchScimGroup")
  public Response updateGroup(@PathParam("accountIdentifier") String accountIdentifier,
      @PathParam("groupIdentifier") String groupIdentifier, PatchRequest patchRequest) {
    return scimGroupService.updateGroup(groupIdentifier, accountIdentifier, patchRequest);
  }

  @PUT
  @Path("Groups/{groupIdentifier}")
  @ApiOperation(value = "Update a group", nickname = "updateScimGroup")
  public Response updateGroup(@PathParam("accountIdentifier") String accountIdentifier,
      @PathParam("groupIdentifier") String groupIdentifier, ScimGroup groupQuery) {
    try {
      return scimGroupService.updateGroup(groupIdentifier, accountIdentifier, groupQuery);
    } catch (Exception ex) {
      log.info("NGSCIM: Failed to update the group with id: {}, accountId {} ", groupIdentifier, accountIdentifier, ex);
      return getExceptionResponse(ex, Response.Status.NOT_FOUND.getStatusCode(), Response.Status.PRECONDITION_FAILED);
    }
  }
}
