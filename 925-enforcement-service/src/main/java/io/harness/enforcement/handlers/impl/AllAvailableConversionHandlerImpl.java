/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.enforcement.handlers.impl;

import io.harness.enforcement.bases.FeatureRestriction;
import io.harness.enforcement.beans.details.AvailabilityRestrictionDTO;
import io.harness.enforcement.beans.details.FeatureRestrictionDetailsDTO;
import io.harness.enforcement.beans.metadata.AvailabilityRestrictionMetadataDTO;
import io.harness.enforcement.beans.metadata.FeatureRestrictionMetadataDTO;
import io.harness.enforcement.beans.metadata.RestrictionMetadataDTO;
import io.harness.enforcement.constants.RestrictionType;
import io.harness.enforcement.handlers.ConversionHandler;
import io.harness.enforcement.handlers.RestrictionHandlerFactory;
import io.harness.licensing.Edition;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Once enforcement feature flag off, all feature details/metadata information force to be available
 */
@Singleton
public class AllAvailableConversionHandlerImpl implements ConversionHandler {
  private final RestrictionHandlerFactory restrictionHandlerFactory;
  private final AvailabilityRestrictionMetadataDTO ENABLED_RESTRICTION_METADATA;
  private final AvailabilityRestrictionDTO ENABLED_RESTRICTION;

  @Inject
  public AllAvailableConversionHandlerImpl(RestrictionHandlerFactory restrictionHandlerFactory) {
    this.restrictionHandlerFactory = restrictionHandlerFactory;

    ENABLED_RESTRICTION_METADATA = AvailabilityRestrictionMetadataDTO.builder()
                                       .restrictionType(RestrictionType.AVAILABILITY)
                                       .enabled(true)
                                       .build();
    ENABLED_RESTRICTION = AvailabilityRestrictionDTO.builder().enabled(true).build();
  }
  @Override
  public FeatureRestrictionMetadataDTO toFeatureMetadataDTO(
      FeatureRestriction feature, Edition edition, String accountIdentifier) {
    FeatureRestrictionMetadataDTO featureDetailsDTO = FeatureRestrictionMetadataDTO.builder()
                                                          .name(feature.getName())
                                                          .moduleType(feature.getModuleType())
                                                          .edition(edition)
                                                          .build();

    Map<Edition, RestrictionMetadataDTO> restrictionMetadataDTOMap =
        feature.getRestrictions().entrySet().stream().collect(
            Collectors.toMap(Map.Entry::getKey, entry -> ENABLED_RESTRICTION_METADATA));
    featureDetailsDTO.setRestrictionMetadata(restrictionMetadataDTOMap);
    return featureDetailsDTO;
  }

  @Override
  public FeatureRestrictionDetailsDTO toFeatureDetailsDTO(
      String accountIdentifier, FeatureRestriction feature, Edition edition) {
    FeatureRestrictionDetailsDTO featureDetailsDTO = FeatureRestrictionDetailsDTO.builder()
                                                         .name(feature.getName())
                                                         .moduleType(feature.getModuleType())
                                                         .description(feature.getDescription())
                                                         .build();

    featureDetailsDTO.setRestrictionType(RestrictionType.AVAILABILITY);
    featureDetailsDTO.setRestriction(ENABLED_RESTRICTION);
    featureDetailsDTO.setAllowed(true);
    return featureDetailsDTO;
  }
}
