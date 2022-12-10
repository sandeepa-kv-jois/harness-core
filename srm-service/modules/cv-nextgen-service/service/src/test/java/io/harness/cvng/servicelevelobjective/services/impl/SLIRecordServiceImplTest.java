/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.services.impl;

import static io.harness.cvng.servicelevelobjective.entities.SLIRecord.SLIState.BAD;
import static io.harness.cvng.servicelevelobjective.entities.SLIRecord.SLIState.GOOD;
import static io.harness.cvng.servicelevelobjective.entities.SLIRecord.SLIState.NO_DATA;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.persistence.HQuery.excludeAuthority;
import static io.harness.rule.OwnerRule.DEEPAK_CHHIKARA;
import static io.harness.rule.OwnerRule.KAMAL;
import static io.harness.rule.OwnerRule.KAPIL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;
import static org.mockito.Mockito.when;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceDTO;
import io.harness.cvng.core.beans.params.MonitoredServiceParams;
import io.harness.cvng.core.entities.MonitoredService;
import io.harness.cvng.core.services.api.monitoredService.MonitoredServiceService;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelIndicatorDTO;
import io.harness.cvng.servicelevelobjective.entities.SLIRecord;
import io.harness.cvng.servicelevelobjective.entities.SLIRecord.SLIRecordKeys;
import io.harness.cvng.servicelevelobjective.entities.SLIRecord.SLIRecordParam;
import io.harness.cvng.servicelevelobjective.entities.SLIRecord.SLIState;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelIndicator;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelIndicatorService;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.mongodb.morphia.query.Sort;

public class SLIRecordServiceImplTest extends CvNextGenTestBase {
  @Spy @Inject private SLIRecordServiceImpl sliRecordService;
  @Inject private HPersistence hPersistence;

  @Inject private ServiceLevelIndicatorService serviceLevelIndicatorService;
  @Inject private MonitoredServiceService monitoredServiceService;

  private String verificationTaskId;
  private String sliId;

  private BuilderFactory builderFactory;

  private ServiceLevelIndicator serviceLevelIndicator;

  private MonitoredService monitoredService;

