/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.pricing.gcp.bigquery;

import static io.harness.batch.processing.pricing.gcp.bigquery.BQConst.GCP_DESCRIPTION_CONDITION;
import static io.harness.batch.processing.pricing.gcp.bigquery.BQConst.GCP_PRODUCT_FAMILY_CASE_CONDITION;
import static io.harness.ccm.billing.GcpServiceAccountServiceImpl.getCredentials;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.batch.processing.config.BatchMainConfig;
import io.harness.batch.processing.config.BillingDataPipelineConfig;
import io.harness.batch.processing.entities.ClusterDataDetails;
import io.harness.batch.processing.pricing.gcp.bigquery.VMInstanceServiceBillingData.VMInstanceServiceBillingDataBuilder;
import io.harness.batch.processing.pricing.vmpricing.VMInstanceBillingData;
import io.harness.beans.FeatureName;
import io.harness.ccm.commons.entities.batch.CEMetadataRecord.CEMetadataRecordBuilder;
import io.harness.ff.FeatureFlagService;

import software.wings.graphql.datafetcher.billing.CloudBillingHelper;

import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.BigQueryOptions;
import com.google.cloud.bigquery.Field;
import com.google.cloud.bigquery.FieldList;
import com.google.cloud.bigquery.FieldValue;
import com.google.cloud.bigquery.FieldValueList;
import com.google.cloud.bigquery.QueryJobConfiguration;
import com.google.cloud.bigquery.Schema;
import com.google.cloud.bigquery.TableResult;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@OwnedBy(HarnessTeam.CE)
@Slf4j
@Service
public class BigQueryHelperServiceImpl implements BigQueryHelperService {
  private static final String CONTENT_STACK_ACCOUNTID = "CYVS6BRkSPKdIE5FZThNRQ";
  private BatchMainConfig mainConfig;
  private CloudBillingHelper cloudBillingHelper;
  private int clusterDetailsCount;
  private double billingSum;

  private FeatureFlagService featureFlagService;
  private static final String preAggregated = "preAggregated";
  private static final String clusterData = "clusterData";
  private static final String countConst = "count";
  private static final String cloudProviderConst = "cloudProvider";

  @Autowired
  public BigQueryHelperServiceImpl(
      BatchMainConfig mainConfig, CloudBillingHelper cloudBillingHelper, FeatureFlagService featureFlagService) {
    this.mainConfig = mainConfig;
    this.cloudBillingHelper = cloudBillingHelper;
    this.featureFlagService = featureFlagService;
  }

  private static final String GOOGLE_CREDENTIALS_PATH = "GOOGLE_CREDENTIALS_PATH";
  private static final String TABLE_SUFFIX = "%s_%s";
  private static final String AWS_CUR_TABLE_NAME = "awscur_%s";
  private static final String AZURE_TABLE_NAME = "unifiedTable";
  private static final String GCP_TABLE_NAME_WITH_WILDCARD = "gcp_billing_export_resource_*";
  private String resourceCondition = "resourceid like '%%%s%%'";

  @Override
  public Map<String, VMInstanceBillingData> getAwsEC2BillingData(
      List<String> resourceId, Instant startTime, Instant endTime, String dataSetId, String accountId) {
    String query = BQConst.AWS_EC2_BILLING_QUERY;
    String resourceIds = String.join("','", resourceId);
    String projectTableName = getAwsProjectTableName(startTime, dataSetId);
    String formattedQuery = format(query, projectTableName, resourceIds, startTime, endTime);
    return query(formattedQuery, "AWS", accountId);
  }

  @Override
  public Map<String, VMInstanceBillingData> getEKSFargateBillingData(
      List<String> resourceIds, Instant startTime, Instant endTime, String dataSetId) {
    String query = BQConst.EKS_FARGATE_BILLING_QUERY;
    String projectTableName = getAwsProjectTableName(startTime, dataSetId);
    String formattedQuery =
        format(query, projectTableName, getResourceConditionWhereClause(resourceIds), startTime, endTime);
    log.info("EKS Fargate Billing Query: {}", formattedQuery);
    return queryEKSFargate(formattedQuery);
  }

