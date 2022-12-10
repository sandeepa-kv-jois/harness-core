/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.budgets.service.impl;

import static io.harness.ccm.budget.AlertThresholdBase.ACTUAL_COST;
import static io.harness.ccm.budget.AlertThresholdBase.FORECASTED_COST;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.Math.max;
import static java.lang.String.format;
import static java.util.Collections.singletonList;
import static org.joda.time.Months.monthsBetween;

import io.harness.batch.processing.config.BatchMainConfig;
import io.harness.batch.processing.mail.CEMailNotificationService;
import io.harness.batch.processing.shard.AccountShardService;
import io.harness.ccm.budget.AlertThreshold;
import io.harness.ccm.budget.BudgetBreakdown;
import io.harness.ccm.budget.BudgetPeriod;
import io.harness.ccm.budget.dao.BudgetDao;
import io.harness.ccm.budget.entities.BudgetAlertsData;
import io.harness.ccm.budget.utils.BudgetUtils;
import io.harness.ccm.commons.entities.billing.Budget;
import io.harness.notification.Team;
import io.harness.notification.dtos.NotificationChannelDTO;
import io.harness.notification.dtos.NotificationChannelDTO.NotificationChannelDTOBuilder;
import io.harness.notification.notificationclient.NotificationResult;
import io.harness.notifications.NotificationResourceClient;
import io.harness.rest.RestResponse;
import io.harness.timescaledb.TimeScaleDBService;

import software.wings.beans.User;
import software.wings.beans.notification.SlackNotificationSetting;
import software.wings.beans.security.UserGroup;
import software.wings.graphql.datafetcher.billing.CloudBillingHelper;
import software.wings.graphql.datafetcher.budget.BudgetTimescaleQueryHelper;
import software.wings.service.intfc.SlackMessageSender;
import software.wings.service.intfc.instance.CloudToHarnessMappingService;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.inject.Singleton;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.utils.URIBuilder;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import retrofit2.Response;

@Service
@Singleton
@Slf4j
public class BudgetAlertsServiceImpl {
  @Autowired private TimeScaleDBService timeScaleDBService;
  @Autowired private CEMailNotificationService emailNotificationService;
  @Autowired private NotificationResourceClient notificationResourceClient;
  @Autowired private SlackMessageSender slackMessageSender;
  @Autowired private BudgetTimescaleQueryHelper budgetTimescaleQueryHelper;
  @Autowired private BudgetDao budgetDao;
  @Autowired private BatchMainConfig mainConfiguration;
  @Autowired private CloudToHarnessMappingService cloudToHarnessMappingService;
  @Autowired private AccountShardService accountShardService;
  @Autowired private CloudBillingHelper cloudBillingHelper;

  private static final String BUDGET_MAIL_ERROR = "Budget alert email couldn't be sent";
  private static final String NG_PATH_CONST = "ng/";
  private static final String BUDGET_DETAILS_URL_FORMAT = "/account/%s/continuous-efficiency/budget/%s";
  private static final String BUDGET_DETAILS_URL_FORMAT_NG = "/account/%s/ce/budget/%s/%s";
  private static final String ACTUAL_COST_BUDGET = "cost";
  private static final String SUBJECT_ACTUAL_COST_BUDGET = "Spent so far";
  private static final String FORECASTED_COST_BUDGET = "forecasted cost";
  private static final String SUBJECT_FORECASTED_COST_BUDGET = "Forecasted cost";
  private static final String DAY = "day";
  private static final String WEEK = "week";
  private static final String MONTH = "month";
  private static final String QUARTER = "quarter";
  private static final String YEAR = "year";

  public void sendBudgetAlerts() {
    List<String> accountIds = accountShardService.getCeEnabledAccountIds();
    accountIds.forEach(accountId -> {
      List<Budget> budgets = budgetDao.list(accountId);
      budgets.forEach(budget -> {
        updateCGBudget(budget);
        try {
          checkAndSendAlerts(budget);
        } catch (Exception e) {
          log.error("Can't send alert for budget : {}, Exception: ", budget.getUuid(), e);
        }
      });
    });
  }

