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

import io.harness.ccm.commons.constants.AnomalyFieldConstants;
import io.harness.ccm.commons.constants.ViewFieldConstants;
import io.harness.ccm.commons.dao.anomaly.AnomalyDao;
import io.harness.ccm.commons.entities.CCMAggregation;
import io.harness.ccm.commons.entities.CCMField;
import io.harness.ccm.commons.entities.CCMFilter;
import io.harness.ccm.commons.entities.CCMGroupBy;
import io.harness.ccm.commons.entities.anomaly.AnomalyData;
import io.harness.ccm.commons.entities.anomaly.AnomalyFeedbackDTO;
import io.harness.ccm.commons.entities.anomaly.AnomalyQueryDTO;
import io.harness.ccm.commons.entities.anomaly.AnomalySummary;
import io.harness.ccm.commons.entities.anomaly.AnomalyWidget;
import io.harness.ccm.commons.entities.anomaly.AnomalyWidgetData;
import io.harness.ccm.commons.entities.anomaly.EntityInfo;
import io.harness.ccm.commons.entities.anomaly.PerspectiveAnomalyData;
import io.harness.ccm.commons.utils.AnomalyQueryBuilder;
import io.harness.ccm.commons.utils.TimeUtils;
import io.harness.ccm.graphql.dto.perspectives.PerspectiveQueryDTO;
import io.harness.ccm.service.intf.AnomalyService;
import io.harness.ccm.utils.PerspectiveToAnomalyQueryHelper;
import io.harness.ccm.views.entities.CEView;
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
  @Inject AnomalyDao anomalyDao;
  @Inject AnomalyQueryBuilder anomalyQueryBuilder;
  @Inject CEViewService viewService;
  @Inject PerspectiveToAnomalyQueryHelper perspectiveToAnomalyQueryHelper;

  private static final Integer DEFAULT_LIMIT = 1000;
  private static final Integer DEFAULT_OFFSET = 0;
  private static final String ANOMALY_RELATIVE_TIME_TEMPLATE = "since %s %s";
  private static final String STATUS_RELATIVE_TIME_TEMPLATE = "%s %s ago";
  private static final String SEPARATOR = "/";

  @Override
  public List<AnomalyData> listAnomalies(@NonNull String accountIdentifier, AnomalyQueryDTO anomalyQuery) {
    if (anomalyQuery == null) {
      anomalyQuery = getDefaultAnomalyQuery();
    }
    Condition condition = anomalyQuery.getFilter() != null
        ? anomalyQueryBuilder.applyAllFilters(anomalyQuery.getFilter())
        : DSL.noCondition();

    List<Anomalies> anomalies = anomalyDao.fetchAnomalies(accountIdentifier, condition,
        anomalyQueryBuilder.getOrderByFields(anomalyQuery.getOrderBy()), anomalyQuery.getOffset(),
        anomalyQuery.getLimit());

    List<AnomalyData> anomalyData = new ArrayList<>();
    anomalies.forEach(anomaly -> anomalyData.add(buildAnomalyData(anomaly)));
    return anomalyData;
  }

  @Override
  public List<PerspectiveAnomalyData> listPerspectiveAnomalies(
      @NonNull String accountIdentifier, @NonNull String perspectiveId, PerspectiveQueryDTO perspectiveQuery) {
    CEView perspective = viewService.get(perspectiveId);
    CCMFilter filters =
        perspectiveToAnomalyQueryHelper.getConvertedFiltersForPerspective(perspective, perspectiveQuery);
    List<AnomalyData> anomalyData = listAnomalies(accountIdentifier,
        AnomalyQueryDTO.builder()
            .filter(filters)
            .orderBy(Collections.emptyList())
            .limit(DEFAULT_LIMIT)
            .offset(DEFAULT_OFFSET)
            .build());
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

    if (anomalyQuery.getGroupBy().size() == 0) {
      return anomalyDao.fetchAnomaliesTotalCost(accountIdentifier, condition);
    }
    return buildDummyAnomalySummaryData(anomalyQuery.getGroupBy().get(0));
  }

  @Override
  public List<AnomalyWidgetData> getAnomalyWidgetData(@NonNull String accountIdentifier, AnomalyQueryDTO anomalyQuery) {
    List<AnomalyWidgetData> anomalyWidgetData = new ArrayList<>();
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
    switch (widget) {
      case TOTAL_COST_IMPACT:
        aggregations.add(CCMAggregation.builder().operationType(SUM).field(COST_IMPACT).build());
        aggregations.add(CCMAggregation.builder().operationType(COUNT).field(null).build());
        break;
      case TOP_N_ANOMALIES:
        groupBy.add(CCMGroupBy.builder().groupByField(ALL).build());
        aggregations.add(CCMAggregation.builder().operationType(SUM).field(CCMField.ACTUAL_COST).build());
        aggregations.add(CCMAggregation.builder().operationType(COUNT).field(null).build());
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
        .build();
  }

  private AnomalyData buildAnomalyData(Anomalies anomaly) {
    long anomalyTime = anomaly.getAnomalytime().toEpochSecond() * 1000;
    return AnomalyData.builder()
        .id(anomaly.getId())
        .time(anomalyTime)
        .anomalyRelativeTime(getRelativeTime(anomalyTime, ANOMALY_RELATIVE_TIME_TEMPLATE))
        .actualAmount(anomaly.getActualcost())
        .expectedAmount(anomaly.getExpectedcost())
        .trend(getAnomalyTrend(anomaly.getActualcost(), anomaly.getExpectedcost()))
        .entity(getEntityInfo(anomaly))
        .resourceName(getResourceName(anomaly))
        .resourceInfo(getResourceInfo(anomaly))
        // Todo : Remove default assignment when status column is added to anomaly table
        .status("Open")
        .statusRelativeTime(getRelativeTime(anomalyTime, STATUS_RELATIVE_TIME_TEMPLATE))
        .cloudProvider(getCloudProvider(anomaly))
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

  private String getRelativeTime(long anomalyTime, String template) {
    long currentTime = System.currentTimeMillis();
    long timeDiff = currentTime - anomalyTime;
    long days = timeDiff / TimeUtils.ONE_DAY_MILLIS;
    if (days != 0) {
      return days > 1 ? String.format(template, days, "days") : String.format(template, 1, "day");
    }
    long hours = timeDiff / TimeUtils.ONE_HOUR_MILLIS;
    if (hours != 0) {
      return hours > 1 ? String.format(template, days, "hours") : String.format(template, 1, "hour");
    }
    long minutes = timeDiff / TimeUtils.ONE_MINUTE_MILLIS;
    return minutes > 1 ? String.format(template, minutes, "minutes") : String.format(template, 1, "minute");
  }

  private Double getAnomalyTrend(Double actualCost, Double expectedCost) {
    return expectedCost != 0 ? Math.round(((actualCost - expectedCost) / expectedCost) * 100D) / 100D : 0;
  }

  private String getResourceName(Anomalies anomaly) {
    StringBuilder builder = new StringBuilder();
    if (anomaly.getClustername() != null) {
      builder.append(anomaly.getClustername());
      if (anomaly.getNamespace() != null) {
        builder.append(SEPARATOR);
        builder.append(anomaly.getNamespace());
      }
      if (anomaly.getWorkloadname() != null) {
        builder.append(SEPARATOR);
        builder.append(anomaly.getWorkloadname());
      }
      return builder.toString();
    } else if (anomaly.getGcpproject() != null) {
      builder.append(anomaly.getGcpproject());
      if (anomaly.getGcpproduct() != null) {
        builder.append(SEPARATOR);
        builder.append(anomaly.getGcpproduct());
      }
      if (anomaly.getGcpskudescription() != null) {
        builder.append(SEPARATOR);
        builder.append(anomaly.getGcpskudescription());
      }
      return builder.toString();
    }
    return "";
  }

  private String getResourceInfo(Anomalies anomaly) {
    StringBuilder builder = new StringBuilder();
    if (anomaly.getClustername() != null) {
      builder.append(ViewFieldConstants.CLUSTER_NAME_FIELD_ID);
      if (anomaly.getNamespace() != null) {
        builder.append(SEPARATOR);
        builder.append(ViewFieldConstants.NAMESPACE_FIELD_ID);
      }
      if (anomaly.getWorkloadname() != null) {
        builder.append(SEPARATOR);
        builder.append(ViewFieldConstants.WORKLOAD_NAME_FIELD_ID);
      }
      return builder.toString();
    } else if (anomaly.getGcpproject() != null) {
      builder.append(ViewFieldConstants.GCP_PROJECT_FIELD_ID);
      if (anomaly.getGcpproduct() != null) {
        builder.append(SEPARATOR);
        builder.append(ViewFieldConstants.GCP_PRODUCT_FIELD_ID);
      }
      if (anomaly.getGcpskuid() != null) {
        builder.append(SEPARATOR);
        builder.append(ViewFieldConstants.GCP_SKU_DESCRIPTION_FIELD_ID);
      }
      return builder.toString();
    }

    return "";
  }

  private EntityInfo getEntityInfo(Anomalies anomaly) {
    return EntityInfo.builder()
        .field(getGroupByField(anomaly))
        .clusterId(anomaly.getClusterid())
        .clusterName(anomaly.getClustername())
        .namespace(anomaly.getNamespace())
        .workloadName(anomaly.getWorkloadname())
        .workloadType(anomaly.getWorkloadtype())
        .gcpProjectId(anomaly.getGcpproject())
        .gcpSKUId(anomaly.getGcpskuid())
        .gcpSKUDescription(anomaly.getGcpskudescription())
        .gcpProduct(anomaly.getGcpproduct())
        .awsAccount(anomaly.getAwsaccount())
        .awsService(anomaly.getAwsservice())
        .awsUsageType(anomaly.getAwsusagetype())
        .awsInstancetype(anomaly.getAwsinstancetype())
        .build();
  }

  private String getGroupByField(Anomalies anomaly) {
    if (anomaly.getClustername() != null) {
      if (anomaly.getWorkloadname() != null) {
        return ViewFieldConstants.WORKLOAD_NAME_FIELD_ID;
      }
      if (anomaly.getNamespace() != null) {
        return ViewFieldConstants.NAMESPACE_FIELD_ID;
      }
      return ViewFieldConstants.CLUSTER_NAME_FIELD_ID;
    } else if (anomaly.getGcpproject() != null) {
      if (anomaly.getGcpskudescription() != null) {
        return ViewFieldConstants.GCP_SKU_DESCRIPTION_FIELD_ID;
      }
      if (anomaly.getGcpproduct() != null) {
        return ViewFieldConstants.GCP_PRODUCT_FIELD_ID;
      }
      return ViewFieldConstants.GCP_PROJECT_FIELD_ID;
    }
    return null;
  }

  private String getCloudProvider(Anomalies anomaly) {
    if (anomaly.getClustername() != null) {
      return AnomalyFieldConstants.CLUSTER.toUpperCase();
    } else if (anomaly.getAwsaccount() != null) {
      return AnomalyFieldConstants.AWS;
    } else if (anomaly.getGcpproject() != null) {
      return AnomalyFieldConstants.GCP;
    }
    return "";
  }

  private AnomalyQueryDTO getDefaultAnomalyQuery() {
    return AnomalyQueryDTO.builder()
        .filter(null)
        .groupBy(new ArrayList<>())
        .orderBy(new ArrayList<>())
        .limit(DEFAULT_LIMIT)
        .offset(DEFAULT_OFFSET)
        .build();
  }

  private List<AnomalySummary> buildDummyAnomalySummaryData(CCMGroupBy groupBy) {
    List<AnomalySummary> dummySummary = new ArrayList<>();
    switch (groupBy.getGroupByField()) {
      case CLOUD_PROVIDER:
        dummySummary.add(buildDummyAnomalySummary("AWS", 10.0, 356.0));
        dummySummary.add(buildDummyAnomalySummary("GCP", 3.0, 154.23));
        dummySummary.add(buildDummyAnomalySummary("KUBERNETES", 1.0, 100.64));
        break;
      case STATUS:
        dummySummary.add(buildDummyAnomalySummary("Open", 9.0, 356.0));
        dummySummary.add(buildDummyAnomalySummary("Resolved", 5.0, 154.23));
        break;
      case ALL:
        dummySummary.add(buildDummyAnomalySummary("e4gt00appkub001-Cost-access/ecom-test01-live", 2.0, 256.0));
        dummySummary.add(buildDummyAnomalySummary("e4gt00appkub002-Cost-access/ecom-test02-live", 3.0, 254.23));
        dummySummary.add(buildDummyAnomalySummary("e4gt00appkub003-Cost-access/ecom-test03-live", 1.0, 130.64));
        break;
      default:
        dummySummary.add(buildDummyAnomalySummary("Total", 15.0, 1356.03));
        break;
    }
    return dummySummary;
  }

  private AnomalySummary buildDummyAnomalySummary(String name, Double count, Double actualCost) {
    return AnomalySummary.builder().name(name).count(count).actualCost(actualCost).build();
  }
}
