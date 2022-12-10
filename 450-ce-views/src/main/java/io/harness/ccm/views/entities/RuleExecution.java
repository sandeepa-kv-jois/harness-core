/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.views.entities;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.StoreIn;
import io.harness.ccm.views.helper.RuleCloudProviderType;
import io.harness.ccm.views.helper.RuleExecutionStatusType;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.mongo.index.SortCompoundMongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UuidAware;

import com.google.common.collect.ImmutableList;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;

@Data
@Builder
@StoreIn(DbAliases.CENG)
@FieldNameConstants(innerTypeName = "RuleExecutionKeys")
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity(value = "governanceRuleExecution", noClassnameStored = true)
@Schema(description = "This object will contain the complete definition of a Cloud Cost Policy Execution")
public final class RuleExecution implements PersistentEntity, UuidAware, CreatedAtAware, UpdatedAtAware, AccountAccess {
  @Id @Schema(description = "unique id") String uuid;
  @Schema(description = "account id") String accountId;
  @Schema(description = "faktory job id") String jobId;
  @Schema(description = "ruleEnforcementIdentifier") String ruleEnforcementIdentifier;
  @Schema(description = "ruleEnforcementName") String ruleEnforcementName;
  @Schema(description = "ruleIdentifier") String ruleIdentifier;
  @Schema(description = "ruleName") String ruleName;
  @Schema(description = "rulePackIdentifier") String rulePackIdentifier;
  @Schema(description = "cloudProvider") RuleCloudProviderType cloudProvider;
  @Schema(description = "isDryRun") Boolean isDryRun;
  @Schema(description = "targetAccounts") String targetAccount;
  @Schema(description = "targetRegions") List<String> targetRegions;
  @Schema(description = "executionLogPath") String executionLogPath;
  @Schema(description = "resourceCount") long resourceCount;
  @Schema(description = "executionLogBucketType") String executionLogBucketType;
  @Schema(description = "executionStatus") RuleExecutionStatusType executionStatus;
  @Schema(description = "executionCompletedAt") Long executionCompletedAt;
  @Schema(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) String orgIdentifier;
  @Schema(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) String projectIdentifier;
  @Schema(description = NGCommonEntityConstants.CREATED_AT_MESSAGE) long createdAt;
  @Schema(description = NGCommonEntityConstants.UPDATED_AT_MESSAGE) long lastUpdatedAt;

  @EqualsAndHashCode.Exclude @FdTtlIndex Instant ttl;

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("ruleExecution")
                 .field(RuleExecutionKeys.accountId)
                 .field(RuleExecutionKeys.cloudProvider)
                 .field(RuleExecutionKeys.ruleEnforcementIdentifier)
                 .field(RuleExecutionKeys.rulePackIdentifier)
                 .field(RuleExecutionKeys.ruleIdentifier)
                 .field(RuleExecutionKeys.orgIdentifier)
                 .field(RuleExecutionKeys.projectIdentifier)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("sort1")
                 .field(RuleExecutionKeys.accountId)
                 .field(RuleExecutionKeys.cloudProvider)
                 .sortField(RuleExecutionKeys.lastUpdatedAt)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("sort2")
                 .field(RuleExecutionKeys.accountId)
                 .field(RuleExecutionKeys.cloudProvider)
                 .sortField(RuleExecutionKeys.createdAt)
                 .build())
        .build();
  }

  public RuleExecution toDTO() {
    return RuleExecution.builder()
        .uuid(getUuid())
        .accountId(getAccountId())
        .ruleEnforcementIdentifier(getRuleEnforcementIdentifier())
        .ruleIdentifier(getRuleIdentifier())
        .rulePackIdentifier(getRulePackIdentifier())
        .cloudProvider(getCloudProvider())
        .isDryRun(getIsDryRun())
        .targetAccount(getTargetAccount())
        .targetRegions(getTargetRegions())
        .executionLogPath(getExecutionLogPath())
        .resourceCount(getResourceCount())
        .executionLogBucketType(getExecutionLogBucketType())
        .executionCompletedAt(getExecutionCompletedAt())
        .orgIdentifier(getOrgIdentifier())
        .projectIdentifier(getProjectIdentifier())
        .createdAt(getCreatedAt())
        .lastUpdatedAt(getLastUpdatedAt())
        .executionStatus(getExecutionStatus())
        .ruleEnforcementName(getRuleEnforcementName())
        .ruleName(getRuleName())
        .build();
  }
}