  public ClusterDataDetails getClusterDataDetails(String accountId, Instant startTime) {
    String query = BQConst.CLUSTER_DATA_QUERY;
    String tableName = cloudBillingHelper.getCloudProviderTableName(
        mainConfig.getBillingDataPipelineConfig().getGcpProjectId(), accountId, clusterData);
    String formattedQuery = format(query, tableName, accountId, startTime.toEpochMilli());
    log.info("BigQuery formatted query : " + formattedQuery);
    BigQuery bigQueryService = getBigQueryService();
    QueryJobConfiguration queryConfig = QueryJobConfiguration.newBuilder(formattedQuery).build();
    TableResult result = null;
    try {
      result = bigQueryService.query(queryConfig);
      FieldList fields = getFieldList(result);
      List<VMInstanceServiceBillingData> instanceServiceBillingDataList = new ArrayList<>();
      Iterable<FieldValueList> fieldValueLists = getFieldValueLists(result);
      clusterDetailsCount = 0;
      billingSum = 0.0;
      for (FieldValueList row : fieldValueLists) {
        for (Field field : fields) {
          switch (field.getName()) {
            case BQConst.count:
              if (getDoubleValue(row, field) != null) {
                clusterDetailsCount = getDoubleValue(row, field).intValue();
              }
              break;
            case BQConst.billingAmountSum:
              if (getDoubleValue(row, field) != null) {
                billingSum = getDoubleValue(row, field);
              }
              break;
            default:
              break;
          }
        }
      }
      return ClusterDataDetails.builder().entriesCount(clusterDetailsCount).billingAmountSum(billingSum).build();
    } catch (InterruptedException e) {
      log.error("Failed to get Billing Data. {}", e);
      Thread.currentThread().interrupt();
    } catch (Exception ex) {
      log.error("Exception Failed to get Billing Data", ex);
    }
    return null;
  }

  private String getResourceConditionWhereClause(List<String> resourceIds) {
    List<String> resourceIdConditions = new ArrayList<>();
    for (String resourceId : resourceIds) {
      resourceIdConditions.add(format(resourceCondition, resourceId));
    }
    return String.join(" OR ", resourceIdConditions);
  }

  private Map<String, VMInstanceBillingData> query(String formattedQuery, String cloudProviderType, String accountId) {
    log.debug("Formatted query for {} : {}", cloudProviderType, formattedQuery);
    BigQuery bigQueryService = getBigQueryService();
    QueryJobConfiguration queryConfig = QueryJobConfiguration.newBuilder(formattedQuery).build();
    TableResult result = null;
    try {
      result = bigQueryService.query(queryConfig);
      switch (cloudProviderType) {
        case "AWS":
          return convertToAwsInstanceBillingData(result, accountId);
        case "AZURE":
          return convertToAzureInstanceBillingData(result);
        case "GCP":
          return convertToGcpInstanceBillingData(result);
        default:
          break;
      }

    } catch (InterruptedException e) {
      log.error("Failed to get {} Billing Data. {}", cloudProviderType, e);
      Thread.currentThread().interrupt();
    } catch (Exception ex) {
      log.error("Exception Failed to get {} Billing Data", cloudProviderType, ex);
    }
    return Collections.emptyMap();
  }

