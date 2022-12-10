/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.service.impl;

import static io.harness.ccm.commons.entities.CCMAggregationOperation.COUNT;
import static io.harness.ccm.commons.entities.CCMAggregationOperation.SUM;
import static io.harness.ccm.commons.entities.CCMField.ACTUAL_COST;
import static io.harness.ccm.commons.entities.CCMField.ALL;
import static io.harness.ccm.commons.entities.CCMField.CLOUD_PROVIDER;
import static io.harness.ccm.commons.entities.CCMField.COST_IMPACT;
import static io.harness.ccm.commons.entities.CCMField.STATUS;
import static io.harness.ccm.commons.entities.anomaly.AnomalyWidget.ANOMALIES_BY_CLOUD_PROVIDERS;
import static io.harness.ccm.commons.entities.anomaly.AnomalyWidget.ANOMALIES_BY_STATUS;
import static io.harness.ccm.commons.entities.anomaly.AnomalyWidget.TOP_N_ANOMALIES;
import static io.harness.ccm.commons.entities.anomaly.AnomalyWidget.TOTAL_COST_IMPACT;

import io.harness.ccm.commons.dao.anomaly.AnomalyDao;
import io.harness.ccm.commons.entities.CCMAggregation;
import io.harness.ccm.commons.entities.CCMField;
import io.harness.ccm.commons.entities.CCMFilter;
import io.harness.ccm.commons.entities.CCMGroupBy;
import io.harness.ccm.commons.entities.CCMSort;
import io.harness.ccm.commons.entities.CCMSortOrder;
import io.harness.ccm.commons.entities.anomaly.AnomalyData;
import io.harness.ccm.commons.entities.anomaly.AnomalyFeedbackDTO;
import io.harness.ccm.commons.entities.anomaly.AnomalyQueryDTO;
import io.harness.ccm.commons.entities.anomaly.AnomalySummary;
import io.harness.ccm.commons.entities.anomaly.AnomalyWidget;
import io.harness.ccm.commons.entities.anomaly.AnomalyWidgetData;
import io.harness.ccm.commons.entities.anomaly.PerspectiveAnomalyData;
import io.harness.ccm.commons.service.intf.EntityMetadataService;
import io.harness.ccm.commons.utils.AnomalyQueryBuilder;
import io.harness.ccm.commons.utils.AnomalyUtils;
import io.harness.ccm.graphql.dto.recommendation.FilterStatsDTO;
import io.harness.ccm.service.intf.AnomalyService;
import io.harness.ccm.views.dto.PerspectiveQueryDTO;
import io.harness.ccm.views.entities.CEView;
import io.harness.ccm.views.helper.PerspectiveToAnomalyQueryHelper;
import io.harness.ccm.views.service.CEViewService;
import io.harness.timescaledb.tables.pojos.Anomalies;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.jooq.Condition;
import org.jooq.impl.DSL;

@Slf4j
public class AnomalyServiceImpl implements AnomalyService {
  @Inject private AnomalyDao anomalyDao;
  @Inject private AnomalyQueryBuilder anomalyQueryBuilder;
  @Inject private CEViewService viewService;
  @Inject private PerspectiveToAnomalyQueryHelper perspectiveToAnomalyQueryHelper;
  @Inject private EntityMetadataService entityMetadataService;

  @Override
  public List<AnomalyData> listAnomalies(@NonNull String accountIdentifier, AnomalyQueryDTO anomalyQuery) {
    return listAnomalies(accountIdentifier, anomalyQuery, Collections.emptyList());
  }

  @Override
  public List<AnomalyData> listAnomalies(
      @NonNull String accountIdentifier, AnomalyQueryDTO anomalyQuery, @NonNull List<CCMFilter> ruleFilters) {
    if (anomalyQuery == null) {
      anomalyQuery = getDefaultAnomalyQuery();
    }
    Condition condition = anomalyQuery.getFilter() != null
        ? anomalyQueryBuilder.applyAllFilters(anomalyQuery.getFilter(), ruleFilters)
        : DSL.noCondition();

    List<CCMSort> sortBy = anomalyQuery.getOrderBy() != null ? anomalyQuery.getOrderBy() : Collections.emptyList();
    List<Anomalies> anomalies =
        anomalyDao.fetchAnomalies(accountIdentifier, condition, anomalyQueryBuilder.getOrderByFields(sortBy),
            anomalyQuery.getOffset() != null ? anomalyQuery.getOffset() : AnomalyUtils.DEFAULT_OFFSET,
            anomalyQuery.getLimit() != null ? anomalyQuery.getLimit() : AnomalyUtils.DEFAULT_LIMIT);

    List<AnomalyData> anomalyData = new ArrayList<>();
    List<String> awsAccountIds = AnomalyUtils.collectAwsAccountIds(anomalies);
    Map<String, String> entityIdToNameMapping =
        entityMetadataService.getAccountNamePerAwsAccountId(awsAccountIds, accountIdentifier);
    anomalies.forEach(anomaly -> anomalyData.add(AnomalyUtils.buildAnomalyData(anomaly, entityIdToNameMapping)));

    return AnomalyUtils.sortDataByNonTableFields(anomalyData, sortBy);
  }

