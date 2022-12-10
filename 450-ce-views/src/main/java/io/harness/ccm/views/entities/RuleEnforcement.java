/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.views.entities;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.StoreIn;
import io.harness.beans.EmbeddedUser;
import io.harness.ccm.views.helper.RuleCloudProviderType;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.mongo.index.SortCompoundMongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.CreatedByAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UpdatedByAware;
import io.harness.persistence.UuidAware;

import com.google.common.collect.ImmutableList;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;

@Data
@Builder
@StoreIn(DbAliases.CENG)
@FieldNameConstants(innerTypeName = "RuleEnforcementId")
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity(value = "governanceRuleEnforcement", noClassnameStored = true)
@Schema(description = "This object will contain the complete definition of a Cloud Cost Policy enforcement")

public final class RuleEnforcement implements PersistentEntity, UuidAware, CreatedAtAware, UpdatedAtAware,
                                              AccountAccess, CreatedByAware, UpdatedByAware {
  @Id @Schema(description = "unique id") String uuid;
  @Schema(description = "account id") String accountId;
  @Schema(description = "name") String name;
  @Schema(description = NGCommonEntityConstants.DESCRIPTION) String description;
  @Schema(description = NGCommonEntityConstants.TAGS) List<String> tags;
  @Schema(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) String orgIdentifier;
  @Schema(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) String projectIdentifier;
  @Schema(description = "cloudProvider") RuleCloudProviderType cloudProvider;
  @Schema(description = "rulesIds") List<String> ruleIds;
  @Schema(description = "ruleSetIDs") List<String> ruleSetIDs;
  @Schema(description = "executionSchedule") String executionSchedule;
  @Schema(description = "executionTimezone") String executionTimezone;
  @Schema(description = "targetAccounts") List<String> targetAccounts;
  @Schema(description = "targetRegions") List<String> targetRegions;
  @Schema(description = "isDryRun") Boolean isDryRun;
  @Schema(description = "deleted") Boolean deleted;
  @Schema(description = "isEnabled") Boolean isEnabled;
  @Schema(description = NGCommonEntityConstants.CREATED_AT_MESSAGE) long createdAt;
  @Schema(description = NGCommonEntityConstants.UPDATED_AT_MESSAGE) long lastUpdatedAt;
  @Schema(description = "created by") private EmbeddedUser createdBy;
  @Schema(description = "updated by") private EmbeddedUser lastUpdatedBy;

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("ruleEnforcement")
                 .field(RuleEnforcementId.name)
                 .field(RuleEnforcementId.accountId)
                 .field(RuleEnforcementId.cloudProvider)
                 .field(RuleEnforcementId.orgIdentifier)
                 .field(RuleEnforcementId.projectIdentifier)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("sort1")
                 .field(RuleEnforcementId.name)
                 .field(RuleEnforcementId.accountId)
                 .sortField(RuleEnforcementId.lastUpdatedAt)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("sort2")
                 .field(RuleEnforcementId.name)
                 .field(RuleEnforcementId.accountId)
                 .sortField(RuleEnforcementId.createdAt)
                 .build())
        .build();
  }
  public RuleEnforcement toDTO() {
    return RuleEnforcement.builder()
        .uuid(getUuid())
        .accountId(getAccountId())
        .name(getName())
        .description(getDescription())
        .tags(getTags())
        .orgIdentifier(getOrgIdentifier())
        .projectIdentifier(getProjectIdentifier())
        .cloudProvider(getCloudProvider())
        .executionSchedule(getExecutionSchedule())
        .executionTimezone(getExecutionTimezone())
        .ruleIds(getRuleIds())
        .ruleSetIDs(getRuleSetIDs())
        .targetAccounts(getTargetAccounts())
        .targetRegions(getTargetRegions())
        .isDryRun(getIsDryRun())
        .isEnabled(getIsEnabled())
        .deleted(getDeleted())
        .createdAt(getCreatedAt())
        .lastUpdatedAt(getLastUpdatedAt())
        .createdBy(getCreatedBy())
        .lastUpdatedBy(getLastUpdatedBy())
        .build();
  }
}
