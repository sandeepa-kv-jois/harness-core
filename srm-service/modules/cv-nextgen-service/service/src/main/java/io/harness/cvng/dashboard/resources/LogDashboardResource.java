/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.dashboard.resources;

import io.harness.annotations.ExposeInternalException;
import io.harness.cvng.analysis.beans.LiveMonitoringLogAnalysisRadarChartClusterDTO;
import io.harness.cvng.core.beans.params.MonitoredServiceParams;
import io.harness.cvng.core.beans.params.PageParams;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.beans.params.filterParams.MonitoredServiceLogAnalysisFilter;
import io.harness.cvng.dashboard.beans.AnalyzedRadarChartLogDataWithCountDTO;
import io.harness.cvng.dashboard.services.api.LogDashboardService;
import io.harness.rest.RestResponse;
import io.harness.security.annotations.NextGenManagerAuth;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import java.util.List;
import javax.validation.constraints.NotNull;
import javax.ws.rs.BeanParam;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

@Api("log-dashboard")
@Path("/log-dashboard")
@Produces("application/json")
@ExposeInternalException
@NextGenManagerAuth
public class LogDashboardResource {
  @Inject private LogDashboardService logDashboardService;
  @GET
  @Path("/logs-radar-chart-cluster")
  @Timed
  @ExceptionMetered
  @ApiOperation(
      value = "get all log cluster data for a time range and filters", nickname = "getAllRadarChartLogsClusterData")
  public RestResponse<List<LiveMonitoringLogAnalysisRadarChartClusterDTO>>
  getLogsRadarChartClusterData(@NotNull @BeanParam ProjectParams projectParams,
      @QueryParam("monitoredServiceIdentifier") String monitoredServiceIdentifier,
      @BeanParam MonitoredServiceLogAnalysisFilter monitoredServiceLogAnalysisFilter) {
    MonitoredServiceParams monitoredServiceParams = MonitoredServiceParams.builder()
                                                        .accountIdentifier(projectParams.getAccountIdentifier())
                                                        .orgIdentifier(projectParams.getOrgIdentifier())
                                                        .projectIdentifier(projectParams.getProjectIdentifier())
                                                        .monitoredServiceIdentifier(monitoredServiceIdentifier)
                                                        .build();

    return new RestResponse<>(logDashboardService.getLogAnalysisRadarChartClusters(
        monitoredServiceParams, monitoredServiceLogAnalysisFilter));
  }

  @GET
  @Path("/logs-radar-chart-data")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "get all log data for a time range and filters", nickname = "getAllRadarChartLogsData")
  public RestResponse<AnalyzedRadarChartLogDataWithCountDTO> getAllLogsRadarChartData(
      @NotNull @BeanParam ProjectParams projectParams,
      @QueryParam("monitoredServiceIdentifier") String monitoredServiceIdentifier,
      @BeanParam MonitoredServiceLogAnalysisFilter monitoredServiceLogAnalysisFilter,
      @BeanParam PageParams pageParams) {
    MonitoredServiceParams monitoredServiceParams = MonitoredServiceParams.builder()
                                                        .accountIdentifier(projectParams.getAccountIdentifier())
                                                        .orgIdentifier(projectParams.getOrgIdentifier())
                                                        .projectIdentifier(projectParams.getProjectIdentifier())
                                                        .monitoredServiceIdentifier(monitoredServiceIdentifier)
                                                        .build();

    return new RestResponse<>(logDashboardService.getAllRadarChartLogsData(
        monitoredServiceParams, monitoredServiceLogAnalysisFilter, pageParams));
  }
}
