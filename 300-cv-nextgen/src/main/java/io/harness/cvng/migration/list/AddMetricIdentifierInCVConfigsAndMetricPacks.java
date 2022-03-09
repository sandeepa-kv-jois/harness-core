/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.migration.list;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.cvng.core.entities.MetricCVConfig;
import io.harness.cvng.core.entities.MetricPack;
import io.harness.cvng.migration.CVNGMigration;
import io.harness.cvng.migration.beans.ChecklistItem;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;

public class AddMetricIdentifierInCVConfigsAndMetricPacks implements CVNGMigration {
  @Inject private HPersistence hPersistence;

  @Override
  public void migrate() {
    hPersistence.createQuery(MetricPack.class).asList().forEach(metricPack -> {
      metricPack.getMetrics().forEach(metricDefinition -> {
        if (isEmpty(metricDefinition.getIdentifier())) {
          String identifier = metricDefinition.getName().replaceAll(" ", "_");
          identifier = identifier.replaceAll("\\(", "");
          identifier = identifier.replaceAll("\\)", "");
          metricDefinition.setIdentifier(identifier);
        }
      });
      hPersistence.save(metricPack);
    });

    hPersistence.createQuery(MetricCVConfig.class).asList().forEach(cvConfig -> {
      if (cvConfig.getMetricPack() != null && cvConfig.getMetricPack().getMetrics() != null) {
        cvConfig.getMetricPack().getMetrics().forEach(metricDefinition -> {
          if (metricDefinition != null && isEmpty(metricDefinition.getIdentifier())) {
            String identifier = metricDefinition.getName().replaceAll(" ", "_");
            identifier = identifier.replaceAll("\\(", "");
            identifier = identifier.replaceAll("\\)", "");
            metricDefinition.setIdentifier(identifier);
          }
        });
      }

      hPersistence.save(cvConfig);
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
