/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.services.impl;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.cvng.beans.cvnglog.CVNGLogDTO;
import io.harness.cvng.core.beans.TimeGraphResponse;
import io.harness.cvng.core.beans.params.MonitoredServiceParams;
import io.harness.cvng.core.beans.params.PageParams;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.beans.params.logsFilterParams.SLILogsFilter;
import io.harness.cvng.core.beans.sidekick.VerificationTaskCleanupSideKickData;
import io.harness.cvng.core.entities.MonitoredService;
import io.harness.cvng.core.services.api.CVNGLogService;
import io.harness.cvng.core.services.api.SideKickService;
import io.harness.cvng.core.services.api.VerificationTaskService;
import io.harness.cvng.core.services.api.monitoredService.MonitoredServiceService;
import io.harness.cvng.events.servicelevelobjective.ServiceLevelObjectiveCreateEvent;
import io.harness.cvng.events.servicelevelobjective.ServiceLevelObjectiveDeleteEvent;
import io.harness.cvng.events.servicelevelobjective.ServiceLevelObjectiveUpdateEvent;
import io.harness.cvng.notification.beans.NotificationRuleRef;
import io.harness.cvng.notification.beans.NotificationRuleRefDTO;
import io.harness.cvng.notification.beans.NotificationRuleResponse;
import io.harness.cvng.notification.beans.NotificationRuleType;
import io.harness.cvng.notification.services.api.NotificationRuleService;
import io.harness.cvng.servicelevelobjective.SLORiskCountResponse;
import io.harness.cvng.servicelevelobjective.beans.ErrorBudgetRisk;
import io.harness.cvng.servicelevelobjective.beans.SLIMissingDataType;
import io.harness.cvng.servicelevelobjective.beans.SLODashboardApiFilter;
import io.harness.cvng.servicelevelobjective.beans.SLOTargetDTO;
import io.harness.cvng.servicelevelobjective.beans.SLOTargetType;
import io.harness.cvng.servicelevelobjective.beans.SLOValue;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelIndicatorType;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveDetailsDTO;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveDetailsRefDTO;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveFilter;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveType;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveV2DTO;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveV2Response;
import io.harness.cvng.servicelevelobjective.beans.slospec.CompositeServiceLevelObjectiveSpec;
import io.harness.cvng.servicelevelobjective.beans.slospec.SimpleServiceLevelObjectiveSpec;
import io.harness.cvng.servicelevelobjective.entities.AbstractServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.entities.AbstractServiceLevelObjective.AbstractServiceLevelObjectiveUpdatableEntity;
import io.harness.cvng.servicelevelobjective.entities.AbstractServiceLevelObjective.ServiceLevelObjectiveV2Keys;
import io.harness.cvng.servicelevelobjective.entities.CompositeSLORecord;
import io.harness.cvng.servicelevelobjective.entities.CompositeServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.entities.CompositeServiceLevelObjective.ServiceLevelObjectivesDetail;
import io.harness.cvng.servicelevelobjective.entities.SLIRecord;
import io.harness.cvng.servicelevelobjective.entities.SLOHealthIndicator;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelIndicator;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.entities.SimpleServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.entities.SimpleServiceLevelObjective.SimpleServiceLevelObjectiveKeys;
import io.harness.cvng.servicelevelobjective.entities.TimePeriod;
import io.harness.cvng.servicelevelobjective.services.api.CompositeSLOService;
import io.harness.cvng.servicelevelobjective.services.api.SLOErrorBudgetResetService;
import io.harness.cvng.servicelevelobjective.services.api.SLOHealthIndicatorService;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelIndicatorService;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelObjectiveV2Service;
import io.harness.cvng.servicelevelobjective.transformer.ServiceLevelObjectiveDetailsTransformer;
import io.harness.cvng.servicelevelobjective.transformer.servicelevelindicator.SLOTargetTransformer;
import io.harness.cvng.servicelevelobjective.transformer.servicelevelobjectivev2.SLOV2Transformer;
import io.harness.cvng.utils.ScopedInformation;
import io.harness.exception.DuplicateFieldException;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.beans.PageResponse;
import io.harness.outbox.api.OutboxService;
import io.harness.persistence.HPersistence;
import io.harness.utils.PageUtils;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.ws.rs.NotFoundException;
import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.Sort;
import org.mongodb.morphia.query.UpdateOperations;

@Slf4j
public class ServiceLevelObjectiveV2ServiceImpl implements ServiceLevelObjectiveV2Service {
  @Inject private HPersistence hPersistence;

  @Inject private MonitoredServiceService monitoredServiceService;
  @Inject private Clock clock;
  @Inject private Map<SLOTargetType, SLOTargetTransformer> sloTargetTypeSLOTargetTransformerMap;
  @Inject private Map<ServiceLevelObjectiveType, SLOV2Transformer> serviceLevelObjectiveTypeSLOV2TransformerMap;
  @Inject private NotificationRuleService notificationRuleService;
  @Inject private SLOHealthIndicatorService sloHealthIndicatorService;
  @Inject private ServiceLevelIndicatorService serviceLevelIndicatorService;
  @Inject private SLOErrorBudgetResetService sloErrorBudgetResetService;
  @Inject private VerificationTaskService verificationTaskService;
  @Inject private CVNGLogService cvngLogService;
  @Inject
  private Map<ServiceLevelObjectiveType, AbstractServiceLevelObjectiveUpdatableEntity>
      serviceLevelObjectiveTypeUpdatableEntityTransformerMap;
  @Inject private OutboxService outboxService;
  @Inject private CompositeSLOService compositeSLOService;
  @Inject private SideKickService sideKickService;
  @Inject private ServiceLevelObjectiveDetailsTransformer serviceLevelObjectiveDetailsTransformer;
  @Inject private SLIRecordServiceImpl sliRecordService;
  @Inject private CompositeSLORecordServiceImpl compositeSLORecordService;

