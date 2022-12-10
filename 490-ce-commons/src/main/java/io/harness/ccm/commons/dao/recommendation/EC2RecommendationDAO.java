/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.commons.dao.recommendation;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.ccm.commons.constants.ViewFieldConstants.THRESHOLD_DAYS;
import static io.harness.ccm.commons.utils.TimeUtils.offsetDateTimeNow;
import static io.harness.ccm.commons.utils.TimeUtils.toOffsetDateTime;
import static io.harness.persistence.HPersistence.upsertReturnNewOptions;
import static io.harness.persistence.HQuery.excludeValidate;
import static io.harness.timescaledb.Tables.CE_RECOMMENDATIONS;
import static io.harness.timescaledb.Tables.UTILIZATION_DATA;

import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.retry.RetryOnException;
import io.harness.ccm.commons.beans.recommendation.EC2InstanceUtilizationData;
import io.harness.ccm.commons.beans.recommendation.ResourceType;
import io.harness.ccm.commons.entities.ec2.recommendation.EC2Recommendation;
import io.harness.ccm.commons.entities.ec2.recommendation.EC2Recommendation.EC2RecommendationKeys;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.jooq.DSLContext;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.UpdateOperations;

/**
 * Data access class to fetch the ec2 instance related details like recommendations
 * and utilization data from data stores.
 */
@Slf4j
@Singleton
@OwnedBy(CE)
public class EC2RecommendationDAO {
  private static final int RETRY_COUNT = 3;
  private static final int SLEEP_DURATION = 100;
  private static final int UTIL_DAYS = 30;

  @Inject private HPersistence hPersistence;
  @Inject private DSLContext dslContext;

  /**
   * This method fetches the recommendation from mongo table EC2Recommendation.
   * @param accountIdentifier
   * @param id
   * @return
   */
  @NonNull
  public Optional<EC2Recommendation> fetchEC2RecommendationById(@NonNull String accountIdentifier, @NonNull String id) {
    return Optional.ofNullable(hPersistence.createQuery(EC2Recommendation.class, excludeValidate)
                                   .filter(EC2RecommendationKeys.accountId, accountIdentifier)
                                   .filter(EC2RecommendationKeys.uuid, new ObjectId(id))
                                   .get());
  }

  /**
   * This method saves the recommendation to the mongo table EC2Recommendation.
   * @param ec2Recommendation
   * @return
   */
  @NonNull
  public EC2Recommendation saveRecommendation(EC2Recommendation ec2Recommendation) {
    Query<EC2Recommendation> query = hPersistence.createQuery(EC2Recommendation.class)
                                         .field(EC2RecommendationKeys.accountId)
                                         .equal(ec2Recommendation.getAccountId())
                                         .field(EC2RecommendationKeys.awsAccountId)
                                         .equal(ec2Recommendation.getAwsAccountId())
                                         .field(EC2RecommendationKeys.instanceId)
                                         .equal(ec2Recommendation.getInstanceId());
    UpdateOperations<EC2Recommendation> updateOperations =
        hPersistence.createUpdateOperations(EC2Recommendation.class)
            .set(EC2RecommendationKeys.accountId, ec2Recommendation.getAccountId())
            .set(EC2RecommendationKeys.awsAccountId, ec2Recommendation.getAwsAccountId())
            .set(EC2RecommendationKeys.instanceId, ec2Recommendation.getInstanceId())
            .set(EC2RecommendationKeys.vcpu, ec2Recommendation.getVcpu())
            .set(EC2RecommendationKeys.instanceName, ec2Recommendation.getInstanceName())
            .set(EC2RecommendationKeys.instanceType, ec2Recommendation.getInstanceType())
            .set(EC2RecommendationKeys.platform, ec2Recommendation.getPlatform())
            .set(EC2RecommendationKeys.region, ec2Recommendation.getRegion())
            .set(EC2RecommendationKeys.memory, ec2Recommendation.getMemory())
            .set(EC2RecommendationKeys.sku, ec2Recommendation.getSku())
            .set(EC2RecommendationKeys.currentMaxCPU, ec2Recommendation.getCurrentMaxCPU())
            .set(EC2RecommendationKeys.currentMaxMemory, ec2Recommendation.getCurrentMaxMemory())
            .set(EC2RecommendationKeys.currentMonthlyCost, ec2Recommendation.getCurrentMonthlyCost())
            .set(EC2RecommendationKeys.currencyCode, ec2Recommendation.getCurrencyCode())
            .set(EC2RecommendationKeys.recommendationInfo, ec2Recommendation.getRecommendationInfo())
            .set(EC2RecommendationKeys.expectedSaving, ec2Recommendation.getExpectedSaving())
            .set(EC2RecommendationKeys.rightsizingType, ec2Recommendation.getRightsizingType())
            .set(EC2RecommendationKeys.lastUpdatedTime, ec2Recommendation.getLastUpdatedTime());

    return hPersistence.upsert(query, updateOperations, upsertReturnNewOptions);
  }