  private void checkAndSendAlerts(Budget budget) {
    checkNotNull(budget.getAlertThresholds());
    checkNotNull(budget.getAccountId());

    List<String> emailAddresses =
        Lists.newArrayList(Optional.ofNullable(budget.getEmailAddresses()).orElse(new String[0]));

    List<String> userGroupIds = Arrays.asList(Optional.ofNullable(budget.getUserGroupIds()).orElse(new String[0]));
    emailAddresses.addAll(getEmailsForUserGroup(budget.getAccountId(), userGroupIds));

    // For sending alerts based on actual cost
    AlertThreshold[] alertsBasedOnActualCost =
        BudgetUtils.getSortedAlertThresholds(ACTUAL_COST, budget.getAlertThresholds());
    double actualCost = budget.getActualCost();
    if (budget.getBudgetMonthlyBreakdown() != null
        && budget.getBudgetMonthlyBreakdown().getBudgetBreakdown() == BudgetBreakdown.MONTHLY) {
      int month = monthDifferenceStartAndCurrentTime(budget.getStartTime());
      if (month != -1) {
        actualCost = budget.getBudgetMonthlyBreakdown().getActualMonthlyCost()[month];
      }
    }
    checkAlertThresholdsAndSendAlerts(budget, alertsBasedOnActualCost, emailAddresses, actualCost);

    // For sending alerts based on forecast cost
    AlertThreshold[] alertsBasedOnForecastCost =
        BudgetUtils.getSortedAlertThresholds(FORECASTED_COST, budget.getAlertThresholds());
    double forecastCost = budget.getForecastCost();
    if (budget.getBudgetMonthlyBreakdown() != null
        && budget.getBudgetMonthlyBreakdown().getBudgetBreakdown() == BudgetBreakdown.MONTHLY) {
      int month = monthDifferenceStartAndCurrentTime(budget.getStartTime());
      if (month != -1) {
        forecastCost = budget.getBudgetMonthlyBreakdown().getForecastMonthlyCost()[month];
      }
    }
    checkAlertThresholdsAndSendAlerts(budget, alertsBasedOnForecastCost, emailAddresses, forecastCost);
  }

  private void checkAlertThresholdsAndSendAlerts(
      Budget budget, AlertThreshold[] alertThresholds, List<String> emailAddresses, double cost) {
    for (AlertThreshold alertThreshold : alertThresholds) {
      List<String> userGroupIds =
          Arrays.asList(Optional.ofNullable(alertThreshold.getUserGroupIds()).orElse(new String[0]));
      emailAddresses.addAll(getEmailsForUserGroup(budget.getAccountId(), userGroupIds));
      if (alertThreshold.getEmailAddresses() != null && alertThreshold.getEmailAddresses().length > 0) {
        emailAddresses.addAll(Arrays.asList(alertThreshold.getEmailAddresses()));
      }

      List<String> slackWebhooks =
          Arrays.asList(Optional.ofNullable(alertThreshold.getSlackWebhooks()).orElse(new String[0]));
      slackWebhooks.addAll(getSlackWebhooksForUserGroup(budget.getAccountId(), userGroupIds));

      if (isEmpty(slackWebhooks) && isEmpty(emailAddresses)) {
        log.warn("The budget with id={} has no associated communication channels for threshold={}.", budget.getUuid(),
            alertThreshold);
        continue;
      }

      BudgetAlertsData data = BudgetAlertsData.builder()
                                  .accountId(budget.getAccountId())
                                  .actualCost(cost)
                                  .budgetedCost(budget.getBudgetAmount())
                                  .budgetId(budget.getUuid())
                                  .alertThreshold(alertThreshold.getPercentage())
                                  .alertBasedOn(alertThreshold.getBasedOn())
                                  .time(System.currentTimeMillis())
                                  .build();

      if (BudgetUtils.isAlertSentInCurrentPeriod(budget,
              budgetTimescaleQueryHelper.getLastAlertTimestamp(data, budget.getAccountId()), budget.getStartTime())) {
        break;
      }
      String costType = ACTUAL_COST_BUDGET;
      String subjectCostType = SUBJECT_ACTUAL_COST_BUDGET;
      try {
        if (alertThreshold.getBasedOn() == FORECASTED_COST) {
          costType = FORECASTED_COST_BUDGET;
          subjectCostType = SUBJECT_FORECASTED_COST_BUDGET;
        }
        log.info("{} has been spent under the budget with id={} ", cost, budget.getUuid());
      } catch (Exception e) {
        log.error(e.getMessage());
        break;
      }

      if (exceedsThreshold(cost, getThresholdAmount(budget, alertThreshold))) {
        try {
          sendBudgetAlertViaSlack(budget, alertThreshold, slackWebhooks);
          log.info("slack budget alert sent!");
        } catch (Exception e) {
          log.error("Notification via slack not send : ", e);
        }
        sendBudgetAlertMail(budget.getAccountId(), emailAddresses, budget.getUuid(), budget.getName(), alertThreshold,
            cost, costType, budget.isNgBudget(), subjectCostType, getBudgetPeriodForEmailAlert(budget));
        // insert in timescale table
        budgetTimescaleQueryHelper.insertAlertEntryInTable(data, budget.getAccountId());
        break;
      }
    }
  }

