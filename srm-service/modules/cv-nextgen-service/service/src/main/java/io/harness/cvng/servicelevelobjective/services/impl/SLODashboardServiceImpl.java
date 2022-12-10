/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.services.impl;

import io.harness.cvng.client.NextGenService;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceDTO;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceResponse;
import io.harness.cvng.core.beans.params.PageParams;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.beans.params.TimeRangeParams;
import io.harness.cvng.core.services.api.monitoredService.MonitoredServiceService;
import io.harness.cvng.core.utils.DateTimeUtils;
import io.harness.cvng.servicelevelobjective.SLORiskCountResponse;
import io.harness.cvng.servicelevelobjective.beans.MSDropdownResponse;
import io.harness.cvng.servicelevelobjective.beans.MonitoredServiceDetail;
import io.harness.cvng.servicelevelobjective.beans.SLOConsumptionBreakdown;
import io.harness.cvng.servicelevelobjective.beans.SLODashboardApiFilter;
import io.harness.cvng.servicelevelobjective.beans.SLODashboardDetail;
import io.harness.cvng.servicelevelobjective.beans.SLODashboardWidget;
import io.harness.cvng.servicelevelobjective.beans.SLODashboardWidget.SLODashboardWidgetBuilder;
import io.harness.cvng.servicelevelobjective.beans.SLOErrorBudgetResetDTO;
import io.harness.cvng.servicelevelobjective.beans.SLOHealthListView;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveDetailsRefDTO;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveResponse;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveType;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveV2DTO;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveV2Response;
import io.harness.cvng.servicelevelobjective.beans.UserJourneyDTO;
import io.harness.cvng.servicelevelobjective.beans.slospec.SimpleServiceLevelObjectiveSpec;
import io.harness.cvng.servicelevelobjective.entities.AbstractServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.entities.CompositeServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.entities.CompositeServiceLevelObjective.ServiceLevelObjectivesDetail;
import io.harness.cvng.servicelevelobjective.entities.SLOHealthIndicator;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.entities.SimpleServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.entities.TimePeriod;
import io.harness.cvng.servicelevelobjective.entities.UserJourney;
import io.harness.cvng.servicelevelobjective.services.api.GraphDataService;
import io.harness.cvng.servicelevelobjective.services.api.SLODashboardService;
import io.harness.cvng.servicelevelobjective.services.api.SLOErrorBudgetResetService;
import io.harness.cvng.servicelevelobjective.services.api.SLOHealthIndicatorService;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelObjectiveService;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelObjectiveV2Service;
import io.harness.cvng.servicelevelobjective.services.api.UserJourneyService;
import io.harness.ng.beans.PageResponse;
import io.harness.utils.PageUtils;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class SLODashboardServiceImpl implements SLODashboardService {
  @Inject private ServiceLevelObjectiveService serviceLevelObjectiveService;
  @Inject private ServiceLevelObjectiveV2Service serviceLevelObjectiveV2Service;
  @Inject private MonitoredServiceService monitoredServiceService;
  @Inject private GraphDataService graphDataService;
  @Inject private SLOHealthIndicatorService sloHealthIndicatorService;
  @Inject private Clock clock;
  @Inject private NextGenService nextGenService;
  @Inject private SLOErrorBudgetResetService sloErrorBudgetResetService;
  @Inject private UserJourneyService userJourneyService;

  @Override
  public PageResponse<SLODashboardWidget> getSloDashboardWidgets(
      ProjectParams projectParams, SLODashboardApiFilter filter, PageParams pageParams) {
    PageResponse<ServiceLevelObjectiveResponse> sloPageResponse =
        serviceLevelObjectiveService.getSLOForDashboard(projectParams, filter, pageParams);

    // sending second param as null deliberately so that this deprecated function does not break
    List<SLODashboardWidget> sloDashboardWidgets =
        sloPageResponse.getContent()
            .stream()
            .map(sloResponse -> getSloDashboardWidget(projectParams, null, null))
            .collect(Collectors.toList());
    return PageResponse.<SLODashboardWidget>builder()
        .pageSize(sloPageResponse.getPageSize())
        .pageIndex(sloPageResponse.getPageIndex())
        .totalPages(sloPageResponse.getTotalPages())
        .totalItems(sloPageResponse.getTotalItems())
        .pageItemCount(sloPageResponse.getPageItemCount())
        .content(sloDashboardWidgets)
        .build();
  }

  @Override
  public PageResponse<SLOHealthListView> getSloHealthListView(
      ProjectParams projectParams, SLODashboardApiFilter filter, PageParams pageParams) {
    PageResponse<AbstractServiceLevelObjective> sloPageResponse =
        serviceLevelObjectiveV2Service.getSLOForListView(projectParams, filter, pageParams);

    Set<String> monitoredServiceIdentifiers =
        sloPageResponse.getContent()
            .stream()
            .filter(slo -> slo.getType().equals(ServiceLevelObjectiveType.SIMPLE))
            .map(slo -> ((SimpleServiceLevelObjective) slo).getMonitoredServiceIdentifier())
            .collect(Collectors.toSet());
    Set<String> scopedMonitoredServices =
        sloPageResponse.getContent()
            .stream()
            .filter(slo -> slo.getType().equals(ServiceLevelObjectiveType.SIMPLE))
            .map(slo
                -> getScopedInformation(slo.getAccountId(), slo.getOrgIdentifier(), slo.getProjectIdentifier(),
                    ((SimpleServiceLevelObjective) slo).getMonitoredServiceIdentifier()))
            .collect(Collectors.toSet());
    List<MonitoredServiceResponse> monitoredServicesFromIdentifiers =
        monitoredServiceService.get(projectParams.getAccountIdentifier(), monitoredServiceIdentifiers);
    List<MonitoredServiceResponse> monitoredServicesFromScopedIdentifiers =
        monitoredServicesFromIdentifiers.stream()
            .filter(monitoredService
                -> scopedMonitoredServices.contains(getScopedInformation(projectParams.getAccountIdentifier(),
                    monitoredService.getMonitoredServiceDTO().getOrgIdentifier(),
                    monitoredService.getMonitoredServiceDTO().getProjectIdentifier(),
                    monitoredService.getMonitoredServiceDTO().getIdentifier())))
            .collect(Collectors.toList());

    List<String> sloIdentifiers = sloPageResponse.getContent()
                                      .stream()
                                      .map(AbstractServiceLevelObjective::getIdentifier)
                                      .collect(Collectors.toList());
    List<String> scopedSLOIdentifiers = sloPageResponse.getContent()
                                            .stream()
                                            .map(slo
                                                -> getScopedInformation(slo.getAccountId(), slo.getOrgIdentifier(),
                                                    slo.getProjectIdentifier(), slo.getIdentifier()))
                                            .collect(Collectors.toList());
    List<SLOHealthIndicator> sloHealthIndicatorsFromIdentifiers =
        sloHealthIndicatorService.getBySLOIdentifiers(projectParams.getAccountIdentifier(), sloIdentifiers);
    List<SLOHealthIndicator> sloHealthIndicatorsFromScopedIdentifiers =
        sloHealthIndicatorsFromIdentifiers.stream()
            .filter(sloHealthIndicator
                -> scopedSLOIdentifiers.contains(getScopedInformation(sloHealthIndicator.getAccountId(),
                    sloHealthIndicator.getOrgIdentifier(), sloHealthIndicator.getProjectIdentifier(),
                    sloHealthIndicator.getServiceLevelObjectiveIdentifier())))
            .collect(Collectors.toList());

    List<UserJourney> userJourneyList = userJourneyService.get(projectParams);

    Map<String, MonitoredServiceDTO> scopedMonitoredServiceIdentifierToDTOMap =
        monitoredServicesFromScopedIdentifiers.stream()
            .map(MonitoredServiceResponse::getMonitoredServiceDTO)
            .collect(Collectors.toMap(monitoredServiceDTO
                -> getScopedInformation(projectParams.getAccountIdentifier(), monitoredServiceDTO.getOrgIdentifier(),
                    monitoredServiceDTO.getProjectIdentifier(), monitoredServiceDTO.getIdentifier()),
                monitoredServiceDTO -> monitoredServiceDTO));
    Map<String, SLOHealthIndicator> scopedSloIdentifierToHealthIndicatorMap =
        sloHealthIndicatorsFromScopedIdentifiers.stream().collect(Collectors.toMap(sloHealthIndicator
            -> getScopedInformation(sloHealthIndicator.getAccountId(), sloHealthIndicator.getOrgIdentifier(),
                sloHealthIndicator.getProjectIdentifier(), sloHealthIndicator.getServiceLevelObjectiveIdentifier()),
            sloHealthIndicator -> sloHealthIndicator));
    Map<String, String> userJourneyIdentifierToNameMap =
        userJourneyList.stream().collect(Collectors.toMap(UserJourney::getIdentifier, UserJourney::getName));

    List<SLOHealthListView> sloWidgets =
        sloPageResponse.getContent()
            .stream()
            .map(sloResponse
                -> getSLOListView(projectParams, sloResponse, scopedMonitoredServiceIdentifierToDTOMap,
                    scopedSloIdentifierToHealthIndicatorMap, userJourneyIdentifierToNameMap))
            .collect(Collectors.toList());

    return PageResponse.<SLOHealthListView>builder()
        .pageSize(sloPageResponse.getPageSize())
        .pageIndex(sloPageResponse.getPageIndex())
        .totalPages(sloPageResponse.getTotalPages())
        .totalItems(sloPageResponse.getTotalItems())
        .pageItemCount(sloPageResponse.getPageItemCount())
        .content(sloWidgets)
        .build();
  }

  @Override
  public PageResponse<SLOConsumptionBreakdown> getSLOConsumptionBreakdownView(
      ProjectParams projectParams, String compositeSLOIdentifier, Long startTime, Long endTime) {
    PageResponse<SLOHealthListView> sloHealthListViewPageResponse = getSloHealthListView(projectParams,
        SLODashboardApiFilter.builder().compositeSLOIdentifier(compositeSLOIdentifier).childResource(true).build(),
        PageParams.builder().page(0).size(20).build());

    CompositeServiceLevelObjective compositeServiceLevelObjective =
        (CompositeServiceLevelObjective) serviceLevelObjectiveV2Service.getEntity(
            projectParams, compositeSLOIdentifier);
    Map<ServiceLevelObjectiveDetailsRefDTO, Double> sloDetailsToWeightPercentageMap =
        compositeServiceLevelObjective.getServiceLevelObjectivesDetails().stream().collect(
            Collectors.toMap(ServiceLevelObjectivesDetail::getServiceLevelObjectiveDetailsRefDTO,
                ServiceLevelObjectivesDetail::getWeightagePercentage));

    List<SLOConsumptionBreakdown> sloConsumptionBreakdownList =
        sloHealthListViewPageResponse.getContent()
            .stream()
            .map(sloDetail
                -> getSLOConsumptionBreakdown(sloDetail, compositeServiceLevelObjective,
                    sloDetailsToWeightPercentageMap, projectParams, startTime, endTime))
            .collect(Collectors.toList());

    return PageResponse.<SLOConsumptionBreakdown>builder()
        .content(sloConsumptionBreakdownList)
        .pageSize(sloHealthListViewPageResponse.getPageSize())
        .pageIndex(sloHealthListViewPageResponse.getPageIndex())
        .totalPages(sloHealthListViewPageResponse.getTotalPages())
        .totalItems(sloHealthListViewPageResponse.getTotalItems())
        .pageItemCount(sloHealthListViewPageResponse.getPageItemCount())
        .build();
  }

  private SLOConsumptionBreakdown getSLOConsumptionBreakdown(SLOHealthListView sloDetail,
      CompositeServiceLevelObjective compositeServiceLevelObjective,
      Map<ServiceLevelObjectiveDetailsRefDTO, Double> sloDetailsToWeightPercentageMap, ProjectParams projectParams,
      Long startTime, Long endTime) {
    ProjectParams simpleSLOProjectParams =
        ProjectParams.builder()
            .accountIdentifier(projectParams.getAccountIdentifier())
            .orgIdentifier(Optional.ofNullable(sloDetail.getProjectParams()).isPresent()
                    ? sloDetail.getProjectParams().getOrgIdentifier()
                    : "")
            .projectIdentifier(Optional.ofNullable(sloDetail.getProjectParams()).isPresent()
                    ? sloDetail.getProjectParams().getProjectIdentifier()
                    : "")
            .build();
    Double weightPercentage = sloDetailsToWeightPercentageMap.get(
        ServiceLevelObjectiveDetailsRefDTO.builder()
            .accountId(projectParams.getAccountIdentifier())
            .orgIdentifier(Optional.ofNullable(sloDetail.getProjectParams()).isPresent()
                    ? sloDetail.getProjectParams().getOrgIdentifier()
                    : "")
            .projectIdentifier(Optional.ofNullable(sloDetail.getProjectParams()).isPresent()
                    ? sloDetail.getProjectParams().getProjectIdentifier()
                    : "")
            .serviceLevelObjectiveRef(sloDetail.getSloIdentifier())
            .build());

    TimeRangeParams filter = null;
    Instant compositeSloStartedAtTime = Instant.ofEpochMilli(compositeServiceLevelObjective.getStartedAt());
    Instant startTimeForCurrentRange = Instant.ofEpochMilli(startTime);
    Instant endTimeForCurrentRange = Instant.ofEpochMilli(endTime);
    startTimeForCurrentRange = (startTimeForCurrentRange.isAfter(compositeSloStartedAtTime))
        ? startTimeForCurrentRange
        : compositeSloStartedAtTime;
    if (Objects.nonNull(startTimeForCurrentRange) && Objects.nonNull(endTimeForCurrentRange)) {
      filter = TimeRangeParams.builder().startTime(startTimeForCurrentRange).endTime(endTimeForCurrentRange).build();
    }

    AbstractServiceLevelObjective serviceLevelObjective =
        serviceLevelObjectiveV2Service.getEntity(simpleSLOProjectParams, sloDetail.getSloIdentifier());
    SLODashboardWidget.SLOGraphData sloGraphData = graphDataService.getGraphData(serviceLevelObjective,
        startTimeForCurrentRange, endTimeForCurrentRange, sloDetail.getTotalErrorBudget(), filter);
    String projectName = getProjectName(projectParams.getAccountIdentifier(), simpleSLOProjectParams.getOrgIdentifier(),
        simpleSLOProjectParams.getProjectIdentifier());
    String orgName = getOrgName(projectParams.getAccountIdentifier(), simpleSLOProjectParams.getOrgIdentifier());
    return SLOConsumptionBreakdown.builder()
        .sloIdentifier(sloDetail.getSloIdentifier())
        .sloName(sloDetail.getName())
        .monitoredServiceIdentifier(sloDetail.getMonitoredServiceIdentifier())
        .serviceName(sloDetail.getServiceName())
        .environmentIdentifier(sloDetail.getEnvironmentIdentifier())
        .sliType(sloDetail.getSliType())
        .weightagePercentage(weightPercentage)
        .sloTargetPercentage(sloDetail.getSloTargetPercentage())
        .sliStatusPercentage(sloGraphData.getSliStatusPercentage())
        .errorBudgetBurned(sloGraphData.getErrorBudgetBurned())
        .contributedErrorBudgetBurned((int) ((weightPercentage / 100) * sloGraphData.getErrorBudgetBurned()))
        .projectParams(simpleSLOProjectParams)
        .projectName(projectName)
        .orgName(orgName)
        .build();
  }

  @Override
  public SLODashboardDetail getSloDashboardDetail(
      ProjectParams projectParams, String identifier, Long startTime, Long endTime) {
    ServiceLevelObjectiveV2Response sloResponse = serviceLevelObjectiveV2Service.get(projectParams, identifier);
    TimeRangeParams filter = null;
    if (Objects.nonNull(startTime) && Objects.nonNull(endTime)) {
      filter = TimeRangeParams.builder()
                   .startTime(Instant.ofEpochMilli(startTime))
                   .endTime(Instant.ofEpochMilli(endTime))
                   .build();
    }
    SLODashboardWidget sloDashboardWidget = getSloDashboardWidget(projectParams, sloResponse, filter);

    return SLODashboardDetail.builder()
        .description(sloResponse.getServiceLevelObjectiveV2DTO().getDescription())
        .createdAt(sloResponse.getCreatedAt())
        .lastModifiedAt(sloResponse.getLastModifiedAt())
        .timeRangeFilters(serviceLevelObjectiveV2Service.getEntity(projectParams, identifier).getTimeRangeFilters())
        .sloDashboardWidget(sloDashboardWidget)
        .build();
  }

  @Override
  public SLORiskCountResponse getRiskCount(
      ProjectParams projectParams, SLODashboardApiFilter serviceLevelObjectiveFilter) {
    return serviceLevelObjectiveService.getRiskCount(projectParams, serviceLevelObjectiveFilter);
  }

  private SLODashboardWidget getSloDashboardWidget(
      ProjectParams projectParams, ServiceLevelObjectiveV2Response sloResponse, TimeRangeParams filter) {
    ServiceLevelObjectiveV2DTO slo = sloResponse.getServiceLevelObjectiveV2DTO();
    AbstractServiceLevelObjective serviceLevelObjective =
        serviceLevelObjectiveV2Service.getEntity(projectParams, slo.getIdentifier());

    LocalDateTime currentLocalDate = LocalDateTime.ofInstant(clock.instant(), serviceLevelObjective.getZoneOffset());
    TimePeriod timePeriod = serviceLevelObjective.getCurrentTimeRange(currentLocalDate);
    Instant currentTimeMinute = DateTimeUtils.roundDownTo1MinBoundary(clock.instant());
    List<SLOErrorBudgetResetDTO> errorBudgetResetDTOS =
        sloErrorBudgetResetService.getErrorBudgetResets(projectParams, slo.getIdentifier());
    int totalErrorBudgetMinutes =
        serviceLevelObjective.getActiveErrorBudgetMinutes(errorBudgetResetDTOS, currentLocalDate);

    SLODashboardWidget.SLOGraphData sloGraphData = graphDataService.getGraphData(serviceLevelObjective,
        timePeriod.getStartTime(serviceLevelObjective.getZoneOffset()), currentTimeMinute, totalErrorBudgetMinutes,
        filter);
    if (slo.getType().equals(ServiceLevelObjectiveType.SIMPLE)) {
      SimpleServiceLevelObjectiveSpec simpleServiceLevelObjectiveSpec =
          (SimpleServiceLevelObjectiveSpec) sloResponse.getServiceLevelObjectiveV2DTO().getSpec();
      MonitoredServiceDTO monitoredService =
          monitoredServiceService.get(projectParams, simpleServiceLevelObjectiveSpec.getMonitoredServiceRef())
              .getMonitoredServiceDTO();

      MonitoredServiceDetail monitoredServiceDetail =
          MonitoredServiceDetail.builder()
              .monitoredServiceIdentifier(simpleServiceLevelObjectiveSpec.getMonitoredServiceRef())
              .monitoredServiceName(monitoredService.getName())
              .environmentIdentifier(monitoredService.getEnvironmentRef())
              .environmentName(
                  nextGenService
                      .getEnvironment(serviceLevelObjective.getAccountId(), serviceLevelObjective.getOrgIdentifier(),
                          serviceLevelObjective.getProjectIdentifier(), monitoredService.getEnvironmentRef())
                      .getName())
              .serviceName(
                  nextGenService
                      .getService(serviceLevelObjective.getAccountId(), serviceLevelObjective.getOrgIdentifier(),
                          serviceLevelObjective.getProjectIdentifier(), monitoredService.getServiceRef())
                      .getName())
              .serviceIdentifier(monitoredService.getServiceRef())
              .healthSourceIdentifier(simpleServiceLevelObjectiveSpec.getHealthSourceRef())
              .healthSourceName(
                  getHealthSourceName(monitoredService, simpleServiceLevelObjectiveSpec.getHealthSourceRef()))
              .projectParams(ProjectParams.builder()
                                 .accountIdentifier(projectParams.getAccountIdentifier())
                                 .orgIdentifier(serviceLevelObjective.getOrgIdentifier())
                                 .projectIdentifier(serviceLevelObjective.getProjectIdentifier())
                                 .build())
              .build();
      List<MonitoredServiceDetail> monitoredServiceDetails = Collections.singletonList(monitoredServiceDetail);

      return getSloDashboardWidgetBuilder(slo, timePeriod, sloGraphData, serviceLevelObjective, totalErrorBudgetMinutes,
          currentLocalDate, monitoredServiceDetails)
          .monitoredServiceIdentifier(simpleServiceLevelObjectiveSpec.getMonitoredServiceRef())
          .monitoredServiceName(monitoredService.getName())
          .environmentIdentifier(monitoredService.getEnvironmentRef())
          .environmentName(
              nextGenService
                  .getEnvironment(serviceLevelObjective.getAccountId(), serviceLevelObjective.getOrgIdentifier(),
                      serviceLevelObjective.getProjectIdentifier(), monitoredService.getEnvironmentRef())
                  .getName())
          .serviceName(nextGenService
                           .getService(serviceLevelObjective.getAccountId(), serviceLevelObjective.getOrgIdentifier(),
                               serviceLevelObjective.getProjectIdentifier(), monitoredService.getServiceRef())
                           .getName())
          .serviceIdentifier(monitoredService.getServiceRef())
          .healthSourceIdentifier(simpleServiceLevelObjectiveSpec.getHealthSourceRef())
          .healthSourceName(getHealthSourceName(monitoredService, simpleServiceLevelObjectiveSpec.getHealthSourceRef()))
          .type(simpleServiceLevelObjectiveSpec.getServiceLevelIndicators().get(0).getType())
          .build();
    } else {
      CompositeServiceLevelObjective compositeSLO = (CompositeServiceLevelObjective) serviceLevelObjective;

      PageResponse<SLOHealthListView> sloHealthListViewPageResponse = getSloHealthListView(projectParams,
          SLODashboardApiFilter.builder()
              .compositeSLOIdentifier(compositeSLO.getIdentifier())
              .childResource(true)
              .build(),
          PageParams.builder().page(0).size(20).build());

      List<MonitoredServiceDetail> monitoredServiceDetails =
          sloHealthListViewPageResponse.getContent()
              .stream()
              .map(sloDetail
                  -> MonitoredServiceDetail.builder()
                         .monitoredServiceIdentifier(sloDetail.getMonitoredServiceIdentifier())
                         .monitoredServiceName(sloDetail.getMonitoredServiceName())
                         .environmentIdentifier(sloDetail.getEnvironmentIdentifier())
                         .environmentName(sloDetail.getEnvironmentName())
                         .serviceName(sloDetail.getServiceName())
                         .serviceIdentifier(sloDetail.getServiceIdentifier())
                         .healthSourceIdentifier(sloDetail.getHealthSourceIdentifier())
                         .healthSourceName(sloDetail.getHealthSourceName())
                         .orgName(sloDetail.getOrgName())
                         .projectName(sloDetail.getProjectName())
                         .projectParams(
                             ProjectParams.builder()
                                 .accountIdentifier(projectParams.getAccountIdentifier())
                                 .orgIdentifier(Optional.ofNullable(sloDetail.getProjectParams()).isPresent()
                                         ? sloDetail.getProjectParams().getOrgIdentifier()
                                         : "")
                                 .projectIdentifier(Optional.ofNullable(sloDetail.getProjectParams()).isPresent()
                                         ? sloDetail.getProjectParams().getProjectIdentifier()
                                         : "")
                                 .build())
                         .build())
              .collect(Collectors.toList());

      return getSloDashboardWidgetBuilder(slo, timePeriod, sloGraphData, serviceLevelObjective, totalErrorBudgetMinutes,
          currentLocalDate, monitoredServiceDetails)
          .build();
    }
  }

  private SLODashboardWidgetBuilder getSloDashboardWidgetBuilder(ServiceLevelObjectiveV2DTO slo, TimePeriod timePeriod,
      SLODashboardWidget.SLOGraphData sloGraphData, AbstractServiceLevelObjective serviceLevelObjective,
      int totalErrorBudgetMinutes, LocalDateTime currentLocalDate,
      List<MonitoredServiceDetail> monitoredServiceDetails) {
    return SLODashboardWidget.withGraphData(sloGraphData)
        .sloIdentifier(slo.getIdentifier())
        .title(slo.getName())
        .sloTargetType(slo.getSloTarget().getType())
        .currentPeriodLengthDays(timePeriod.getTotalDays())
        .currentPeriodStartTime(timePeriod.getStartTime(serviceLevelObjective.getZoneOffset()).toEpochMilli())
        .currentPeriodEndTime(timePeriod.getEndTime(serviceLevelObjective.getZoneOffset()).toEpochMilli())
        .monitoredServiceDetails(monitoredServiceDetails)
        .sloTargetPercentage(serviceLevelObjective.getSloTargetPercentage())
        .tags(slo.getTags())
        .totalErrorBudget(totalErrorBudgetMinutes)
        .timeRemainingDays(timePeriod.getRemainingDays(currentLocalDate))
        .burnRate(SLODashboardWidget.BurnRate.builder()
                      .currentRatePercentage(sloGraphData.dailyBurnRate(serviceLevelObjective.getZoneOffset()))
                      .build())
        .sloType(slo.getType());
  }

  @Override
  public PageResponse<MSDropdownResponse> getSLOAssociatedMonitoredServices(
      ProjectParams projectParams, PageParams pageParams) {
    List<ServiceLevelObjective> serviceLevelObjectiveList = serviceLevelObjectiveService.getAllSLOs(projectParams);
    Set<String> monitoredServiceIdentifiers = serviceLevelObjectiveList.stream()
                                                  .map(ServiceLevelObjective::getMonitoredServiceIdentifier)
                                                  .collect(Collectors.toSet());
    List<MonitoredServiceResponse> monitoredServiceResponseList =
        monitoredServiceService.get(projectParams, monitoredServiceIdentifiers);

    List<MSDropdownResponse> msDropdownResponseList =
        monitoredServiceResponseList.stream()
            .map(monitoredServiceResponse -> getMSDropdownResponse(monitoredServiceResponse.getMonitoredServiceDTO()))
            .collect(Collectors.toList());

    return PageUtils.offsetAndLimit(msDropdownResponseList, pageParams.getPage(), pageParams.getSize());
  }

  private MSDropdownResponse getMSDropdownResponse(MonitoredServiceDTO monitoredServiceDTO) {
    return MSDropdownResponse.builder()
        .identifier(monitoredServiceDTO.getIdentifier())
        .name(monitoredServiceDTO.getName())
        .serviceRef(monitoredServiceDTO.getServiceRef())
        .environmentRef(monitoredServiceDTO.getEnvironmentRef())
        .build();
  }

  private SLOHealthListView getSLOListView(ProjectParams projectParams, AbstractServiceLevelObjective slo,
      Map<String, MonitoredServiceDTO> scopedMonitoredServiceIdentifierToDTOMap,
      Map<String, SLOHealthIndicator> scopedSloIdentifierToHealthIndicatorMap,
      Map<String, String> userJourneyIdentifierToNameMap) {
    LocalDateTime currentLocalDate = LocalDateTime.ofInstant(clock.instant(), slo.getZoneOffset());
    List<SLOErrorBudgetResetDTO> errorBudgetResetDTOS =
        sloErrorBudgetResetService.getErrorBudgetResets(projectParams, slo.getIdentifier());
    int totalErrorBudgetMinutes = slo.getActiveErrorBudgetMinutes(errorBudgetResetDTOS, currentLocalDate);
    SLOHealthIndicator sloHealthIndicator = scopedSloIdentifierToHealthIndicatorMap.get(getScopedInformation(
        slo.getAccountId(), slo.getOrgIdentifier(), slo.getProjectIdentifier(), slo.getIdentifier()));
    List<UserJourneyDTO> userJourneys = slo.getUserJourneyIdentifiers()
                                            .stream()
                                            .map(userJourneyIdentifier
                                                -> UserJourneyDTO.builder()
                                                       .identifier(userJourneyIdentifier)
                                                       .name(userJourneyIdentifierToNameMap.get(userJourneyIdentifier))
                                                       .build())
                                            .collect(Collectors.toList());

    if (slo.getType().equals(ServiceLevelObjectiveType.SIMPLE)) {
      SimpleServiceLevelObjective simpleServiceLevelObjective = (SimpleServiceLevelObjective) slo;
      Preconditions.checkState(simpleServiceLevelObjective.getServiceLevelIndicators().size() == 1,
          "Only one service level indicator is supported");
      MonitoredServiceDTO monitoredService =
          scopedMonitoredServiceIdentifierToDTOMap.get(getScopedInformation(simpleServiceLevelObjective.getAccountId(),
              simpleServiceLevelObjective.getOrgIdentifier(), simpleServiceLevelObjective.getProjectIdentifier(),
              simpleServiceLevelObjective.getMonitoredServiceIdentifier()));
      String projectName =
          getProjectName(projectParams.getAccountIdentifier(), slo.getOrgIdentifier(), slo.getProjectIdentifier());
      String orgName = getOrgName(projectParams.getAccountIdentifier(), slo.getOrgIdentifier());
      return SLOHealthListView
          .getSLOHealthListViewBuilder(slo, userJourneys, totalErrorBudgetMinutes, sloHealthIndicator)
          .monitoredServiceIdentifier(monitoredService.getIdentifier())
          .monitoredServiceName(monitoredService.getName())
          .environmentIdentifier(monitoredService.getEnvironmentRef())
          .projectName(projectName)
          .orgName(orgName)
          .environmentName(nextGenService
                               .getEnvironment(slo.getAccountId(), slo.getOrgIdentifier(), slo.getProjectIdentifier(),
                                   monitoredService.getEnvironmentRef())
                               .getName())
          .serviceName(nextGenService
                           .getService(slo.getAccountId(), slo.getOrgIdentifier(), slo.getProjectIdentifier(),
                               monitoredService.getServiceRef())
                           .getName())
          .serviceIdentifier(monitoredService.getServiceRef())
          .healthSourceIdentifier(simpleServiceLevelObjective.getHealthSourceIdentifier())
          .healthSourceName(
              getHealthSourceName(monitoredService, simpleServiceLevelObjective.getHealthSourceIdentifier()))
          .sliType(simpleServiceLevelObjective.getServiceLevelIndicatorType())
          .sliIdentifier(simpleServiceLevelObjective.getServiceLevelIndicators().get(0))
          .build();
    }

    return SLOHealthListView.getSLOHealthListViewBuilder(slo, userJourneys, totalErrorBudgetMinutes, sloHealthIndicator)
        .build();
  }

  private String getHealthSourceName(MonitoredServiceDTO monitoredServiceDTO, String healthSourceRef) {
    return monitoredServiceDTO.getSources()
        .getHealthSources()
        .stream()
        .filter(healthSource -> healthSource.getIdentifier().equals(healthSourceRef))
        .findFirst()
        .orElseThrow(()
                         -> new IllegalStateException(
                             "Health source identifier" + healthSourceRef + " not found in monitored service"))
        .getName();
  }

  private String getScopedInformation(
      String accountId, String orgIdentifier, String projectIdentifier, String identifier) {
    return accountId + '.' + orgIdentifier + '.' + projectIdentifier + '.' + identifier;
  }

  private String getOrgName(String accountIdentifier, String orgIdentifier) {
    String orgName = "";
    if (orgIdentifier != null) {
      orgName = nextGenService.getOrganization(accountIdentifier, orgIdentifier).getName();
    }
    return orgName;
  }

  private String getProjectName(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    String projectName = "";
    if (orgIdentifier != null) {
      projectName = nextGenService.getCachedProject(accountIdentifier, orgIdentifier, projectIdentifier).getName();
    }
    return projectName;
  }
}
