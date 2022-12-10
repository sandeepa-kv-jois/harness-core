/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.cvng.servicelevelobjective.services.impl;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.persistence.HQuery.excludeAuthorityCount;

import io.harness.annotations.retry.RetryOnException;
import io.harness.cvng.servicelevelobjective.beans.SLIMissingDataType;
import io.harness.cvng.servicelevelobjective.entities.CompositeSLORecord;
import io.harness.cvng.servicelevelobjective.entities.CompositeSLORecord.CompositeSLORecordKeys;
import io.harness.cvng.servicelevelobjective.entities.CompositeServiceLevelObjective.ServiceLevelObjectivesDetail;
import io.harness.cvng.servicelevelobjective.entities.SLIRecord;
import io.harness.cvng.servicelevelobjective.services.api.CompositeSLORecordService;
import io.harness.persistence.HPersistence;

import com.google.common.collect.ImmutableSortedSet;
import com.google.inject.Inject;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.mongodb.morphia.query.FindOptions;
import org.mongodb.morphia.query.Sort;

public class CompositeSLORecordServiceImpl implements CompositeSLORecordService {
  private static final int RETRY_COUNT = 3;
  @Inject private HPersistence hPersistence;

  @Override
  public void create(
      Map<ServiceLevelObjectivesDetail, List<SLIRecord>> serviceLevelObjectivesDetailCompositeSLORecordMap,
      Map<ServiceLevelObjectivesDetail, SLIMissingDataType> objectivesDetailSLIMissingDataTypeMap, int sloVersion,
      String verificationTaskId, Instant startTime, Instant endTime) {
    if (isEmpty(serviceLevelObjectivesDetailCompositeSLORecordMap)) {
      return;
    }
    double runningGoodCount = 0;
    double runningBadCount = 0;
    CompositeSLORecord lastCompositeSLORecord = getLastCompositeSLORecord(verificationTaskId, startTime);
    CompositeSLORecord latestCompositeSLORecord = getLatestCompositeSLORecord(verificationTaskId);
    if (Objects.nonNull(lastCompositeSLORecord)) {
      runningGoodCount = lastCompositeSLORecord.getRunningGoodCount();
      runningBadCount = lastCompositeSLORecord.getRunningBadCount();
    }
    if (Objects.nonNull(latestCompositeSLORecord) && latestCompositeSLORecord.getTimestamp().isAfter(startTime)) {
      // Update flow: fetch CompositeSLO Records to be updated
      updateCompositeSLORecords(serviceLevelObjectivesDetailCompositeSLORecordMap,
          objectivesDetailSLIMissingDataTypeMap, sloVersion, runningGoodCount, runningBadCount, verificationTaskId,
          startTime, endTime);
    } else {
      List<CompositeSLORecord> compositeSLORecords =
          getCompositeSLORecordsFromSLIsDetails(serviceLevelObjectivesDetailCompositeSLORecordMap,
              objectivesDetailSLIMissingDataTypeMap, sloVersion, runningGoodCount, runningBadCount, verificationTaskId);
      hPersistence.save(compositeSLORecords);
    }
  }

  @Override
  public List<CompositeSLORecord> getSLORecords(String sloId, Instant startTimeStamp, Instant endTimeStamp) {
    return hPersistence.createQuery(CompositeSLORecord.class, excludeAuthorityCount)
        .filter(CompositeSLORecordKeys.sloId, sloId)
        .field(CompositeSLORecordKeys.timestamp)
        .greaterThanOrEq(startTimeStamp)
        .field(CompositeSLORecordKeys.timestamp)
        .lessThan(endTimeStamp)
        .order(Sort.ascending(CompositeSLORecordKeys.timestamp))
        .asList();
  }

  @Override
  public CompositeSLORecord getLatestCompositeSLORecord(String sloId) {
    return hPersistence.createQuery(CompositeSLORecord.class, excludeAuthorityCount)
        .filter(CompositeSLORecordKeys.sloId, sloId)
        .order(Sort.descending(CompositeSLORecordKeys.timestamp))
        .get();
  }

