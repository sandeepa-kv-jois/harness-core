/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.environment.services.impl;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.eventsframework.EventsFrameworkConstants.ENTITY_CRUD;
import static io.harness.exception.WingsException.USER_SRE;
import static io.harness.outbox.TransactionOutboxModule.OUTBOX_TRANSACTION_TEMPLATE;
import static io.harness.springdata.PersistenceUtils.DEFAULT_RETRY_POLICY;

import static com.google.common.base.Preconditions.checkArgument;
import static java.lang.String.format;
import static java.util.stream.Collectors.groupingBy;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.EntityType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.gitops.service.ClusterService;
import io.harness.cdng.visitor.YamlTypes;
import io.harness.data.structure.EmptyPredicate;
import io.harness.eventsframework.EventsFrameworkMetadataConstants;
import io.harness.eventsframework.api.EventsFrameworkDownException;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.entity_crud.EntityChangeDTO;
import io.harness.eventsframework.producer.Message;
import io.harness.exception.DuplicateFieldException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnexpectedException;
import io.harness.ng.core.EntityDetail;
import io.harness.ng.core.entitysetupusage.dto.EntitySetupUsageDTO;
import io.harness.ng.core.entitysetupusage.service.EntitySetupUsageService;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.beans.Environment.EnvironmentKeys;
import io.harness.ng.core.environment.beans.EnvironmentInputSetYamlAndServiceOverridesMetadata;
import io.harness.ng.core.environment.beans.EnvironmentInputSetYamlAndServiceOverridesMetadataDTO;
import io.harness.ng.core.environment.beans.EnvironmentInputsMergedResponseDto;
import io.harness.ng.core.environment.beans.ServiceOverridesMetadata;
import io.harness.ng.core.environment.services.EnvironmentService;
import io.harness.ng.core.events.EnvironmentCreateEvent;
import io.harness.ng.core.events.EnvironmentDeleteEvent;
import io.harness.ng.core.events.EnvironmentUpdatedEvent;
import io.harness.ng.core.events.EnvironmentUpsertEvent;
import io.harness.ng.core.infrastructure.services.InfrastructureEntityService;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.services.ServiceEntityService;
import io.harness.ng.core.service.services.impl.InputSetMergeUtility;
import io.harness.ng.core.serviceoverride.services.ServiceOverrideService;
import io.harness.outbox.api.OutboxService;
import io.harness.pms.merger.helpers.RuntimeInputFormHelper;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.repositories.UpsertOptions;
import io.harness.repositories.environment.spring.EnvironmentRepository;
import io.harness.utils.YamlPipelineUtils;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.protobuf.StringValue;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.transaction.support.TransactionTemplate;

@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
@Slf4j
public class EnvironmentServiceImpl implements EnvironmentService {
  private final EnvironmentRepository environmentRepository;
  private final EntitySetupUsageService entitySetupUsageService;
  private final Producer eventProducer;
  private final OutboxService outboxService;
  private final RetryPolicy<Object> transactionRetryPolicy = DEFAULT_RETRY_POLICY;
  @Inject @Named(OUTBOX_TRANSACTION_TEMPLATE) private final TransactionTemplate transactionTemplate;
  private static final String DUP_KEY_EXP_FORMAT_STRING_FOR_PROJECT =
      "Environment [%s] under Project[%s], Organization [%s] in Account [%s] already exists";
  private static final String DUP_KEY_EXP_FORMAT_STRING_FOR_ORG =
      "Environment [%s] under Organization [%s] in Account [%s] already exists";
  private static final String DUP_KEY_EXP_FORMAT_STRING_FOR_ACCOUNT = "Environment [%s] in Account [%s] already exists";
  private final InfrastructureEntityService infrastructureEntityService;
  private final ClusterService clusterService;
  private final ServiceOverrideService serviceOverrideService;
  private final ServiceEntityService serviceEntityService;

  @Inject
  public EnvironmentServiceImpl(EnvironmentRepository environmentRepository,
      EntitySetupUsageService entitySetupUsageService, @Named(ENTITY_CRUD) Producer eventProducer,
      OutboxService outboxService, TransactionTemplate transactionTemplate,
      InfrastructureEntityService infrastructureEntityService, ClusterService clusterService,
      ServiceOverrideService serviceOverrideService, ServiceEntityService serviceEntityService) {
    this.environmentRepository = environmentRepository;
    this.entitySetupUsageService = entitySetupUsageService;
    this.eventProducer = eventProducer;
    this.outboxService = outboxService;
    this.transactionTemplate = transactionTemplate;
    this.infrastructureEntityService = infrastructureEntityService;
    this.clusterService = clusterService;
    this.serviceOverrideService = serviceOverrideService;
    this.serviceEntityService = serviceEntityService;
  }

