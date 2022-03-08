/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.migration;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.gitsync.common.beans.BranchSyncStatus.SYNCING;
import static io.harness.gitsync.common.beans.BranchSyncStatus.UNSYNCED;

import static org.springframework.data.mongodb.core.query.Query.query;
import static org.springframework.data.mongodb.core.query.Update.update;

import io.harness.annotations.dev.OwnedBy;
import io.harness.gitsync.common.beans.GitBranch;
import io.harness.gitsync.common.beans.GitBranch.GitBranchKeys;
import io.harness.migration.NGMigration;

import com.google.inject.Inject;
import com.mongodb.client.result.UpdateResult;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(PL)
public class UpdateStuckGitBranchesStatusToUnsyncedMigration implements NGMigration {
  private final MongoTemplate mongoTemplate;

  @Override
  public void migrate() {
    Update update = update(GitBranchKeys.branchSyncStatus, UNSYNCED);
    Criteria criteria = Criteria.where(GitBranchKeys.branchSyncStatus).is(SYNCING);
    log.info("Starting migration to change the status of stuck branches");
    try {
      UpdateResult updateResult = mongoTemplate.updateMulti(query(criteria), update, GitBranch.class);
      log.info("UpdateStuckBranches Migration completed");
      log.info("Total {} branches updated", updateResult.getModifiedCount());
    } catch (Exception ex) {
      log.error("Error occurred while migrating stuck branches", ex);
    }
  }
}