  @Before
  public void setup() {
    builderFactory = BuilderFactory.getDefault();
    MockitoAnnotations.initMocks(this);
    SLIRecordServiceImpl.MAX_NUMBER_OF_POINTS = 5;
    verificationTaskId = generateUuid();
    /*sliId = generateUuid();*/
    monitoredService = createMonitoredService();
    sliId = createServiceLevelIndicator();
    serviceLevelIndicator = getServiceLevelIndicator();
  }
  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testCreate_multipleSaves() {
    Instant startTime = Instant.parse("2020-07-27T10:50:00Z").minus(Duration.ofMinutes(10));
    List<SLIState> sliStates = Arrays.asList(BAD, GOOD, GOOD, NO_DATA, GOOD, GOOD, BAD, BAD, BAD, BAD);
    createData(startTime, sliStates);
    SLIRecord lastRecord = getLastRecord(serviceLevelIndicator.getUuid());
    assertThat(lastRecord.getRunningBadCount()).isEqualTo(5);
    assertThat(lastRecord.getRunningGoodCount()).isEqualTo(4);
    createData(startTime.plus(Duration.ofMinutes(10)), sliStates);
    lastRecord = getLastRecord(serviceLevelIndicator.getUuid());
    assertThat(lastRecord.getRunningBadCount()).isEqualTo(10);
    assertThat(lastRecord.getRunningGoodCount()).isEqualTo(8);
  }

  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void testUpdate_completeOverlap() {
    Instant startTime = Instant.parse("2020-07-27T10:50:00Z");
    List<SLIState> sliStates = Arrays.asList(BAD, GOOD, GOOD, NO_DATA, GOOD, GOOD, BAD, BAD, BAD, BAD);
    List<SLIRecordParam> sliRecordParams = getSLIRecordParam(startTime, sliStates);
    sliRecordService.create(sliRecordParams, sliId, verificationTaskId, 0);
    SLIRecord lastRecord = getLastRecord(sliId);
    assertThat(lastRecord.getRunningBadCount()).isEqualTo(5);
    assertThat(lastRecord.getRunningGoodCount()).isEqualTo(4);
    assertThat(lastRecord.getSliVersion()).isZero();
    List<SLIState> updatedSliStates = Arrays.asList(GOOD, BAD, BAD, NO_DATA, GOOD, BAD, BAD, BAD, BAD, BAD);
    List<SLIRecordParam> updatedSliRecordParams = getSLIRecordParam(startTime, updatedSliStates);
    sliRecordService.create(updatedSliRecordParams, sliId, verificationTaskId, 1);
    SLIRecord updatedLastRecord = getLastRecord(sliId);
    assertThat(updatedLastRecord.getRunningBadCount()).isEqualTo(7);
    assertThat(updatedLastRecord.getRunningGoodCount()).isEqualTo(2);
    assertThat(updatedLastRecord.getSliVersion()).isEqualTo(1);
  }

  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void testUpdate_partialOverlap() {
    Instant startTime = Instant.parse("2020-07-27T10:50:00Z");
    List<SLIState> sliStates = Arrays.asList(BAD, GOOD, GOOD, NO_DATA, GOOD, GOOD, BAD, BAD, BAD, BAD);
    List<SLIRecordParam> sliRecordParams = getSLIRecordParam(startTime, sliStates);
    sliRecordService.create(sliRecordParams, sliId, verificationTaskId, 0);
    SLIRecord lastRecord = getLastRecord(sliId);
    assertThat(lastRecord.getRunningBadCount()).isEqualTo(5);
    assertThat(lastRecord.getRunningGoodCount()).isEqualTo(4);
    assertThat(lastRecord.getSliVersion()).isZero();
    Instant updatedStartTime = Instant.parse("2020-07-27T10:55:00Z");
    List<SLIState> updatedSliStates = Arrays.asList(BAD, BAD, BAD, BAD, BAD);
    List<SLIRecordParam> updatedSliRecordParams = getSLIRecordParam(updatedStartTime, updatedSliStates);
    sliRecordService.create(updatedSliRecordParams, sliId, verificationTaskId, 1);
    SLIRecord updatedLastRecord = getLastRecord(sliId);
    assertThat(updatedLastRecord.getRunningBadCount()).isEqualTo(6);
    assertThat(updatedLastRecord.getRunningGoodCount()).isEqualTo(3);
    assertThat(updatedLastRecord.getSliVersion()).isEqualTo(1);
  }

  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void testUpdate_MissingRecords() {
    Instant startTime = Instant.parse("2020-07-27T10:00:00Z");
    List<SLIState> sliStates = Arrays.asList(BAD, GOOD, GOOD, NO_DATA, GOOD, GOOD, BAD, BAD, BAD, BAD);
    List<SLIRecordParam> sliRecordParams = getSLIRecordParam(startTime, sliStates);
    sliRecordService.create(sliRecordParams, sliId, verificationTaskId, 0);
    SLIRecord lastRecord = getLastRecord(sliId);
    assertThat(lastRecord.getRunningBadCount()).isEqualTo(5);
    assertThat(lastRecord.getRunningGoodCount()).isEqualTo(4);
    assertThat(lastRecord.getSliVersion()).isZero();
    Instant updatedStartTime = Instant.parse("2020-07-27T10:05:00Z");
    List<SLIState> updatedSliStates =
        Arrays.asList(BAD, BAD, BAD, BAD, BAD, NO_DATA, NO_DATA, NO_DATA, NO_DATA, NO_DATA);
    List<SLIRecordParam> updatedSliRecordParams = getSLIRecordParam(updatedStartTime, updatedSliStates);
    sliRecordService.create(updatedSliRecordParams, sliId, verificationTaskId, 1);
    SLIRecord updatedLastRecord = getLastRecord(sliId);
    assertThat(updatedLastRecord.getRunningBadCount()).isEqualTo(6);
    assertThat(updatedLastRecord.getRunningGoodCount()).isEqualTo(3);
    assertThat(updatedLastRecord.getSliVersion()).isEqualTo(1);
    assertThat(updatedLastRecord.getTimestamp()).isEqualTo(Instant.parse("2020-07-27T10:14:00Z"));
  }

  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void testUpdate_NotSyncedRecords() {
    Instant startTime = Instant.parse("2020-07-27T10:00:00Z");
    List<SLIState> sliStates = Arrays.asList(BAD, GOOD, GOOD, NO_DATA, GOOD, GOOD, BAD, BAD, BAD, BAD);
    List<SLIRecordParam> sliRecordParams = getSLIRecordParam(startTime, sliStates);
    sliRecordService.create(sliRecordParams, sliId, verificationTaskId, 0);
    SLIRecord lastRecord = getLastRecord(sliId);
    assertThat(lastRecord.getRunningBadCount()).isEqualTo(5);
    assertThat(lastRecord.getRunningGoodCount()).isEqualTo(4);
    assertThat(lastRecord.getSliVersion()).isZero();
    Instant updatedStartTime = Instant.parse("2020-07-27T10:05:00Z");
    List<SLIState> updatedSliStates = Arrays.asList(BAD, BAD, BAD, BAD, BAD, GOOD, BAD, GOOD, BAD, GOOD);
    List<SLIRecordParam> updatedSliRecordParams = getSLIRecordParam(updatedStartTime, updatedSliStates);
    sliRecordService.create(updatedSliRecordParams, sliId, verificationTaskId, 1);
    SLIRecord updatedLastRecord = getLastRecord(sliId);
    assertThat(updatedLastRecord.getRunningBadCount()).isEqualTo(8);
    assertThat(updatedLastRecord.getRunningGoodCount()).isEqualTo(6);
    assertThat(updatedLastRecord.getSliVersion()).isEqualTo(1);
    assertThat(updatedLastRecord.getTimestamp()).isEqualTo(Instant.parse("2020-07-27T10:14:00Z"));
  }

