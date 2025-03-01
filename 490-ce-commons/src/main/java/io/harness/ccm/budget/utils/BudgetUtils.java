/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.budget.utils;

import static io.harness.ccm.budget.BudgetPeriod.DAILY;
import static io.harness.ccm.budget.BudgetScopeType.PERSPECTIVE;
import static io.harness.ccm.budget.BudgetType.SPECIFIED_AMOUNT;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.budget.AlertThreshold;
import io.harness.ccm.budget.AlertThresholdBase;
import io.harness.ccm.budget.BudgetBreakdown;
import io.harness.ccm.budget.BudgetMonthlyBreakdown;
import io.harness.ccm.budget.BudgetPeriod;
import io.harness.ccm.budget.BudgetScope;
import io.harness.ccm.budget.ValueDataPoint;
import io.harness.ccm.commons.entities.billing.Budget;
import io.harness.exception.InvalidRequestException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.List;
import java.util.TimeZone;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.CE)
public class BudgetUtils {
  private static final double BUDGET_AMOUNT_UPPER_LIMIT = 100000000;
  private static final String NO_BUDGET_AMOUNT_EXCEPTION = "Error in creating budget. No budget amount specified.";
  private static final String NO_MONTHLY_BUDGET_AMOUNT_EXCEPTION =
      "Error in creating/updating budget. Twelve budget cost entries required for monthly budget amount.";
  private static final String BUDGET_AMOUNT_NOT_WITHIN_BOUNDS_EXCEPTION =
      "Error in creating budget. The budget amount should be positive and less than 100 million dollars.";
  private static final String BUDGET_NAME_EXISTS_EXCEPTION =
      "Error in creating budget. Budget with given name already exists";
  private static final String BUDGET_NAME_NOT_PROVIDED_EXCEPTION = "Please provide a name for clone budget.";
  public static final String INVALID_ENTITY_ID_EXCEPTION =
      "Error in create/update budget operation. Some of the appliesTo ids are invalid.";
  public static final String INVALID_START_TIME_EXCEPTION =
      "Error in create budget operation. Start time of budget is invalid.";
  public static final String INVALID_PERSPECTIVE_ID_EXCEPTION = "Invalid perspective id";
  public static final String INVALID_BUDGET_ID_EXCEPTION = "Invalid budget id";
  public static final String MISSING_BUDGET_DATA_EXCEPTION = "Missing Budget data exception";
  private static final String UNDEFINED_BUDGET = "undefined";
  public static final String UNDEFINED_PERSPECTIVE = "undefined";
  private static final String DEFAULT_TIMEZONE = "GMT";
  public static final long ONE_DAY_MILLIS = 86400000;
  public static final String DEFAULT_TIME_UNIT = "days";
  public static final String DEFAULT_TIME_SCOPE = "monthly";
  public static final long OBSERVATION_PERIOD = 29 * ONE_DAY_MILLIS;
  public static final int MONTHS = 12;
  public static final double HUNDRED = 100.0;

  public static void validateBudget(Budget budget, List<Budget> existingBudgets) {
    populateDefaultBudgetBreakdown(budget);
    validateBudgetAmount(budget);
    validateBudgetName(budget, existingBudgets);
  }

  private static void populateDefaultBudgetBreakdown(Budget budget) {
    if (budget.getBudgetMonthlyBreakdown() == null) {
      budget.setBudgetMonthlyBreakdown(
          BudgetMonthlyBreakdown.builder().budgetBreakdown(BudgetBreakdown.YEARLY).build());
      return;
    }
    if (budget.getBudgetMonthlyBreakdown().getBudgetBreakdown() == null) {
      budget.getBudgetMonthlyBreakdown().setBudgetBreakdown(BudgetBreakdown.YEARLY);
    }
  }