  @Override
  public TimeGraphResponse getOnboardingGraph(CompositeServiceLevelObjectiveSpec compositeServiceLevelObjectiveSpec) {
    Instant endTime = clock.instant().truncatedTo(ChronoUnit.MINUTES);
    Instant startTime = endTime.minus(Duration.ofDays(1));
    List<ServiceLevelObjectivesDetail> serviceLevelObjectivesDetails =
        compositeServiceLevelObjectiveSpec.getServiceLevelObjectivesDetails()
            .stream()
            .map(serviceLevelObjectiveDetailsDTO
                -> serviceLevelObjectiveDetailsTransformer.getServiceLevelObjectiveDetails(
                    serviceLevelObjectiveDetailsDTO))
            .collect(Collectors.toList());
    Pair<Map<ServiceLevelObjectivesDetail, List<SLIRecord>>, Map<ServiceLevelObjectivesDetail, SLIMissingDataType>>
        sloDetailsSLIRecordsAndSLIMissingDataType = sliRecordService.getSLODetailsSLIRecordsAndSLIMissingDataType(
            serviceLevelObjectivesDetails, startTime, endTime);
    List<CompositeSLORecord> compositeSLORecords = new ArrayList<>();
    if (sloDetailsSLIRecordsAndSLIMissingDataType.getKey().size()
        == compositeServiceLevelObjectiveSpec.getServiceLevelObjectivesDetails().size()) {
      compositeSLORecords = getCompositeSLORecords(
          sloDetailsSLIRecordsAndSLIMissingDataType.getKey(), sloDetailsSLIRecordsAndSLIMissingDataType.getValue());
    }
    compositeSLORecords.sort(Comparator.comparing(CompositeSLORecord::getTimestamp));
    return TimeGraphResponse.builder()
        .startTime(startTime.toEpochMilli())
        .endTime(endTime.toEpochMilli())
        .dataPoints(compositeSLORecords.stream()
                        .map(compositeSLORecord
                            -> TimeGraphResponse.DataPoints.builder()
                                   .timeStamp(compositeSLORecord.getTimestamp().toEpochMilli())
                                   .value(SLOValue.builder()
                                              .goodCount((int) (compositeSLORecord.getRunningGoodCount()))
                                              .badCount((int) compositeSLORecord.getRunningBadCount())
                                              .build()
                                              .sloPercentage())
                                   .build())
                        .collect(Collectors.toList()))
        .build();
  }

  @Override
  public ServiceLevelObjectiveV2Response create(
      ProjectParams projectParams, ServiceLevelObjectiveV2DTO serviceLevelObjectiveDTO) {
    validateCreate(serviceLevelObjectiveDTO, projectParams);
    if (serviceLevelObjectiveDTO.getType().equals(ServiceLevelObjectiveType.SIMPLE)) {
      MonitoredService monitoredService = monitoredServiceService.getMonitoredService(
          MonitoredServiceParams.builderWithProjectParams(projectParams)
              .monitoredServiceIdentifier(
                  ((SimpleServiceLevelObjectiveSpec) serviceLevelObjectiveDTO.getSpec()).getMonitoredServiceRef())
              .build());
      SimpleServiceLevelObjectiveSpec simpleServiceLevelObjectiveSpec =
          (SimpleServiceLevelObjectiveSpec) serviceLevelObjectiveDTO.getSpec();
      List<String> serviceLevelIndicators = serviceLevelIndicatorService.create(projectParams,
          simpleServiceLevelObjectiveSpec.getServiceLevelIndicators(), serviceLevelObjectiveDTO.getIdentifier(),
          simpleServiceLevelObjectiveSpec.getMonitoredServiceRef(),
          simpleServiceLevelObjectiveSpec.getHealthSourceRef());
      simpleServiceLevelObjectiveSpec.setServiceLevelIndicators(
          serviceLevelIndicatorService.get(projectParams, serviceLevelIndicators));
      serviceLevelObjectiveDTO.setSpec(simpleServiceLevelObjectiveSpec);

      SimpleServiceLevelObjective simpleServiceLevelObjective =
          (SimpleServiceLevelObjective) saveServiceLevelObjectiveV2Entity(
              projectParams, serviceLevelObjectiveDTO, monitoredService.isEnabled());

      sloHealthIndicatorService.upsert(simpleServiceLevelObjective);
      return getSLOResponse(simpleServiceLevelObjective.getIdentifier(), projectParams);
    } else {
      CompositeServiceLevelObjective compositeServiceLevelObjective =
          (CompositeServiceLevelObjective) saveServiceLevelObjectiveV2Entity(
              projectParams, serviceLevelObjectiveDTO, true);
      sloHealthIndicatorService.upsert(compositeServiceLevelObjective);
      verificationTaskService.createCompositeSLOVerificationTask(
          compositeServiceLevelObjective.getAccountId(), compositeServiceLevelObjective.getUuid(), new HashMap<>());
      return getSLOResponse(compositeServiceLevelObjective.getIdentifier(), projectParams);
    }
  }

  @Override
  public ServiceLevelObjectiveV2Response update(
      ProjectParams projectParams, String identifier, ServiceLevelObjectiveV2DTO serviceLevelObjectiveDTO) {
    Preconditions.checkArgument(identifier.equals(serviceLevelObjectiveDTO.getIdentifier()),
        String.format("Identifier %s does not match with path identifier %s", serviceLevelObjectiveDTO.getIdentifier(),
            identifier));
    AbstractServiceLevelObjective serviceLevelObjective =
        checkIfSLOPresent(projectParams, serviceLevelObjectiveDTO.getIdentifier());
    ServiceLevelObjectiveV2DTO existingServiceLevelObjective =
        sloEntityToSLOResponse(serviceLevelObjective).getServiceLevelObjectiveV2DTO();
    List<String> serviceLevelIndicators = Collections.emptyList();
    if (serviceLevelObjectiveDTO.getType().equals(ServiceLevelObjectiveType.SIMPLE)) {
      validateSimpleSLO(serviceLevelObjectiveDTO, projectParams);
      SimpleServiceLevelObjective simpleServiceLevelObjective = (SimpleServiceLevelObjective) serviceLevelObjective;
      SimpleServiceLevelObjectiveSpec simpleServiceLevelObjectiveSpec =
          (SimpleServiceLevelObjectiveSpec) serviceLevelObjectiveDTO.getSpec();

      LocalDateTime currentLocalDate = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
      ServiceLevelObjective.SLOTarget sloTarget =
          sloTargetTypeSLOTargetTransformerMap.get(serviceLevelObjectiveDTO.getSloTarget().getType())
              .getSLOTarget(serviceLevelObjectiveDTO.getSloTarget().getSpec());
      TimePeriod timePeriod = sloTarget.getCurrentTimeRange(currentLocalDate);
      TimePeriod currentTimePeriod = serviceLevelObjective.getCurrentTimeRange(currentLocalDate);

      List<String> referencedCompositeSLOIdentifiers =
          compositeSLOService.getReferencedCompositeSLOs(projectParams, simpleServiceLevelObjective.getIdentifier())
              .stream()
              .map(CompositeServiceLevelObjective::getIdentifier)
              .collect(Collectors.toList());
      if (isNotEmpty(referencedCompositeSLOIdentifiers)
          && !sloTarget.equals(simpleServiceLevelObjective.getSloTarget())) {
        throw new InvalidRequestException(String.format(
            "Can't update the compliance time period for SLO with identifier %s, accountId %s, orgIdentifier %s, and projectIdentifier %s as it is associated with Composite SLO with identifier%s %s.",
            identifier, projectParams.getAccountIdentifier(), projectParams.getOrgIdentifier(),
            projectParams.getProjectIdentifier(), referencedCompositeSLOIdentifiers.size() > 1 ? "s" : "",
            String.join(", ", referencedCompositeSLOIdentifiers)));
      }

      serviceLevelIndicators = serviceLevelIndicatorService.update(projectParams,
          simpleServiceLevelObjectiveSpec.getServiceLevelIndicators(), serviceLevelObjectiveDTO.getIdentifier(),
          simpleServiceLevelObjective.getServiceLevelIndicators(),
          simpleServiceLevelObjectiveSpec.getMonitoredServiceRef(),
          simpleServiceLevelObjectiveSpec.getHealthSourceRef(), timePeriod, currentTimePeriod);
    } else {
      validateCompositeSLO(serviceLevelObjectiveDTO);
      CompositeServiceLevelObjective compositeServiceLevelObjective =
          (CompositeServiceLevelObjective) serviceLevelObjective;
      AbstractServiceLevelObjective newCompositeServiceLevelObjective =
          serviceLevelObjectiveTypeSLOV2TransformerMap.get(ServiceLevelObjectiveType.COMPOSITE)
              .getSLOV2(projectParams, serviceLevelObjectiveDTO, true);
      if (compositeSLOService.shouldReset(compositeServiceLevelObjective, newCompositeServiceLevelObjective)) {
        compositeSLOService.reset(compositeServiceLevelObjective);
      } else if (compositeSLOService.shouldRecalculate(
                     compositeServiceLevelObjective, newCompositeServiceLevelObjective)) {
        compositeSLOService.recalculate(compositeServiceLevelObjective);
      }
    }
    serviceLevelObjective =
        updateSLOV2Entity(projectParams, serviceLevelObjective, serviceLevelObjectiveDTO, serviceLevelIndicators);
    sloHealthIndicatorService.upsert(serviceLevelObjective);
    sloErrorBudgetResetService.clearErrorBudgetResets(projectParams, identifier);

    outboxService.save(ServiceLevelObjectiveUpdateEvent.builder()
                           .resourceName(serviceLevelObjectiveDTO.getName())
                           .oldServiceLevelObjectiveDTO(existingServiceLevelObjective)
                           .newServiceLevelObjectiveDTO(serviceLevelObjectiveDTO)
                           .accountIdentifier(projectParams.getAccountIdentifier())
                           .serviceLevelObjectiveIdentifier(serviceLevelObjectiveDTO.getIdentifier())
                           .orgIdentifier(projectParams.getOrgIdentifier())
                           .projectIdentifier(projectParams.getProjectIdentifier())
                           .build());
    return getSLOResponse(serviceLevelObjectiveDTO.getIdentifier(), projectParams);
  }