  private Map<String, VMInstanceBillingData> convertToGcpInstanceBillingData(TableResult result) {
    List<VMInstanceServiceBillingData> vmInstanceServiceBillingDataList =
        convertToGcpInstanceServiceBillingData(result);

    Map<String, VMInstanceBillingData> vmInstanceBillingDataMap = new HashMap<>();
    for (VMInstanceServiceBillingData vmInstanceServiceBillingData : vmInstanceServiceBillingDataList) {
      String resourceId = vmInstanceServiceBillingData.getResourceId();
      double cpuCost = 0;
      double ramCost = 0;
      double networkCost = 0;
      VMInstanceBillingData vmInstanceBillingData = VMInstanceBillingData.builder().resourceId(resourceId).build();
      if (vmInstanceBillingDataMap.containsKey(resourceId)) {
        vmInstanceBillingData = vmInstanceBillingDataMap.get(resourceId);
        cpuCost = vmInstanceBillingData.getCpuCost();
        ramCost = vmInstanceBillingData.getMemoryCost();
        networkCost = vmInstanceBillingData.getNetworkCost();
      }

      if (BQConst.gcpComputeService.equals(vmInstanceServiceBillingData.getServiceCode())
          || vmInstanceServiceBillingData.getProductFamily() == null) {
        double rate = vmInstanceServiceBillingData.getRate();

        if (vmInstanceServiceBillingData.getProductFamily().equalsIgnoreCase("CPU Cost")) {
          cpuCost += vmInstanceServiceBillingData.getCost();
        } else if (vmInstanceServiceBillingData.getProductFamily().equalsIgnoreCase("RAM Cost")) {
          ramCost += vmInstanceServiceBillingData.getCost();
        } else if (vmInstanceServiceBillingData.getProductFamily().equalsIgnoreCase("Network Cost")) {
          networkCost += vmInstanceServiceBillingData.getCost();
        }

        double computeCost = cpuCost + ramCost;

        vmInstanceBillingData = vmInstanceBillingData.toBuilder()
                                    .computeCost(computeCost)
                                    .cpuCost(cpuCost)
                                    .memoryCost(ramCost)
                                    .networkCost(networkCost)
                                    .rate(rate)
                                    .build();
      }

      vmInstanceBillingDataMap.put(resourceId, vmInstanceBillingData);
    }

    log.debug("GCP: resource map data {} ", vmInstanceBillingDataMap);
    return vmInstanceBillingDataMap;
  }

  private List<VMInstanceServiceBillingData> convertToGcpInstanceServiceBillingData(TableResult result) {
    FieldList fields = getFieldList(result);
    List<VMInstanceServiceBillingData> instanceServiceBillingDataList = new ArrayList<>();
    Iterable<FieldValueList> fieldValueLists = getFieldValueLists(result);
    for (FieldValueList row : fieldValueLists) {
      VMInstanceServiceBillingDataBuilder dataBuilder = VMInstanceServiceBillingData.builder();
      for (Field field : fields) {
        switch (field.getName()) {
          case BQConst.gcpResourceName:
            dataBuilder.resourceId(fetchStringValue(row, field));
            break;
          case BQConst.cost:
            dataBuilder.cost(getNumericValue(row, field));
            break;
          case BQConst.gcpRate:
            dataBuilder.rate(getNumericValue(row, field));
            break;
          case BQConst.gcpServiceName:
            dataBuilder.serviceCode(fetchStringValue(row, field));
            break;
          case BQConst.productFamily:
            dataBuilder.productFamily(fetchStringValue(row, field));
            break;
          default:
            break;
        }
      }
      instanceServiceBillingDataList.add(dataBuilder.build());
    }
    return instanceServiceBillingDataList;
  }

