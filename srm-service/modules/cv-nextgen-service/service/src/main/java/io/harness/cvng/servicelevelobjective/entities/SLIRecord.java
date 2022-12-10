/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.entities;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.mongo.index.CompoundMongoIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.mongo.index.MongoIndex;
import io.harness.ng.DbAliases;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UpdatedAtAware;
import io.harness.persistence.UuidAware;

import com.google.common.collect.ImmutableList;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;
import org.mongodb.morphia.annotations.Version;

@Data
@Builder(buildMethodName = "unsafeBuild")
@FieldNameConstants(innerTypeName = "SLIRecordKeys")
@FieldDefaults(level = AccessLevel.PRIVATE)
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@StoreIn(DbAliases.CVNG)
@Entity(value = "sliRecords", noClassnameStored = true)
@HarnessEntity(exportable = true)
@OwnedBy(HarnessTeam.CV)
public class SLIRecord implements PersistentEntity, UuidAware, UpdatedAtAware, CreatedAtAware {
  public static List<MongoIndex> mongoIndexes() {
    return ImmutableList.<MongoIndex>builder()
        .add(CompoundMongoIndex.builder()
                 .name("sli_timestamp")
                 .field(SLIRecordKeys.sliId)
                 .field(SLIRecordKeys.timestamp)
                 .build())
        .build();
  }
  public static class SLIRecordBuilder {
    public SLIRecord build() {
      SLIRecord sliRecord = unsafeBuild();
      sliRecord.setEpochMinute(TimeUnit.MILLISECONDS.toMinutes(timestamp.toEpochMilli()));
      return sliRecord;
    }
  }
  @Version long version;
  @Id private String uuid;
  @FdIndex private String verificationTaskId;
  private String sliId;
  private Instant timestamp; // minute
  @Setter(AccessLevel.PRIVATE) private long epochMinute;
  private SLIState sliState;
  private long runningBadCount; // prevMinuteRecord.runningBadCount + sliState == BAD ? 1 : 0
  private long runningGoodCount; // // prevMinuteRecord.runningGoodCount + sliState == GOOD ? 1 : 0
  private long lastUpdatedAt;
  private long createdAt;
  private int sliVersion;
  public enum SLIState { NO_DATA, GOOD, BAD }
  @Builder.Default @FdTtlIndex private Date validUntil = Date.from(OffsetDateTime.now().plusDays(180).toInstant());
  @Data
  @Builder
  public static class SLIRecordParam {
    private SLIState sliState;
    private Instant timeStamp;
  }
}
