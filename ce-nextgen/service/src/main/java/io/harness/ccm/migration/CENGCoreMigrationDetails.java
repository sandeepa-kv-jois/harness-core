/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.migration;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.migration.MigrationDetails;
import io.harness.migration.NGMigration;
import io.harness.migration.beans.MigrationType;

import com.google.common.collect.ImmutableList;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;

@OwnedBy(HarnessTeam.CE)
public class CENGCoreMigrationDetails implements MigrationDetails {
  @Override
  public MigrationType getMigrationTypeName() {
    return MigrationType.MongoMigration;
  }

  @Override
  public boolean isBackground() {
    return false;
  }

  @Override
  public List<Pair<Integer, Class<? extends NGMigration>>> getMigrations() {
    return new ImmutableList.Builder<Pair<Integer, Class<? extends NGMigration>>>()
        .add(Pair.of(1, NoopCENGCoreMigration.class))
        .add(Pair.of(2, CEViewPreferencesMigration.class))
        .add(Pair.of(3, CEViewsFolderMigration.class))
        .add(Pair.of(4, CEViewsFolderRenameMigration.class))
        .add(Pair.of(5, CEViewPreferencesMigration.class))
        .add(Pair.of(6, CCMAdminRoleAssignmentMigration.class))
        .add(Pair.of(7, CEViewDataSourcesMigration.class))
        .add(Pair.of(8, BusinessMappingDataSourcesMigration.class))
        .add(Pair.of(9, CEMetadataRecordMigration.class))
        .add(Pair.of(10, BusinessMappingUnallocatedLabelMigration.class))
        .add(Pair.of(11, BudgetAddBreakdownMigration.class))
        .build();
  }
}
