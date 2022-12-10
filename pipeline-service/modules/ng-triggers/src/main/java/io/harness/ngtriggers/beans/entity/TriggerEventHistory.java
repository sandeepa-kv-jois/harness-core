/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.beans.entity;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.mongo.index.SortCompoundMongoIndex;
import io.harness.ng.DbAliases;
import io.harness.ngtriggers.beans.response.TargetExecutionSummary;
import io.harness.persistence.PersistentEntity;

import com.google.common.collect.ImmutableList;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.List;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.mongodb.morphia.annotations.Entity;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@FieldNameConstants(innerTypeName = "TriggerEventHistoryKeys")
@StoreIn(DbAliases.PMS)
@Entity(value = "triggerEventHistory", noClassnameStored = true)
@Document("triggerEventHistory")
@TypeAlias("triggerEventHistory")
@HarnessEntity(exportable = true)
@OwnedBy(PIPELINE)
public class TriggerEventHistory implements PersistentEntity {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(SortCompoundMongoIndex.builder()
                 .name("las")
                 .field(TriggerEventHistoryKeys.accountId)
                 .field(TriggerEventHistoryKeys.orgIdentifier)
                 .field(TriggerEventHistoryKeys.projectIdentifier)
                 .field(TriggerEventHistoryKeys.triggerIdentifier)
                 .field(TriggerEventHistoryKeys.targetIdentifier)
                 .descSortField(TriggerEventHistoryKeys.createdAt)
                 .build())
        .add(SortCompoundMongoIndex.builder()
                 .name("accountId_createdAt_desc")
                 .field(TriggerEventHistoryKeys.accountId)
                 .descSortField(TriggerEventHistoryKeys.createdAt)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("accountId_eventCorrelationId")
                 .field(TriggerEventHistoryKeys.accountId)
                 .field(TriggerEventHistoryKeys.eventCorrelationId)
                 .build())
        .build();
  }

  @Id @org.mongodb.morphia.annotations.Id String uuid;
  String accountId;
  String orgIdentifier;
  String projectIdentifier;
  String targetIdentifier;
  String eventCorrelationId;
  String payload;
  Long eventCreatedAt;
  String finalStatus;
  String message;
  String planExecutionId;
  boolean exceptionOccurred;
  String triggerIdentifier;
  @FdTtlIndex @Default Date validUntil = Date.from(OffsetDateTime.now().plusDays(7).toInstant());
  TargetExecutionSummary targetExecutionSummary;

  @CreatedDate Long createdAt;
}