  private Map<String, VMInstanceBillingData> convertToAzureInstanceBillingData(TableResult result) {
    List<VMInstanceServiceBillingData> vmInstanceServiceBillingDataList =
        convertToAzureInstanceServiceBillingData(result);
    Map<String, VMInstanceBillingData> vmInstanceBillingDataMap = new HashMap<>();
    vmInstanceServiceBillingDataList.forEach(vmInstanceServiceBillingData -> {
      String resourceId = vmInstanceServiceBillingData.getResourceId();
      VMInstanceBillingData vmInstanceBillingData = VMInstanceBillingData.builder().resourceId(resourceId).build();
      if (vmInstanceBillingDataMap.containsKey(resourceId)) {
        vmInstanceBillingData = vmInstanceBillingDataMap.get(resourceId);
      }
      // TODO: Take care of network costs too
      // if ("Bandwidth".equals(vmInstanceServiceBillingData.getProductFamily())) {
      //  vmInstanceBillingData =
      //          vmInstanceBillingData.toBuilder().networkCost(vmInstanceServiceBillingData.getCost()).build();
      //}

      if (BQConst.azureVMMeterCategory.equals(vmInstanceServiceBillingData.getProductFamily())
          || vmInstanceServiceBillingData.getProductFamily() == null) {
        double cost = vmInstanceServiceBillingData.getCost();
        double rate = vmInstanceServiceBillingData.getRate();
        if (null != vmInstanceServiceBillingData.getEffectiveCost()) {
          cost = vmInstanceServiceBillingData.getEffectiveCost();
        }
        vmInstanceBillingData = vmInstanceBillingData.toBuilder().computeCost(cost).rate(rate).build();
      }

      vmInstanceBillingDataMap.put(resourceId, vmInstanceBillingData);
    });

    log.debug("Azure: resource map data {} ", vmInstanceBillingDataMap);
    return vmInstanceBillingDataMap;
  }

  private List<VMInstanceServiceBillingData> convertToAzureInstanceServiceBillingData(TableResult result) {
    FieldList fields = getFieldList(result);
    List<VMInstanceServiceBillingData> instanceServiceBillingDataList = new ArrayList<>();
    Iterable<FieldValueList> fieldValueLists = getFieldValueLists(result);
    for (FieldValueList row : fieldValueLists) {
      VMInstanceServiceBillingDataBuilder dataBuilder = VMInstanceServiceBillingData.builder();
      for (Field field : fields) {
        switch (field.getName()) {
          case BQConst.azureVMProviderId:
            dataBuilder.resourceId(fetchStringValue(row, field));
            break;
          case BQConst.cost:
            dataBuilder.cost(getNumericValue(row, field));
            break;
          case BQConst.azureRate:
            dataBuilder.rate(getNumericValue(row, field));
            break;
          case BQConst.azureMeterCategory:
            dataBuilder.productFamily(fetchStringValue(row, field));
            break;
          default:
            break;
        }
      }
      instanceServiceBillingDataList.add(dataBuilder.build());
    }
    log.info("Resource Id data {} ", instanceServiceBillingDataList);
    return instanceServiceBillingDataList;
  }

  private Map<String, VMInstanceBillingData> queryEKSFargate(String formattedQuery) {
    BigQuery bigQueryService = getBigQueryService();
    QueryJobConfiguration queryConfig = QueryJobConfiguration.newBuilder(formattedQuery).build();
    TableResult result = null;
    try {
      result = bigQueryService.query(queryConfig);
      return convertToEksFargateInstanceBillingData(result);
    } catch (InterruptedException e) {
      log.error("Failed to get EKS Fargate Data from CUR. {}", e);
      Thread.currentThread().interrupt();
    }
    return Collections.emptyMap();
  }

  private String getTableNameSuffix(Instant startTime) {
    Date date = Date.from(startTime);
    SimpleDateFormat monthFormatter = new SimpleDateFormat("MM");
    String month = monthFormatter.format(date);
    SimpleDateFormat yearFormatter = new SimpleDateFormat("yyyy");
    String year = yearFormatter.format(date);
    return format(TABLE_SUFFIX, year, month);
  }

  private String getAwsProjectTableName(Instant startTime, String dataSetId) {
    String tableSuffix = getTableNameSuffix(startTime);
    String tableName = format(AWS_CUR_TABLE_NAME, tableSuffix);
    BillingDataPipelineConfig billingDataPipelineConfig = mainConfig.getBillingDataPipelineConfig();
    return format("%s.%s.%s", billingDataPipelineConfig.getGcpProjectId(), dataSetId, tableName);
  }

