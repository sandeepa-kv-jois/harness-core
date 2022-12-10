/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective;

import static io.harness.cvng.CVNGTestConstants.TIME_FOR_TESTS;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ABHIJITH;
import static io.harness.rule.OwnerRule.DEEPAK_CHHIKARA;
import static io.harness.rule.OwnerRule.VARSHA_LALWANI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.beans.DataCollectionInfo;
import io.harness.cvng.beans.DataCollectionRequest;
import io.harness.cvng.beans.DataCollectionRequestType;
import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.beans.SyncDataCollectionRequest;
import io.harness.cvng.core.beans.OnboardingRequestDTO;
import io.harness.cvng.core.beans.OnboardingResponseDTO;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceDTO;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.beans.sli.SLIOnboardingGraphs;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.entities.MetricCVConfig;
import io.harness.cvng.core.services.api.AppDynamicsServiceimplTest;
import io.harness.cvng.core.services.api.DataCollectionSLIInfoMapper;
import io.harness.cvng.core.services.api.MetricPackService;
import io.harness.cvng.core.services.api.OnboardingService;
import io.harness.cvng.core.services.api.monitoredService.HealthSourceService;
import io.harness.cvng.core.services.api.monitoredService.MonitoredServiceService;
import io.harness.cvng.servicelevelobjective.beans.SLIMetricType;
import io.harness.cvng.servicelevelobjective.beans.SLIMissingDataType;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelIndicatorDTO;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelIndicatorSpec;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelIndicatorType;
import io.harness.cvng.servicelevelobjective.beans.slimetricspec.RatioSLIMetricEventType;
import io.harness.cvng.servicelevelobjective.beans.slimetricspec.RatioSLIMetricSpec;
import io.harness.cvng.servicelevelobjective.beans.slimetricspec.ThresholdSLIMetricSpec;
import io.harness.cvng.servicelevelobjective.beans.slimetricspec.ThresholdType;
import io.harness.cvng.servicelevelobjective.entities.SLIRecord;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelIndicator;
import io.harness.cvng.servicelevelobjective.entities.TimePeriod;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelIndicatorService;
import io.harness.cvng.servicelevelobjective.transformer.servicelevelindicator.ServiceLevelIndicatorTransformer;
import io.harness.cvng.statemachine.services.api.OrchestrationService;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;
import io.harness.serializer.JsonUtils;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import com.google.inject.Inject;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.SneakyThrows;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ServiceLevelIndicatorServiceImplTest extends CvNextGenTestBase {
  BuilderFactory builderFactory;
  @Inject ServiceLevelIndicatorService serviceLevelIndicatorService;
  @Inject Map<SLIMetricType, ServiceLevelIndicatorTransformer> serviceLevelIndicatorTransformerMap;
  @Inject private Map<DataSourceType, DataCollectionSLIInfoMapper> dataSourceTypeDataCollectionInfoMapperMap;
  @Inject private MetricPackService metricPackService;
  @Inject HPersistence hPersistence;
  @Inject Clock clock;
  @Inject private MonitoredServiceService monitoredServiceService;
  private String monitoredServiceIdentifier;

  private Instant startTime;

  private Instant endTime;

  @Before
  @SneakyThrows
  public void setup() {
    builderFactory = BuilderFactory.getDefault();
    monitoredServiceIdentifier = "monitoredServiceIdentifier";
    createMonitoredService();
    clock = Clock.fixed(TIME_FOR_TESTS, ZoneOffset.UTC);
    startTime = TIME_FOR_TESTS.minus(5, ChronoUnit.MINUTES);
    endTime = TIME_FOR_TESTS;
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void testGetOnboardingGraph() throws IOException, IllegalAccessException {
    String tracingId = "tracingId";
    ServiceLevelIndicatorDTO serviceLevelIndicatorDTO =
        builderFactory.getThresholdServiceLevelIndicatorDTOBuilder().build();
    CVConfig cvConfig = builderFactory.appDynamicsCVConfigBuilder()
                            .identifier(HealthSourceService.getNameSpacedIdentifier(
                                monitoredServiceIdentifier, serviceLevelIndicatorDTO.getHealthSourceRef()))
                            .build();
    hPersistence.save(cvConfig);
    metricPackService.populateDataCollectionDsl(cvConfig.getType(), ((MetricCVConfig) cvConfig).getMetricPack());

    String textLoad = Resources.toString(
        AppDynamicsServiceimplTest.class.getResource("/timeseries/appd_metric_data_validation.json"), Charsets.UTF_8);

    ServiceLevelIndicator serviceLevelIndicator =
        serviceLevelIndicatorTransformerMap.get(serviceLevelIndicatorDTO.getSpec().getType())
            .getEntity(builderFactory.getProjectParams(), serviceLevelIndicatorDTO, monitoredServiceIdentifier,
                serviceLevelIndicatorDTO.getHealthSourceRef(), true);
    DataCollectionInfo dataCollectionInfo = dataSourceTypeDataCollectionInfoMapperMap.get(cvConfig.getType())
                                                .toDataCollectionInfo(Arrays.asList(cvConfig), serviceLevelIndicator);

    Instant endTime = clock.instant().truncatedTo(ChronoUnit.MINUTES);
    Instant startTime = endTime.minus(Duration.ofDays(1));

    DataCollectionRequest request = SyncDataCollectionRequest.builder()
                                        .type(DataCollectionRequestType.SYNC_DATA_COLLECTION)
                                        .dataCollectionInfo(dataCollectionInfo)
                                        .endTime(endTime)
                                        .startTime(startTime)
                                        .build();

    OnboardingRequestDTO onboardingRequestDTO =
        OnboardingRequestDTO.builder()
            .dataCollectionRequest(request)
            .connectorIdentifier(cvConfig.getConnectorIdentifier())
            .accountId(builderFactory.getProjectParams().getAccountIdentifier())
            .orgIdentifier(builderFactory.getProjectParams().getOrgIdentifier())
            .projectIdentifier(builderFactory.getProjectParams().getProjectIdentifier())
            .tracingId(tracingId)
            .build();

    OnboardingService mockOnboardingService = mock(OnboardingService.class);
    FieldUtils.writeField(serviceLevelIndicatorService, "onboardingService", mockOnboardingService, true);
    when(mockOnboardingService.getOnboardingResponse(
             eq(builderFactory.getContext().getAccountId()), eq(onboardingRequestDTO)))
        .thenReturn(JsonUtils.asObject(textLoad, OnboardingResponseDTO.class));

    SLIOnboardingGraphs sliOnboardingGraphs =
        serviceLevelIndicatorService.getOnboardingGraphs(builderFactory.getContext().getProjectParams(),
            monitoredServiceIdentifier, serviceLevelIndicatorDTO, tracingId);

    assertThat(sliOnboardingGraphs.getSliGraph().getStartTime()).isEqualTo(1595760600000L);
    assertThat(sliOnboardingGraphs.getSliGraph().getEndTime()).isEqualTo(1595847000000L);
    assertThat(sliOnboardingGraphs.getSliGraph().getDataPoints().get(1).getValue()).isEqualTo(50.0);
    assertThat(sliOnboardingGraphs.getSliGraph().getDataPoints().get(1).getTimeStamp()).isEqualTo(1595760660000L);
    assertThat(sliOnboardingGraphs.getMetricGraphs()).hasSize(1);
    assertThat(sliOnboardingGraphs.getMetricGraphs().get("Calls per Minute").getMetricName())
        .isEqualTo("Calls per Minute");
    assertThat(sliOnboardingGraphs.getMetricGraphs().get("Calls per Minute").getMetricIdentifier())
        .isEqualTo("Calls per Minute");
    assertThat(sliOnboardingGraphs.getMetricGraphs().get("Calls per Minute").getDataPoints().get(0).getValue())
        .isEqualTo(343.0);
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void testGetOnboardingGraph_NoDataPresent() throws IOException, IllegalAccessException {
    String tracingId = "tracingId";
    ServiceLevelIndicatorDTO serviceLevelIndicatorDTO =
        builderFactory.getThresholdServiceLevelIndicatorDTOBuilder().build();
    CVConfig cvConfig = builderFactory.appDynamicsCVConfigBuilder()
                            .identifier(HealthSourceService.getNameSpacedIdentifier(
                                monitoredServiceIdentifier, serviceLevelIndicatorDTO.getHealthSourceRef()))
                            .build();
    hPersistence.save(cvConfig);

    String textLoad = Resources.toString(
        AppDynamicsServiceimplTest.class.getResource("/timeseries/empty_timeseries_dsl_response.json"), Charsets.UTF_8);

    OnboardingService mockOnboardingService = mock(OnboardingService.class);
    FieldUtils.writeField(serviceLevelIndicatorService, "onboardingService", mockOnboardingService, true);
    when(mockOnboardingService.getOnboardingResponse(eq(builderFactory.getContext().getAccountId()), any()))
        .thenReturn(JsonUtils.asObject(textLoad, OnboardingResponseDTO.class));

    SLIOnboardingGraphs sliOnboardingGraphs =
        serviceLevelIndicatorService.getOnboardingGraphs(builderFactory.getContext().getProjectParams(),
            monitoredServiceIdentifier, serviceLevelIndicatorDTO, tracingId);

    assertThat(sliOnboardingGraphs.getSliGraph().getStartTime()).isEqualTo(1595760600000L);
    assertThat(sliOnboardingGraphs.getSliGraph().getEndTime()).isEqualTo(1595847000000L);
    assertThat(sliOnboardingGraphs.getSliGraph().getDataPoints().get(1).getValue()).isEqualTo(100.0);
    assertThat(sliOnboardingGraphs.getSliGraph().getDataPoints().get(1).getTimeStamp()).isEqualTo(1595760660000L);
    assertThat(sliOnboardingGraphs.getMetricGraphs()).hasSize(0);
  }

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  public void testGetOnboardingGraph_ratio() throws IOException, IllegalAccessException {
    String tracingId = "tracingId";
    ServiceLevelIndicatorDTO serviceLevelIndicatorDTO =
        builderFactory.getRatioServiceLevelIndicatorDTOBuilder().build();
    CVConfig cvConfig = builderFactory.appDynamicsCVConfigBuilder()
                            .identifier(HealthSourceService.getNameSpacedIdentifier(
                                monitoredServiceIdentifier, serviceLevelIndicatorDTO.getHealthSourceRef()))
                            .build();
    hPersistence.save(cvConfig);

    String textLoad = Resources.toString(
        AppDynamicsServiceimplTest.class.getResource("/timeseries/appd_metric_data_validation.json"), Charsets.UTF_8);

    OnboardingService mockOnboardingService = mock(OnboardingService.class);
    FieldUtils.writeField(serviceLevelIndicatorService, "onboardingService", mockOnboardingService, true);
    when(mockOnboardingService.getOnboardingResponse(eq(builderFactory.getContext().getAccountId()), any()))
        .thenReturn(JsonUtils.asObject(textLoad, OnboardingResponseDTO.class));

    SLIOnboardingGraphs sliOnboardingGraphs =
        serviceLevelIndicatorService.getOnboardingGraphs(builderFactory.getContext().getProjectParams(),
            monitoredServiceIdentifier, serviceLevelIndicatorDTO, tracingId);

    assertThat(sliOnboardingGraphs.getSliGraph().getStartTime()).isEqualTo(1595760600000L);
    assertThat(sliOnboardingGraphs.getSliGraph().getEndTime()).isEqualTo(1595847000000L);
    assertThat(sliOnboardingGraphs.getSliGraph().getDataPoints().get(1).getValue()).isEqualTo(50.0);
    assertThat(sliOnboardingGraphs.getSliGraph().getDataPoints().get(1).getTimeStamp()).isEqualTo(1595760660000L);
    assertThat(sliOnboardingGraphs.getMetricGraphs()).hasSize(2);
    assertThat(sliOnboardingGraphs.getMetricGraphs().get("Calls per Minute").getMetricName())
        .isEqualTo("Calls per Minute");
    assertThat(sliOnboardingGraphs.getMetricGraphs().get("Calls per Minute").getMetricIdentifier())
        .isEqualTo("Calls per Minute");
    assertThat(sliOnboardingGraphs.getMetricGraphs().get("Calls per Minute").getDataPoints().get(0).getValue())
        .isEqualTo(343.0);
    assertThat(sliOnboardingGraphs.getMetricGraphs().get("Errors per Minute").getDataPoints().get(0).getValue())
        .isEqualTo(233.0);
  }

  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void testCreateThreshold_success() {
    ServiceLevelIndicatorDTO serviceLevelIndicatorDTO = createServiceLevelIndicator(SLIMetricType.THRESHOLD);
    ProjectParams projectParams = builderFactory.getProjectParams();
    List<String> serviceLevelIndicatorIdentifiers =
        serviceLevelIndicatorService.create(projectParams, Collections.singletonList(serviceLevelIndicatorDTO),
            generateUuid(), monitoredServiceIdentifier, generateUuid());
    List<ServiceLevelIndicatorDTO> serviceLevelIndicatorDTOList =
        serviceLevelIndicatorService.get(projectParams, serviceLevelIndicatorIdentifiers);
    assertThat(Collections.singletonList(serviceLevelIndicatorDTO)).isEqualTo(serviceLevelIndicatorDTOList);
  }

  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void testCreateRatio_success() {
    ServiceLevelIndicatorDTO serviceLevelIndicatorDTO = createServiceLevelIndicator(SLIMetricType.RATIO);
    ProjectParams projectParams = builderFactory.getProjectParams();
    List<String> serviceLevelIndicatorIdentifiers =
        serviceLevelIndicatorService.create(projectParams, Collections.singletonList(serviceLevelIndicatorDTO),
            generateUuid(), monitoredServiceIdentifier, generateUuid());
    List<ServiceLevelIndicatorDTO> serviceLevelIndicatorDTOList =
        serviceLevelIndicatorService.get(projectParams, serviceLevelIndicatorIdentifiers);
    assertThat(Collections.singletonList(serviceLevelIndicatorDTO)).isEqualTo(serviceLevelIndicatorDTOList);
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testUpdate_queueAnalysis_success() throws IllegalAccessException {
    OrchestrationService orchestrationService = mock(OrchestrationService.class);
    FieldUtils.writeField(serviceLevelIndicatorService, "orchestrationService", orchestrationService, true);
    ServiceLevelIndicatorDTO serviceLevelIndicatorDTO = createServiceLevelIndicator(SLIMetricType.THRESHOLD);
    ProjectParams projectParams = builderFactory.getProjectParams();
    String serviceLevelObjectiveIdentifier = generateUuid();
    String healthSourceIdentifier = generateUuid();
    List<String> serviceLevelIndicatorIdentifiers =
        serviceLevelIndicatorService.create(projectParams, Collections.singletonList(serviceLevelIndicatorDTO),
            serviceLevelObjectiveIdentifier, monitoredServiceIdentifier, healthSourceIdentifier);
    List<SLIRecord.SLIState> sliStateList = Arrays.asList(SLIRecord.SLIState.GOOD, SLIRecord.SLIState.GOOD,
        SLIRecord.SLIState.BAD, SLIRecord.SLIState.NO_DATA, SLIRecord.SLIState.BAD);
    String sliId =
        serviceLevelIndicatorService
            .getServiceLevelIndicator(builderFactory.getProjectParams(), serviceLevelIndicatorIdentifiers.get(0))
            .getUuid();
    createSLIRecords(sliId, sliStateList);
    serviceLevelIndicatorService.update(projectParams, Collections.singletonList(serviceLevelIndicatorDTO),
        serviceLevelObjectiveIdentifier, serviceLevelIndicatorIdentifiers, monitoredServiceIdentifier,
        healthSourceIdentifier,
        TimePeriod.builder()
            .startDate(LocalDate.ofInstant(clock.instant().minus(21, ChronoUnit.DAYS), ZoneOffset.UTC))
            .endDate(LocalDate.ofInstant(clock.instant().plus(9, ChronoUnit.DAYS), ZoneOffset.UTC))
            .build(),
        TimePeriod.builder()
            .startDate(LocalDate.ofInstant(clock.instant().minus(7, ChronoUnit.DAYS), ZoneOffset.UTC))
            .endDate(LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC))
            .build());
    verify(orchestrationService, times(1)).queueAnalysis(sliId, startTime, endTime);
  }

  private void createMonitoredService() {
    MonitoredServiceDTO monitoredServiceDTO =
        builderFactory.monitoredServiceDTOBuilder().identifier(monitoredServiceIdentifier).build();
    monitoredServiceDTO.setSources(MonitoredServiceDTO.Sources.builder().build());
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
  }

  private ServiceLevelIndicatorDTO createServiceLevelIndicator(SLIMetricType sliMetricType) {
    if (SLIMetricType.RATIO.equals(sliMetricType)) {
      return createRatioServiceLevelIndicator();
    } else {
      return createThresholdServiceLevelIndicator();
    }
  }

  private ServiceLevelIndicatorDTO createRatioServiceLevelIndicator() {
    return ServiceLevelIndicatorDTO.builder()
        .identifier("sliIndicator")
        .name("sliName")
        .type(ServiceLevelIndicatorType.LATENCY)
        .spec(ServiceLevelIndicatorSpec.builder()
                  .type(SLIMetricType.RATIO)
                  .spec(RatioSLIMetricSpec.builder()
                            .eventType(RatioSLIMetricEventType.GOOD)
                            .metric1("metric1")
                            .metric2("metric2")
                            .build())
                  .build())
        .build();
  }

  private ServiceLevelIndicatorDTO createThresholdServiceLevelIndicator() {
    return ServiceLevelIndicatorDTO.builder()
        .identifier("sliIndicator")
        .name("sliName")
        .type(ServiceLevelIndicatorType.LATENCY)
        .spec(ServiceLevelIndicatorSpec.builder()
                  .type(SLIMetricType.THRESHOLD)
                  .spec(ThresholdSLIMetricSpec.builder()
                            .metric1("metric1")
                            .thresholdValue(50.0)
                            .thresholdType(ThresholdType.GREATER_THAN)
                            .build())
                  .build())
        .sliMissingDataType(SLIMissingDataType.GOOD)
        .build();
  }

  private List<SLIRecord> createSLIRecords(String sliId, List<SLIRecord.SLIState> states) {
    int index = 0;
    List<SLIRecord> sliRecords = new ArrayList<>();
    for (Instant instant = startTime; instant.isBefore(endTime); instant = instant.plus(1, ChronoUnit.MINUTES)) {
      SLIRecord sliRecord = SLIRecord.builder()
                                .verificationTaskId(sliId)
                                .sliId(sliId)
                                .version(0)
                                .sliState(states.get(index))
                                .runningBadCount(0)
                                .runningGoodCount(1)
                                .sliVersion(0)
                                .timestamp(instant)
                                .build();
      sliRecords.add(sliRecord);
      index++;
    }
    hPersistence.save(sliRecords);
    return sliRecords;
  }
}
