/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.resources;

import static io.harness.cvng.core.services.CVNextGenConstants.LOG_RECORD_RESOURCE_PATH;

import io.harness.annotations.ExposeInternalException;
import io.harness.cvng.beans.LogRecordDTO;
import io.harness.cvng.core.services.api.LogRecordService;
import io.harness.rest.RestResponse;
import io.harness.security.annotations.DelegateAuth;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import retrofit2.http.Body;

@Api(LOG_RECORD_RESOURCE_PATH)
@Path(LOG_RECORD_RESOURCE_PATH)
@Produces("application/json")
@ExposeInternalException
public class LogRecordResource {
  @Inject private LogRecordService logRecordService;

  @POST
  @Timed
  @ExceptionMetered
  @DelegateAuth
  @ApiOperation(value = "saves log data collected for verification", nickname = "saveLogRecords")
  public RestResponse<Void> saveLogRecords(
      @QueryParam("accountId") @NotNull String accountId, @NotNull @Valid @Body List<LogRecordDTO> logRecords) {
    logRecordService.save(logRecords);
    return new RestResponse<>(null);
  }
}
