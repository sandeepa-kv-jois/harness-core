/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.migration.list;

import io.harness.cvng.analysis.beans.Risk;
import io.harness.cvng.analysis.entities.DeploymentTimeSeriesAnalysis;
import io.harness.cvng.migration.CVNGMigration;
import io.harness.cvng.migration.beans.ChecklistItem;
import io.harness.persistence.HPersistence;
import io.harness.persistence.PersistentEntity;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.UpdateOperations;
import org.mongodb.morphia.query.UpdateResults;

@Slf4j
public class UpdateRiskIntToRiskEnum implements CVNGMigration {
  @Inject private HPersistence hPersistence;
  @Override
  public void migrate() {
    update(DeploymentTimeSeriesAnalysis.class);
  }

  private <T extends PersistentEntity> void update(Class<T> entityClazz) {
    for (Risk risk : Risk.values()) {
      UpdateOperations<T> updateOperations = hPersistence.createUpdateOperations(entityClazz);
      updateOperations.set("risk", risk);
      Query<T> query = hPersistence.createQuery(entityClazz).filter("risk", risk.getValue());
      log.info("updating risk {} for class {}", risk, entityClazz);
      UpdateResults updateResults = hPersistence.update(query, updateOperations);
      log.info("Updated count: {}", updateResults.getUpdatedCount());
    }
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
