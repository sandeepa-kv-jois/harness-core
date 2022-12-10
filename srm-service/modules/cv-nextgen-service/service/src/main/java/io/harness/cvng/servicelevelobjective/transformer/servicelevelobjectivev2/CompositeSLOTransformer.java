/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.transformer.servicelevelobjectivev2;

import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.notification.beans.NotificationRuleType;
import io.harness.cvng.notification.services.api.NotificationRuleService;
import io.harness.cvng.servicelevelobjective.beans.SLOTargetDTO;
import io.harness.cvng.servicelevelobjective.beans.SLOTargetType;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveDTO;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveType;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveV2DTO;
import io.harness.cvng.servicelevelobjective.beans.slospec.CompositeServiceLevelObjectiveSpec;
import io.harness.cvng.servicelevelobjective.entities.CompositeServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.transformer.ServiceLevelObjectiveDetailsTransformer;
import io.harness.cvng.servicelevelobjective.transformer.servicelevelindicator.SLOTargetTransformer;
import io.harness.ng.core.mapper.TagMapper;

import com.google.inject.Inject;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

public class CompositeSLOTransformer implements SLOV2Transformer<CompositeServiceLevelObjective> {
  @Inject NotificationRuleService notificationRuleService;

  @Inject ServiceLevelObjectiveDetailsTransformer serviceLevelObjectiveDetailsTransformer;

  @Inject private Map<SLOTargetType, SLOTargetTransformer> sloTargetTypeSLOTargetTransformerMap;

  @Override
  public CompositeServiceLevelObjective getSLOV2(
      ProjectParams projectParams, ServiceLevelObjectiveV2DTO serviceLevelObjectiveV2DTO, Boolean isEnabled) {
    CompositeServiceLevelObjectiveSpec compositeServiceLevelObjectiveSpec =
        (CompositeServiceLevelObjectiveSpec) serviceLevelObjectiveV2DTO.getSpec();
    return CompositeServiceLevelObjective.builder()
        .type(ServiceLevelObjectiveType.COMPOSITE)
        .accountId(projectParams.getAccountIdentifier())
        .orgIdentifier(projectParams.getOrgIdentifier())
        .projectIdentifier(projectParams.getProjectIdentifier())
        .identifier(serviceLevelObjectiveV2DTO.getIdentifier())
        .name(serviceLevelObjectiveV2DTO.getName())
        .desc(serviceLevelObjectiveV2DTO.getDescription())
        .tags(TagMapper.convertToList(serviceLevelObjectiveV2DTO.getTags()))
        .userJourneyIdentifiers(serviceLevelObjectiveV2DTO.getUserJourneyRefs())
        .notificationRuleRefs(notificationRuleService.getNotificationRuleRefs(projectParams,
            serviceLevelObjectiveV2DTO.getNotificationRuleRefs(), NotificationRuleType.SLO, Instant.ofEpochSecond(0)))
        .sloTarget(sloTargetTypeSLOTargetTransformerMap.get(serviceLevelObjectiveV2DTO.getSloTarget().getType())
                       .getSLOTarget(serviceLevelObjectiveV2DTO.getSloTarget().getSpec()))
        .serviceLevelObjectivesDetails(
            compositeServiceLevelObjectiveSpec.getServiceLevelObjectivesDetails()
                .stream()
                .map(serviceLevelObjectiveDetailsDTO
                    -> serviceLevelObjectiveDetailsTransformer.getServiceLevelObjectiveDetails(
                        serviceLevelObjectiveDetailsDTO))
                .collect(Collectors.toList()))
        .sloTargetPercentage(serviceLevelObjectiveV2DTO.getSloTarget().getSloTargetPercentage())
        .startedAt(System.currentTimeMillis())
        .version(0)
        .enabled(isEnabled)
        .build();
  }

  @Override
  public CompositeServiceLevelObjective getSLOV2(ServiceLevelObjective serviceLevelObjective) {
    throw new RuntimeException("Can't convert SLO into composite SLO");
  }

  @Override
  public ServiceLevelObjectiveV2DTO getSLOV2DTO(CompositeServiceLevelObjective serviceLevelObjective) {
    return ServiceLevelObjectiveV2DTO.builder()
        .type(ServiceLevelObjectiveType.COMPOSITE)
        .orgIdentifier(serviceLevelObjective.getOrgIdentifier())
        .projectIdentifier(serviceLevelObjective.getProjectIdentifier())
        .identifier(serviceLevelObjective.getIdentifier())
        .name(serviceLevelObjective.getName())
        .description(serviceLevelObjective.getDesc())
        .spec(CompositeServiceLevelObjectiveSpec.builder()
                  .serviceLevelObjectivesDetails(
                      serviceLevelObjective.getServiceLevelObjectivesDetails()
                          .stream()
                          .map(serviceLevelObjectivesDetail
                              -> serviceLevelObjectiveDetailsTransformer.getServiceLevelObjectiveDetailsDTO(
                                  serviceLevelObjectivesDetail))
                          .collect(Collectors.toList()))
                  .build())
        .notificationRuleRefs(
            notificationRuleService.getNotificationRuleRefDTOs(serviceLevelObjective.getNotificationRuleRefs()))
        .sloTarget(SLOTargetDTO.builder()
                       .type(serviceLevelObjective.getSloTarget().getType())
                       .spec(sloTargetTypeSLOTargetTransformerMap.get(serviceLevelObjective.getSloTarget().getType())
                                 .getSLOTargetSpec(serviceLevelObjective.getSloTarget()))
                       .sloTargetPercentage(serviceLevelObjective.getSloTargetPercentage())
                       .build())
        .tags(TagMapper.convertToMap(serviceLevelObjective.getTags()))
        .userJourneyRefs(serviceLevelObjective.getUserJourneyIdentifiers())
        .build();
  }

  public ServiceLevelObjectiveV2DTO getSLOV2DTO(ServiceLevelObjectiveDTO serviceLevelObjectiveDTO) {
    throw new RuntimeException("Can't convert SLODTO into composite SLODTO");
  }
}
