/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.resources;

import io.harness.annotations.ExposeInternalException;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.services.api.AwsService;
import io.harness.cvng.core.services.api.CloudWatchService;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.security.annotations.NextGenManagerAuth;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.ws.rs.BeanParam;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

@Api("cloudwatch")
@Path("/cloudwatch")
@Produces("application/json")
@NextGenManagerAuth
@ExposeInternalException
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@OwnedBy(HarnessTeam.CV)
public class CloudWatchMetricsResource {
  @Inject private CloudWatchService cloudWatchService;
  @Inject private AwsService awsService;

  @GET
  @Path("/metrics/fetch-sample-data")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "get sample data for given query", nickname = "getSampleDataForQuery")
  public ResponseDTO<Map> getSampleDataForQuery(@NotNull @BeanParam ProjectParams projectParams,
      @QueryParam("connectorIdentifier") @NotNull @NotBlank String connectorIdentifier,
      @QueryParam("requestGuid") @NotNull @NotBlank String requestGuid,
      @QueryParam("region") @NotNull @NotBlank String region,
      @QueryParam("expression") @NotNull @NotBlank String expression, @QueryParam("metricName") String metricName,
      @QueryParam("metricIdentifier") String metricIdentifier) {
    return ResponseDTO.newResponse(cloudWatchService.fetchSampleData(
        projectParams, connectorIdentifier, requestGuid, expression, region, metricName, metricIdentifier));
  }

  @GET
  @Path("/metrics/regions")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "get regions", nickname = "getRegions")
  @Deprecated
  public ResponseDTO<List<String>> getRegions() {
    return ResponseDTO.newResponse(awsService.fetchRegions());
  }
}
