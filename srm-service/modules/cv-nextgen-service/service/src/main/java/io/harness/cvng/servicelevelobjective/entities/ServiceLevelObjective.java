/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.entities;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cvng.notification.beans.NotificationRuleRef;
import io.harness.cvng.servicelevelobjective.beans.DayOfWeek;
import io.harness.cvng.servicelevelobjective.beans.SLOCalenderType;
import io.harness.cvng.servicelevelobjective.beans.SLODashboardDetail.TimeRangeFilter;
import io.harness.cvng.servicelevelobjective.beans.SLOErrorBudgetResetDTO;
import io.harness.cvng.servicelevelobjective.beans.SLOTargetType;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelIndicatorType;
import io.harness.data.structure.CollectionUtils;
import io.harness.iterator.PersistentRegularIterable;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UuidAware;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.common.collect.ImmutableList;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.Singular;
import lombok.Value;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;

@Data
@Builder
@FieldNameConstants(innerTypeName = "ServiceLevelObjectiveKeys")
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@StoreIn(DbAliases.CVNG)
@Entity(value = "serviceLevelObjectives", noClassnameStored = true)
@HarnessEntity(exportable = true)
@OwnedBy(HarnessTeam.CV)
public class ServiceLevelObjective
    implements PersistentEntity, UuidAware, AccountAccess, UpdatedAtAware, CreatedAtAware, PersistentRegularIterable {
  String accountId;
  String orgIdentifier;
  String projectIdentifier;
  @Id private String uuid;
  String identifier;
  String name;
  String desc;
  @NotNull @Singular @Size(max = 128) List<NGTag> tags;
  String userJourneyIdentifier;
  String healthSourceIdentifier;
  String monitoredServiceIdentifier;
  List<String> serviceLevelIndicators;
  List<NotificationRuleRef> notificationRuleRefs;
  SLOTarget sloTarget;
  ServiceLevelIndicatorType type;
  private boolean enabled;
  private long lastUpdatedAt;
  private long createdAt;
  private Double sloTargetPercentage;
  @FdIndex private long nextNotificationIteration;

  public ZoneOffset getZoneOffset() {
    return ZoneOffset.UTC; // hardcoding it to UTC for now. We need to ask it from user.
  }

  public List<NotificationRuleRef> getNotificationRuleRefs() {
    if (notificationRuleRefs == null) {
      return Collections.emptyList();
    }
    return notificationRuleRefs;
  }

  public int getActiveErrorBudgetMinutes(
      List<SLOErrorBudgetResetDTO> sloErrorBudgetResets, LocalDateTime currentDateTime) {
    int totalErrorBudgetMinutes = getTotalErrorBudgetMinutes(currentDateTime);
    long totalErrorBudgetIncrementMinutesFromReset =
        CollectionUtils.emptyIfNull(sloErrorBudgetResets)
            .stream()
            .mapToLong(sloErrorBudgetResetDTO -> sloErrorBudgetResetDTO.getErrorBudgetIncrementMinutes())
            .sum();
    return Math.toIntExact(Math.min(getCurrentTimeRange(currentDateTime).totalMinutes(),
        totalErrorBudgetMinutes + totalErrorBudgetIncrementMinutesFromReset));
  }

  public int getTotalErrorBudgetMinutes(LocalDateTime currentDateTime) {
    int currentWindowMinutes = getCurrentTimeRange(currentDateTime).totalMinutes();
    Double errorBudgetPercentage = getSloTargetPercentage();
    return (int) Math.round(((100 - errorBudgetPercentage) * currentWindowMinutes) / 100);
  }

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("unique_query_idx")
                 .unique(true)
                 .field(ServiceLevelObjectiveKeys.accountId)
                 .field(ServiceLevelObjectiveKeys.orgIdentifier)
                 .field(ServiceLevelObjectiveKeys.projectIdentifier)
                 .field(ServiceLevelObjectiveKeys.identifier)
                 .build())
        .build();
  }

  public TimePeriod getCurrentTimeRange(LocalDateTime currentDateTime) {
    return sloTarget.getCurrentTimeRange(currentDateTime);
  }

  public List<TimeRangeFilter> getTimeRangeFilters() {
    return sloTarget.getTimeRangeFilters();
  }

  @Override
  public Long obtainNextIteration(String fieldName) {
    if (ServiceLevelObjectiveKeys.nextNotificationIteration.equals(fieldName)) {
      return this.nextNotificationIteration;
    }
    throw new IllegalArgumentException("Invalid fieldName " + fieldName);
  }

  @Override
  public void updateNextIteration(String fieldName, long nextIteration) {
    if (ServiceLevelObjectiveKeys.nextNotificationIteration.equals(fieldName)) {
      this.nextNotificationIteration = nextIteration;
      return;
    }
    throw new IllegalArgumentException("Invalid fieldName " + fieldName);
  }

  @Data
  @SuperBuilder
  @EqualsAndHashCode
  public abstract static class SLOTarget {
    public abstract TimePeriod getCurrentTimeRange(LocalDateTime currentDateTime);
    public abstract SLOTargetType getType();
    public abstract List<TimeRangeFilter> getTimeRangeFilters();
  }
  @Data
  @SuperBuilder
  @EqualsAndHashCode(callSuper = true)
  public abstract static class CalenderSLOTarget extends SLOTarget {
    private final SLOTargetType type = SLOTargetType.CALENDER;
    public abstract SLOCalenderType getCalenderType();
  }

  @Value
  @SuperBuilder
  @EqualsAndHashCode(callSuper = true)
  public static class WeeklyCalenderTarget extends CalenderSLOTarget {
    private DayOfWeek dayOfWeek;
    private final SLOCalenderType calenderType = SLOCalenderType.WEEKLY;

    @Override
    public TimePeriod getCurrentTimeRange(LocalDateTime currentDateTime) {
      LocalDate nextDayOfWeek = dayOfWeek.getNextDayOfWeek(currentDateTime.toLocalDate());
      return TimePeriod.builder().startDate(nextDayOfWeek.minusDays(6)).endDate(nextDayOfWeek.plusDays(1)).build();
    }

    @Override
    public List<TimeRangeFilter> getTimeRangeFilters() {
      List<TimeRangeFilter> timeRangeFilterList = new ArrayList<>();
      timeRangeFilterList.add(TimeRangeFilter.ONE_HOUR_FILTER);
      timeRangeFilterList.add(TimeRangeFilter.ONE_DAY_FILTER);
      return timeRangeFilterList;
    }
  }

  @SuperBuilder
  @Data
  @EqualsAndHashCode(callSuper = true)
  public static class MonthlyCalenderTarget extends CalenderSLOTarget {
    int windowEndDayOfMonth;
    private final SLOCalenderType calenderType = SLOCalenderType.MONTHLY;

    @Override
    public TimePeriod getCurrentTimeRange(LocalDateTime currentDateTime) {
      LocalDate windowStart =
          getWindowEnd(currentDateTime.toLocalDate().minusMonths(1), windowEndDayOfMonth).plusDays(1);
      LocalDate windowEnd = getWindowEnd(currentDateTime.toLocalDate(), windowEndDayOfMonth).plusDays(1);
      return TimePeriod.builder().startDate(windowStart).endDate(windowEnd).build();
    }

    @Override
    public List<TimeRangeFilter> getTimeRangeFilters() {
      List<TimeRangeFilter> timeRangeFilterList = new ArrayList<>();
      timeRangeFilterList.add(TimeRangeFilter.ONE_HOUR_FILTER);
      timeRangeFilterList.add(TimeRangeFilter.ONE_DAY_FILTER);
      timeRangeFilterList.add(TimeRangeFilter.ONE_WEEK_FILTER);
      return timeRangeFilterList;
    }

    private LocalDate getWindowEnd(LocalDate currentDateTime, int windowEndDayOfMonth) {
      LocalDate windowEnd;
      if (windowEndDayOfMonth > 28) {
        windowEnd = currentDateTime.with(TemporalAdjusters.lastDayOfMonth());
      } else if (currentDateTime.getDayOfMonth() <= windowEndDayOfMonth) {
        windowEnd = getWindowEnd(currentDateTime);
      } else {
        windowEnd = getWindowEnd(currentDateTime.plusMonths(1));
      }
      return windowEnd;
    }

    private LocalDate getWindowEnd(LocalDate date) {
      return date.plusDays(windowEndDayOfMonth - date.getDayOfMonth());
    }
  }

  @SuperBuilder
  @Data
  @EqualsAndHashCode(callSuper = true)
  public static class QuarterlyCalenderTarget extends CalenderSLOTarget {
    private final SLOCalenderType calenderType = SLOCalenderType.QUARTERLY;

    @Override
    public TimePeriod getCurrentTimeRange(LocalDateTime currentDateTime) {
      LocalDate firstDayOfQuarter = currentDateTime.toLocalDate()
                                        .with(currentDateTime.toLocalDate().getMonth().firstMonthOfQuarter())
                                        .with(TemporalAdjusters.firstDayOfMonth());

      LocalDate lastDayOfQuarter = firstDayOfQuarter.plusMonths(2).with(TemporalAdjusters.lastDayOfMonth());
      return TimePeriod.builder().startDate(firstDayOfQuarter).endDate(lastDayOfQuarter.plusDays(1)).build();
    }

    @Override
    public List<TimeRangeFilter> getTimeRangeFilters() {
      List<TimeRangeFilter> timeRangeFilterList = new ArrayList<>();
      timeRangeFilterList.add(TimeRangeFilter.ONE_HOUR_FILTER);
      timeRangeFilterList.add(TimeRangeFilter.ONE_DAY_FILTER);
      timeRangeFilterList.add(TimeRangeFilter.ONE_WEEK_FILTER);
      timeRangeFilterList.add(TimeRangeFilter.ONE_MONTH_FILTER);
      return timeRangeFilterList;
    }
  }

  @SuperBuilder
  @Data
  @EqualsAndHashCode(callSuper = true)
  public static class RollingSLOTarget extends SLOTarget {
    int periodLengthDays;
    private final SLOTargetType type = SLOTargetType.ROLLING;

    @Override
    public TimePeriod getCurrentTimeRange(LocalDateTime currentDateTime) {
      currentDateTime = currentDateTime.truncatedTo(ChronoUnit.MINUTES);
      return TimePeriod.createWithLocalTime(
          currentDateTime.minusMinutes(TimeUnit.DAYS.toMinutes(periodLengthDays)), currentDateTime);
    }

    @Override
    public List<TimeRangeFilter> getTimeRangeFilters() {
      List<TimeRangeFilter> timeRangeFilterList = new ArrayList<>();
      timeRangeFilterList.add(TimeRangeFilter.ONE_HOUR_FILTER);
      timeRangeFilterList.add(TimeRangeFilter.ONE_DAY_FILTER);
      if (this.periodLengthDays > 7) {
        timeRangeFilterList.add(TimeRangeFilter.ONE_WEEK_FILTER);
      }
      return timeRangeFilterList;
    }
  }
}
