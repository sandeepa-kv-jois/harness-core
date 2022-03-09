/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.api;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import static java.lang.String.format;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ngmigration.beans.DiscoveryInput;
import io.harness.ngmigration.beans.MigrationInputDTO;
import io.harness.ngmigration.beans.MigrationInputResult;
import io.harness.ngmigration.service.DiscoveryService;
import io.harness.rest.RestResponse;

import software.wings.ngmigration.DiscoveryResult;
import software.wings.ngmigration.NGMigrationEntityType;
import software.wings.ngmigration.NGYamlFile;
import software.wings.security.PermissionAttribute.ResourceType;
import software.wings.security.annotations.Scope;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import java.util.List;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDC)
@Slf4j
@Path("/")
@Consumes(APPLICATION_JSON)
@Produces(APPLICATION_JSON)
@Scope(ResourceType.APPLICATION)
public class NgMigrationResource {
  @Inject DiscoveryService discoveryService;

  @POST
  @Path("/discover-multi")
  @Timed
  @ExceptionMetered
  public RestResponse<DiscoveryResult> discoverMultipleEntities(@QueryParam("accountId") String accountId,
      @QueryParam("exportImg") boolean exportImage, DiscoveryInput discoveryInput) {
    discoveryInput.setExportImage(discoveryInput.isExportImage() || exportImage);
    return new RestResponse<>(discoveryService.discoverMulti(accountId, discoveryInput));
  }

  @GET
  @Path("/discover")
  @Timed
  @ExceptionMetered
  public RestResponse<DiscoveryResult> discoverEntities(@QueryParam("entityId") String entityId,
      @QueryParam("appId") String appId, @QueryParam("accountId") String accountId,
      @QueryParam("entityType") NGMigrationEntityType entityType, @QueryParam("exportImg") boolean exportImage) {
    return new RestResponse<>(discoveryService.discover(accountId, appId, entityId, entityType, exportImage));
  }

  @POST
  @Path("/files")
  @Timed
  @ExceptionMetered
  public RestResponse<List<NGYamlFile>> getMigratedFiles(@HeaderParam("Authorization") String auth,
      @QueryParam("entityId") String entityId, @QueryParam("appId") String appId,
      @QueryParam("accountId") String accountId, @QueryParam("entityType") NGMigrationEntityType entityType,
      @QueryParam("dryRun") boolean dryRun, MigrationInputDTO inputDTO) {
    DiscoveryResult result = discoveryService.discover(accountId, appId, entityId, entityType, false);
    return new RestResponse<>(discoveryService.migrateEntity(auth, inputDTO, result, dryRun));
  }

  @POST
  @Path("/export")
  @Timed
  @ExceptionMetered
  public Response exportZippedYamlFiles(@HeaderParam("Authorization") String auth,
      @QueryParam("entityId") String entityId, @QueryParam("appId") String appId,
      @QueryParam("accountId") String accountId, @QueryParam("entityType") NGMigrationEntityType entityType,
      MigrationInputDTO inputDTO) {
    DiscoveryResult result = discoveryService.discover(accountId, appId, entityId, entityType, false);
    return Response.ok(discoveryService.exportYamlFilesAsZip(inputDTO, result), MediaType.APPLICATION_OCTET_STREAM)
        .header("content-disposition", format("attachment; filename = %s_%s_%s.zip", accountId, entityId, entityType))
        .build();
  }

  @GET
  @Path("/input")
  @Timed
  @ExceptionMetered
  public RestResponse<MigrationInputResult> getInputs(@QueryParam("entityId") String entityId,
      @QueryParam("appId") String appId, @QueryParam("accountId") String accountId,
      @QueryParam("entityType") NGMigrationEntityType entityType) {
    DiscoveryResult result = discoveryService.discover(accountId, appId, entityId, entityType, false);
    return new RestResponse<>(discoveryService.migrationInput(result));
  }
}