  private String getAzureProjectTableName(String dataSetId) {
    BillingDataPipelineConfig billingDataPipelineConfig = mainConfig.getBillingDataPipelineConfig();
    return format("%s.%s.%s", billingDataPipelineConfig.getGcpProjectId(), dataSetId, AZURE_TABLE_NAME);
  }

  @Override
  public Map<String, VMInstanceBillingData> getAwsBillingData(
      Instant startTime, Instant endTime, String dataSetId, String accountId) {
    String query = BQConst.AWS_BILLING_DATA;
    String projectTableName = getAwsProjectTableName(startTime, dataSetId);
    String formattedQuery = format(query, projectTableName, startTime, endTime);
    return query(formattedQuery, "AWS", accountId);
  }

  @Override
  public void updateCloudProviderMetaData(String accountId, CEMetadataRecordBuilder ceMetadataRecordBuilder) {
    String tableName = cloudBillingHelper.getCloudProviderTableName(
        mainConfig.getBillingDataPipelineConfig().getGcpProjectId(), accountId, preAggregated);
    String formattedQuery = format(BQConst.CLOUD_PROVIDER_AGG_DATA, tableName);
    QueryJobConfiguration queryConfig = QueryJobConfiguration.newBuilder(formattedQuery).build();
    try {
      TableResult result = getBigQueryService().query(queryConfig);
      for (FieldValueList row : result.iterateAll()) {
        String cloudProvider = row.get(cloudProviderConst).getStringValue();
        switch (cloudProvider) {
          case "AWS":
            ceMetadataRecordBuilder.awsDataPresent(row.get(countConst).getDoubleValue() > 0);
            break;
          case "GCP":
            ceMetadataRecordBuilder.gcpDataPresent(row.get(countConst).getDoubleValue() > 0);
            break;
          case "AZURE":
            ceMetadataRecordBuilder.azureDataPresent(row.get(countConst).getDoubleValue() > 0);
            break;
          default:
            break;
        }
      }

    } catch (InterruptedException e) {
      log.error("Failed to get CloudProvider overview data. {}", e);
      Thread.currentThread().interrupt();
    } catch (Exception ex) {
      log.error("Exception while executing query", ex);
    }
  }

  @Override
  public Map<String, VMInstanceBillingData> getAzureVMBillingData(
      List<String> resourceIds, Instant startTime, Instant endTime, String dataSetId) {
    String query = BQConst.AZURE_VM_BILLING_QUERY;
    String resourceId = String.join("','", resourceIds);
    String projectTableName = getAzureProjectTableName(dataSetId);
    String formattedQuery = format(query, projectTableName, resourceId, startTime, endTime);
    return query(formattedQuery, "AZURE", null);
  }

  @Override
  public Map<String, VMInstanceBillingData> getGcpVMBillingData(
      List<String> resourceIds, Instant startTime, Instant endTime, String dataSetId) {
    String resourceIdSubQuery = createSubQuery(resourceIds);
    String query = BQConst.GCP_VM_BILLING_QUERY;
    String projectTableName = getGcpProjectTableName(dataSetId);
    String formattedQuery =
        format(query, GCP_PRODUCT_FAMILY_CASE_CONDITION, projectTableName, startTime.minus(1, ChronoUnit.DAYS),
            endTime.plus(7, ChronoUnit.DAYS), resourceIdSubQuery, startTime, endTime, GCP_DESCRIPTION_CONDITION);
    return query(formattedQuery, "GCP", null);
  }

  private static String createSubQuery(List<String> resourceIds) {
    StringBuffer sb = new StringBuffer(100);
    sb.append(" (");
    for (int i = 0; i < resourceIds.size(); i++) {
      sb.append("resource.name like '%" + resourceIds.get(i) + "%' ");
      if (i < resourceIds.size() - 1) {
        sb.append("OR ");
      }
    }
    sb.append(") ");
    return sb.toString();
  }