  @Override
  public AbstractServiceLevelObjective getEntity(ProjectParams projectParams, String identifier) {
    return hPersistence.createQuery(AbstractServiceLevelObjective.class)
        .filter(ServiceLevelObjectiveV2Keys.accountId, projectParams.getAccountIdentifier())
        .filter(ServiceLevelObjectiveV2Keys.orgIdentifier, projectParams.getOrgIdentifier())
        .filter(ServiceLevelObjectiveV2Keys.projectIdentifier, projectParams.getProjectIdentifier())
        .filter(ServiceLevelObjectiveV2Keys.identifier, identifier)
        .get();
  }

  @Override
  public void deleteByProjectIdentifier(
      Class<AbstractServiceLevelObjective> clazz, String accountId, String orgIdentifier, String projectIdentifier) {
    List<AbstractServiceLevelObjective> serviceLevelObjectives =
        hPersistence.createQuery(AbstractServiceLevelObjective.class)
            .filter(ServiceLevelObjectiveV2Keys.accountId, accountId)
            .filter(ServiceLevelObjectiveV2Keys.orgIdentifier, orgIdentifier)
            .filter(ServiceLevelObjectiveV2Keys.projectIdentifier, projectIdentifier)
            .asList();

    serviceLevelObjectives.forEach(serviceLevelObjective
        -> delete(ProjectParams.builder()
                      .accountIdentifier(serviceLevelObjective.getAccountId())
                      .projectIdentifier(serviceLevelObjective.getProjectIdentifier())
                      .orgIdentifier(serviceLevelObjective.getOrgIdentifier())
                      .build(),
            serviceLevelObjective.getIdentifier()));
  }

  @Override
  public void deleteByOrgIdentifier(
      Class<AbstractServiceLevelObjective> clazz, String accountId, String orgIdentifier) {
    List<AbstractServiceLevelObjective> serviceLevelObjectives =
        hPersistence.createQuery(AbstractServiceLevelObjective.class)
            .filter(AbstractServiceLevelObjective.ServiceLevelObjectiveV2Keys.accountId, accountId)
            .filter(AbstractServiceLevelObjective.ServiceLevelObjectiveV2Keys.orgIdentifier, orgIdentifier)
            .asList();

    serviceLevelObjectives.forEach(serviceLevelObjective
        -> delete(ProjectParams.builder()
                      .accountIdentifier(serviceLevelObjective.getAccountId())
                      .projectIdentifier(serviceLevelObjective.getProjectIdentifier())
                      .orgIdentifier(serviceLevelObjective.getOrgIdentifier())
                      .build(),
            serviceLevelObjective.getIdentifier()));
  }

  @Override
  public void deleteByAccountIdentifier(Class<AbstractServiceLevelObjective> clazz, String accountId) {
    List<AbstractServiceLevelObjective> serviceLevelObjectives =
        hPersistence.createQuery(AbstractServiceLevelObjective.class)
            .filter(AbstractServiceLevelObjective.ServiceLevelObjectiveV2Keys.accountId, accountId)
            .asList();

    serviceLevelObjectives.forEach(serviceLevelObjective
        -> delete(ProjectParams.builder()
                      .accountIdentifier(serviceLevelObjective.getAccountId())
                      .projectIdentifier(serviceLevelObjective.getProjectIdentifier())
                      .orgIdentifier(serviceLevelObjective.getOrgIdentifier())
                      .build(),
            serviceLevelObjective.getIdentifier()));
  }

