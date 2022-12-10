/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.polling.bean;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UuidAware;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.mongodb.morphia.annotations.Entity;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@FieldNameConstants(innerTypeName = "PollingDocumentKeys")
@StoreIn(DbAliases.NG_MANAGER)
@Entity(value = "pollingDocuments", noClassnameStored = true)
@Document("pollingDocuments")
@HarnessEntity(exportable = true)
@OwnedBy(HarnessTeam.CDC)
public class PollingDocument implements PersistentEntity, AccountAccess, UuidAware {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("accountId_organizationIdentifier_projectIdentifier_pollingType_pollingItem")
                 .field(PollingDocumentKeys.accountId)
                 .field(PollingDocumentKeys.orgIdentifier)
                 .field(PollingDocumentKeys.projectIdentifier)
                 .field(PollingDocumentKeys.pollingType)
                 .field(PollingDocumentKeys.pollingInfo)
                 .field(PollingDocumentKeys.signatures)
                 .build())
        .build();
  }

  @Id @org.mongodb.morphia.annotations.Id private String uuid;

  @NotNull private String accountId;
  private String orgIdentifier;
  private String projectIdentifier;
  @NotNull private List<String> signatures;

  @JsonProperty("type") private PollingType pollingType;

  private PollingInfo pollingInfo;

  private PolledResponse polledResponse;

  private String perpetualTaskId;

  private int failedAttempts;

  @CreatedDate Long createdAt;
  @LastModifiedDate Long lastModifiedAt;
}
