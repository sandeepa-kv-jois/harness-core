/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.enforcement.handlers.impl;

import static io.harness.enforcement.utils.EnforcementUtils.getRestriction;

import io.harness.ModuleType;
import io.harness.enforcement.bases.AvailabilityRestriction;
import io.harness.enforcement.bases.FeatureRestriction;
import io.harness.enforcement.bases.Restriction;
import io.harness.enforcement.beans.details.FeatureRestrictionDetailsDTO;
import io.harness.enforcement.beans.metadata.FeatureRestrictionMetadataDTO;
import io.harness.enforcement.beans.metadata.RestrictionMetadataDTO;
import io.harness.enforcement.constants.RestrictionType;
import io.harness.enforcement.handlers.ConversionHandler;
import io.harness.enforcement.handlers.RestrictionHandler;
import io.harness.enforcement.handlers.RestrictionHandlerFactory;
import io.harness.licensing.Edition;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Once enforcement feature flag on, feature details/metadata will be calculated accordingly
 */
@Singleton
public class ConversionHandlerImpl implements ConversionHandler {
  private final RestrictionHandlerFactory restrictionHandlerFactory;
  private static final AvailabilityRestriction DISABLED_RESTRICTION =
      new AvailabilityRestriction(RestrictionType.AVAILABILITY, false);

  @Inject
  public ConversionHandlerImpl(RestrictionHandlerFactory restrictionHandlerFactory) {
    this.restrictionHandlerFactory = restrictionHandlerFactory;
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
        feature.getRestrictions().entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,
            entry -> toRestrictionMetadataDTO(entry.getValue(), accountIdentifier, feature.getModuleType())));
    featureDetailsDTO.setRestrictionMetadata(restrictionMetadataDTOMap);
    return featureDetailsDTO;
  }

  private RestrictionMetadataDTO toRestrictionMetadataDTO(
      Restriction restriction, String accountIdentifer, ModuleType moduleType) {
    RestrictionHandler handler = restrictionHandlerFactory.getHandler(restriction.getRestrictionType());
    return handler.getMetadataDTO(restriction, accountIdentifer, moduleType);
  }

  @Override
  public FeatureRestrictionDetailsDTO toFeatureDetailsDTO(
      String accountIdentifier, FeatureRestriction feature, Edition edition) {
    Restriction restriction = getRestriction(feature, edition);
    RestrictionHandler handler = restrictionHandlerFactory.getHandler(restriction.getRestrictionType());
    FeatureRestrictionDetailsDTO featureDetailsDTO = FeatureRestrictionDetailsDTO.builder()
                                                         .name(feature.getName())
                                                         .moduleType(feature.getModuleType())
                                                         .description(feature.getDescription())
                                                         .build();
    handler.fillRestrictionDTO(feature.getName(), restriction, accountIdentifier, edition, featureDetailsDTO);
    return featureDetailsDTO;
  }
}