  private static void validateBudgetAmount(Budget budget) {
    if (budget.getBudgetAmount() == null) {
      throw new InvalidRequestException(NO_BUDGET_AMOUNT_EXCEPTION);
    }
    if (budget.getBudgetAmount() < 0 || budget.getBudgetAmount() > BUDGET_AMOUNT_UPPER_LIMIT) {
      throw new InvalidRequestException(BUDGET_AMOUNT_NOT_WITHIN_BOUNDS_EXCEPTION);
    }
    if (budget.getPeriod() == BudgetPeriod.YEARLY
        && budget.getBudgetMonthlyBreakdown().getBudgetBreakdown() == BudgetBreakdown.MONTHLY) {
      if (budget.getBudgetMonthlyBreakdown().getBudgetMonthlyAmount() != null
          && budget.getBudgetMonthlyBreakdown().getBudgetMonthlyAmount().size() != MONTHS) {
        throw new InvalidRequestException(NO_MONTHLY_BUDGET_AMOUNT_EXCEPTION);
      }
      Double totalAmount = 0.0;
      for (Double amount : getYearlyMonthWiseValues(budget.getBudgetMonthlyBreakdown().getBudgetMonthlyAmount())) {
        if (amount < 0 || amount > BUDGET_AMOUNT_UPPER_LIMIT) {
          throw new InvalidRequestException(BUDGET_AMOUNT_NOT_WITHIN_BOUNDS_EXCEPTION);
        }
        totalAmount += amount;
        if (totalAmount < 0 || totalAmount > BUDGET_AMOUNT_UPPER_LIMIT) {
          throw new InvalidRequestException(BUDGET_AMOUNT_NOT_WITHIN_BOUNDS_EXCEPTION);
        }
      }
      if (Double.compare(totalAmount, budget.getBudgetAmount()) != 0) {
        budget.setBudgetAmount(totalAmount);
      }
    }
  }

  private static void validateBudgetName(Budget budget, List<Budget> existingBudgets) {
    if (!existingBudgets.isEmpty() && (!existingBudgets.get(0).getUuid().equals(budget.getUuid()))) {
      throw new InvalidRequestException(BUDGET_NAME_EXISTS_EXCEPTION);
    }
  }

  public static void validateCloneBudgetName(String cloneBudgetName) {
    if (cloneBudgetName.equals(UNDEFINED_BUDGET)) {
      throw new InvalidRequestException(BUDGET_NAME_NOT_PROVIDED_EXCEPTION);
    }
  }

  public static String[] getAppliesToIds(BudgetScope scope) {
    String[] entityIds = {};
    if (scope == null) {
      return entityIds;
    }
    log.debug(
        "Budget scope info is: {} {} {}", scope.getBudgetScopeType(), scope.getEntityIds(), scope.getEntityNames());
    return scope.getEntityIds().toArray(new String[0]);
  }

  public static long getStartOfMonthForCurrentBillingCycle() {
    ZoneId zoneId = ZoneId.of(DEFAULT_TIMEZONE);
    LocalDate today = LocalDate.now(zoneId);
    ZonedDateTime zdtStart = today.withDayOfMonth(1).atStartOfDay(zoneId);
    return zdtStart.toEpochSecond() * 1000;
  }

  public static long getEndOfMonthForCurrentBillingCycle() {
    ZoneId zoneId = ZoneId.of(DEFAULT_TIMEZONE);
    LocalDate today = LocalDate.now(zoneId);
    Calendar cal = Calendar.getInstance();
    int daysInMonth = cal.getActualMaximum(Calendar.DATE);
    ZonedDateTime zdtStart = today.withDayOfMonth(daysInMonth).atStartOfDay(zoneId);
    return zdtStart.toEpochSecond() * 1000 + ONE_DAY_MILLIS - 1000;
  }

  public static long getStartOfDay(long day) {
    Calendar c = Calendar.getInstance();
    c.setTimeZone(TimeZone.getTimeZone(DEFAULT_TIMEZONE));
    c.setTimeInMillis(day);
    c.set(Calendar.MILLISECOND, 0);
    c.set(Calendar.SECOND, 0);
    c.set(Calendar.MINUTE, 0);
    c.set(Calendar.HOUR, 0);
    c.set(Calendar.HOUR_OF_DAY, 0);
    return c.getTimeInMillis();
  }

  public static long getStartOfMonthGivenTime(long startTime) {
    Calendar c = Calendar.getInstance();
    c.setTimeZone(TimeZone.getTimeZone(DEFAULT_TIMEZONE));
    c.setTimeInMillis(startTime);
    c.set(Calendar.DAY_OF_MONTH, 1);
    c.set(Calendar.HOUR_OF_DAY, 0);
    c.set(Calendar.MINUTE, 0);
    c.set(Calendar.SECOND, 0);
    c.set(Calendar.MILLISECOND, 0);
    return c.getTimeInMillis();
  }