  @Override
  public boolean delete(ProjectParams projectParams, String identifier) {
    AbstractServiceLevelObjective serviceLevelObjectiveV2 = checkIfSLOPresent(projectParams, identifier);
    ServiceLevelObjectiveV2DTO serviceLevelObjectiveDTO =
        sloEntityToSLOResponse(serviceLevelObjectiveV2).getServiceLevelObjectiveV2DTO();
    if (serviceLevelObjectiveV2.getType().equals(ServiceLevelObjectiveType.SIMPLE)) {
      List<String> referencedCompositeSLOIdentifiers =
          compositeSLOService.getReferencedCompositeSLOs(projectParams, identifier)
              .stream()
              .map(CompositeServiceLevelObjective::getIdentifier)
              .collect(Collectors.toList());
      if (isNotEmpty(referencedCompositeSLOIdentifiers)) {
        throw new InvalidRequestException(String.format(
            "Can't delete the SLO with identifier %s, accountId %s, orgIdentifier %s and projectIdentifier %s. This is associated with Composite SLO with identifier%s %s.",
            identifier, projectParams.getAccountIdentifier(), projectParams.getOrgIdentifier(),
            projectParams.getProjectIdentifier(), referencedCompositeSLOIdentifiers.size() > 1 ? "s" : "",
            String.join(", ", referencedCompositeSLOIdentifiers)));
      }
      serviceLevelIndicatorService.deleteByIdentifier(
          projectParams, ((SimpleServiceLevelObjective) serviceLevelObjectiveV2).getServiceLevelIndicators());
    }
    sloErrorBudgetResetService.clearErrorBudgetResets(projectParams, identifier);
    sloHealthIndicatorService.delete(projectParams, serviceLevelObjectiveV2.getIdentifier());
    notificationRuleService.delete(projectParams,
        serviceLevelObjectiveV2.getNotificationRuleRefs()
            .stream()
            .map(NotificationRuleRef::getNotificationRuleRef)
            .collect(Collectors.toList()));
    if (serviceLevelObjectiveV2.getType().equals(ServiceLevelObjectiveType.COMPOSITE)) {
      String verificationTaskId = verificationTaskService.getCompositeSLOVerificationTaskId(
          serviceLevelObjectiveV2.getAccountId(), serviceLevelObjectiveV2.getUuid());
      if (StringUtils.isNotBlank(verificationTaskId)) {
        sideKickService.schedule(
            VerificationTaskCleanupSideKickData.builder().verificationTaskId(verificationTaskId).build(),
            clock.instant().plus(Duration.ofMinutes(15)));
      }
    }

    outboxService.save(ServiceLevelObjectiveDeleteEvent.builder()
                           .resourceName(serviceLevelObjectiveDTO.getName())
                           .oldServiceLevelObjectiveDTO(serviceLevelObjectiveDTO)
                           .accountIdentifier(projectParams.getAccountIdentifier())
                           .serviceLevelObjectiveIdentifier(serviceLevelObjectiveDTO.getIdentifier())
                           .orgIdentifier(projectParams.getOrgIdentifier())
                           .projectIdentifier(projectParams.getProjectIdentifier())
                           .build());
    return hPersistence.delete(serviceLevelObjectiveV2);
  }

  @Override
  public void setMonitoredServiceSLOsEnableFlag(
      ProjectParams projectParams, String monitoredServiceIdentifier, boolean isEnabled) {
    hPersistence.update(
        hPersistence.createQuery(SimpleServiceLevelObjective.class)
            .filter(ServiceLevelObjectiveV2Keys.accountId, projectParams.getAccountIdentifier())
            .filter(ServiceLevelObjectiveV2Keys.orgIdentifier, projectParams.getOrgIdentifier())
            .filter(ServiceLevelObjectiveV2Keys.projectIdentifier, projectParams.getProjectIdentifier())
            .filter(SimpleServiceLevelObjectiveKeys.monitoredServiceIdentifier, monitoredServiceIdentifier),
        hPersistence.createUpdateOperations(SimpleServiceLevelObjective.class)
            .set(ServiceLevelObjectiveV2Keys.enabled, isEnabled));
  }

  @Override
  public void updateNotificationRuleRefInSLO(ProjectParams projectParams,
      AbstractServiceLevelObjective serviceLevelObjective, List<String> notificationRuleRefs) {
    List<NotificationRuleRef> allNotificationRuleRefs = new ArrayList<>();
    List<NotificationRuleRef> notificationRuleRefsWithoutChange =
        serviceLevelObjective.getNotificationRuleRefs()
            .stream()
            .filter(notificationRuleRef -> !notificationRuleRefs.contains(notificationRuleRef.getNotificationRuleRef()))
            .collect(Collectors.toList());
    List<NotificationRuleRefDTO> notificationRuleRefDTOs =
        notificationRuleRefs.stream()
            .map(notificationRuleRef
                -> NotificationRuleRefDTO.builder().notificationRuleRef(notificationRuleRef).enabled(true).build())
            .collect(Collectors.toList());
    List<NotificationRuleRef> notificationRuleRefsWithChange = notificationRuleService.getNotificationRuleRefs(
        projectParams, notificationRuleRefDTOs, NotificationRuleType.SLO, clock.instant());
    allNotificationRuleRefs.addAll(notificationRuleRefsWithChange);
    allNotificationRuleRefs.addAll(notificationRuleRefsWithoutChange);
    UpdateOperations<AbstractServiceLevelObjective> updateOperations =
        hPersistence.createUpdateOperations(AbstractServiceLevelObjective.class);
    updateOperations.set(
        AbstractServiceLevelObjective.ServiceLevelObjectiveV2Keys.notificationRuleRefs, allNotificationRuleRefs);

    hPersistence.update(serviceLevelObjective, updateOperations);
  }

  @Override
  public PageResponse<ServiceLevelObjectiveV2Response> get(ProjectParams projectParams, Integer offset,
      Integer pageSize, ServiceLevelObjectiveFilter serviceLevelObjectiveFilter) {
    return get(projectParams, offset, pageSize,
        Filter.builder()
            .userJourneys(serviceLevelObjectiveFilter.getUserJourneys())
            .identifiers(serviceLevelObjectiveFilter.getIdentifiers())
            .targetTypes(serviceLevelObjectiveFilter.getTargetTypes())
            .sliTypes(serviceLevelObjectiveFilter.getSliTypes())
            .errorBudgetRisks(serviceLevelObjectiveFilter.getErrorBudgetRisks())
            .build());
  }

  @Override
  public ServiceLevelObjectiveV2Response get(ProjectParams projectParams, String identifier) {
    AbstractServiceLevelObjective serviceLevelObjectiveV2 = getEntity(projectParams, identifier);
    if (Objects.isNull(serviceLevelObjectiveV2)) {
      throw new NotFoundException("SLO with identifier " + identifier + " not found.");
    }
    return sloEntityToSLOResponse(serviceLevelObjectiveV2);
  }

  @Override
  public SLORiskCountResponse getRiskCount(ProjectParams projectParams, SLODashboardApiFilter sloDashboardApiFilter) {
    List<AbstractServiceLevelObjective> serviceLevelObjectiveList = get(projectParams,
        Filter.builder()
            .userJourneys(sloDashboardApiFilter.getUserJourneyIdentifiers())
            .monitoredServiceIdentifier(sloDashboardApiFilter.getMonitoredServiceIdentifier())
            .targetTypes(sloDashboardApiFilter.getTargetTypes())
            .sliTypes(sloDashboardApiFilter.getSliTypes())
            .build());
    List<SLOHealthIndicator> sloHealthIndicators = sloHealthIndicatorService.getBySLOIdentifiers(projectParams,
        serviceLevelObjectiveList.stream()
            .map(AbstractServiceLevelObjective::getIdentifier)
            .collect(Collectors.toList()));

    Map<ErrorBudgetRisk, Long> riskToCountMap =
        sloHealthIndicators.stream()
            .map(SLOHealthIndicator::getErrorBudgetRisk)
            .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

    return SLORiskCountResponse.builder()
        .totalCount(serviceLevelObjectiveList.size())
        .riskCounts(Arrays.stream(ErrorBudgetRisk.values())
                        .map(risk
                            -> SLORiskCountResponse.RiskCount.builder()
                                   .errorBudgetRisk(risk)
                                   .count(riskToCountMap.getOrDefault(risk, 0L).intValue())
                                   .build())
                        .collect(Collectors.toList()))
        .build();
  }