  private void sendBudgetAlertViaSlack(Budget budget, AlertThreshold alertThreshold, List<String> slackWebhooks)
      throws IOException, URISyntaxException {
    if ((isEmpty(slackWebhooks) || !budget.isNotifyOnSlack()) && alertThreshold.getSlackWebhooks() == null) {
      return;
    }
    String budgetUrl = buildAbsoluteUrl(budget.getAccountId(), budget.getUuid(), budget.getName(), budget.isNgBudget());
    Map<String, String> templateData =
        ImmutableMap.<String, String>builder()
            .put("THRESHOLD_PERCENTAGE", String.format("%.1f", alertThreshold.getPercentage()))
            .put("BUDGET_NAME", budget.getName())
            .put("BUDGET_URL", budgetUrl)
            .build();
    NotificationChannelDTOBuilder slackChannelBuilder = NotificationChannelDTO.builder()
                                                            .accountId(budget.getAccountId())
                                                            .templateData(templateData)
                                                            .webhookUrls(slackWebhooks)
                                                            .team(Team.OTHER)
                                                            .templateId("slack_ccm_budget_alert")
                                                            .userGroups(Collections.emptyList());
    Response<RestResponse<NotificationResult>> response =
        notificationResourceClient.sendNotification(budget.getAccountId(), slackChannelBuilder.build()).execute();
    if (!response.isSuccessful()) {
      log.error("Failed to send slack notification: {}",
          (response.errorBody() != null) ? response.errorBody().string() : response.code());
    }
  }

  private List<String> getEmailsForUserGroup(String accountId, List<String> userGroupIds) {
    List<String> emailAddresses = new ArrayList<>();
    for (String userGroupId : userGroupIds) {
      UserGroup userGroup = cloudToHarnessMappingService.getUserGroup(accountId, userGroupId, true);
      if (userGroup != null && userGroup.getMemberIds() != null) {
        emailAddresses.addAll(userGroup.getMemberIds()
                                  .stream()
                                  .map(memberId -> {
                                    User user = cloudToHarnessMappingService.getUser(memberId);
                                    return user.getEmail();
                                  })
                                  .collect(Collectors.toList()));
      }
    }
    return emailAddresses;
  }

  private List<String> getSlackWebhooksForUserGroup(String accountId, List<String> userGroupIds) {
    List<String> slackWebhooks = new ArrayList<>();
    for (String userGroupId : userGroupIds) {
      UserGroup userGroup = cloudToHarnessMappingService.getUserGroup(accountId, userGroupId, true);
      if (userGroup != null && userGroup.getNotificationSettings() != null) {
        SlackNotificationSetting slackNotificationSetting = userGroup.getNotificationSettings().getSlackConfig();
        if (!StringUtils.isEmpty(slackNotificationSetting.getOutgoingWebhookUrl())) {
          slackWebhooks.add(slackNotificationSetting.getOutgoingWebhookUrl());
        }
      }
    }
    return slackWebhooks;
  }

  private void sendBudgetAlertMail(String accountId, List<String> emailAddresses, String budgetId, String budgetName,
      AlertThreshold alertThreshold, double currentCost, String costType, boolean isNgBudget, String subjectCostType,
      String period) {
    List<String> uniqueEmailAddresses = new ArrayList<>(new HashSet<>(emailAddresses));

    try {
      String budgetUrl = buildAbsoluteUrl(accountId, budgetId, budgetName, isNgBudget);

      Map<String, String> templateModel = new HashMap<>();
      templateModel.put("url", budgetUrl);
      templateModel.put("BUDGET_NAME", budgetName);
      templateModel.put("THRESHOLD_PERCENTAGE", String.format("%.1f", alertThreshold.getPercentage()));
      templateModel.put("CURRENT_COST", String.format("%.2f", currentCost));
      templateModel.put("COST_TYPE", costType);
      templateModel.put("SUBJECT_COST_TYPE", subjectCostType);
      templateModel.put("PERIOD", period);

      uniqueEmailAddresses.forEach(emailAddress -> {
        templateModel.put("name", emailAddress.substring(0, emailAddress.lastIndexOf('@')));
        NotificationChannelDTOBuilder emailChannelBuilder = NotificationChannelDTO.builder()
                                                                .accountId(accountId)
                                                                .emailRecipients(singletonList(emailAddress))
                                                                .team(Team.OTHER)
                                                                .templateId("email_ccm_budget_alert")
                                                                .templateData(ImmutableMap.copyOf(templateModel))
                                                                .userGroups(Collections.emptyList());

        try {
          Response<RestResponse<NotificationResult>> response =
              notificationResourceClient.sendNotification(accountId, emailChannelBuilder.build()).execute();
          if (!response.isSuccessful()) {
            log.error("Failed to send email notification: {}",
                (response.errorBody() != null) ? response.errorBody().string() : response.code());
          } else {
            log.info("email sent to {} successfully", emailAddress);
          }
        } catch (IOException e) {
          log.error(BUDGET_MAIL_ERROR, e);
        }
      });
    } catch (URISyntaxException e) {
      log.error(BUDGET_MAIL_ERROR, e);
    }
  }

