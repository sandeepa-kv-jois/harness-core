/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.featureflagfilter;

import static io.harness.beans.FeatureName.CD_TRIGGER_CATALOG;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.beans.FeatureName;
import io.harness.ngtriggers.beans.entity.metadata.catalog.TriggerCatalogType;
import io.harness.utils.PmsFeatureFlagHelper;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import java.util.EnumMap;
import java.util.Set;
import java.util.function.Predicate;
import javax.ejb.Singleton;

@Singleton
public class TriggerCatalogFilter {
  @Inject private PmsFeatureFlagHelper pmsFeatureFlagHelper;

  private final EnumMap<FeatureName, Set<Enum<?>>> enumTypeFeatureFlagMap = new EnumMap<>(FeatureName.class);

  public TriggerCatalogFilter() {
    enumTypeFeatureFlagMap.put(CD_TRIGGER_CATALOG, Sets.newHashSet(TriggerCatalogType.NEXUS));
  }

  public Predicate<TriggerCatalogType> filter(String accountId, FeatureName featureName) {
    return object -> {
      Set<Enum<?>> filter = enumTypeFeatureFlagMap.get(featureName);
      if (!isEmpty(filter) && filter.contains(object)) {
        return isFeatureFlagEnabled(featureName, accountId);
      }
      return true;
    };
  }

  public boolean isFeatureFlagEnabled(FeatureName featureName, String accountId) {
    return pmsFeatureFlagHelper.isEnabled(accountId, featureName);
  }
}
