/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.acl.persistence;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.ng.DbAliases.ACCESS_CONTROL;

import io.harness.accesscontrol.acl.persistence.SourceMetadata.SourceMetadataKeys;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.persistence.PersistentEntity;

import com.google.common.collect.ImmutableList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import org.mongodb.morphia.annotations.Entity;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

@OwnedBy(PL)
@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@FieldNameConstants(innerTypeName = "ACLKeys")
@StoreIn(ACCESS_CONTROL)
@Document(ACL.PRIMARY_COLLECTION)
@Entity(value = "acl", noClassnameStored = true)
@TypeAlias("acl")
public class ACL implements PersistentEntity {
  private static final String DELIMITER = "$";
  public static final String ROLE_ASSIGNMENT_IDENTIFIER_KEY =
      ACLKeys.sourceMetadata + "." + SourceMetadataKeys.roleAssignmentIdentifier;
  public static final String USER_GROUP_IDENTIFIER_KEY =
      ACLKeys.sourceMetadata + "." + SourceMetadataKeys.userGroupIdentifier;
  public static final String ROLE_IDENTIFIER_KEY = ACLKeys.sourceMetadata + "." + SourceMetadataKeys.roleIdentifier;
  public static final String RESOURCE_GROUP_IDENTIFIER_KEY =
      ACLKeys.sourceMetadata + "." + SourceMetadataKeys.resourceGroupIdentifier;
  public static final String IMPLICITLY_CREATED_FOR_SCOPE_ACCESS_KEY =
      ACLKeys.sourceMetadata + "." + SourceMetadataKeys.implicitlyCreatedForScopeAccess;
  public static final String PRIMARY_COLLECTION = "acl";
  public static final String SECONDARY_COLLECTION = "acl_secondary";

  @Id @org.mongodb.morphia.annotations.Id private String id;
  @CreatedDate Long createdAt;
  @LastModifiedDate Long lastModifiedAt;

  String roleAssignmentId;
  String scopeIdentifier;
  String permissionIdentifier;
  SourceMetadata sourceMetadata;
  String resourceSelector;
  String principalType;
  String principalIdentifier;
  String aclQueryString;
  @FdIndex @Getter(value = AccessLevel.NONE) Boolean conditional;
  String condition;
  @Getter(value = AccessLevel.NONE) private Boolean enabled;

  public boolean isEnabled() {
    return Boolean.TRUE.equals(enabled);
  }

  public boolean isConditional() {
    return Boolean.TRUE.equals(conditional);
  }

  public boolean isImplicit() {
    return sourceMetadata.getImplicitlyCreatedForScopeAccess();
  }

  public static String getAclQueryString(String scopeIdentifier, String resourceSelector, String principalType,
      String principalIdentifier, String permissionIdentifier) {
    return scopeIdentifier + DELIMITER + permissionIdentifier + DELIMITER + resourceSelector + DELIMITER + principalType
        + DELIMITER + principalIdentifier;
  }

  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("uniqueIdxV2")
                 .field(ACLKeys.scopeIdentifier)
                 .field(ROLE_ASSIGNMENT_IDENTIFIER_KEY)
                 .field(USER_GROUP_IDENTIFIER_KEY)
                 .field(ROLE_IDENTIFIER_KEY)
                 .field(RESOURCE_GROUP_IDENTIFIER_KEY)
                 .field(ACLKeys.resourceSelector)
                 .field(ACLKeys.permissionIdentifier)
                 .field(ACLKeys.principalIdentifier)
                 .field(ACLKeys.principalType)
                 .field(ACLKeys.aclQueryString)
                 .field(ACLKeys.condition)
                 .field(ACLKeys.conditional)
                 .field(IMPLICITLY_CREATED_FOR_SCOPE_ACCESS_KEY)
                 .unique(true)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("roleAssignmentIdResourceSelectorIdx")
                 .field(ACLKeys.roleAssignmentId)
                 .field(ACLKeys.resourceSelector)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("roleAssignmentIdPermissionIdx")
                 .field(ACLKeys.roleAssignmentId)
                 .field(ACLKeys.permissionIdentifier)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("roleAssignmentIdPrincipalIdx")
                 .field(ACLKeys.roleAssignmentId)
                 .field(ACLKeys.principalIdentifier)
                 .build())
        .add(CompoundMongoIndex.builder()
                 .name("aclQueryStringEnabledIdx")
                 .field(ACLKeys.aclQueryString)
                 .field(ACLKeys.enabled)
                 .build())
        .build();
  }
}
