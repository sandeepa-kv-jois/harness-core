/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.services.impl;

import static io.harness.cvng.CVNGTestConstants.TIME_FOR_TESTS;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.VARSHA_LALWANI;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceDTO;
import io.harness.cvng.core.services.api.monitoredService.MonitoredServiceService;
import io.harness.cvng.servicelevelobjective.beans.SLIMissingDataType;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveDetailsDTO;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveV2DTO;
import io.harness.cvng.servicelevelobjective.beans.slospec.CompositeServiceLevelObjectiveSpec;
import io.harness.cvng.servicelevelobjective.beans.slospec.SimpleServiceLevelObjectiveSpec;
import io.harness.cvng.servicelevelobjective.entities.CompositeSLORecord;
import io.harness.cvng.servicelevelobjective.entities.CompositeServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.entities.SLIRecord;
import io.harness.cvng.servicelevelobjective.entities.SimpleServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelIndicatorService;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelObjectiveV2Service;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

public class CompositeSLORecordServiceImplTest extends CvNextGenTestBase {
  @Spy @Inject private CompositeSLORecordServiceImpl sloRecordService;
  @Inject private HPersistence hPersistence;
  @Inject private ServiceLevelObjectiveV2Service serviceLevelObjectiveV2Service;
  @Inject private MonitoredServiceService monitoredServiceService;
  @Inject ServiceLevelIndicatorService serviceLevelIndicatorService;
  BuilderFactory builderFactory;
  private Instant startTime;
  private Instant endTime;
  private String verificationTaskId;
  ServiceLevelObjectiveV2DTO serviceLevelObjectiveV2DTO;
  CompositeServiceLevelObjective compositeServiceLevelObjective;
  ServiceLevelObjectiveV2DTO simpleServiceLevelObjectiveDTO1;
  ServiceLevelObjectiveV2DTO simpleServiceLevelObjectiveDTO2;
  SimpleServiceLevelObjective simpleServiceLevelObjective1;
  SimpleServiceLevelObjective simpleServiceLevelObjective2;