  @Override
  public Environment create(@NotNull @Valid Environment environment) {
    try {
      validatePresenceOfRequiredFields(environment.getAccountId(), environment.getIdentifier());
      setName(environment);

      Environment createdEnvironment =
          Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
            Environment tempEnvironment = environmentRepository.save(environment);
            outboxService.save(EnvironmentCreateEvent.builder()
                                   .accountIdentifier(environment.getAccountId())
                                   .orgIdentifier(environment.getOrgIdentifier())
                                   .projectIdentifier(environment.getProjectIdentifier())
                                   .environment(environment)
                                   .build());
            return tempEnvironment;
          }));
      publishEvent(environment.getAccountId(), environment.getOrgIdentifier(), environment.getProjectIdentifier(),
          environment.getIdentifier(), EventsFrameworkMetadataConstants.CREATE_ACTION);
      return createdEnvironment;
    } catch (DuplicateKeyException ex) {
      throw new DuplicateFieldException(
          getDuplicateServiceExistsErrorMessage(environment.getAccountId(), environment.getOrgIdentifier(),
              environment.getProjectIdentifier(), environment.getIdentifier()),
          USER_SRE, ex);
    }
  }

  String getDuplicateServiceExistsErrorMessage(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String serviceIdentifier) {
    if (EmptyPredicate.isEmpty(orgIdentifier)) {
      return String.format(DUP_KEY_EXP_FORMAT_STRING_FOR_ACCOUNT, serviceIdentifier, accountIdentifier);
    } else if (EmptyPredicate.isEmpty(projectIdentifier)) {
      return String.format(DUP_KEY_EXP_FORMAT_STRING_FOR_ORG, serviceIdentifier, orgIdentifier, accountIdentifier);
    }
    return String.format(
        DUP_KEY_EXP_FORMAT_STRING_FOR_PROJECT, serviceIdentifier, projectIdentifier, orgIdentifier, accountIdentifier);
  }

  @Override
  public Optional<Environment> get(
      String accountId, String orgIdentifier, String projectIdentifier, String environmentIdentifier, boolean deleted) {
    return environmentRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifierAndDeletedNot(
        accountId, orgIdentifier, projectIdentifier, environmentIdentifier, !deleted);
  }

  @Override
  public Environment update(@Valid Environment requestEnvironment) {
    validatePresenceOfRequiredFields(requestEnvironment.getAccountId(), requestEnvironment.getIdentifier());
    setName(requestEnvironment);
    Criteria criteria = getEnvironmentEqualityCriteria(requestEnvironment, requestEnvironment.getDeleted());

    Optional<Environment> environmentOptional =
        get(requestEnvironment.getAccountId(), requestEnvironment.getOrgIdentifier(),
            requestEnvironment.getProjectIdentifier(), requestEnvironment.getIdentifier(), false);
    if (environmentOptional.isPresent()) {
      Environment updatedResult =
          Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
            Environment tempResult = environmentRepository.update(criteria, requestEnvironment);
            if (tempResult == null) {
              throw new InvalidRequestException(String.format(
                  "Environment [%s] under Project[%s], Organization [%s] couldn't be updated or doesn't exist.",
                  requestEnvironment.getIdentifier(), requestEnvironment.getProjectIdentifier(),
                  requestEnvironment.getOrgIdentifier()));
            }
            outboxService.save(EnvironmentUpdatedEvent.builder()
                                   .accountIdentifier(requestEnvironment.getAccountId())
                                   .orgIdentifier(requestEnvironment.getOrgIdentifier())
                                   .projectIdentifier(requestEnvironment.getProjectIdentifier())
                                   .newEnvironment(requestEnvironment)
                                   .resourceType(EnvironmentUpdatedEvent.ResourceType.ENVIRONMENT)
                                   .status(EnvironmentUpdatedEvent.Status.UPDATED)
                                   .oldEnvironment(environmentOptional.get())
                                   .build());
            return tempResult;
          }));
      publishEvent(requestEnvironment.getAccountId(), requestEnvironment.getOrgIdentifier(),
          requestEnvironment.getProjectIdentifier(), requestEnvironment.getIdentifier(),
          EventsFrameworkMetadataConstants.UPDATE_ACTION);
      return updatedResult;
    } else {
      throw new InvalidRequestException(String.format(
          "Environment [%s] under Project[%s], Organization [%s] doesn't exist.", requestEnvironment.getIdentifier(),
          requestEnvironment.getProjectIdentifier(), requestEnvironment.getOrgIdentifier()));
    }
  }

  @Override
  public Environment upsert(Environment requestEnvironment, UpsertOptions upsertOptions) {
    validatePresenceOfRequiredFields(requestEnvironment.getAccountId(), requestEnvironment.getIdentifier());
    setName(requestEnvironment);
    Criteria criteria = getEnvironmentEqualityCriteria(requestEnvironment, requestEnvironment.getDeleted());
    Environment updatedResult = Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
      Environment tempResult = environmentRepository.upsert(criteria, requestEnvironment);
      if (tempResult == null) {
        throw new InvalidRequestException(String.format(
            "Environment [%s] under Project[%s], Organization [%s] couldn't be upserted or doesn't exist.",
            requestEnvironment.getIdentifier(), requestEnvironment.getProjectIdentifier(),
            requestEnvironment.getOrgIdentifier()));
      }
      if (upsertOptions.isSendOutboxEvent()) {
        outboxService.save(EnvironmentUpsertEvent.builder()
                               .accountIdentifier(requestEnvironment.getAccountId())
                               .orgIdentifier(requestEnvironment.getOrgIdentifier())
                               .projectIdentifier(requestEnvironment.getProjectIdentifier())
                               .environment(requestEnvironment)
                               .build());
      }
      return tempResult;
    }));
    publishEvent(requestEnvironment.getAccountId(), requestEnvironment.getOrgIdentifier(),
        requestEnvironment.getProjectIdentifier(), requestEnvironment.getIdentifier(),
        EventsFrameworkMetadataConstants.UPSERT_ACTION);
    return updatedResult;
  }

  @Override
  public Page<Environment> list(Criteria criteria, Pageable pageable) {
    return environmentRepository.findAll(criteria, pageable);
  }

  @Override
  public boolean delete(
      String accountId, String orgIdentifier, String projectIdentifier, String environmentIdentifier, Long version) {
    checkArgument(isNotEmpty(accountId), "accountId must be present");
    checkArgument(isNotEmpty(environmentIdentifier), "environment Identifier must be present");

    Environment environment = Environment.builder()
                                  .accountId(accountId)
                                  .orgIdentifier(orgIdentifier)
                                  .projectIdentifier(projectIdentifier)
                                  .identifier(environmentIdentifier)
                                  .version(version)
                                  .build();
    checkThatEnvironmentIsNotReferredByOthers(environment);
    Criteria criteria = getEnvironmentEqualityCriteria(environment, false);
    Optional<Environment> environmentOptional =
        get(accountId, orgIdentifier, projectIdentifier, environmentIdentifier, false);
    if (environmentOptional.isPresent()) {
      Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
        final boolean deleted = environmentRepository.delete(criteria);
        if (!deleted) {
          throw new InvalidRequestException(
              String.format("Environment [%s] under Project[%s], Organization [%s] couldn't be deleted.",
                  environmentIdentifier, projectIdentifier, orgIdentifier));
        }
        outboxService.save(EnvironmentDeleteEvent.builder()
                               .accountIdentifier(accountId)
                               .orgIdentifier(orgIdentifier)
                               .projectIdentifier(projectIdentifier)
                               .environment(environmentOptional.get())
                               .build());

        return true;
      }));
      publishEvent(accountId, orgIdentifier, projectIdentifier, environmentIdentifier,
          EventsFrameworkMetadataConstants.DELETE_ACTION);
      processDownstreamDeletions(accountId, orgIdentifier, projectIdentifier, environmentIdentifier);
      return true;
    } else {
      throw new InvalidRequestException(
          String.format("Environment [%s] under Project[%s], Organization [%s] doesn't exist.", environmentIdentifier,
              projectIdentifier, orgIdentifier));
    }
  }

  private void processDownstreamDeletions(
      String accountId, String orgIdentifier, String projectIdentifier, String environmentIdentifier) {
    processQuietly(()
                       -> infrastructureEntityService.forceDeleteAllInEnv(
                           accountId, orgIdentifier, projectIdentifier, environmentIdentifier));
    processQuietly(
        () -> clusterService.deleteAllFromEnv(accountId, orgIdentifier, projectIdentifier, environmentIdentifier));
    processQuietly(()
                       -> serviceOverrideService.deleteAllInEnv(
                           accountId, orgIdentifier, projectIdentifier, environmentIdentifier));
  }

  @Override
  public boolean forceDeleteAllInProject(String accountId, String orgIdentifier, String projectIdentifier) {
    checkArgument(isNotEmpty(accountId), "accountId must be present");
    checkArgument(isNotEmpty(orgIdentifier), "orgIdentifier must be present");
    checkArgument(isNotEmpty(projectIdentifier), "project Identifier must be present");

    Criteria criteria = getAllEnvironmentsEqualityCriteriaWithinProject(accountId, orgIdentifier, projectIdentifier);
    return Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
      boolean deleted = environmentRepository.delete(criteria);
      if (!deleted) {
        throw new InvalidRequestException(
            String.format("Environments under Project[%s], Organization [%s] couldn't be deleted.", projectIdentifier,
                orgIdentifier));
      }
      return true;
    }));
  }

  @Override
  public List<Environment> listAccess(Criteria criteria) {
    return environmentRepository.findAllRunTimeAccess(criteria);
  }

  @Override
  public List<String> fetchesNonDeletedEnvIdentifiersFromList(
      String accountId, String orgIdentifier, String projectIdentifier, List<String> envIdentifierList) {
    Criteria criteria = Criteria.where(EnvironmentKeys.accountId)
                            .is(accountId)
                            .and(EnvironmentKeys.orgIdentifier)
                            .is(orgIdentifier)
                            .and(EnvironmentKeys.projectIdentifier)
                            .is(projectIdentifier)
                            .and(EnvironmentKeys.deleted)
                            .is(false)
                            .and(EnvironmentKeys.identifier)
                            .in(envIdentifierList);
    return environmentRepository.fetchesNonDeletedEnvIdentifiersFromList(criteria);
  }

  @Override
  public List<Environment> fetchesNonDeletedEnvironmentFromListOfIdentifiers(
      String accountId, String orgIdentifier, String projectIdentifier, List<String> envIdentifierList) {
    Criteria criteria = Criteria.where(EnvironmentKeys.accountId)
                            .is(accountId)
                            .and(EnvironmentKeys.orgIdentifier)
                            .is(orgIdentifier)
                            .and(EnvironmentKeys.projectIdentifier)
                            .is(projectIdentifier)
                            .and(EnvironmentKeys.deleted)
                            .is(false)
                            .and(EnvironmentKeys.identifier)
                            .in(envIdentifierList);
    return environmentRepository.fetchesNonDeletedEnvironmentFromListOfIdentifiers(criteria);
  }

  @Override
  public String createEnvironmentInputsYaml(
      String accountId, String orgIdentifier, String projectIdentifier, String envIdentifier) {
    Map<String, Object> yamlInputs;
    Optional<Environment> environment = get(accountId, orgIdentifier, projectIdentifier, envIdentifier, false);
    if (environment.isPresent()) {
      if (EmptyPredicate.isEmpty(environment.get().fetchNonEmptyYaml())) {
        throw new InvalidRequestException("Environment yaml cannot be empty");
      }
      yamlInputs = createEnvironmentInputsYamlInternal(environment.get().fetchNonEmptyYaml());
    } else {
      throw new NotFoundException(String.format("Environment with identifier [%s] in project [%s], org [%s] not found",
          envIdentifier, projectIdentifier, orgIdentifier));
    }
    if (isEmpty(yamlInputs)) {
      return null;
    }
    return YamlPipelineUtils.writeYamlString(yamlInputs);
  }

  @Override
  public List<Map<String, String>> getAttributes(
      String accountId, String orgIdentifier, String projectIdentifier, List<String> envIdentifiers) {
    Map<String, List<Environment>> environments =
        fetchesNonDeletedEnvironmentFromListOfIdentifiers(accountId, orgIdentifier, projectIdentifier, envIdentifiers)
            .stream()
            .collect(groupingBy(Environment::getIdentifier));

    List<Map<String, String>> attributes = new ArrayList<>();
    for (String envId : envIdentifiers) {
      if (environments.containsKey(envId)) {
        attributes.add(ImmutableMap.of("type", environments.get(envId).get(0).getType().name()));
      } else {
        attributes.add(Collections.emptyMap());
      }
    }

    return attributes;
  }

  public Map<String, Object> createEnvironmentInputsYamlInternal(String environmentYaml) {
    Map<String, Object> yamlInputs = new HashMap<>();
    try {
      String environmentInputsYaml = RuntimeInputFormHelper.createRuntimeInputForm(environmentYaml, true);
      if (isEmpty(environmentInputsYaml)) {
        return null;
      }
      YamlField environmentYamlField =
          YamlUtils.readTree(environmentInputsYaml).getNode().getField(YamlTypes.ENVIRONMENT_YAML);
      ObjectNode environmentNode = (ObjectNode) environmentYamlField.getNode().getCurrJsonNode();
      yamlInputs.put(YamlTypes.ENVIRONMENT_INPUTS, environmentNode);
    } catch (IOException e) {
      throw new InvalidRequestException("Error occurred while creating environment inputs", e);
    }
    return yamlInputs;
  }

  private void checkThatEnvironmentIsNotReferredByOthers(Environment environment) {
    List<EntityDetail> referredByEntities;
    IdentifierRef identifierRef = IdentifierRef.builder()
                                      .accountIdentifier(environment.getAccountId())
                                      .orgIdentifier(environment.getOrgIdentifier())
                                      .projectIdentifier(environment.getProjectIdentifier())
                                      .identifier(environment.getIdentifier())
                                      .build();
    try {
      Page<EntitySetupUsageDTO> entitySetupUsageDTOS = entitySetupUsageService.listAllEntityUsage(
          0, 10, environment.getAccountId(), identifierRef.getFullyQualifiedName(), EntityType.ENVIRONMENT, "");
      referredByEntities = entitySetupUsageDTOS.stream()
                               .map(EntitySetupUsageDTO::getReferredByEntity)
                               .collect(Collectors.toCollection(LinkedList::new));
    } catch (Exception ex) {
      log.info("Encountered exception while requesting the Entity Reference records of [{}], with exception",
          environment.getIdentifier(), ex);
      throw new UnexpectedException(
          "Error while deleting the Environment as was not able to check entity reference records.");
    }
    if (isNotEmpty(referredByEntities)) {
      throw new InvalidRequestException(String.format(
          "Could not delete the Environment %s as it is referenced by other entities - " + referredByEntities,
          environment.getIdentifier()));
    }
  }

  void validatePresenceOfRequiredFields(Object... fields) {
    Lists.newArrayList(fields).forEach(field -> Objects.requireNonNull(field, "One of the required fields is null."));
  }

  private void setName(Environment requestEnvironment) {
    if (isEmpty(requestEnvironment.getName())) {
      requestEnvironment.setName(requestEnvironment.getIdentifier());
    }
    requestEnvironment.setName(requestEnvironment.getName().trim());
  }

  private Criteria getEnvironmentEqualityCriteria(Environment requestEnvironment, boolean deleted) {
    Criteria criteria = Criteria.where(EnvironmentKeys.accountId)
                            .is(requestEnvironment.getAccountId())
                            .and(EnvironmentKeys.orgIdentifier)
                            .is(requestEnvironment.getOrgIdentifier())
                            .and(EnvironmentKeys.projectIdentifier)
                            .is(requestEnvironment.getProjectIdentifier())
                            .and(EnvironmentKeys.identifier)
                            .is(requestEnvironment.getIdentifier())
                            .and(EnvironmentKeys.deleted)
                            .is(deleted);

    if (requestEnvironment.getVersion() != null) {
      criteria.and(EnvironmentKeys.version).is(requestEnvironment.getVersion());
    }

    return criteria;
  }

  private Criteria getAllEnvironmentsEqualityCriteriaWithinProject(String accountId, String orgId, String projectId) {
    return Criteria.where(EnvironmentKeys.accountId)
        .is(accountId)
        .and(EnvironmentKeys.orgIdentifier)
        .is(orgId)
        .and(EnvironmentKeys.projectIdentifier)
        .is(projectId);
  }

  private void publishEvent(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String identifier, String action) {
    try {
      EntityChangeDTO.Builder environmentChangeEvent = EntityChangeDTO.newBuilder()
                                                           .setAccountIdentifier(StringValue.of(accountIdentifier))
                                                           .setIdentifier(StringValue.of(identifier));
      if (isNotBlank(orgIdentifier)) {
        environmentChangeEvent.setOrgIdentifier(StringValue.of(orgIdentifier));
      }
      if (isNotBlank(projectIdentifier)) {
        environmentChangeEvent.setProjectIdentifier(StringValue.of(projectIdentifier));
      }
      eventProducer.send(
          Message.newBuilder()
              .putAllMetadata(ImmutableMap.of("accountId", accountIdentifier,
                  EventsFrameworkMetadataConstants.ENTITY_TYPE, EventsFrameworkMetadataConstants.ENVIRONMENT_ENTITY,
                  EventsFrameworkMetadataConstants.ACTION, action))
              .setData(environmentChangeEvent.build().toByteString())
              .build());
    } catch (EventsFrameworkDownException e) {
      log.error("Failed to send event to events framework environment Identifier: {}", identifier, e);
    }
  }

  boolean processQuietly(BooleanSupplier b) {
    try {
      return b.getAsBoolean();
    } catch (Exception ex) {
      // ignore this
    }
    return false;
  }

  public EnvironmentInputSetYamlAndServiceOverridesMetadataDTO getEnvironmentsInputYamlAndServiceOverridesMetadata(
      String accountId, String orgIdentifier, String projectIdentifier, List<String> envIdentifiers,
      List<String> serviceIdentifiers) {
    List<EnvironmentInputSetYamlAndServiceOverridesMetadata> environmentInputSetYamlAndServiceOverridesMetadataList =
        new ArrayList<>();
    for (String env : envIdentifiers) {
      Optional<Environment> environment =
          environmentRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifier(
              accountId, orgIdentifier, projectIdentifier, env);
      if (environment.isPresent()) {
        String inputYaml = createEnvironmentInputsYaml(accountId, orgIdentifier, projectIdentifier, env);
        List<ServiceOverridesMetadata> serviceOverridesMetadataList = new ArrayList<>();
        for (String serviceId : serviceIdentifiers) {
          Optional<ServiceEntity> serviceEntity =
              serviceEntityService.getService(accountId, orgIdentifier, projectIdentifier, serviceId);
          if (serviceEntity.isPresent()) {
            String serviceOverrides = serviceOverrideService.createServiceOverrideInputsYaml(
                accountId, orgIdentifier, projectIdentifier, env, serviceId);
            serviceOverridesMetadataList.add(ServiceOverridesMetadata.builder()
                                                 .serviceRef(serviceId)
                                                 .serviceOverridesYaml(serviceOverrides)
                                                 .serviceYaml(serviceEntity.get().getYaml())
                                                 .serviceRuntimeInputYaml(serviceEntityService.createServiceInputsYaml(
                                                     serviceEntity.get().getYaml(), serviceId))
                                                 .build());
          }
        }
        environmentInputSetYamlAndServiceOverridesMetadataList.add(
            EnvironmentInputSetYamlAndServiceOverridesMetadata.builder()
                .envRef(env)
                .envRuntimeInputYaml(inputYaml)
                .servicesOverrides(serviceOverridesMetadataList)
                .envYaml(environment.get().getYaml())
                .build());
      }
    }
    return EnvironmentInputSetYamlAndServiceOverridesMetadataDTO.builder()
        .environmentsInputYamlAndServiceOverrides(environmentInputSetYamlAndServiceOverridesMetadataList)
        .build();
  }

  @Override
  public EnvironmentInputsMergedResponseDto mergeEnvironmentInputs(
      String accountId, String orgId, String projectId, String envId, String oldEnvironmentInputsYaml) {
    Optional<Environment> envEntity = get(accountId, orgId, projectId, envId, false);
    if (envEntity.isEmpty()) {
      throw new NotFoundException(
          format("Environment with identifier [%s] in project [%s], org [%s] not found", envId, projectId, orgId));
    }

    String environmentYaml = envEntity.get().getYaml();
    if (isEmpty(environmentYaml)) {
      return EnvironmentInputsMergedResponseDto.builder().mergedEnvironmentInputsYaml("").environmentYaml("").build();
    }
    try {
      Map<String, Object> yamlInputs = createEnvironmentInputsYamlInternal(environmentYaml);

      String newEnvironmentInputsYaml =
          isEmpty(yamlInputs) ? StringUtils.EMPTY : YamlPipelineUtils.writeYamlString(yamlInputs);
      return EnvironmentInputsMergedResponseDto.builder()
          .mergedEnvironmentInputsYaml(
              InputSetMergeUtility.mergeInputs(oldEnvironmentInputsYaml, newEnvironmentInputsYaml))
          .environmentYaml(environmentYaml)
          .build();
    } catch (Exception ex) {
      throw new InvalidRequestException("Error occurred while merging old and new environment inputs", ex);
    }
  }
}