  @Test
  @Owner(developers = DEEPAK_CHHIKARA)
  @Category(UnitTests.class)
  public void testUpdate_retryConcurrency() {
    Instant startTime = Instant.parse("2020-07-27T10:00:00Z");
    List<SLIState> sliStates = Arrays.asList(BAD, GOOD, GOOD, NO_DATA, GOOD, GOOD, BAD, BAD, BAD, BAD);
    List<SLIRecordParam> sliRecordParams = getSLIRecordParam(startTime, sliStates);
    Instant endTime = sliRecordParams.get(sliRecordParams.size() - 1).getTimeStamp();
    sliRecordService.create(sliRecordParams, sliId, verificationTaskId, 0);
    SLIRecord lastRecord = getLastRecord(sliId);
    assertThat(lastRecord.getRunningBadCount()).isEqualTo(5);
    assertThat(lastRecord.getRunningGoodCount()).isEqualTo(4);
    assertThat(lastRecord.getSliVersion()).isZero();
    List<SLIRecord> firstTimeResponse = sliRecordService.getSLIRecords(lastRecord.getSliId(), startTime, endTime);
    for (SLIRecord sliRecord : firstTimeResponse) {
      sliRecord.setVersion(-1);
    }
    List<SLIRecord> secondTimeResponse = sliRecordService.getSLIRecords(lastRecord.getSliId(), startTime, endTime);
    Instant updatedStartTime = Instant.parse("2020-07-27T10:05:00Z");
    List<SLIState> updatedSliStates = Arrays.asList(BAD, BAD, BAD, BAD, BAD, GOOD, BAD, GOOD, BAD, GOOD);
    List<SLIRecordParam> updatedSliRecordParams = getSLIRecordParam(updatedStartTime, updatedSliStates);
    when(sliRecordService.getSLIRecords(sliId, updatedStartTime,
             updatedSliRecordParams.get(updatedSliRecordParams.size() - 1).getTimeStamp().plus(1, ChronoUnit.MINUTES)))
        .thenReturn(firstTimeResponse, secondTimeResponse);
    sliRecordService.create(updatedSliRecordParams, sliId, verificationTaskId, 1);
    SLIRecord updatedLastRecord = getLastRecord(sliId);
    assertThat(updatedLastRecord.getRunningBadCount()).isEqualTo(8);
    assertThat(updatedLastRecord.getRunningGoodCount()).isEqualTo(6);
    assertThat(updatedLastRecord.getSliVersion()).isEqualTo(1);
    assertThat(updatedLastRecord.getTimestamp()).isEqualTo(Instant.parse("2020-07-27T10:14:00Z"));
  }
  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testGetErrorBudgetBurnRate() {
    Instant startTime = Instant.parse("2020-07-27T10:50:00Z").minus(Duration.ofMinutes(10));
    List<SLIState> sliStates = Arrays.asList(BAD, GOOD, GOOD, NO_DATA, GOOD, GOOD, BAD, BAD, BAD, BAD);
    createData(startTime, sliStates);
    double errorBudgetBurnRate = sliRecordService.getErrorBudgetBurnRate(
        serviceLevelIndicator.getUuid(), Duration.ofMinutes(10).toMillis(), 120);
    assertThat(errorBudgetBurnRate).isCloseTo(3.333, offset(0.001));
  }

