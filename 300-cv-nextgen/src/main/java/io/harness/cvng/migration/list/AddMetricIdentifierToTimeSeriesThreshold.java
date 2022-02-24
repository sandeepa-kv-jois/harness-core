/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.migration.list;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.cvng.core.entities.TimeSeriesThreshold;
import io.harness.cvng.migration.CVNGMigration;
import io.harness.cvng.migration.beans.ChecklistItem;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;

public class AddMetricIdentifierToTimeSeriesThreshold implements CVNGMigration {
  @Inject private HPersistence hPersistence;

  @Override
  public void migrate() {
    hPersistence.createQuery(TimeSeriesThreshold.class).asList().forEach(timeSeriesThreshold -> {
      if (isEmpty(timeSeriesThreshold.getMetricIdentifier())) {
        String identifier = timeSeriesThreshold.getMetricName().replaceAll(" ", "_");
        identifier = identifier.replaceAll("\\(", "");
        identifier = identifier.replaceAll("\\)", "");
        timeSeriesThreshold.setMetricIdentifier(identifier);
      }
      hPersistence.save(timeSeriesThreshold);
    });
  }

  @Override
  public ChecklistItem whatHappensOnRollback() {
    return ChecklistItem.NA;
  }

  @Override
  public ChecklistItem whatHappensIfOldVersionIteratorPicksMigratedEntity() {
    return ChecklistItem.NA;
  }
}