  private String getGcpProjectTableName(String dataSetId) {
    BillingDataPipelineConfig billingDataPipelineConfig = mainConfig.getBillingDataPipelineConfig();
    return format("%s.%s.%s", billingDataPipelineConfig.getGcpProjectId(), dataSetId, GCP_TABLE_NAME_WITH_WILDCARD);
  }

  public FieldList getFieldList(TableResult result) {
    Schema schema = result.getSchema();
    return schema.getFields();
  }

  public Iterable<FieldValueList> getFieldValueLists(TableResult result) {
    return result.iterateAll();
  }

  private List<VMInstanceServiceBillingData> convertToAwsInstanceServiceBillingData(TableResult result) {
    FieldList fields = getFieldList(result);
    List<VMInstanceServiceBillingData> instanceServiceBillingDataList = new ArrayList<>();
    Iterable<FieldValueList> fieldValueLists = getFieldValueLists(result);
    for (FieldValueList row : fieldValueLists) {
      VMInstanceServiceBillingDataBuilder dataBuilder = VMInstanceServiceBillingData.builder();
      for (Field field : fields) {
        switch (field.getName()) {
          case BQConst.resourceId:
            dataBuilder.resourceId(getIdFromArn(fetchStringValue(row, field)));
            break;
          case BQConst.serviceCode:
            dataBuilder.serviceCode(fetchStringValue(row, field));
            break;
          case BQConst.usageType:
            dataBuilder.usageType(fetchStringValue(row, field));
            break;
          case BQConst.productFamily:
            dataBuilder.productFamily(fetchStringValue(row, field));
            break;
          case BQConst.cost:
            dataBuilder.cost(getNumericValue(row, field));
            break;
          case BQConst.effectiveCost:
            dataBuilder.effectiveCost(getDoubleValue(row, field));
            break;
          case BQConst.amortisedCost:
            dataBuilder.amortisedCost(getDoubleValue(row, field));
            break;
          case BQConst.netAmortisedCost:
            dataBuilder.netAmortisedCost(getDoubleValue(row, field));
            break;
          default:
            break;
        }
      }
      instanceServiceBillingDataList.add(dataBuilder.build());
    }
    log.info("Resource Id data {} ", instanceServiceBillingDataList);
    return instanceServiceBillingDataList;
  }

  private String getIdFromArn(String arn) {
    return arn.substring(arn.lastIndexOf('/') + 1);
  }

  private Map<String, VMInstanceBillingData> convertToAwsInstanceBillingData(TableResult result, String accountId) {
    List<VMInstanceServiceBillingData> vmInstanceServiceBillingDataList =
        convertToAwsInstanceServiceBillingData(result);
    Map<String, VMInstanceBillingData> vmInstanceBillingDataMap = new HashMap<>();
    vmInstanceServiceBillingDataList.forEach(vmInstanceServiceBillingData -> {
      String resourceId = vmInstanceServiceBillingData.getResourceId();
      VMInstanceBillingData vmInstanceBillingData = VMInstanceBillingData.builder().resourceId(resourceId).build();
      if (vmInstanceBillingDataMap.containsKey(resourceId)) {
        vmInstanceBillingData = vmInstanceBillingDataMap.get(resourceId);
      }

      if (BQConst.networkProductFamily.equals(vmInstanceServiceBillingData.getProductFamily())) {
        vmInstanceBillingData =
            vmInstanceBillingData.toBuilder().networkCost(vmInstanceServiceBillingData.getCost()).build();
      }

      if (BQConst.computeProductFamily.equals(vmInstanceServiceBillingData.getProductFamily())
          || vmInstanceServiceBillingData.getProductFamily() == null) {
        double cost = vmInstanceServiceBillingData.getCost();

        boolean netAmortisedCostCalculationEnabled =
            featureFlagService.isEnabled(FeatureName.CE_NET_AMORTISED_COST_ENABLED, accountId);

        if ((netAmortisedCostCalculationEnabled || CONTENT_STACK_ACCOUNTID.equals(accountId))
            && null != vmInstanceServiceBillingData.getNetAmortisedCost()) {
          cost = vmInstanceServiceBillingData.getNetAmortisedCost();
          log.info("accountId: {} - net amortisedCost: {}", accountId, cost);
        } else if (null != vmInstanceServiceBillingData.getEffectiveCost()) {
          cost = vmInstanceServiceBillingData.getEffectiveCost();
          log.info("netAmortisedCostCalculationEnabled: {}, accountId: {} - effectiveCost: {}",
              netAmortisedCostCalculationEnabled, accountId, cost);
        }
        vmInstanceBillingData = vmInstanceBillingData.toBuilder().computeCost(cost).build();
      }

      vmInstanceBillingDataMap.put(resourceId, vmInstanceBillingData);
    });

    log.debug("AWS: resource map data {} ", vmInstanceBillingDataMap);
    return vmInstanceBillingDataMap;
  }

