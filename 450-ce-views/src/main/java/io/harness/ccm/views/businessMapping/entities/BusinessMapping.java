/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.views.businessMapping.entities;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EmbeddedUser;
import io.harness.ccm.views.entities.ViewFieldIdentifier;
import io.harness.ng.DbAliases;
import io.harness.persistence.AccountAccess;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.CreatedByAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UpdatedByAware;
import io.harness.persistence.UuidAware;

import java.util.List;
import javax.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import org.hibernate.validator.constraints.NotBlank;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;

@Data
@Builder
@StoreIn(DbAliases.CENG)
@FieldNameConstants(innerTypeName = "BusinessMappingKeys")
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity(value = "businessMapping", noClassnameStored = true)
@OwnedBy(CE)
public final class BusinessMapping implements PersistentEntity, UuidAware, CreatedAtAware, UpdatedAtAware,
                                              AccountAccess, CreatedByAware, UpdatedByAware {
  @Id String uuid;
  @Size(min = 1, max = 32, message = "Name must be between 1 and 32 characters long") @NotBlank String name;
  String accountId;

  List<CostTarget> costTargets;
  List<SharedCost> sharedCosts;
  UnallocatedCost unallocatedCost;
  List<ViewFieldIdentifier> dataSources;

  long createdAt;
  long lastUpdatedAt;
  private EmbeddedUser createdBy;
  private EmbeddedUser lastUpdatedBy;

  public BusinessMapping toDTO() {
    return BusinessMapping.builder()
        .uuid(getUuid())
        .name(getName())
        .accountId(getAccountId())
        .costTargets(getCostTargets())
        .sharedCosts(getSharedCosts())
        .unallocatedCost(getUnallocatedCost())
        .createdAt(getCreatedAt())
        .lastUpdatedAt(getLastUpdatedAt())
        .createdBy(getCreatedBy())
        .lastUpdatedBy(getLastUpdatedBy())
        .build();
  }
}
