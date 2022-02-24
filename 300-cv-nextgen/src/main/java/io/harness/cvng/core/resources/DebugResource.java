/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.resources;

import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.OrgIdentifier;
import io.harness.accesscontrol.ProjectIdentifier;
import io.harness.accesscontrol.ResourceIdentifier;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cvng.core.beans.SLODebugResponse;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.services.api.DebugService;
import io.harness.rest.RestResponse;
import io.harness.security.annotations.NextGenManagerAuth;

import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import javax.validation.constraints.NotNull;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

@Api("debug")
@Path("debug")
@Produces("application/json")
@OwnedBy(HarnessTeam.CV)
@NextGenManagerAuth
public class DebugResource {
  @Inject DebugService debugService;

  @GET
  @Timed
  @Path("slo/{identifier}")
  @ApiOperation(value = "Gets SLO debug data", nickname = "getSLODebugData")
  public RestResponse<SLODebugResponse> getSLODebug(
      @ApiParam(required = true) @NotNull @QueryParam("accountId") @AccountIdentifier String accountId,
      @ApiParam(required = true) @NotNull @PathParam("identifier") @ResourceIdentifier String identifier,
      @ApiParam(required = true) @NotNull @QueryParam("orgIdentifier") @OrgIdentifier String orgIdentifier,
      @ApiParam(required = true) @NotNull @QueryParam(
          "projectIdentifier") @ProjectIdentifier String projectIdentifier) {
    ProjectParams projectParams = ProjectParams.builder()
                                      .accountIdentifier(accountId)
                                      .orgIdentifier(orgIdentifier)
                                      .projectIdentifier(projectIdentifier)
                                      .build();

    return new RestResponse<>(debugService.getSLODebugResponse(projectParams, identifier));
  }
}