  @Before
  public void setup() {
    builderFactory = BuilderFactory.getDefault();
    MockitoAnnotations.initMocks(this);
    MonitoredServiceDTO monitoredServiceDTO1 =
        builderFactory.monitoredServiceDTOBuilder().sources(MonitoredServiceDTO.Sources.builder().build()).build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO1);
    simpleServiceLevelObjectiveDTO1 = builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder().build();
    SimpleServiceLevelObjectiveSpec simpleServiceLevelObjectiveSpec1 =
        (SimpleServiceLevelObjectiveSpec) simpleServiceLevelObjectiveDTO1.getSpec();
    simpleServiceLevelObjectiveSpec1.setMonitoredServiceRef(monitoredServiceDTO1.getIdentifier());
    simpleServiceLevelObjectiveSpec1.setHealthSourceRef(generateUuid());
    simpleServiceLevelObjectiveDTO1.setSpec(simpleServiceLevelObjectiveSpec1);
    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(), simpleServiceLevelObjectiveDTO1);
    simpleServiceLevelObjective1 = (SimpleServiceLevelObjective) serviceLevelObjectiveV2Service.getEntity(
        builderFactory.getProjectParams(), simpleServiceLevelObjectiveDTO1.getIdentifier());

    MonitoredServiceDTO monitoredServiceDTO2 = builderFactory.monitoredServiceDTOBuilder()
                                                   .sources(MonitoredServiceDTO.Sources.builder().build())
                                                   .serviceRef("service1")
                                                   .environmentRef("env1")
                                                   .identifier("service1_env1")
                                                   .build();
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO2);
    simpleServiceLevelObjectiveDTO2 =
        builderFactory.getSimpleServiceLevelObjectiveV2DTOBuilder().identifier("sloIdentifier2").build();
    SimpleServiceLevelObjectiveSpec simpleServiceLevelObjectiveSpec2 =
        (SimpleServiceLevelObjectiveSpec) simpleServiceLevelObjectiveDTO2.getSpec();
    simpleServiceLevelObjectiveSpec2.setMonitoredServiceRef(monitoredServiceDTO2.getIdentifier());
    simpleServiceLevelObjectiveSpec2.setHealthSourceRef(generateUuid());
    simpleServiceLevelObjectiveDTO2.setSpec(simpleServiceLevelObjectiveSpec2);
    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(), simpleServiceLevelObjectiveDTO2);
    simpleServiceLevelObjective2 = (SimpleServiceLevelObjective) serviceLevelObjectiveV2Service.getEntity(
        builderFactory.getProjectParams(), simpleServiceLevelObjectiveDTO2.getIdentifier());

    serviceLevelObjectiveV2DTO =
        builderFactory.getCompositeServiceLevelObjectiveV2DTOBuilder()
            .spec(CompositeServiceLevelObjectiveSpec.builder()
                      .serviceLevelObjectivesDetails(
                          Arrays.asList(ServiceLevelObjectiveDetailsDTO.builder()
                                            .serviceLevelObjectiveRef(simpleServiceLevelObjective1.getIdentifier())
                                            .weightagePercentage(75.0)
                                            .accountId(simpleServiceLevelObjective1.getAccountId())
                                            .orgIdentifier(simpleServiceLevelObjective1.getOrgIdentifier())
                                            .projectIdentifier(simpleServiceLevelObjective1.getProjectIdentifier())
                                            .build(),
                              ServiceLevelObjectiveDetailsDTO.builder()
                                  .serviceLevelObjectiveRef(simpleServiceLevelObjective2.getIdentifier())
                                  .weightagePercentage(25.0)
                                  .accountId(simpleServiceLevelObjective2.getAccountId())
                                  .orgIdentifier(simpleServiceLevelObjective2.getOrgIdentifier())
                                  .projectIdentifier(simpleServiceLevelObjective2.getProjectIdentifier())
                                  .build()))
                      .build())
            .build();
    serviceLevelObjectiveV2Service.create(builderFactory.getProjectParams(), serviceLevelObjectiveV2DTO);
    compositeServiceLevelObjective = (CompositeServiceLevelObjective) serviceLevelObjectiveV2Service.getEntity(
        builderFactory.getProjectParams(), serviceLevelObjectiveV2DTO.getIdentifier());

    verificationTaskId = compositeServiceLevelObjective.getUuid();
    startTime = TIME_FOR_TESTS.minus(10, ChronoUnit.MINUTES);
    endTime = TIME_FOR_TESTS.minus(5, ChronoUnit.MINUTES);
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testCreate_multipleSaves() {
    List<SLIRecord.SLIState> sliStateList1 = Arrays.asList(SLIRecord.SLIState.GOOD, SLIRecord.SLIState.GOOD,
        SLIRecord.SLIState.BAD, SLIRecord.SLIState.NO_DATA, SLIRecord.SLIState.BAD);
    List<SLIRecord.SLIState> sliStateList2 = Arrays.asList(SLIRecord.SLIState.BAD, SLIRecord.SLIState.GOOD,
        SLIRecord.SLIState.NO_DATA, SLIRecord.SLIState.BAD, SLIRecord.SLIState.GOOD);
    String sliId1 = serviceLevelIndicatorService
                        .getServiceLevelIndicator(builderFactory.getProjectParams(),
                            simpleServiceLevelObjective1.getServiceLevelIndicators().get(0))
                        .getUuid();
    String sliId2 = serviceLevelIndicatorService
                        .getServiceLevelIndicator(builderFactory.getProjectParams(),
                            simpleServiceLevelObjective2.getServiceLevelIndicators().get(0))
                        .getUuid();
    List<SLIRecord> sliRecordList1 = createSLIRecords(sliId1, sliStateList1);
    List<SLIRecord> sliRecordList2 = createSLIRecords(sliId2, sliStateList2);

    Map<CompositeServiceLevelObjective.ServiceLevelObjectivesDetail, List<SLIRecord>>
        serviceLevelObjectivesDetailCompositeSLORecordMap = new HashMap<>();
    serviceLevelObjectivesDetailCompositeSLORecordMap.put(
        compositeServiceLevelObjective.getServiceLevelObjectivesDetails().get(0), sliRecordList1);
    serviceLevelObjectivesDetailCompositeSLORecordMap.put(
        compositeServiceLevelObjective.getServiceLevelObjectivesDetails().get(1), sliRecordList2);
    Map<CompositeServiceLevelObjective.ServiceLevelObjectivesDetail, SLIMissingDataType>
        objectivesDetailSLIMissingDataTypeMap = new HashMap<>();
    objectivesDetailSLIMissingDataTypeMap.put(
        compositeServiceLevelObjective.getServiceLevelObjectivesDetails().get(0), SLIMissingDataType.GOOD);
    objectivesDetailSLIMissingDataTypeMap.put(
        compositeServiceLevelObjective.getServiceLevelObjectivesDetails().get(1), SLIMissingDataType.BAD);
    sloRecordService.create(serviceLevelObjectivesDetailCompositeSLORecordMap, objectivesDetailSLIMissingDataTypeMap, 0,
        verificationTaskId, startTime, endTime);
    List<CompositeSLORecord> sloRecords = sloRecordService.getSLORecords(verificationTaskId, startTime, endTime);
    assertThat(sloRecords.size()).isEqualTo(5);
    assertThat(sloRecords.get(4).getRunningBadCount()).isEqualTo(2.25);
    assertThat(sloRecords.get(4).getRunningGoodCount()).isEqualTo(2.75);
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testUpdate_completeOverlap() {
    List<Double> runningGoodCount = Arrays.asList(0.75, 1.75, 1.75, 2.5, 2.75);
    List<Double> runningBadCount = Arrays.asList(0.25, 0.25, 1.25, 1.5, 2.25);
    createSLORecords(startTime, endTime, runningGoodCount, runningBadCount);
    List<CompositeSLORecord> sloRecords = sloRecordService.getSLORecords(verificationTaskId, startTime, endTime);
    assertThat(sloRecords.size()).isEqualTo(5);
    assertThat(sloRecords.get(4).getRunningBadCount()).isEqualTo(2.25);
    assertThat(sloRecords.get(4).getRunningGoodCount()).isEqualTo(2.75);

    List<SLIRecord.SLIState> sliStateList1 = Arrays.asList(SLIRecord.SLIState.BAD, SLIRecord.SLIState.BAD,
        SLIRecord.SLIState.GOOD, SLIRecord.SLIState.NO_DATA, SLIRecord.SLIState.GOOD);
    List<SLIRecord.SLIState> sliStateList2 = Arrays.asList(SLIRecord.SLIState.GOOD, SLIRecord.SLIState.GOOD,
        SLIRecord.SLIState.NO_DATA, SLIRecord.SLIState.BAD, SLIRecord.SLIState.BAD);
    String sliId1 = serviceLevelIndicatorService
                        .getServiceLevelIndicator(builderFactory.getProjectParams(),
                            simpleServiceLevelObjective1.getServiceLevelIndicators().get(0))
                        .getUuid();
    String sliId2 = serviceLevelIndicatorService
                        .getServiceLevelIndicator(builderFactory.getProjectParams(),
                            simpleServiceLevelObjective2.getServiceLevelIndicators().get(0))
                        .getUuid();
    List<SLIRecord> sliRecordList3 = createSLIRecords(sliId1, sliStateList1);
    List<SLIRecord> sliRecordList4 = createSLIRecords(sliId2, sliStateList2);
    Map<CompositeServiceLevelObjective.ServiceLevelObjectivesDetail, List<SLIRecord>>
        serviceLevelObjectivesDetailCompositeSLORecordMap1 = new HashMap<>();
    serviceLevelObjectivesDetailCompositeSLORecordMap1.put(
        compositeServiceLevelObjective.getServiceLevelObjectivesDetails().get(0), sliRecordList3);
    serviceLevelObjectivesDetailCompositeSLORecordMap1.put(
        compositeServiceLevelObjective.getServiceLevelObjectivesDetails().get(1), sliRecordList4);
    Map<CompositeServiceLevelObjective.ServiceLevelObjectivesDetail, SLIMissingDataType>
        objectivesDetailSLIMissingDataTypeMap1 = new HashMap<>();
    objectivesDetailSLIMissingDataTypeMap1.put(
        compositeServiceLevelObjective.getServiceLevelObjectivesDetails().get(0), SLIMissingDataType.GOOD);
    objectivesDetailSLIMissingDataTypeMap1.put(
        compositeServiceLevelObjective.getServiceLevelObjectivesDetails().get(1), SLIMissingDataType.GOOD);
    sloRecordService.create(serviceLevelObjectivesDetailCompositeSLORecordMap1, objectivesDetailSLIMissingDataTypeMap1,
        1, verificationTaskId, startTime, endTime);
    List<CompositeSLORecord> sloRecords1 = sloRecordService.getSLORecords(verificationTaskId, startTime, endTime);
    assertThat(sloRecords1.size()).isEqualTo(5);
    assertThat(sloRecords1.get(4).getRunningBadCount()).isEqualTo(2.0);
    assertThat(sloRecords1.get(4).getRunningGoodCount()).isEqualTo(3.0);
    assertThat(sloRecords1.get(4).getSloVersion()).isEqualTo(1);
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testUpdate_partialOverlap() {
    List<Double> runningGoodCount = Arrays.asList(0.75, 1.75, 1.75);
    List<Double> runningBadCount = Arrays.asList(0.25, 0.25, 1.25);
    createSLORecords(startTime, endTime.minusSeconds(120), runningGoodCount, runningBadCount);
    List<CompositeSLORecord> sloRecords = sloRecordService.getSLORecords(verificationTaskId, startTime, endTime);
    assertThat(sloRecords.size()).isEqualTo(3);
    assertThat(sloRecords.get(2).getRunningBadCount()).isEqualTo(1.25);
    assertThat(sloRecords.get(2).getRunningGoodCount()).isEqualTo(1.75);

    List<SLIRecord.SLIState> sliStateList1 = Arrays.asList(SLIRecord.SLIState.BAD, SLIRecord.SLIState.BAD,
        SLIRecord.SLIState.GOOD, SLIRecord.SLIState.NO_DATA, SLIRecord.SLIState.GOOD);
    List<SLIRecord.SLIState> sliStateList2 = Arrays.asList(SLIRecord.SLIState.GOOD, SLIRecord.SLIState.GOOD,
        SLIRecord.SLIState.NO_DATA, SLIRecord.SLIState.BAD, SLIRecord.SLIState.BAD);
    String sliId1 = serviceLevelIndicatorService
                        .getServiceLevelIndicator(builderFactory.getProjectParams(),
                            simpleServiceLevelObjective1.getServiceLevelIndicators().get(0))
                        .getUuid();
    String sliId2 = serviceLevelIndicatorService
                        .getServiceLevelIndicator(builderFactory.getProjectParams(),
                            simpleServiceLevelObjective2.getServiceLevelIndicators().get(0))
                        .getUuid();
    List<SLIRecord> sliRecordList3 = createSLIRecords(sliId1, sliStateList1);
    List<SLIRecord> sliRecordList4 = createSLIRecords(sliId2, sliStateList2);
    Map<CompositeServiceLevelObjective.ServiceLevelObjectivesDetail, List<SLIRecord>>
        serviceLevelObjectivesDetailCompositeSLORecordMap1 = new HashMap<>();
    serviceLevelObjectivesDetailCompositeSLORecordMap1.put(
        compositeServiceLevelObjective.getServiceLevelObjectivesDetails().get(0), sliRecordList3);
    serviceLevelObjectivesDetailCompositeSLORecordMap1.put(
        compositeServiceLevelObjective.getServiceLevelObjectivesDetails().get(1), sliRecordList4);
    Map<CompositeServiceLevelObjective.ServiceLevelObjectivesDetail, SLIMissingDataType>
        objectivesDetailSLIMissingDataTypeMap1 = new HashMap<>();
    objectivesDetailSLIMissingDataTypeMap1.put(
        compositeServiceLevelObjective.getServiceLevelObjectivesDetails().get(0), SLIMissingDataType.GOOD);
    objectivesDetailSLIMissingDataTypeMap1.put(
        compositeServiceLevelObjective.getServiceLevelObjectivesDetails().get(1), SLIMissingDataType.GOOD);
    sloRecordService.create(serviceLevelObjectivesDetailCompositeSLORecordMap1, objectivesDetailSLIMissingDataTypeMap1,
        1, verificationTaskId, startTime, endTime);
    List<CompositeSLORecord> sloRecords1 = sloRecordService.getSLORecords(verificationTaskId, startTime, endTime);
    assertThat(sloRecords1.size()).isEqualTo(5);
    assertThat(sloRecords1.get(4).getRunningBadCount()).isEqualTo(2.0);
    assertThat(sloRecords1.get(4).getRunningGoodCount()).isEqualTo(3.0);
    assertThat(sloRecords1.get(4).getSloVersion()).isEqualTo(1);
  }

  private List<SLIRecord> createSLIRecords(String sliId, List<SLIRecord.SLIState> states) {
    int index = 0;
    List<SLIRecord> sliRecords = new ArrayList<>();
    for (Instant instant = startTime; instant.isBefore(endTime); instant = instant.plus(1, ChronoUnit.MINUTES)) {
      SLIRecord sliRecord = SLIRecord.builder()
                                .verificationTaskId(verificationTaskId)
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
    return sliRecords;
  }

  private List<CompositeSLORecord> createSLORecords(
      Instant start, Instant end, List<Double> runningGoodCount, List<Double> runningBadCount) {
    int index = 0;
    List<CompositeSLORecord> sloRecords = new ArrayList<>();
    for (Instant instant = start; instant.isBefore(end); instant = instant.plus(1, ChronoUnit.MINUTES)) {
      CompositeSLORecord sloRecord = CompositeSLORecord.builder()
                                         .verificationTaskId(verificationTaskId)
                                         .sloId(compositeServiceLevelObjective.getUuid())
                                         .version(0)
                                         .runningBadCount(runningBadCount.get(index))
                                         .runningGoodCount(runningGoodCount.get(index))
                                         .sloVersion(0)
                                         .timestamp(instant)
                                         .build();
      sloRecords.add(sloRecord);
      index++;
    }
    hPersistence.save(sloRecords);
    return sloRecords;
  }
}