  /**
   * This method saves the ec2 recommendation to the common table in timescale CE_RECOMMENDATIONS.
   * This will be used by the overview api to lise down all recommendations.
   * @param uuid
   * @param accountId
   * @param instanceId
   * @param awsAccountId
   * @param instanceName
   * @param monthlyCost
   * @param monthlySaving
   * @param lastReceivedUntilAt
   */
  @RetryOnException(retryCount = RETRY_COUNT, sleepDurationInMilliseconds = SLEEP_DURATION)
  public void upsertCeRecommendation(@NonNull String uuid, @NonNull String accountId, @NonNull String instanceId,
      @NonNull String awsAccountId, String instanceName, @Nullable Double monthlyCost, @Nullable Double monthlySaving,
      @NonNull Instant lastReceivedUntilAt) {
    dslContext.insertInto(CE_RECOMMENDATIONS)
        .set(CE_RECOMMENDATIONS.ACCOUNTID, accountId)
        .set(CE_RECOMMENDATIONS.ID, uuid)
        .set(CE_RECOMMENDATIONS.CLUSTERNAME, instanceId)
        .set(CE_RECOMMENDATIONS.NAMESPACE, awsAccountId)
        .set(CE_RECOMMENDATIONS.NAME, instanceName)
        .set(CE_RECOMMENDATIONS.RESOURCETYPE, ResourceType.EC2_INSTANCE.name())
        .set(CE_RECOMMENDATIONS.MONTHLYCOST, monthlyCost)
        .set(CE_RECOMMENDATIONS.MONTHLYSAVING, monthlySaving)
        .set(CE_RECOMMENDATIONS.ISVALID, true)
        .set(CE_RECOMMENDATIONS.LASTPROCESSEDAT,
            toOffsetDateTime(lastReceivedUntilAt.minus(THRESHOLD_DAYS - 1, ChronoUnit.DAYS)))
        .set(CE_RECOMMENDATIONS.UPDATEDAT, offsetDateTimeNow())
        .onConflictOnConstraint(CE_RECOMMENDATIONS.getPrimaryKey())
        .doUpdate()
        .set(CE_RECOMMENDATIONS.MONTHLYCOST, monthlyCost)
        .set(CE_RECOMMENDATIONS.MONTHLYSAVING, monthlySaving)
        .set(CE_RECOMMENDATIONS.LASTPROCESSEDAT,
            toOffsetDateTime(lastReceivedUntilAt.minus(THRESHOLD_DAYS - 1, ChronoUnit.DAYS)))
        .set(CE_RECOMMENDATIONS.UPDATEDAT, offsetDateTimeNow())
        .execute();
  }

  /**
   * This method fetches the util data for ec2 instance from the timescale table utilization_data.
   * @param accountId
   * @param instanceId
   * @return
   */
  @RetryOnException(retryCount = RETRY_COUNT, sleepDurationInMilliseconds = SLEEP_DURATION)
  public List<EC2InstanceUtilizationData> fetchInstanceData(@NonNull String accountId, @NonNull String instanceId) {
    return dslContext.selectFrom(UTILIZATION_DATA)
        .where(UTILIZATION_DATA.ACCOUNTID.eq(accountId).and(UTILIZATION_DATA.INSTANCEID.eq(instanceId)))
        .orderBy(UTILIZATION_DATA.STARTTIME.desc().nullsLast())
        .offset(0)
        .limit(UTIL_DAYS)
        .fetchInto(EC2InstanceUtilizationData.class);
  }
}
