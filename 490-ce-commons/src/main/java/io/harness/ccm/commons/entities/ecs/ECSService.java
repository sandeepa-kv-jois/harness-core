package io.harness.ccm.commons.entities.ecs;

import com.google.common.collect.ImmutableList;
import io.harness.annotation.StoreIn;
import io.harness.ccm.commons.beans.Resource;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;

import java.util.List;
import java.util.Map;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity(value = "ecsService", noClassnameStored = true)
@FieldNameConstants(innerTypeName = "ECSServiceKeys")
@StoreIn(DbAliases.CENG)
public final class ECSService implements PersistentEntity, UuidAware, CreatedAtAware, UpdatedAtAware, AccountAccess {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
            .name("no_dup")
            .unique(true)
            .field(ECSServiceKeys.accountId)
            .field(ECSServiceKeys.clusterId)
            .field(ECSServiceKeys.serviceArn)
            .field(ECSServiceKeys.serviceName)
            .build())
        .build();
  }
  @Id
  String uuid;
  String accountId;
  String clusterId;
  String serviceArn;
  String serviceName;
  Resource resource;
  Map<String, String> labels;
  long createdAt;
  long lastUpdatedAt;
}