  private String buildAbsoluteUrl(String accountId, String budgetId, String budgetName, boolean isNgBudget)
      throws URISyntaxException {
    String baseUrl = mainConfiguration.getBaseUrl();
    URIBuilder uriBuilder = new URIBuilder(baseUrl);
    if (isNgBudget) {
      uriBuilder.setPath(NG_PATH_CONST);
      uriBuilder.setFragment(format(BUDGET_DETAILS_URL_FORMAT_NG, accountId, budgetId, budgetName));
    } else {
      uriBuilder.setFragment(format(BUDGET_DETAILS_URL_FORMAT, accountId, budgetId));
    }
    return uriBuilder.toString();
  }

  private boolean exceedsThreshold(double currentAmount, double thresholdAmount) {
    return currentAmount >= thresholdAmount;
  }

  private double getThresholdAmount(Budget budget, AlertThreshold alertThreshold) {
    switch (alertThreshold.getBasedOn()) {
      case ACTUAL_COST:
      case FORECASTED_COST:
        if (budget.getBudgetMonthlyBreakdown() != null
            && budget.getBudgetMonthlyBreakdown().getBudgetBreakdown() == BudgetBreakdown.MONTHLY) {
          int month = monthDifferenceStartAndCurrentTime(budget.getStartTime());
          if (month != -1) {
            return BudgetUtils.getYearlyMonthWiseValues(
                       budget.getBudgetMonthlyBreakdown().getBudgetMonthlyAmount())[month]
                * alertThreshold.getPercentage() / BudgetUtils.HUNDRED;
          }
        }
        return budget.getBudgetAmount() * alertThreshold.getPercentage() / BudgetUtils.HUNDRED;
      default:
        return 0;
    }
  }

  private void updateCGBudget(Budget budget) {
    try {
      log.info("Updating CG budget {}", budget.toString());
      if (budget.getPeriod() == null) {
        budget.setPeriod(BudgetPeriod.MONTHLY);
        budget.setStartTime(BudgetUtils.getStartOfMonth(false));
        budget.setEndTime(BudgetUtils.getEndTimeForBudget(budget.getStartTime(), budget.getPeriod()));
        budget.setGrowthRate(0D);
        budget.setNgBudget(false);
        budgetDao.update(budget.getUuid(), budget);
      }
    } catch (Exception e) {
      log.error("Can't update CG budget : {}, Exception: ", budget.getUuid(), e);
    }
  }

  private int monthDifferenceStartAndCurrentTime(long startTime) {
    long currentTime = BudgetUtils.getStartOfMonthGivenTime(max(startTime, BudgetUtils.getStartOfCurrentDay()));
    long startTimeUpdated = BudgetUtils.getStartOfMonthGivenTime(startTime);
    int monthDiff = monthsBetween(new DateTime(startTimeUpdated), new DateTime(currentTime)).getMonths();
    if (monthDiff > 11) {
      return -1;
    }
    return monthDiff;
  }

  // We don't have monthly here as one of the period in cases
  // We have placed it in default itself
  private String getBudgetPeriodForEmailAlert(Budget budget) {
    switch (budget.getPeriod()) {
      case DAILY:
        return DAY;
      case WEEKLY:
        return WEEK;
      case QUARTERLY:
        return QUARTER;
      case YEARLY:
        if (budget.getBudgetMonthlyBreakdown() != null
            && budget.getBudgetMonthlyBreakdown().getBudgetBreakdown() == BudgetBreakdown.MONTHLY) {
          return MONTH;
        } else {
          return YEAR;
        }
      default:
        return MONTH;
    }
  }
}