  public static long getStartOfMonth(boolean prevMonth) {
    Calendar c = Calendar.getInstance();
    c.setTimeZone(TimeZone.getTimeZone(DEFAULT_TIMEZONE));
    c.set(Calendar.DAY_OF_MONTH, 1);
    c.set(Calendar.HOUR_OF_DAY, 0);
    c.set(Calendar.MINUTE, 0);
    c.set(Calendar.SECOND, 0);
    c.set(Calendar.MILLISECOND, 0);
    if (prevMonth) {
      c.add(Calendar.MONTH, -1);
    }
    return c.getTimeInMillis();
  }

  private static long getStartOfPeriod(long startTime, BudgetPeriod period) {
    Calendar c = Calendar.getInstance();
    c.setTimeZone(TimeZone.getTimeZone(DEFAULT_TIMEZONE));
    c.setTimeInMillis(startTime);
    switch (period) {
      case WEEKLY:
        c.set(Calendar.DAY_OF_WEEK, 1);
        break;
      case MONTHLY:
        c.set(Calendar.DAY_OF_MONTH, 1);
        break;
      case QUARTERLY:
        c.set(Calendar.DAY_OF_MONTH, 1);
        c.set(Calendar.MONTH, c.get(Calendar.MONTH) / 3 * 3);
        break;
      case YEARLY:
        c.set(Calendar.DAY_OF_MONTH, 1);
        c.set(Calendar.MONTH, 0);
        break;
      default:
        break;
    }
    return c.getTimeInMillis();
  }

  public static long getStartOfCurrentDay() {
    ZoneId zoneId = ZoneId.of(DEFAULT_TIMEZONE);
    LocalDate today = LocalDate.now(zoneId);
    ZonedDateTime zdtStart = today.atStartOfDay(zoneId);
    return zdtStart.toEpochSecond() * 1000;
  }

  public static long getStartTimeForForecasting() {
    return getStartOfCurrentDay() - 30 * ONE_DAY_MILLIS;
  }

  public static long getStartTimeForForecasting(long startOfPeriod) {
    return startOfPeriod - 30 * ONE_DAY_MILLIS;
  }

  public static long getStartTimeForCurrentBillingCycle() {
    Calendar c = Calendar.getInstance();
    c.set(Calendar.DAY_OF_MONTH, 1);
    c.set(Calendar.YEAR, c.get(Calendar.YEAR) - 1);
    return c.getTimeInMillis();
  }

  public static double getBudgetVariance(double budgetedAmount, double actualCost) {
    return actualCost - budgetedAmount;
  }

  public static double getBudgetVariancePercentage(double budgetVariance, double budgetedAmount) {
    return budgetedAmount != 0 ? (budgetVariance / budgetedAmount) * 100 : 0.0;
  }

  public static String getPerspectiveIdForBudget(Budget budget) {
    if (isPerspectiveBudget(budget)) {
      return budget.getScope().getEntityIds().get(0);
    }
    return UNDEFINED_PERSPECTIVE;
  }

  public static String getPerspectiveNameForBudget(Budget budget) {
    if (isPerspectiveBudget(budget)) {
      return budget.getScope().getEntityNames().get(0);
    }
    return UNDEFINED_PERSPECTIVE;
  }

  public static boolean isBudgetBasedOnGivenPerspective(Budget budget, String perspectiveId) {
    if (isPerspectiveBudget(budget)) {
      return budget.getScope().getEntityIds().get(0).equals(perspectiveId);
    }
    return false;
  }

  public static boolean isPerspectiveBudget(Budget budget) {
    return budget.getScope().getBudgetScopeType().equals(PERSPECTIVE);
  }

  public static int getTimeLeftForBudget(Budget budget) {
    return Math.toIntExact((budget.getEndTime() - getStartOfCurrentDay()) / ONE_DAY_MILLIS);
  }