  private Map<String, VMInstanceBillingData> convertToEksFargateInstanceBillingData(TableResult result) {
    List<VMInstanceServiceBillingData> vmInstanceServiceBillingDataList =
        convertToAwsInstanceServiceBillingData(result);
    Map<String, VMInstanceBillingData> vmInstanceBillingDataMap = new HashMap<>();
    vmInstanceServiceBillingDataList.forEach(vmInstanceServiceBillingData -> {
      String resourceId = vmInstanceServiceBillingData.getResourceId();
      VMInstanceBillingData vmInstanceBillingData = VMInstanceBillingData.builder().resourceId(resourceId).build();
      double cost = vmInstanceServiceBillingData.getCost();
      if (vmInstanceBillingDataMap.containsKey(resourceId)) {
        vmInstanceBillingData = vmInstanceBillingDataMap.get(resourceId);
      }
      if (null != vmInstanceServiceBillingData.getUsageType()) {
        if (vmInstanceServiceBillingData.getUsageType().contains(BQConst.eksNetworkInstanceType)) {
          vmInstanceBillingData = vmInstanceBillingData.toBuilder().networkCost(cost).build();
        }
        if (vmInstanceServiceBillingData.getUsageType().contains(BQConst.eksCpuInstanceType)) {
          double memoryCost = vmInstanceBillingData.getMemoryCost();
          vmInstanceBillingData =
              vmInstanceBillingData.toBuilder().computeCost(memoryCost + cost).cpuCost(cost).build();
        }
        if (vmInstanceServiceBillingData.getUsageType().contains(BQConst.eksMemoryInstanceType)) {
          double cpuCost = vmInstanceBillingData.getCpuCost();
          vmInstanceBillingData =
              vmInstanceBillingData.toBuilder().computeCost(cpuCost + cost).memoryCost(cost).build();
        }
      }
      vmInstanceBillingDataMap.put(resourceId, vmInstanceBillingData);
    });

    log.debug("EKS Fargate resource map data {} ", vmInstanceBillingDataMap);
    return vmInstanceBillingDataMap;
  }

  private String fetchStringValue(FieldValueList row, Field field) {
    Object value = row.get(field.getName()).getValue();
    if (value != null) {
      return value.toString();
    }
    return null;
  }

  private double getNumericValue(FieldValueList row, Field field) {
    FieldValue value = row.get(field.getName());
    if (!value.isNull()) {
      return value.getNumericValue().doubleValue();
    }
    return 0;
  }

  private Double getDoubleValue(FieldValueList row, Field field) {
    FieldValue value = row.get(field.getName());
    if (!value.isNull()) {
      return value.getNumericValue().doubleValue();
    }
    return null;
  }

  public BigQuery getBigQueryService() {
    ServiceAccountCredentials credentials = getCredentials(GOOGLE_CREDENTIALS_PATH);
    return BigQueryOptions.newBuilder().setCredentials(credentials).build().getService();
  }
}