  @Override
  public CompositeSLORecord getLatestCompositeSLORecordWithVersion(String sloId, int sloVersion) {
    return hPersistence.createQuery(CompositeSLORecord.class, excludeAuthorityCount)
        .filter(CompositeSLORecordKeys.sloId, sloId)
        .filter(CompositeSLORecordKeys.sloVersion, sloVersion)
        .order(Sort.descending(CompositeSLORecordKeys.timestamp))
        .get();
  }

  public List<CompositeSLORecord> getCompositeSLORecordsFromSLIsDetails(
      Map<ServiceLevelObjectivesDetail, List<SLIRecord>> serviceLevelObjectivesDetailCompositeSLORecordMap,
      Map<ServiceLevelObjectivesDetail, SLIMissingDataType> objectivesDetailSLIMissingDataTypeMap, int sloVersion,
      double runningGoodCount, double runningBadCount, String verificationTaskId) {
    Map<Instant, Double> timeStampToGoodValue = new HashMap<>();
    Map<Instant, Double> timeStampToBadValue = new HashMap<>();
    Map<Instant, Integer> timeStampToTotalValue = new HashMap<>();
    getTimeStampToValueMaps(serviceLevelObjectivesDetailCompositeSLORecordMap, objectivesDetailSLIMissingDataTypeMap,
        timeStampToGoodValue, timeStampToBadValue, timeStampToTotalValue);
    List<CompositeSLORecord> sloRecordList = new ArrayList<>();
    for (Instant instant : ImmutableSortedSet.copyOf(timeStampToTotalValue.keySet())) {
      if (timeStampToTotalValue.get(instant).equals(serviceLevelObjectivesDetailCompositeSLORecordMap.size())) {
        runningGoodCount += timeStampToGoodValue.getOrDefault(instant, 0.0);
        runningBadCount += timeStampToBadValue.getOrDefault(instant, 0.0);
        CompositeSLORecord sloRecord = CompositeSLORecord.builder()
                                           .runningBadCount(runningBadCount)
                                           .runningGoodCount(runningGoodCount)
                                           .sloId(verificationTaskId)
                                           .sloVersion(sloVersion)
                                           .verificationTaskId(verificationTaskId)
                                           .timestamp(instant)
                                           .build();
        sloRecordList.add(sloRecord);
      }
    }
    return sloRecordList;
  }

  @RetryOnException(retryCount = RETRY_COUNT, retryOn = ConcurrentModificationException.class)
  public void updateCompositeSLORecords(
      Map<ServiceLevelObjectivesDetail, List<SLIRecord>> serviceLevelObjectivesDetailCompositeSLORecordMap,
      Map<ServiceLevelObjectivesDetail, SLIMissingDataType> objectivesDetailSLIMissingDataTypeMap, int sloVersion,
      double runningGoodCount, double runningBadCount, String verificationTaskId, Instant startTime, Instant endTime) {
    List<CompositeSLORecord> toBeUpdatedSLORecords =
        getSLORecords(verificationTaskId, startTime, endTime.plus(1, ChronoUnit.MINUTES));
    Map<Instant, CompositeSLORecord> sloRecordMap =
        toBeUpdatedSLORecords.stream().collect(Collectors.toMap(CompositeSLORecord::getTimestamp, Function.identity()));
    List<CompositeSLORecord> updateOrCreateSLORecords = new ArrayList<>();
    Map<Instant, Double> timeStampToGoodValue = new HashMap<>();
    Map<Instant, Double> timeStampToBadValue = new HashMap<>();
    Map<Instant, Integer> timeStampToTotalValue = new HashMap<>();
    getTimeStampToValueMaps(serviceLevelObjectivesDetailCompositeSLORecordMap, objectivesDetailSLIMissingDataTypeMap,
        timeStampToGoodValue, timeStampToBadValue, timeStampToTotalValue);
    for (Instant instant : ImmutableSortedSet.copyOf(timeStampToTotalValue.keySet())) {
      if (timeStampToTotalValue.get(instant).equals(serviceLevelObjectivesDetailCompositeSLORecordMap.size())) {
        CompositeSLORecord sloRecord = sloRecordMap.get(instant);
        runningGoodCount += timeStampToGoodValue.getOrDefault(instant, 0.0);
        runningBadCount += timeStampToBadValue.getOrDefault(instant, 0.0);
        if (Objects.nonNull(sloRecord)) {
          sloRecord.setRunningGoodCount(runningGoodCount);
          sloRecord.setRunningBadCount(runningBadCount);
          sloRecord.setSloVersion(sloVersion);
        } else {
          sloRecord = CompositeSLORecord.builder()
                          .runningBadCount(runningBadCount)
                          .runningGoodCount(runningGoodCount)
                          .sloId(verificationTaskId)
                          .sloVersion(sloVersion)
                          .verificationTaskId(verificationTaskId)
                          .timestamp(instant)
                          .build();
        }
        updateOrCreateSLORecords.add(sloRecord);
      }
    }
    hPersistence.save(updateOrCreateSLORecords);
  }