  private void createData(Instant startTime, List<SLIState> sliStates) {
    List<SLIRecordParam> sliRecordParams = getSLIRecordParam(startTime, sliStates);
    sliRecordService.create(sliRecordParams, serviceLevelIndicator.getUuid(), verificationTaskId, 0);
  }

  private List<SLIRecordParam> getSLIRecordParam(Instant startTime, List<SLIState> sliStates) {
    List<SLIRecordParam> sliRecordParams = new ArrayList<>();
    for (int i = 0; i < sliStates.size(); i++) {
      SLIState sliState = sliStates.get(i);
      sliRecordParams.add(
          SLIRecordParam.builder().sliState(sliState).timeStamp(startTime.plus(Duration.ofMinutes(i))).build());
    }
    return sliRecordParams;
  }
  private SLIRecord getLastRecord(String sliId) {
    return hPersistence.createQuery(SLIRecord.class, excludeAuthority)
        .filter(SLIRecordKeys.sliId, sliId)
        .order(Sort.descending(SLIRecordKeys.timestamp))
        .get();
  }

  private MonitoredService createMonitoredService() {
    MonitoredServiceDTO monitoredServiceDTO =
        builderFactory.monitoredServiceDTOBuilder().identifier("monitoredServiceIdentifier").build();
    monitoredServiceDTO.setSources(MonitoredServiceDTO.Sources.builder().build());
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    return monitoredServiceService.getMonitoredService(
        MonitoredServiceParams.builderWithProjectParams(builderFactory.getProjectParams())
            .monitoredServiceIdentifier("monitoredServiceIdentifier")
            .build());
  }

  private String createServiceLevelIndicator() {
    ServiceLevelIndicatorDTO serviceLevelIndicatorDTO =
        builderFactory.getThresholdServiceLevelIndicatorDTOBuilder().build();
    List<String> sliId = serviceLevelIndicatorService.create(builderFactory.getProjectParams(),
        Collections.singletonList(serviceLevelIndicatorDTO), "slo", "monitoredServiceIdentifier", null);
    return sliId.get(0);
  }

  private ServiceLevelIndicator getServiceLevelIndicator() {
    return serviceLevelIndicatorService.getServiceLevelIndicator(builderFactory.getProjectParams(), sliId);
  }
}
