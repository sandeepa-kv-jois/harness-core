/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.serviceoverride.services.impl;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.eventsframework.EventsFrameworkConstants.ENTITY_CRUD;
import static io.harness.outbox.TransactionOutboxModule.OUTBOX_TRANSACTION_TEMPLATE;
import static io.harness.springdata.PersistenceUtils.DEFAULT_RETRY_POLICY;

import static com.google.common.base.Preconditions.checkArgument;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.visitor.YamlTypes;
import io.harness.data.structure.EmptyPredicate;
import io.harness.eventsframework.api.Producer;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.entitysetupusage.service.EntitySetupUsageService;
import io.harness.ng.core.events.EnvironmentUpdatedEvent;
import io.harness.ng.core.serviceoverride.beans.NGServiceOverridesEntity;
import io.harness.ng.core.serviceoverride.beans.NGServiceOverridesEntity.NGServiceOverridesEntityKeys;
import io.harness.ng.core.serviceoverride.services.ServiceOverrideService;
import io.harness.ng.core.serviceoverride.yaml.NGServiceOverrideConfig;
import io.harness.outbox.api.OutboxService;
import io.harness.pms.merger.helpers.RuntimeInputFormHelper;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.repositories.serviceoverride.spring.ServiceOverrideRepository;
import io.harness.utils.YamlPipelineUtils;
import io.harness.yaml.core.variables.NGVariable;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.mongodb.client.result.DeleteResult;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.transaction.support.TransactionTemplate;

@OwnedBy(HarnessTeam.CDC)
@Singleton
@Slf4j
public class ServiceOverrideServiceImpl implements ServiceOverrideService {
  private final ServiceOverrideRepository serviceOverrideRepository;
  private final EntitySetupUsageService entitySetupUsageService;
  private final Producer eventProducer;
  private final OutboxService outboxService;
  private final RetryPolicy<Object> transactionRetryPolicy = DEFAULT_RETRY_POLICY;
  @Inject @Named(OUTBOX_TRANSACTION_TEMPLATE) private final TransactionTemplate transactionTemplate;

  @Inject
  public ServiceOverrideServiceImpl(ServiceOverrideRepository serviceOverrideRepository,
      EntitySetupUsageService entitySetupUsageService, @Named(ENTITY_CRUD) Producer eventProducer,
      OutboxService outboxService, TransactionTemplate transactionTemplate) {
    this.serviceOverrideRepository = serviceOverrideRepository;
    this.entitySetupUsageService = entitySetupUsageService;
    this.eventProducer = eventProducer;
    this.outboxService = outboxService;
    this.transactionTemplate = transactionTemplate;
  }

  @Override
  public Optional<NGServiceOverridesEntity> get(
      String accountId, String orgIdentifier, String projectIdentifier, String environmentRef, String serviceRef) {
    return serviceOverrideRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndEnvironmentRefAndServiceRef(
        accountId, orgIdentifier, projectIdentifier, environmentRef, serviceRef);
  }

