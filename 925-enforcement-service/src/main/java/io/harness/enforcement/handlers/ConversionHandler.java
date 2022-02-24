/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.enforcement.handlers;

import io.harness.enforcement.bases.FeatureRestriction;
import io.harness.enforcement.beans.details.FeatureRestrictionDetailsDTO;
import io.harness.enforcement.beans.metadata.FeatureRestrictionMetadataDTO;
import io.harness.licensing.Edition;

public interface ConversionHandler {
  FeatureRestrictionMetadataDTO toFeatureMetadataDTO(
      FeatureRestriction feature, Edition edition, String accountIdentifier);
  FeatureRestrictionDetailsDTO toFeatureDetailsDTO(
      String accountIdentifier, FeatureRestriction feature, Edition edition);
}