  @Override
  public List<FilterStatsDTO> getAnomalyFilterStats(
      @NonNull String accountIdentifier, List<String> anomalyColumnsList) {
    if (anomalyColumnsList == null) {
      anomalyColumnsList = new ArrayList<>();
    }
    List<FilterStatsDTO> result = new ArrayList<>();

    for (String column : anomalyColumnsList) {
      List<String> columnValues = anomalyDao.getDistinctStringValues(accountIdentifier, column);
      result.add(FilterStatsDTO.builder().key(column).values(columnValues).build());
    }

    return result;
  }

  @Override
  public List<PerspectiveAnomalyData> listPerspectiveAnomalies(
      @NonNull String accountIdentifier, @NonNull String perspectiveId, PerspectiveQueryDTO perspectiveQuery) {
    CEView perspective = viewService.get(perspectiveId);
    List<CCMFilter> ruleFilters = perspectiveToAnomalyQueryHelper.getConvertedRulesForPerspective(perspective);
    CCMFilter filters =
        perspectiveToAnomalyQueryHelper.getConvertedFiltersForPerspective(perspective, perspectiveQuery);
    List<AnomalyData> anomalyData = listAnomalies(accountIdentifier,
        AnomalyQueryDTO.builder()
            .filter(filters)
            .orderBy(Collections.emptyList())
            .limit(AnomalyUtils.DEFAULT_LIMIT)
            .offset(AnomalyUtils.DEFAULT_OFFSET)
            .build(),
        ruleFilters);
    return buildPerspectiveAnomalyData(anomalyData);
  }

  @Override
  public Boolean updateAnomalyFeedback(
      @NonNull String accountIdentifier, String anomalyId, AnomalyFeedbackDTO feedback) {
    try {
      anomalyDao.updateAnomalyFeedback(accountIdentifier, anomalyId, feedback);
      return true;
    } catch (Exception e) {
      log.info("Exception while updating anomaly feedback: ", e);
    }
    return false;
  }

  @Override
  public List<AnomalySummary> getAnomalySummary(@NonNull String accountIdentifier, AnomalyQueryDTO anomalyQuery) {
    if (anomalyQuery == null) {
      return Collections.emptyList();
    }
    Condition condition = anomalyQuery.getFilter() != null
        ? anomalyQueryBuilder.applyAllFilters(anomalyQuery.getFilter())
        : DSL.noCondition();

    if (anomalyQuery.getGroupBy().isEmpty()) {
      List<AnomalySummary> totalCostSummary = anomalyDao.fetchAnomaliesTotalCost(accountIdentifier, condition);
      List<AnomalySummary> updatedTotalCostSummary = new ArrayList<>();
      totalCostSummary.forEach(entry -> {
        if (entry.getActualCost() != null) {
          updatedTotalCostSummary.add(buildAnomalySummary(entry.getName(), entry.getCount(), entry.getActualCost(),
              entry.getActualCost() - entry.getExpectedCost()));
        }
      });
      return updatedTotalCostSummary;
    } else {
      List<AnomalyData> anomalies = listAnomalies(accountIdentifier, anomalyQuery);
      if (anomalyQuery.getGroupBy().get(0).getGroupByField() == ALL) {
        return buildTopAnomaliesSummary(anomalies);
      } else if (anomalyQuery.getGroupBy().get(0).getGroupByField() == CLOUD_PROVIDER) {
        return buildAnomalyByCloudProviderSummary(anomalies);
      } else if (anomalyQuery.getGroupBy().get(0).getGroupByField() == STATUS) {
        return buildAnomalyByStatusSummary(anomalies);
      }
    }
    return Collections.emptyList();
  }

  @Override
  public List<AnomalySummary> getAnomalySummary(List<AnomalyData> anomalies, AnomalyWidget widget) {
    switch (widget) {
      case TOP_N_ANOMALIES:
        return buildTopAnomaliesSummary(anomalies);
      case ANOMALIES_BY_CLOUD_PROVIDERS:
        return buildAnomalyByCloudProviderSummary(anomalies);
      case ANOMALIES_BY_STATUS:
        return buildAnomalyByStatusSummary(anomalies);
      default:
        return Collections.emptyList();
    }
  }