  @Override
  public NGServiceOverridesEntity upsert(NGServiceOverridesEntity requestServiceOverride) {
    validatePresenceOfRequiredFields(requestServiceOverride.getAccountId(), requestServiceOverride.getOrgIdentifier(),
        requestServiceOverride.getProjectIdentifier(), requestServiceOverride.getEnvironmentRef(),
        requestServiceOverride.getServiceRef());
    validateOverrideValues(requestServiceOverride);
    Criteria criteria = getServiceOverrideEqualityCriteria(requestServiceOverride);

    Optional<NGServiceOverridesEntity> serviceOverrideOptional = get(requestServiceOverride.getAccountId(),
        requestServiceOverride.getOrgIdentifier(), requestServiceOverride.getProjectIdentifier(),
        requestServiceOverride.getEnvironmentRef(), requestServiceOverride.getServiceRef());

    return Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
      NGServiceOverridesEntity tempResult = serviceOverrideRepository.upsert(criteria, requestServiceOverride);
      if (tempResult == null) {
        throw new InvalidRequestException(String.format(
            "NGServiceOverridesEntity under Project[%s], Organization [%s], Environment [%s] and Service [%s] couldn't be upserted or doesn't exist.",
            requestServiceOverride.getProjectIdentifier(), requestServiceOverride.getOrgIdentifier(),
            requestServiceOverride.getEnvironmentRef(), requestServiceOverride.getServiceRef()));
      }
      if (serviceOverrideOptional.isPresent()) {
        outboxService.save(EnvironmentUpdatedEvent.builder()
                               .accountIdentifier(requestServiceOverride.getAccountId())
                               .orgIdentifier(requestServiceOverride.getOrgIdentifier())
                               .status(EnvironmentUpdatedEvent.Status.UPDATED)
                               .resourceType(EnvironmentUpdatedEvent.ResourceType.SERVICE_OVERRIDE)
                               .projectIdentifier(requestServiceOverride.getProjectIdentifier())
                               .newServiceOverridesEntity(requestServiceOverride)
                               .oldServiceOverridesEntity(serviceOverrideOptional.get())
                               .build());
      } else {
        outboxService.save(EnvironmentUpdatedEvent.builder()
                               .accountIdentifier(requestServiceOverride.getAccountId())
                               .orgIdentifier(requestServiceOverride.getOrgIdentifier())
                               .status(EnvironmentUpdatedEvent.Status.CREATED)
                               .resourceType(EnvironmentUpdatedEvent.ResourceType.SERVICE_OVERRIDE)
                               .projectIdentifier(requestServiceOverride.getProjectIdentifier())
                               .newServiceOverridesEntity(requestServiceOverride)
                               .build());
      }

      return tempResult;
    }));
  }

  void validateOverrideValues(NGServiceOverridesEntity requestServiceOverride) {
    List<NGVariable> variableOverrides = null;
    if (EmptyPredicate.isNotEmpty(requestServiceOverride.getYaml())) {
      try {
        final NGServiceOverrideConfig config =
            YamlPipelineUtils.read(requestServiceOverride.getYaml(), NGServiceOverrideConfig.class);
        variableOverrides = config.getServiceOverrideInfoConfig().getVariables();
      } catch (IOException e) {
        throw new InvalidRequestException("Cannot create Service Overrides config due to " + e.getMessage());
      }
    }
    if (variableOverrides != null) {
      variableOverrides.removeIf(Objects::isNull);
      Set<String> variableKeys = new HashSet<>();
      Set<String> duplicates = new HashSet<>();
      int emptyOverrides = 0;
      for (NGVariable variableOverride : variableOverrides) {
        if (StringUtils.isBlank(variableOverride.getName())) {
          emptyOverrides++;
        } else if (!variableKeys.add(variableOverride.getName())) {
          duplicates.add(variableOverride.getName());
        }
      }
      if (emptyOverrides != 0) {
        String plural = emptyOverrides == 1 ? "" : "s";
        throw new InvalidRequestException(
            String.format("Empty variable name%s for %s variable override%s in service ref: [%s]", plural,
                emptyOverrides, plural, requestServiceOverride.getServiceRef()));
      }
      if (!duplicates.isEmpty()) {
        throw new InvalidRequestException(String.format("Duplicate Service overrides provided: [%s] for service: [%s]",
            Joiner.on(",").skipNulls().join(duplicates), requestServiceOverride.getServiceRef()));
      }
    }
  }

  @Override
  public Page<NGServiceOverridesEntity> list(Criteria criteria, Pageable pageable) {
    return serviceOverrideRepository.findAll(criteria, pageable);
  }

  @Override
  public boolean delete(
      String accountId, String orgIdentifier, String projectIdentifier, String environmentRef, String serviceRef) {
    NGServiceOverridesEntity serviceOverridesEntity = NGServiceOverridesEntity.builder()
                                                          .accountId(accountId)
                                                          .orgIdentifier(orgIdentifier)
                                                          .projectIdentifier(projectIdentifier)
                                                          .environmentRef(environmentRef)
                                                          .serviceRef(serviceRef)
                                                          .build();

    // todo: check for override usage in pipelines
    Criteria criteria = getServiceOverrideEqualityCriteria(serviceOverridesEntity);

    Optional<NGServiceOverridesEntity> entityOptional =
        get(accountId, orgIdentifier, projectIdentifier, environmentRef, serviceRef);
    if (entityOptional.isPresent()) {
      return Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
        DeleteResult deleteResult = serviceOverrideRepository.delete(criteria);
        if (!deleteResult.wasAcknowledged() || deleteResult.getDeletedCount() != 1) {
          throw new InvalidRequestException(String.format(
              "Service Override for Service [%s], Environment [%s], Project[%s], Organization [%s] couldn't be deleted.",
              serviceRef, environmentRef, projectIdentifier, orgIdentifier));
        }
        outboxService.save(EnvironmentUpdatedEvent.builder()
                               .accountIdentifier(accountId)
                               .orgIdentifier(orgIdentifier)
                               .projectIdentifier(projectIdentifier)
                               .status(EnvironmentUpdatedEvent.Status.DELETED)
                               .resourceType(EnvironmentUpdatedEvent.ResourceType.SERVICE_OVERRIDE)
                               .oldServiceOverridesEntity(entityOptional.get())
                               .build());
        return true;
      }));
    } else {
      throw new InvalidRequestException(String.format(
          "Service Override for Service [%s], Environment [%s], Project[%s], Organization [%s] doesn't exist.",
          serviceRef, environmentRef, projectIdentifier, orgIdentifier));
    }
  }

  @Override
  public boolean deleteAllInEnv(
      String accountId, String orgIdentifier, String projectIdentifier, String environmentRef) {
    checkArgument(isNotEmpty(accountId), "accountId must be present");
    checkArgument(isNotEmpty(orgIdentifier), "orgId must be present");
    checkArgument(isNotEmpty(projectIdentifier), "projectId must be present");
    checkArgument(isNotEmpty(environmentRef), "environment ref must be present");

    Criteria criteria =
        getServiceOverrideEqualityCriteriaForEnv(accountId, orgIdentifier, projectIdentifier, environmentRef);
    DeleteResult delete = serviceOverrideRepository.delete(criteria);
    return delete.wasAcknowledged();
  }

  @Override
  public boolean deleteAllInProject(String accountId, String orgIdentifier, String projectIdentifier) {
    checkArgument(isNotEmpty(accountId), "accountId must be present");
    checkArgument(isNotEmpty(orgIdentifier), "orgId must be present");
    checkArgument(isNotEmpty(projectIdentifier), "projectId must be present");

    Criteria criteria = getServiceOverrideEqualityCriteriaForProj(accountId, orgIdentifier, projectIdentifier);
    DeleteResult delete = serviceOverrideRepository.delete(criteria);
    return delete.wasAcknowledged();
  }

  @Override
  public boolean deleteAllInProjectForAService(
      String accountId, String orgIdentifier, String projectIdentifier, String serviceRef) {
    checkArgument(isNotEmpty(accountId), "accountId must be present");
    checkArgument(isNotEmpty(orgIdentifier), "orgId must be present");
    checkArgument(isNotEmpty(projectIdentifier), "projectId must be present");
    checkArgument(isNotEmpty(serviceRef), "service ref must be present");

    Criteria criteria =
        getServiceOverrideEqualityCriteriaForService(accountId, orgIdentifier, projectIdentifier, serviceRef);
    DeleteResult delete = serviceOverrideRepository.delete(criteria);
    return delete.wasAcknowledged() && delete.getDeletedCount() > 0;
  }

  @Override
  public String createServiceOverrideInputsYaml(String accountId, String orgIdentifier, String projectIdentifier,
      String environmentIdentifier, String serviceIdentifier) {
    Map<String, Object> yamlInputs = createServiceOverrideInputsYamlInternal(
        accountId, orgIdentifier, projectIdentifier, environmentIdentifier, serviceIdentifier);
    if (isEmpty(yamlInputs)) {
      return null;
    }
    return YamlPipelineUtils.writeYamlString(yamlInputs);
  }

  public Map<String, Object> createServiceOverrideInputsYamlInternal(String accountId, String orgIdentifier,
      String projectIdentifier, String envIdentifier, String serviceIdentifier) {
    Map<String, Object> yamlInputs = new HashMap<>();
    Optional<NGServiceOverridesEntity> serviceOverridesEntityOptional =
        get(accountId, orgIdentifier, projectIdentifier, envIdentifier, serviceIdentifier);
    if (serviceOverridesEntityOptional.isPresent()) {
      String yaml = serviceOverridesEntityOptional.get().getYaml();
      if (isEmpty(yaml)) {
        throw new InvalidRequestException("Service overrides yaml is empty.");
      }
      try {
        String serviceOverrideInputs = RuntimeInputFormHelper.createRuntimeInputForm(yaml, true);
        if (isEmpty(serviceOverrideInputs)) {
          return null;
        }
        YamlField serviceOverridesYamlField =
            YamlUtils.readTree(serviceOverrideInputs).getNode().getField(YamlTypes.SERVICE_OVERRIDE);
        ObjectNode serviceOverridesNode = (ObjectNode) serviceOverridesYamlField.getNode().getCurrJsonNode();

        yamlInputs.put(YamlTypes.SERVICE_OVERRIDE_INPUTS, serviceOverridesNode);
      } catch (IOException e) {
        throw new InvalidRequestException("Error occurred while creating Service Override inputs ", e);
      }
    }
    return yamlInputs;
  }

  void validatePresenceOfRequiredFields(Object... fields) {
    Lists.newArrayList(fields).forEach(field -> Objects.requireNonNull(field, "One of the required fields is null."));
  }

  private Criteria getServiceOverrideEqualityCriteria(NGServiceOverridesEntity requestServiceOverride) {
    return Criteria.where(NGServiceOverridesEntityKeys.accountId)
        .is(requestServiceOverride.getAccountId())
        .and(NGServiceOverridesEntityKeys.orgIdentifier)
        .is(requestServiceOverride.getOrgIdentifier())
        .and(NGServiceOverridesEntityKeys.projectIdentifier)
        .is(requestServiceOverride.getProjectIdentifier())
        .and(NGServiceOverridesEntityKeys.environmentRef)
        .is(requestServiceOverride.getEnvironmentRef())
        .and(NGServiceOverridesEntityKeys.serviceRef)
        .is(requestServiceOverride.getServiceRef());
  }

  private Criteria getServiceOverrideEqualityCriteriaForEnv(
      String accountId, String orgId, String projId, String envId) {
    return Criteria.where(NGServiceOverridesEntityKeys.accountId)
        .is(accountId)
        .and(NGServiceOverridesEntityKeys.orgIdentifier)
        .is(orgId)
        .and(NGServiceOverridesEntityKeys.projectIdentifier)
        .is(projId)
        .and(NGServiceOverridesEntityKeys.environmentRef)
        .is(envId);
  }

  private Criteria getServiceOverrideEqualityCriteriaForService(
      String accountId, String orgId, String projId, String serviceId) {
    return Criteria.where(NGServiceOverridesEntityKeys.accountId)
        .is(accountId)
        .and(NGServiceOverridesEntityKeys.orgIdentifier)
        .is(orgId)
        .and(NGServiceOverridesEntityKeys.projectIdentifier)
        .is(projId)
        .and(NGServiceOverridesEntityKeys.serviceRef)
        .is(serviceId);
  }

  private Criteria getServiceOverrideEqualityCriteriaForProj(String accountId, String orgId, String projId) {
    return Criteria.where(NGServiceOverridesEntityKeys.accountId)
        .is(accountId)
        .and(NGServiceOverridesEntityKeys.orgIdentifier)
        .is(orgId)
        .and(NGServiceOverridesEntityKeys.projectIdentifier)
        .is(projId);
  }
}