  @Override
  public List<AbstractServiceLevelObjective> getAllSLOs(ProjectParams projectParams) {
    return get(projectParams, Filter.builder().build());
  }

  @Override
  public List<AbstractServiceLevelObjective> getByMonitoredServiceIdentifier(
      ProjectParams projectParams, String monitoredServiceIdentifier) {
    return get(projectParams, Filter.builder().monitoredServiceIdentifier(monitoredServiceIdentifier).build());
  }

  @Override
  public PageResponse<CVNGLogDTO> getCVNGLogs(
      ProjectParams projectParams, String identifier, SLILogsFilter sliLogsFilter, PageParams pageParams) {
    AbstractServiceLevelObjective serviceLevelObjective = getEntity(projectParams, identifier);
    if (Objects.isNull(serviceLevelObjective)) {
      throw new NotFoundException("SLO with identifier " + identifier + " not found.");
    }
    if (serviceLevelObjective.getType().equals(ServiceLevelObjectiveType.SIMPLE)) {
      SimpleServiceLevelObjective simpleServiceLevelObjective = (SimpleServiceLevelObjective) serviceLevelObjective;
      List<String> sliIds = serviceLevelIndicatorService
                                .getEntities(projectParams, simpleServiceLevelObjective.getServiceLevelIndicators())
                                .stream()
                                .map(ServiceLevelIndicator::getUuid)
                                .collect(Collectors.toList());
      List<String> verificationTaskIds =
          verificationTaskService.getSLIVerificationTaskIds(projectParams.getAccountIdentifier(), sliIds);

      return cvngLogService.getCVNGLogs(
          projectParams.getAccountIdentifier(), verificationTaskIds, sliLogsFilter, pageParams);
    } else {
      String verificationTaskId = verificationTaskService.getCompositeSLOVerificationTaskId(
          projectParams.getAccountIdentifier(), serviceLevelObjective.getUuid());
      return cvngLogService.getCVNGLogs(projectParams.getAccountIdentifier(),
          Collections.singletonList(verificationTaskId), sliLogsFilter, pageParams);
    }
  }

  @Override
  public PageResponse<AbstractServiceLevelObjective> getSLOForListView(
      ProjectParams projectParams, SLODashboardApiFilter filter, PageParams pageParams) {
    List<String> simpleSLOIdentifiers = Collections.emptyList();
    List<ServiceLevelObjectivesDetail> serviceLevelObjectivesDetailList = Collections.emptyList();
    if (isNotEmpty(filter.getCompositeSLOIdentifier())) {
      CompositeServiceLevelObjective compositeSLO = (CompositeServiceLevelObjective) checkIfSLOPresentWithType(
          projectParams, filter.getCompositeSLOIdentifier(), ServiceLevelObjectiveType.COMPOSITE);

      simpleSLOIdentifiers = compositeSLO.getServiceLevelObjectivesDetails()
                                 .stream()
                                 .map(ServiceLevelObjectivesDetail::getServiceLevelObjectiveRef)
                                 .collect(Collectors.toList());
      serviceLevelObjectivesDetailList = compositeSLO.getServiceLevelObjectivesDetails();
    }
    return getResponse(projectParams, pageParams.getPage(), pageParams.getSize(), serviceLevelObjectivesDetailList,
        Filter.builder()
            .identifiers(simpleSLOIdentifiers)
            .monitoredServiceIdentifier(filter.getMonitoredServiceIdentifier())
            .userJourneys(filter.getUserJourneyIdentifiers())
            .sliTypes(filter.getSliTypes())
            .errorBudgetRisks(filter.getErrorBudgetRisks())
            .targetTypes(filter.getTargetTypes())
            .searchFilter(filter.getSearchFilter())
            .sloType(filter.getType())
            .sloTarget(filter.getSloTargetFilterDTO() != null
                    ? sloTargetTypeSLOTargetTransformerMap.get(filter.getSloTargetFilterDTO().getType())
                          .getSLOTarget(filter.getSloTargetFilterDTO().getSpec())
                    : null)
            .childResource(filter.isChildResource())
            .build());
  }

  @Override
  public SimpleServiceLevelObjective getFromSLIIdentifier(
      ProjectParams projectParams, String serviceLevelIndicatorIdentifier) {
    return hPersistence.createQuery(SimpleServiceLevelObjective.class)
        .filter(
            AbstractServiceLevelObjective.ServiceLevelObjectiveV2Keys.accountId, projectParams.getAccountIdentifier())
        .filter(
            AbstractServiceLevelObjective.ServiceLevelObjectiveV2Keys.orgIdentifier, projectParams.getOrgIdentifier())
        .filter(AbstractServiceLevelObjective.ServiceLevelObjectiveV2Keys.projectIdentifier,
            projectParams.getProjectIdentifier())
        .filter(SimpleServiceLevelObjectiveKeys.serviceLevelIndicators, serviceLevelIndicatorIdentifier)
        .get();
  }

  @Override
  public PageResponse<NotificationRuleResponse> getNotificationRules(
      ProjectParams projectParams, String sloIdentifier, PageParams pageParams) {
    AbstractServiceLevelObjective serviceLevelObjectiveV2 = getEntity(projectParams, sloIdentifier);
    if (serviceLevelObjectiveV2 == null) {
      throw new InvalidRequestException(String.format(
          "SLO with identifier %s, accountId %s, orgIdentifier %s, and projectIdentifier %s  is not present.",
          sloIdentifier, projectParams.getAccountIdentifier(), projectParams.getOrgIdentifier(),
          projectParams.getProjectIdentifier()));
    }
    List<NotificationRuleRef> notificationRuleRefList = serviceLevelObjectiveV2.getNotificationRuleRefs();
    List<NotificationRuleResponse> notificationRuleResponseList =
        notificationRuleService.getNotificationRuleResponse(projectParams, notificationRuleRefList);
    PageResponse<NotificationRuleResponse> notificationRulePageResponse =
        PageUtils.offsetAndLimit(notificationRuleResponseList, pageParams.getPage(), pageParams.getSize());

    return PageResponse.<NotificationRuleResponse>builder()
        .pageSize(pageParams.getSize())
        .pageIndex(pageParams.getPage())
        .totalPages(notificationRulePageResponse.getTotalPages())
        .totalItems(notificationRulePageResponse.getTotalItems())
        .pageItemCount(notificationRulePageResponse.getPageItemCount())
        .content(notificationRulePageResponse.getContent())
        .build();
  }

  @Override
  public void beforeNotificationRuleDelete(ProjectParams projectParams, String notificationRuleRef) {
    List<AbstractServiceLevelObjective> serviceLevelObjectives =
        get(projectParams, Filter.builder().notificationRuleRef(notificationRuleRef).build());
    Preconditions.checkArgument(isEmpty(serviceLevelObjectives),
        "Deleting notification rule is used in SLOs, "
            + "Please delete the notification rule inside SLOs before deleting notification rule. SLOs : "
            + serviceLevelObjectives.stream()
                  .map(AbstractServiceLevelObjective::getName)
                  .collect(Collectors.joining(", ")));
  }