  public static List<Double> getAlertThresholdsForBudget(Budget budget, AlertThresholdBase basedOn) {
    AlertThreshold[] alertThresholds = budget.getAlertThresholds();
    List<Double> costAlertsPercentage = new ArrayList<>();
    if (alertThresholds != null) {
      for (AlertThreshold alertThreshold : alertThresholds) {
        if (alertThreshold.getBasedOn() == basedOn) {
          costAlertsPercentage.add(alertThreshold.getPercentage());
        }
      }
    }
    return costAlertsPercentage;
  }

  public static long getStartOfLastPeriod(long startOfCurrentPeriod, BudgetPeriod period) {
    Calendar c = Calendar.getInstance();
    c.setTimeZone(TimeZone.getTimeZone(DEFAULT_TIMEZONE));
    c.setTimeInMillis(startOfCurrentPeriod);
    switch (period) {
      case DAILY:
        return startOfCurrentPeriod - ONE_DAY_MILLIS;
      case WEEKLY:
        return startOfCurrentPeriod - 7 * ONE_DAY_MILLIS;
      case MONTHLY:
        c.add(Calendar.MONTH, -1);
        return c.getTimeInMillis();
      case QUARTERLY:
        c.add(Calendar.MONTH, -3);
        return c.getTimeInMillis();
      case YEARLY:
        c.add(Calendar.YEAR, -1);
        return c.getTimeInMillis();
      default:
        return startOfCurrentPeriod;
    }
  }

  public static long getEndTimeForBudget(long startOfCurrentPeriod, BudgetPeriod period) {
    Calendar c = Calendar.getInstance();
    c.setTimeZone(TimeZone.getTimeZone(DEFAULT_TIMEZONE));
    c.setTimeInMillis(startOfCurrentPeriod);
    switch (period) {
      case DAILY:
        return startOfCurrentPeriod + ONE_DAY_MILLIS;
      case WEEKLY:
        return startOfCurrentPeriod + 7 * ONE_DAY_MILLIS;
      case MONTHLY:
        c.add(Calendar.MONTH, 1);
        return c.getTimeInMillis();
      case QUARTERLY:
        c.add(Calendar.MONTH, 3);
        return c.getTimeInMillis();
      case YEARLY:
        c.add(Calendar.YEAR, 1);
        return c.getTimeInMillis();
      default:
        return startOfCurrentPeriod;
    }
  }

  public static long getStartTimeForCostGraph(long startOfCurrentPeriod, BudgetPeriod period) {
    Calendar c = Calendar.getInstance();
    c.setTimeZone(TimeZone.getTimeZone(DEFAULT_TIMEZONE));
    c.setTimeInMillis(startOfCurrentPeriod);
    switch (period) {
      case DAILY:
        return startOfCurrentPeriod - 10 * ONE_DAY_MILLIS;
      case WEEKLY:
        return startOfCurrentPeriod - 70 * ONE_DAY_MILLIS;
      case MONTHLY:
        c.add(Calendar.MONTH, -8);
        return c.getTimeInMillis();
      case QUARTERLY:
        c.add(Calendar.MONTH, -9);
        return c.getTimeInMillis();
      case YEARLY:
        c.add(Calendar.YEAR, -1);
        return c.getTimeInMillis();
      default:
        return startOfCurrentPeriod;
    }
  }

  public static int getTimeOffsetInDays(Budget budget) {
    try {
      return (
          int) ((budget.getStartTime() - getStartOfPeriod(budget.getStartTime(), budget.getPeriod())) / ONE_DAY_MILLIS);
    } catch (Exception e) {
      return 0;
    }
  }

  public static double getUpdatedBudgetAmount(Budget budget) {
    try {
      if (budget.getType() == SPECIFIED_AMOUNT) {
        double growthMultiplier = 1 + (budget.getGrowthRate() / 100);
        return budget.getBudgetAmount() * growthMultiplier;
      } else {
        return budget.getLastMonthCost();
      }
    } catch (Exception e) {
      log.error(
          "Exception while calculating updated budget amount for budget : {}. Exception: {}", budget.getUuid(), e);
      return budget.getBudgetAmount();
    }
  }