  private void getTimeStampToValueMaps(
      Map<ServiceLevelObjectivesDetail, List<SLIRecord>> serviceLevelObjectivesDetailCompositeSLORecordMap,
      Map<ServiceLevelObjectivesDetail, SLIMissingDataType> objectivesDetailSLIMissingDataTypeMap,
      Map<Instant, Double> timeStampToGoodValue, Map<Instant, Double> timeStampToBadValue,
      Map<Instant, Integer> timeStampToTotalValue) {
    for (ServiceLevelObjectivesDetail objectivesDetail : serviceLevelObjectivesDetailCompositeSLORecordMap.keySet()) {
      for (SLIRecord sliRecord : serviceLevelObjectivesDetailCompositeSLORecordMap.get(objectivesDetail)) {
        if (SLIRecord.SLIState.GOOD.equals(sliRecord.getSliState())
            || (SLIRecord.SLIState.NO_DATA.equals(sliRecord.getSliState())
                && objectivesDetailSLIMissingDataTypeMap.get(objectivesDetail).equals(SLIMissingDataType.GOOD))) {
          double goodCount = timeStampToGoodValue.getOrDefault(sliRecord.getTimestamp(), 0.0);
          goodCount += objectivesDetail.getWeightagePercentage() / 100;
          timeStampToGoodValue.put(sliRecord.getTimestamp(), goodCount);
        } else if (SLIRecord.SLIState.BAD.equals(sliRecord.getSliState())
            || (SLIRecord.SLIState.NO_DATA.equals(sliRecord.getSliState())
                && objectivesDetailSLIMissingDataTypeMap.get(objectivesDetail).equals(SLIMissingDataType.BAD))) {
          double badCount = timeStampToBadValue.getOrDefault(sliRecord.getTimestamp(), 0.0);
          badCount += objectivesDetail.getWeightagePercentage() / 100;
          timeStampToBadValue.put(sliRecord.getTimestamp(), badCount);
        }
        timeStampToTotalValue.put(
            sliRecord.getTimestamp(), timeStampToTotalValue.getOrDefault(sliRecord.getTimestamp(), 0) + 1);
      }
    }
  }
  @Override
  public List<CompositeSLORecord> getLatestCountSLORecords(String sloId, int count) {
    return hPersistence.createQuery(CompositeSLORecord.class, excludeAuthorityCount)
        .filter(CompositeSLORecordKeys.sloId, sloId)
        .order(Sort.descending(CompositeSLORecordKeys.timestamp))
        .asList(new FindOptions().limit(count));
  }

  @Override
  public CompositeSLORecord getLastCompositeSLORecord(String sloId, Instant startTimeStamp) {
    return hPersistence.createQuery(CompositeSLORecord.class, excludeAuthorityCount)
        .filter(CompositeSLORecordKeys.sloId, sloId)
        .field(CompositeSLORecordKeys.timestamp)
        .lessThan(startTimeStamp)
        .order(Sort.descending(CompositeSLORecordKeys.timestamp))
        .get();
  }

  @Override
  public CompositeSLORecord getFirstCompositeSLORecord(String sloId, Instant timestampInclusive) {
    return hPersistence.createQuery(CompositeSLORecord.class, excludeAuthorityCount)
        .filter(CompositeSLORecordKeys.sloId, sloId)
        .field(CompositeSLORecordKeys.timestamp)
        .greaterThanOrEq(timestampInclusive)
        .order(Sort.ascending(CompositeSLORecordKeys.timestamp))
        .get();
  }
}