  @Nullable
  @Override
  public AbstractServiceLevelObjective get(String sloId) {
    return hPersistence.get(AbstractServiceLevelObjective.class, sloId);
  }

  private AbstractServiceLevelObjective updateSLOV2Entity(ProjectParams projectParams,
      AbstractServiceLevelObjective abstractServiceLevelObjective,
      ServiceLevelObjectiveV2DTO serviceLevelObjectiveV2DTO, List<String> serviceLevelIndicators) {
    boolean isSLOEnabled = true;
    if (abstractServiceLevelObjective.getType().equals(ServiceLevelObjectiveType.SIMPLE)) {
      isSLOEnabled =
          monitoredServiceService
              .getMonitoredService(MonitoredServiceParams.builderWithProjectParams(projectParams)
                                       .monitoredServiceIdentifier(
                                           ((SimpleServiceLevelObjectiveSpec) serviceLevelObjectiveV2DTO.getSpec())
                                               .getMonitoredServiceRef())
                                       .build())
              .isEnabled();
    }
    UpdateOperations<AbstractServiceLevelObjective> updateOperations =
        hPersistence.createUpdateOperations(AbstractServiceLevelObjective.class);
    serviceLevelObjectiveTypeUpdatableEntityTransformerMap.get(serviceLevelObjectiveV2DTO.getType())
        .setUpdateOperations(updateOperations,
            serviceLevelObjectiveTypeSLOV2TransformerMap.get(serviceLevelObjectiveV2DTO.getType())
                .getSLOV2(projectParams, serviceLevelObjectiveV2DTO, isSLOEnabled));
    updateOperations.set(ServiceLevelObjectiveV2Keys.sloTarget,
        sloTargetTypeSLOTargetTransformerMap.get(serviceLevelObjectiveV2DTO.getSloTarget().getType())
            .getSLOTarget(serviceLevelObjectiveV2DTO.getSloTarget().getSpec()));
    if (abstractServiceLevelObjective.getType().equals(ServiceLevelObjectiveType.SIMPLE)) {
      updateOperations.set(SimpleServiceLevelObjectiveKeys.serviceLevelIndicators, serviceLevelIndicators);
    }
    updateOperations.set(ServiceLevelObjectiveV2Keys.notificationRuleRefs,
        getNotificationRuleRefs(projectParams, abstractServiceLevelObjective, serviceLevelObjectiveV2DTO));
    hPersistence.update(abstractServiceLevelObjective, updateOperations);
    abstractServiceLevelObjective = getEntity(projectParams, abstractServiceLevelObjective.getIdentifier());
    return abstractServiceLevelObjective;
  }

  private List<NotificationRuleRef> getNotificationRuleRefs(ProjectParams projectParams,
      AbstractServiceLevelObjective serviceLevelObjective, ServiceLevelObjectiveV2DTO serviceLevelObjectiveV2DTO) {
    List<NotificationRuleRef> notificationRuleRefs = notificationRuleService.getNotificationRuleRefs(projectParams,
        serviceLevelObjectiveV2DTO.getNotificationRuleRefs(), NotificationRuleType.SLO, Instant.ofEpochSecond(0));
    deleteNotificationRuleRefs(projectParams, serviceLevelObjective, notificationRuleRefs);
    return notificationRuleRefs;
  }

  private void deleteNotificationRuleRefs(ProjectParams projectParams,
      AbstractServiceLevelObjective serviceLevelObjective, List<NotificationRuleRef> notificationRuleRefs) {
    List<String> existingNotificationRuleRefs = serviceLevelObjective.getNotificationRuleRefs()
                                                    .stream()
                                                    .map(NotificationRuleRef::getNotificationRuleRef)
                                                    .collect(Collectors.toList());
    List<String> updatedNotificationRuleRefs =
        notificationRuleRefs.stream().map(NotificationRuleRef::getNotificationRuleRef).collect(Collectors.toList());
    notificationRuleService.deleteNotificationRuleRefs(
        projectParams, existingNotificationRuleRefs, updatedNotificationRuleRefs);
  }

  private ServiceLevelObjectiveV2Response getSLOResponse(String identifier, ProjectParams projectParams) {
    AbstractServiceLevelObjective serviceLevelObjective = getEntity(projectParams, identifier);
    return sloEntityToSLOResponse(serviceLevelObjective);
  }

  private ServiceLevelObjectiveV2Response sloEntityToSLOResponse(AbstractServiceLevelObjective serviceLevelObjective) {
    ServiceLevelObjectiveV2DTO serviceLevelObjectiveDTO =
        serviceLevelObjectiveTypeSLOV2TransformerMap.get(serviceLevelObjective.getType())
            .getSLOV2DTO(serviceLevelObjective);
    return ServiceLevelObjectiveV2Response.builder()
        .serviceLevelObjectiveV2DTO(serviceLevelObjectiveDTO)
        .createdAt(serviceLevelObjective.getCreatedAt())
        .lastModifiedAt(serviceLevelObjective.getLastUpdatedAt())
        .build();
  }

  private AbstractServiceLevelObjective saveServiceLevelObjectiveV2Entity(
      ProjectParams projectParams, ServiceLevelObjectiveV2DTO serviceLevelObjectiveDTO, boolean isEnabled) {
    AbstractServiceLevelObjective serviceLevelObjectiveV2 =
        serviceLevelObjectiveTypeSLOV2TransformerMap.get(serviceLevelObjectiveDTO.getType())
            .getSLOV2(projectParams, serviceLevelObjectiveDTO, isEnabled);
    hPersistence.save(serviceLevelObjectiveV2);
    outboxService.save(ServiceLevelObjectiveCreateEvent.builder()
                           .resourceName(serviceLevelObjectiveDTO.getName())
                           .newServiceLevelObjectiveDTO(serviceLevelObjectiveDTO)
                           .accountIdentifier(projectParams.getAccountIdentifier())
                           .serviceLevelObjectiveIdentifier(serviceLevelObjectiveDTO.getIdentifier())
                           .orgIdentifier(projectParams.getOrgIdentifier())
                           .projectIdentifier(projectParams.getProjectIdentifier())
                           .build());
    return serviceLevelObjectiveV2;
  }