  private List<AnomalySummary> buildTopAnomaliesSummary(List<AnomalyData> anomalies) {
    List<AnomalySummary> topAnomalies = new ArrayList<>();
    anomalies.forEach(anomaly
        -> topAnomalies.add(buildAnomalySummary(
            anomaly.getResourceName(), 1.0, anomaly.getActualAmount(), anomaly.getAnomalousSpend())));
    return topAnomalies;
  }

  private List<AnomalySummary> buildAnomalyByCloudProviderSummary(List<AnomalyData> anomalies) {
    Map<String, AnomalySummary> cloudProviderToSummaryMap = new HashMap<>();
    anomalies.forEach(anomaly -> {
      String cloudProvider = anomaly.getCloudProvider();
      if (cloudProviderToSummaryMap.containsKey(cloudProvider)) {
        cloudProviderToSummaryMap.put(cloudProvider,
            buildAnomalySummary(cloudProvider, 1 + cloudProviderToSummaryMap.get(cloudProvider).getCount(),
                anomaly.getActualAmount() + cloudProviderToSummaryMap.get(cloudProvider).getActualCost(),
                anomaly.getAnomalousSpend() + cloudProviderToSummaryMap.get(cloudProvider).getAnomalousCost()));
      } else {
        cloudProviderToSummaryMap.put(cloudProvider,
            buildAnomalySummary(cloudProvider, 1.0, anomaly.getActualAmount(), anomaly.getAnomalousSpend()));
      }
    });
    List<AnomalySummary> cloudProviderSummary = new ArrayList<>(cloudProviderToSummaryMap.values());
    cloudProviderSummary.sort(Comparator.comparing(AnomalySummary::getCount).reversed());
    return cloudProviderSummary;
  }

  private List<AnomalySummary> buildAnomalyByStatusSummary(List<AnomalyData> anomalies) {
    if (anomalies.isEmpty()) {
      return Collections.emptyList();
    }
    Double count = (double) anomalies.size();
    Double cost = anomalies.stream().mapToDouble(AnomalyData::getActualAmount).sum();
    Double anomalousCost = anomalies.stream().mapToDouble(AnomalyData::getAnomalousSpend).sum();
    return Collections.singletonList(buildAnomalySummary("Open", count, cost, anomalousCost));
  }

  @Override
  public List<AnomalyWidgetData> getAnomalyWidgetData(@NonNull String accountIdentifier, AnomalyQueryDTO anomalyQuery) {
    List<AnomalyWidgetData> anomalyWidgetData = new ArrayList<>();
    List<AnomalyData> anomalies = listAnomalies(accountIdentifier, anomalyQuery);
    anomalyWidgetData.add(
        AnomalyWidgetData.builder()
            .widgetDescription(AnomalyWidget.TOP_N_ANOMALIES)
            .widgetData(getAnomalySummary(accountIdentifier, getAnomalyWidgetQuery(anomalyQuery, TOP_N_ANOMALIES)))
            .build());
    anomalyWidgetData.add(
        AnomalyWidgetData.builder()
            .widgetDescription(AnomalyWidget.TOTAL_COST_IMPACT)
            .widgetData(getAnomalySummary(accountIdentifier, getAnomalyWidgetQuery(anomalyQuery, TOTAL_COST_IMPACT)))
            .build());
    anomalyWidgetData.add(AnomalyWidgetData.builder()
                              .widgetDescription(ANOMALIES_BY_CLOUD_PROVIDERS)
                              .widgetData(getAnomalySummary(
                                  accountIdentifier, getAnomalyWidgetQuery(anomalyQuery, ANOMALIES_BY_CLOUD_PROVIDERS)))
                              .build());
    anomalyWidgetData.add(
        AnomalyWidgetData.builder()
            .widgetDescription(ANOMALIES_BY_STATUS)
            .widgetData(getAnomalySummary(accountIdentifier, getAnomalyWidgetQuery(anomalyQuery, ANOMALIES_BY_STATUS)))
            .build());
    return anomalyWidgetData;
  }

