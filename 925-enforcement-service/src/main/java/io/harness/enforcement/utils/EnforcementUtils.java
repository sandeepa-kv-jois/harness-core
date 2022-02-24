/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.enforcement.utils;

import io.harness.enforcement.bases.AvailabilityRestriction;
import io.harness.enforcement.bases.FeatureRestriction;
import io.harness.enforcement.bases.Restriction;
import io.harness.enforcement.constants.RestrictionType;
import io.harness.licensing.Edition;

import lombok.experimental.UtilityClass;

@UtilityClass
public class EnforcementUtils {
  private static final AvailabilityRestriction DISABLED_RESTRICTION =
      new AvailabilityRestriction(RestrictionType.AVAILABILITY, false);

  public static Restriction getRestriction(FeatureRestriction feature, Edition edition) {
    Restriction restriction = feature.getRestrictions().get(edition);
    if (restriction == null) {
      return DISABLED_RESTRICTION;
    }
    return restriction;
  }
}