  private void validateCompositeSLO(ServiceLevelObjectiveV2DTO serviceLevelObjectiveDTO) {
    CompositeServiceLevelObjectiveSpec compositeServiceLevelObjectiveSpec =
        (CompositeServiceLevelObjectiveSpec) serviceLevelObjectiveDTO.getSpec();
    double sum = compositeServiceLevelObjectiveSpec.getServiceLevelObjectivesDetails()
                     .stream()
                     .peek(sloDetail -> checkIfValidSLOPresent(sloDetail, serviceLevelObjectiveDTO))
                     .mapToDouble(ServiceLevelObjectiveDetailsDTO::getWeightagePercentage)
                     .sum();

    if (sum != 100) {
      throw new InvalidRequestException(String.format(
          "The weightage percentage of all the SLOs constituting the Composite SLO with identifier %s is %s. It should sum up to 100.",
          serviceLevelObjectiveDTO.getIdentifier(), sum));
    }

    Set<String> scopedReferencedSimpleSLOs =
        compositeServiceLevelObjectiveSpec.getServiceLevelObjectivesDetails()
            .stream()
            .map(serviceLevelObjectiveDetailsDTO
                -> ScopedInformation.getScopedInformation(serviceLevelObjectiveDetailsDTO.getAccountId(),
                    serviceLevelObjectiveDetailsDTO.getOrgIdentifier(),
                    serviceLevelObjectiveDetailsDTO.getProjectIdentifier(),
                    serviceLevelObjectiveDetailsDTO.getServiceLevelObjectiveRef()))
            .collect(Collectors.toSet());
    if (scopedReferencedSimpleSLOs.size()
        != compositeServiceLevelObjectiveSpec.getServiceLevelObjectivesDetails().size()) {
      throw new InvalidRequestException(String.format("An SLO can't be referenced more than once"));
    }
  }

  private void checkIfValidSLOPresent(ServiceLevelObjectiveDetailsDTO serviceLevelObjectiveDetailsDTO,
      ServiceLevelObjectiveV2DTO serviceLevelObjectiveDTO) {
    ProjectParams projectParams = ProjectParams.builder()
                                      .accountIdentifier(serviceLevelObjectiveDetailsDTO.getAccountId())
                                      .projectIdentifier(serviceLevelObjectiveDetailsDTO.getProjectIdentifier())
                                      .orgIdentifier(serviceLevelObjectiveDetailsDTO.getOrgIdentifier())
                                      .build();
    AbstractServiceLevelObjective serviceLevelObjective = checkIfSLOPresentWithType(
        projectParams, serviceLevelObjectiveDetailsDTO.getServiceLevelObjectiveRef(), ServiceLevelObjectiveType.SIMPLE);
    if (!sloTargetTypeSLOTargetTransformerMap.get(serviceLevelObjectiveDTO.getSloTarget().getType())
             .getSLOTarget(serviceLevelObjectiveDTO.getSloTarget().getSpec())
             .equals(serviceLevelObjective.getSloTarget())) {
      throw new InvalidRequestException(String.format(
          "Composite SLO with identifier %s, accountId %s, orgIdentifier %s, and projectIdentifier %s can not be created/updated as the compliance time period of the SLO and the associated SLOs is different.",
          serviceLevelObjectiveDTO.getIdentifier(), serviceLevelObjectiveDetailsDTO.getAccountId(),
          serviceLevelObjectiveDTO.getOrgIdentifier(), serviceLevelObjectiveDTO.getProjectIdentifier()));
    }
  }

  private AbstractServiceLevelObjective checkIfSLOPresent(ProjectParams projectParams, String identifier) {
    AbstractServiceLevelObjective serviceLevelObjective = getEntity(projectParams, identifier);
    if (serviceLevelObjective == null) {
      throw new InvalidRequestException(String.format(
          "SLO with identifier %s, accountId %s, orgIdentifier %s, and projectIdentifier %s is not present.",
          identifier, projectParams.getAccountIdentifier(), projectParams.getOrgIdentifier(),
          projectParams.getProjectIdentifier()));
    }
    return serviceLevelObjective;
  }

  private AbstractServiceLevelObjective checkIfSLOPresentWithType(
      ProjectParams projectParams, String identifier, ServiceLevelObjectiveType type) {
    AbstractServiceLevelObjective serviceLevelObjective = checkIfSLOPresent(projectParams, identifier);
    if (serviceLevelObjective.getType() != type) {
      throw new InvalidRequestException(String.format(
          "SLO with identifier %s, accountId %s, orgIdentifier %s, and projectIdentifier %s is not a %s SLO.",
          identifier, projectParams.getAccountIdentifier(), projectParams.getOrgIdentifier(),
          projectParams.getProjectIdentifier(), type));
    }
    return serviceLevelObjective;
  }

  private void validateCreate(ServiceLevelObjectiveV2DTO sloCreateDTO, ProjectParams projectParams) {
    AbstractServiceLevelObjective serviceLevelObjective = getEntity(projectParams, sloCreateDTO.getIdentifier());
    if (serviceLevelObjective != null) {
      throw new DuplicateFieldException(String.format(
          "serviceLevelObjectiveV2 with identifier %s and orgIdentifier %s and projectIdentifier %s is already present",
          sloCreateDTO.getIdentifier(), projectParams.getOrgIdentifier(), projectParams.getProjectIdentifier()));
    }
    if (sloCreateDTO.getType().equals(ServiceLevelObjectiveType.SIMPLE)) {
      validateSimpleSLO(sloCreateDTO, projectParams);
    } else {
      validateCompositeSLO(sloCreateDTO);
    }
  }

  private void validateSimpleSLO(ServiceLevelObjectiveV2DTO sloCreateDTO, ProjectParams projectParams) {
    monitoredServiceService.get(
        projectParams, ((SimpleServiceLevelObjectiveSpec) sloCreateDTO.getSpec()).getMonitoredServiceRef());
  }

  public PageResponse<ServiceLevelObjectiveV2Response> get(
      ProjectParams projectParams, Integer offset, Integer pageSize, Filter filter) {
    List<AbstractServiceLevelObjective> serviceLevelObjectiveList = get(projectParams, filter);
    PageResponse<AbstractServiceLevelObjective> sloEntitiesPageResponse =
        PageUtils.offsetAndLimit(serviceLevelObjectiveList, offset, pageSize);
    List<ServiceLevelObjectiveV2Response> sloPageResponse =
        sloEntitiesPageResponse.getContent().stream().map(this::sloEntityToSLOResponse).collect(Collectors.toList());

    return PageResponse.<ServiceLevelObjectiveV2Response>builder()
        .pageSize(pageSize)
        .pageIndex(offset)
        .totalPages(sloEntitiesPageResponse.getTotalPages())
        .totalItems(sloEntitiesPageResponse.getTotalItems())
        .pageItemCount(sloEntitiesPageResponse.getPageItemCount())
        .content(sloPageResponse)
        .build();
  }