  private AnomalyQueryDTO getAnomalyWidgetQuery(AnomalyQueryDTO anomalyQuery, AnomalyWidget widget) {
    List<CCMGroupBy> groupBy = new ArrayList<>();
    List<CCMAggregation> aggregations = new ArrayList<>();
    List<CCMSort> sortOrders = new ArrayList<>();
    Integer limit = AnomalyUtils.DEFAULT_LIMIT;
    Integer offset = AnomalyUtils.DEFAULT_OFFSET;
    switch (widget) {
      case TOTAL_COST_IMPACT:
        aggregations.add(CCMAggregation.builder().operationType(SUM).field(COST_IMPACT).build());
        aggregations.add(CCMAggregation.builder().operationType(COUNT).field(null).build());
        break;
      case TOP_N_ANOMALIES:
        groupBy.add(CCMGroupBy.builder().groupByField(ALL).build());
        aggregations.add(CCMAggregation.builder().operationType(SUM).field(CCMField.ACTUAL_COST).build());
        aggregations.add(CCMAggregation.builder().operationType(COUNT).field(null).build());
        sortOrders.add(CCMSort.builder().field(CCMField.ACTUAL_COST).order(CCMSortOrder.DESCENDING).build());
        limit = 3;
        break;
      case ANOMALIES_BY_STATUS:
        groupBy.add(CCMGroupBy.builder().groupByField(STATUS).build());
        aggregations.add(CCMAggregation.builder().operationType(SUM).field(ACTUAL_COST).build());
        aggregations.add(CCMAggregation.builder().operationType(COUNT).field(null).build());
        break;
      case ANOMALIES_BY_CLOUD_PROVIDERS:
        groupBy.add(CCMGroupBy.builder().groupByField(CLOUD_PROVIDER).build());
        aggregations.add(CCMAggregation.builder().operationType(SUM).field(ACTUAL_COST).build());
        aggregations.add(CCMAggregation.builder().operationType(COUNT).field(null).build());
        break;
      default:
    }
    return AnomalyQueryDTO.builder()
        .filter(anomalyQuery.getFilter())
        .groupBy(groupBy)
        .aggregations(aggregations)
        .orderBy(sortOrders)
        .limit(limit)
        .offset(offset)
        .build();
  }

  private List<PerspectiveAnomalyData> buildPerspectiveAnomalyData(List<AnomalyData> anomalies) {
    Map<Long, PerspectiveAnomalyData> timestampToAnomaly = new HashMap<>();
    for (AnomalyData data : anomalies) {
      Long anomalyTime = data.getTime();
      if (timestampToAnomaly.containsKey(anomalyTime)) {
        timestampToAnomaly.put(anomalyTime, getUpdatedPerspectiveAnomaly(data, timestampToAnomaly.get(anomalyTime)));
      } else {
        timestampToAnomaly.put(anomalyTime, getPerspectiveAnomaly(data));
      }
    }
    List<PerspectiveAnomalyData> perspectiveAnomalies = new ArrayList<>(timestampToAnomaly.values());
    perspectiveAnomalies.sort(Comparator.comparing(PerspectiveAnomalyData::getTimestamp));
    return perspectiveAnomalies;
  }

  private PerspectiveAnomalyData getUpdatedPerspectiveAnomaly(
      AnomalyData anomaly, PerspectiveAnomalyData cumulativeAnomalies) {
    return PerspectiveAnomalyData.builder()
        .timestamp(anomaly.getTime())
        .anomalyCount(1 + cumulativeAnomalies.getAnomalyCount())
        .actualCost(anomaly.getActualAmount() + cumulativeAnomalies.getActualCost())
        .differenceFromExpectedCost(anomaly.getActualAmount() - anomaly.getExpectedAmount()
            + cumulativeAnomalies.getDifferenceFromExpectedCost())
        .associatedResources(cumulativeAnomalies.getAssociatedResources())
        .resourceType(cumulativeAnomalies.getResourceType())
        .build();
  }

  private PerspectiveAnomalyData getPerspectiveAnomaly(AnomalyData anomaly) {
    return PerspectiveAnomalyData.builder()
        .timestamp(anomaly.getTime())
        .anomalyCount(1)
        .actualCost(anomaly.getActualAmount())
        .differenceFromExpectedCost(anomaly.getActualAmount() - anomaly.getExpectedAmount())
        .associatedResources(Arrays.asList(anomaly.getEntity()))
        .resourceType("")
        .build();
  }

  private AnomalyQueryDTO getDefaultAnomalyQuery() {
    return AnomalyQueryDTO.builder()
        .filter(null)
        .groupBy(new ArrayList<>())
        .orderBy(new ArrayList<>())
        .limit(AnomalyUtils.DEFAULT_LIMIT)
        .offset(AnomalyUtils.DEFAULT_OFFSET)
        .build();
  }

  private AnomalySummary buildAnomalySummary(String name, Double count, Double actualCost, Double anomalousCost) {
    return AnomalySummary.builder().name(name).count(count).actualCost(actualCost).anomalousCost(anomalousCost).build();
  }
}