  public static List<ValueDataPoint> getUpdatedBudgetAmountMonthlyCost(Budget budget) {
    try {
      if (budget.getType() == SPECIFIED_AMOUNT) {
        double growthMultiplier = 1 + (budget.getGrowthRate() / 100);
        Double[] budgetMonthlyCost =
            getYearlyMonthWiseValues(budget.getBudgetMonthlyBreakdown().getBudgetMonthlyAmount());
        for (int month = 0; month < budgetMonthlyCost.length; month++) {
          budgetMonthlyCost[month] *= growthMultiplier;
        }
        return getYearlyMonthWiseKeyValuePairs(budget.getStartTime(), budgetMonthlyCost);
      } else {
        return getYearlyMonthWiseKeyValuePairs(
            budget.getStartTime(), budget.getBudgetMonthlyBreakdown().getYearlyLastPeriodCost());
      }
    } catch (Exception e) {
      log.error(
          "Exception while calculating updated budget amount for budget : {}. Exception: {}", budget.getUuid(), e);
      return budget.getBudgetMonthlyBreakdown().getBudgetMonthlyAmount();
    }
  }

  public static BudgetPeriod getBudgetPeriod(Budget budget) {
    if (budget.getPeriod() != null) {
      return budget.getPeriod();
    }
    return BudgetPeriod.MONTHLY;
  }

  public static Double getBudgetGrowthRate(Budget budget) {
    if (budget.getGrowthRate() != null) {
      return budget.getGrowthRate();
    }
    return 0D;
  }

  public static long getBudgetStartTime(Budget budget) {
    if (budget.getStartTime() != 0) {
      if (budget.getPeriod() == DAILY) {
        return budget.getStartTime() - 2 * ONE_DAY_MILLIS;
      }
      return budget.getStartTime();
    }
    return getStartOfMonth(false);
  }

  public static AlertThreshold[] getSortedAlertThresholds(
      AlertThresholdBase costType, AlertThreshold[] alertThresholds) {
    List<AlertThreshold> alerts = new ArrayList<>();
    for (AlertThreshold alertThreshold : alertThresholds) {
      if (alertThreshold.getBasedOn() == costType) {
        alerts.add(alertThreshold);
      }
    }
    alerts.sort(Comparator.comparing(AlertThreshold::getPercentage).reversed());
    return alerts.toArray(new AlertThreshold[0]);
  }

  public static boolean isAlertSentInCurrentPeriod(Budget budget, long crossedAt, long startOfBudgetPeriod) {
    if (budget.getBudgetMonthlyBreakdown() != null
        && budget.getBudgetMonthlyBreakdown().getBudgetBreakdown() == BudgetBreakdown.MONTHLY) {
      int lastAlertMonth =
          LocalDateTime.ofInstant(Instant.ofEpochMilli(crossedAt), ZoneId.of(DEFAULT_TIMEZONE)).getMonthValue();
      int currentMonth =
          LocalDateTime.ofInstant(Instant.ofEpochMilli(getStartOfCurrentDay()), ZoneId.of(DEFAULT_TIMEZONE))
              .getMonthValue();
      return lastAlertMonth == currentMonth;
    }
    return startOfBudgetPeriod <= crossedAt;
  }

  public static List<ValueDataPoint> getYearlyMonthWiseKeyValuePairs(long startTime, Double[] cost) {
    List<ValueDataPoint> response = new ArrayList<>();
    if (cost == null || cost.length != MONTHS) {
      return response;
    }
    long startOfMonth = getStartOfMonthGivenTime(startTime);
    Calendar c = Calendar.getInstance();
    c.setTimeZone(TimeZone.getTimeZone(DEFAULT_TIMEZONE));
    c.setTimeInMillis(startOfMonth);
    int month = 0;
    while (month < MONTHS) {
      response.add(ValueDataPoint.builder().time(c.getTimeInMillis()).value(cost[month]).build());
      c.add(Calendar.MONTH, 1);
      month++;
    }
    return response;
  }

  public static Double[] getYearlyMonthWiseValues(List<ValueDataPoint> dataPoints) {
    if (dataPoints == null) {
      return null;
    }
    return dataPoints.stream().map(ValueDataPoint::getValue).toArray(Double[] ::new);
  }

  public static Double getRoundedValue(double value) {
    return Math.round(value * HUNDRED) / HUNDRED;
  }
}