  private List<AbstractServiceLevelObjective> get(ProjectParams projectParams, Filter filter) {
    Query<AbstractServiceLevelObjective> sloQuery =
        hPersistence.createQuery(AbstractServiceLevelObjective.class)
            .disableValidation()
            .filter(AbstractServiceLevelObjective.ServiceLevelObjectiveV2Keys.accountId,
                projectParams.getAccountIdentifier())
            .order(Sort.descending(AbstractServiceLevelObjective.ServiceLevelObjectiveV2Keys.lastUpdatedAt));
    if (!filter.isChildResource()) {
      sloQuery = sloQuery
                     .filter(AbstractServiceLevelObjective.ServiceLevelObjectiveV2Keys.orgIdentifier,
                         projectParams.getOrgIdentifier())
                     .filter(AbstractServiceLevelObjective.ServiceLevelObjectiveV2Keys.projectIdentifier,
                         projectParams.getProjectIdentifier());
    }
    if (isNotEmpty(filter.getUserJourneys())) {
      sloQuery.field(ServiceLevelObjectiveV2Keys.userJourneyIdentifiers).hasAnyOf(filter.getUserJourneys());
    }
    if (isNotEmpty(filter.getIdentifiers())) {
      sloQuery.field(ServiceLevelObjectiveV2Keys.identifier).in(filter.getIdentifiers());
    }
    if (isNotEmpty(filter.getSliTypes())) {
      sloQuery.field(SimpleServiceLevelObjectiveKeys.serviceLevelIndicatorType).in(filter.getSliTypes());
    }
    if (isNotEmpty(filter.getTargetTypes())) {
      sloQuery.field(ServiceLevelObjectiveV2Keys.sloTarget + "." + SLOTargetDTO.SLOTargetKeys.type)
          .in(filter.getTargetTypes());
    }
    if (filter.getSloTarget() != null) {
      sloQuery.filter(ServiceLevelObjectiveV2Keys.sloTarget, filter.getSloTarget());
    }
    if (filter.getSloType() != null) {
      sloQuery.filter(ServiceLevelObjectiveV2Keys.type, filter.getSloType());
    }
    if (filter.getMonitoredServiceIdentifier() != null) {
      sloQuery.filter(SimpleServiceLevelObjectiveKeys.monitoredServiceIdentifier, filter.monitoredServiceIdentifier);
    }
    List<AbstractServiceLevelObjective> serviceLevelObjectiveList = sloQuery.asList();
    if (filter.getNotificationRuleRef() != null) {
      serviceLevelObjectiveList =
          serviceLevelObjectiveList.stream()
              .filter(slo
                  -> slo.getNotificationRuleRefs().stream().anyMatch(notificationRuleRef
                      -> notificationRuleRef.getNotificationRuleRef().equals(filter.getNotificationRuleRef())))
              .collect(Collectors.toList());
    }
    if (isNotEmpty(filter.getErrorBudgetRisks())) {
      List<SLOHealthIndicator> sloHealthIndicators = sloHealthIndicatorService.getBySLOIdentifiers(projectParams,
          serviceLevelObjectiveList.stream()
              .map(AbstractServiceLevelObjective::getIdentifier)
              .collect(Collectors.toList()));
      Map<String, ErrorBudgetRisk> sloIdToRiskMap =
          sloHealthIndicators.stream().collect(Collectors.toMap(SLOHealthIndicator::getServiceLevelObjectiveIdentifier,
              SLOHealthIndicator::getErrorBudgetRisk, (slo1, slo2) -> slo1));
      serviceLevelObjectiveList =
          serviceLevelObjectiveList.stream()
              .filter(slo -> sloIdToRiskMap.containsKey(slo.getIdentifier()))
              .filter(slo -> filter.getErrorBudgetRisks().contains(sloIdToRiskMap.get(slo.getIdentifier())))
              .collect(Collectors.toList());
    }
    return serviceLevelObjectiveList;
  }

  private PageResponse<AbstractServiceLevelObjective> getResponse(ProjectParams projectParams, Integer offset,
      Integer pageSize, List<ServiceLevelObjectivesDetail> serviceLevelObjectivesDetailList, Filter filter) {
    List<AbstractServiceLevelObjective> serviceLevelObjectiveList = get(projectParams, filter);
    if (isNotEmpty(serviceLevelObjectivesDetailList)) {
      serviceLevelObjectiveList =
          getSLOsAssociatedWithCompositeSLO(serviceLevelObjectivesDetailList, serviceLevelObjectiveList);
    }
    if (isNotEmpty(filter.getSearchFilter())) {
      serviceLevelObjectiveList = filterSLOs(serviceLevelObjectiveList, filter.getSearchFilter());
    }
    return PageUtils.offsetAndLimit(serviceLevelObjectiveList, offset, pageSize);
  }

  private List<AbstractServiceLevelObjective> getSLOsAssociatedWithCompositeSLO(
      List<ServiceLevelObjectivesDetail> serviceLevelObjectivesDetailList,
      List<AbstractServiceLevelObjective> serviceLevelObjectiveList) {
    Map<ServiceLevelObjectiveDetailsRefDTO, AbstractServiceLevelObjective>
        sloDetailsToAbstractServiceLevelObjectiveMap = serviceLevelObjectiveList.stream().collect(Collectors.toMap(slo
            -> ServiceLevelObjectiveDetailsRefDTO.builder()
                   .accountId(slo.getAccountId())
                   .orgIdentifier(slo.getOrgIdentifier())
                   .projectIdentifier(slo.getProjectIdentifier())
                   .serviceLevelObjectiveRef(slo.getIdentifier())
                   .build(),
            slo -> slo));

    List<ServiceLevelObjectiveDetailsRefDTO> sloDetailsList =
        serviceLevelObjectivesDetailList.stream()
            .map(ServiceLevelObjectivesDetail::getServiceLevelObjectiveDetailsRefDTO)
            .collect(Collectors.toList());

    return sloDetailsList.stream()
        .filter(sloDetailsToAbstractServiceLevelObjectiveMap::containsKey)
        .map(sloDetailsToAbstractServiceLevelObjectiveMap::get)
        .collect(Collectors.toList());
  }

  private List<AbstractServiceLevelObjective> filterSLOs(
      List<AbstractServiceLevelObjective> serviceLevelObjectiveList, String searchFilter) {
    return serviceLevelObjectiveList.stream()
        .filter(serviceLevelObjective
            -> serviceLevelObjective.getName().toLowerCase().contains(searchFilter.trim().toLowerCase()))
        .collect(Collectors.toList());
  }

  private List<CompositeSLORecord> getCompositeSLORecords(
      Map<ServiceLevelObjectivesDetail, List<SLIRecord>> serviceLevelObjectivesDetailCompositeSLORecordMap,
      Map<ServiceLevelObjectivesDetail, SLIMissingDataType> objectivesDetailSLIMissingDataTypeMap) {
    if (isEmpty(serviceLevelObjectivesDetailCompositeSLORecordMap)) {
      return new ArrayList<>();
    }
    double runningGoodCount = 0;
    double runningBadCount = 0;
    return compositeSLORecordService.getCompositeSLORecordsFromSLIsDetails(
        serviceLevelObjectivesDetailCompositeSLORecordMap, objectivesDetailSLIMissingDataTypeMap, 0, runningGoodCount,
        runningBadCount, null);
  }

  @Value
  @Builder
  private static class Filter {
    List<String> userJourneys;
    List<String> identifiers;
    List<ServiceLevelIndicatorType> sliTypes;
    List<SLOTargetType> targetTypes;
    List<ErrorBudgetRisk> errorBudgetRisks;
    String monitoredServiceIdentifier;
    String notificationRuleRef;
    String searchFilter;
    ServiceLevelObjective.SLOTarget sloTarget;
    ServiceLevelObjectiveType sloType;
    boolean childResource;
  }
}
