/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.service.services.impl;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.eventsframework.EventsFrameworkConstants.ENTITY_CRUD;
import static io.harness.exception.WingsException.USER_SRE;
import static io.harness.outbox.TransactionOutboxModule.OUTBOX_TRANSACTION_TEMPLATE;
import static io.harness.springdata.PersistenceUtils.DEFAULT_RETRY_POLICY;

import static com.google.common.base.Preconditions.checkArgument;
import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.EntityType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.visitor.YamlTypes;
import io.harness.common.NGExpressionUtils;
import io.harness.data.structure.EmptyPredicate;
import io.harness.eventsframework.EventsFrameworkMetadataConstants;
import io.harness.eventsframework.api.EventsFrameworkDownException;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.entity_crud.EntityChangeDTO;
import io.harness.eventsframework.producer.Message;
import io.harness.exception.DuplicateFieldException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.ReferencedEntityException;
import io.harness.exception.UnexpectedException;
import io.harness.exception.YamlException;
import io.harness.expression.EngineExpressionEvaluator;
import io.harness.ng.DuplicateKeyExceptionParser;
import io.harness.ng.core.EntityDetail;
import io.harness.ng.core.entitysetupusage.dto.EntitySetupUsageDTO;
import io.harness.ng.core.entitysetupusage.service.EntitySetupUsageService;
import io.harness.ng.core.events.ServiceCreateEvent;
import io.harness.ng.core.events.ServiceDeleteEvent;
import io.harness.ng.core.events.ServiceUpdateEvent;
import io.harness.ng.core.events.ServiceUpsertEvent;
import io.harness.ng.core.service.entity.ArtifactSourcesResponseDTO;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.entity.ServiceEntity.ServiceEntityKeys;
import io.harness.ng.core.service.entity.ServiceInputsMergedResponseDto;
import io.harness.ng.core.service.mappers.ServiceFilterHelper;
import io.harness.ng.core.service.services.ServiceEntityService;
import io.harness.ng.core.serviceoverride.services.ServiceOverrideService;
import io.harness.ng.core.utils.CoreCriteriaUtils;
import io.harness.outbox.api.OutboxService;
import io.harness.pms.merger.helpers.RuntimeInputFormHelper;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlNodeUtils;
import io.harness.pms.yaml.YamlUtils;
import io.harness.repositories.UpsertOptions;
import io.harness.repositories.service.spring.ServiceRepository;
import io.harness.utils.PageUtils;
import io.harness.utils.YamlPipelineUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.protobuf.StringValue;
import com.mongodb.client.result.DeleteResult;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;
import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.ws.rs.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.json.JSONObject;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.transaction.support.TransactionTemplate;

@OwnedBy(PIPELINE)
@Singleton
@Slf4j
public class ServiceEntityServiceImpl implements ServiceEntityService {
  private final ServiceRepository serviceRepository;
  private final EntitySetupUsageService entitySetupUsageService;
  private final Producer eventProducer;
  private static final Integer QUERY_PAGE_SIZE = 10000;
  @Inject @Named(OUTBOX_TRANSACTION_TEMPLATE) private final TransactionTemplate transactionTemplate;
  private final OutboxService outboxService;
  private final RetryPolicy<Object> transactionRetryPolicy = DEFAULT_RETRY_POLICY;
  private final ServiceOverrideService serviceOverrideService;
  private final ServiceEntitySetupUsageHelper entitySetupUsageHelper;

  private static final String DUP_KEY_EXP_FORMAT_STRING_FOR_PROJECT =
      "Service [%s] under Project[%s], Organization [%s] in Account [%s] already exists";
  private static final String DUP_KEY_EXP_FORMAT_STRING_FOR_ORG =
      "Service [%s] under Organization [%s] in Account [%s] already exists";
  private static final String DUP_KEY_EXP_FORMAT_STRING_FOR_ACCOUNT = "Service [%s] in Account [%s] already exists";

  @Inject
  public ServiceEntityServiceImpl(ServiceRepository serviceRepository, EntitySetupUsageService entitySetupUsageService,
      @Named(ENTITY_CRUD) Producer eventProducer, OutboxService outboxService, TransactionTemplate transactionTemplate,
      ServiceOverrideService serviceOverrideService, ServiceEntitySetupUsageHelper entitySetupUsageHelper) {
    this.serviceRepository = serviceRepository;
    this.entitySetupUsageService = entitySetupUsageService;
    this.eventProducer = eventProducer;
    this.outboxService = outboxService;
    this.transactionTemplate = transactionTemplate;
    this.serviceOverrideService = serviceOverrideService;
    this.entitySetupUsageHelper = entitySetupUsageHelper;
  }

  void validatePresenceOfRequiredFields(Object... fields) {
    Lists.newArrayList(fields).forEach(field -> Objects.requireNonNull(field, "One of the required fields is null."));
  }

  @Override
  public ServiceEntity create(@NotNull @Valid ServiceEntity serviceEntity) {
    try {
      validatePresenceOfRequiredFields(serviceEntity.getAccountId(), serviceEntity.getIdentifier());
      setNameIfNotPresent(serviceEntity);
      modifyServiceRequest(serviceEntity);
      ServiceEntity createdService =
          Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
            ServiceEntity service = serviceRepository.save(serviceEntity);
            outboxService.save(ServiceCreateEvent.builder()
                                   .accountIdentifier(serviceEntity.getAccountId())
                                   .orgIdentifier(serviceEntity.getOrgIdentifier())
                                   .projectIdentifier(serviceEntity.getProjectIdentifier())
                                   .service(serviceEntity)
                                   .build());
            return service;
          }));
      entitySetupUsageHelper.updateSetupUsages(createdService);
      publishEvent(serviceEntity.getAccountId(), serviceEntity.getOrgIdentifier(), serviceEntity.getProjectIdentifier(),
          serviceEntity.getIdentifier(), EventsFrameworkMetadataConstants.CREATE_ACTION);
      return createdService;
    } catch (DuplicateKeyException ex) {
      throw new DuplicateFieldException(
          getDuplicateServiceExistsErrorMessage(serviceEntity.getAccountId(), serviceEntity.getOrgIdentifier(),
              serviceEntity.getProjectIdentifier(), serviceEntity.getIdentifier()),
          USER_SRE, ex);
    }
  }

  @Override
  public Optional<ServiceEntity> get(
      String accountId, String orgIdentifier, String projectIdentifier, String serviceIdentifier, boolean deleted) {
    return serviceRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifierAndDeletedNot(
        accountId, orgIdentifier, projectIdentifier, serviceIdentifier, !deleted);
  }

  @Override
  public ServiceEntity update(@Valid ServiceEntity requestService) {
    validatePresenceOfRequiredFields(requestService.getAccountId(), requestService.getIdentifier());
    setNameIfNotPresent(requestService);
    modifyServiceRequest(requestService);
    Criteria criteria = getServiceEqualityCriteria(requestService, requestService.getDeleted());
    Optional<ServiceEntity> serviceEntityOptional =
        get(requestService.getAccountId(), requestService.getOrgIdentifier(), requestService.getProjectIdentifier(),
            requestService.getIdentifier(), false);
    if (serviceEntityOptional.isPresent()) {
      ServiceEntity oldService = serviceEntityOptional.get();
      ServiceEntity updatedService =
          Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
            ServiceEntity updatedResult = serviceRepository.update(criteria, requestService);
            if (updatedResult == null) {
              throw new InvalidRequestException(String.format(
                  "Service [%s] under Project[%s], Organization [%s] couldn't be updated or doesn't exist.",
                  requestService.getIdentifier(), requestService.getProjectIdentifier(),
                  requestService.getOrgIdentifier()));
            }
            outboxService.save(ServiceUpdateEvent.builder()
                                   .accountIdentifier(requestService.getAccountId())
                                   .orgIdentifier(requestService.getOrgIdentifier())
                                   .projectIdentifier(requestService.getProjectIdentifier())
                                   .newService(updatedResult)
                                   .oldService(oldService)
                                   .build());
            return updatedResult;
          }));
      entitySetupUsageHelper.updateSetupUsages(updatedService);
      publishEvent(requestService.getAccountId(), requestService.getOrgIdentifier(),
          requestService.getProjectIdentifier(), requestService.getIdentifier(),
          EventsFrameworkMetadataConstants.UPDATE_ACTION);
      return updatedService;
    } else {
      throw new InvalidRequestException(String.format(
          "Service [%s] under Project[%s], Organization [%s] doesn't exist.", requestService.getIdentifier(),
          requestService.getProjectIdentifier(), requestService.getOrgIdentifier()));
    }
  }

  @Override
  public ServiceEntity upsert(@Valid ServiceEntity requestService, UpsertOptions upsertOptions) {
    validatePresenceOfRequiredFields(requestService.getAccountId(), requestService.getIdentifier());
    setNameIfNotPresent(requestService);
    modifyServiceRequest(requestService);
    Criteria criteria = getServiceEqualityCriteria(requestService, requestService.getDeleted());
    ServiceEntity upsertedService =
        Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
          ServiceEntity result = serviceRepository.upsert(criteria, requestService);
          if (result == null) {
            throw new InvalidRequestException(
                String.format("Service [%s] under Project[%s], Organization [%s] couldn't be upserted.",
                    requestService.getIdentifier(), requestService.getProjectIdentifier(),
                    requestService.getOrgIdentifier()));
          }
          if (upsertOptions.isSendOutboxEvent()) {
            outboxService.save(ServiceUpsertEvent.builder()
                                   .accountIdentifier(requestService.getAccountId())
                                   .orgIdentifier(requestService.getOrgIdentifier())
                                   .projectIdentifier(requestService.getProjectIdentifier())
                                   .service(requestService)
                                   .build());
          }
          return result;
        }));
    if (upsertOptions.isPublishSetupUsages()) {
      entitySetupUsageHelper.updateSetupUsages(upsertedService);
    }
    publishEvent(requestService.getAccountId(), requestService.getOrgIdentifier(),
        requestService.getProjectIdentifier(), requestService.getIdentifier(),
        EventsFrameworkMetadataConstants.UPSERT_ACTION);
    return upsertedService;
  }

  @Override
  public Page<ServiceEntity> list(@NotNull Criteria criteria, @NotNull Pageable pageable) {
    return serviceRepository.findAll(criteria, pageable);
  }

  @Override
  public List<ServiceEntity> listRunTimePermission(Criteria criteria) {
    return serviceRepository.findAllRunTimePermission(criteria);
  }

  @Override
  public boolean delete(
      String accountId, String orgIdentifier, String projectIdentifier, String serviceIdentifier, Long version) {
    checkArgument(isNotEmpty(accountId), "accountId must be present");
    checkArgument(isNotEmpty(serviceIdentifier), "serviceIdentifier must be present");

    ServiceEntity serviceEntity = ServiceEntity.builder()
                                      .accountId(accountId)
                                      .orgIdentifier(orgIdentifier)
                                      .projectIdentifier(projectIdentifier)
                                      .identifier(serviceIdentifier)
                                      .version(version)
                                      .build();
    checkThatServiceIsNotReferredByOthers(serviceEntity);
    Criteria criteria = getServiceEqualityCriteria(serviceEntity, false);
    Optional<ServiceEntity> serviceEntityOptional =
        get(accountId, orgIdentifier, projectIdentifier, serviceIdentifier, false);

    if (serviceEntityOptional.isPresent()) {
      boolean success = Failsafe.with(DEFAULT_RETRY_POLICY).get(() -> transactionTemplate.execute(status -> {
        ServiceEntity serviceEntityRetrieved = serviceEntityOptional.get();
        final boolean deleted = serviceRepository.delete(criteria);
        if (!deleted) {
          throw new InvalidRequestException(
              String.format("Service [%s] under Project[%s], Organization [%s] couldn't be deleted.",
                  serviceEntity.getIdentifier(), projectIdentifier, orgIdentifier));
        }

        outboxService.save(new ServiceDeleteEvent(accountId, serviceEntityRetrieved.getOrgIdentifier(),
            serviceEntityRetrieved.getProjectIdentifier(), serviceEntityRetrieved));
        return true;
      }));
      processQuietly(()
                         -> serviceOverrideService.deleteAllInProjectForAService(
                             accountId, orgIdentifier, projectIdentifier, serviceIdentifier));
      entitySetupUsageHelper.deleteSetupUsages(serviceEntityOptional.get());
      publishEvent(accountId, orgIdentifier, projectIdentifier, serviceIdentifier,
          EventsFrameworkMetadataConstants.DELETE_ACTION);
      return success;
    } else {
      throw new InvalidRequestException(
          String.format("Service [%s] under Project[%s], Organization [%s] doesn't exist.", serviceIdentifier,
              projectIdentifier, orgIdentifier));
    }
  }

  private void publishEvent(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String identifier, String action) {
    try {
      EntityChangeDTO.Builder serviceChangeEvent = EntityChangeDTO.newBuilder()
                                                       .setAccountIdentifier(StringValue.of(accountIdentifier))
                                                       .setIdentifier(StringValue.of(identifier));
      if (isNotBlank(orgIdentifier)) {
        serviceChangeEvent.setOrgIdentifier(StringValue.of(orgIdentifier));
      }
      if (isNotBlank(projectIdentifier)) {
        serviceChangeEvent.setProjectIdentifier(StringValue.of(projectIdentifier));
      }
      eventProducer.send(
          Message.newBuilder()
              .putAllMetadata(
                  ImmutableMap.of("accountId", accountIdentifier, EventsFrameworkMetadataConstants.ENTITY_TYPE,
                      EventsFrameworkMetadataConstants.SERVICE_ENTITY, EventsFrameworkMetadataConstants.ACTION, action))
              .setData(serviceChangeEvent.build().toByteString())
              .build());
    } catch (EventsFrameworkDownException e) {
      log.error("Failed to send event to events framework service Identifier: {}", identifier, e);
    }
  }

  private void checkThatServiceIsNotReferredByOthers(ServiceEntity serviceEntity) {
    List<EntityDetail> referredByEntities;
    IdentifierRef identifierRef = IdentifierRef.builder()
                                      .accountIdentifier(serviceEntity.getAccountId())
                                      .orgIdentifier(serviceEntity.getOrgIdentifier())
                                      .projectIdentifier(serviceEntity.getProjectIdentifier())
                                      .identifier(serviceEntity.getIdentifier())
                                      .build();
    try {
      Page<EntitySetupUsageDTO> entitySetupUsageDTOS = entitySetupUsageService.listAllEntityUsage(
          0, 10, serviceEntity.getAccountId(), identifierRef.getFullyQualifiedName(), EntityType.SERVICE, "");
      referredByEntities = entitySetupUsageDTOS.stream()
                               .map(EntitySetupUsageDTO::getReferredByEntity)
                               .collect(Collectors.toCollection(LinkedList::new));
    } catch (Exception ex) {
      log.info("Encountered exception while requesting the Entity Reference records of [{}], with exception",
          serviceEntity.getIdentifier(), ex);
      throw new UnexpectedException(
          "Error while deleting the Service as was not able to check entity reference records.");
    }
    if (isNotEmpty(referredByEntities)) {
      throw new ReferencedEntityException(String.format(
          "The service %s cannot be deleted because it is being referenced in %d %s. To delete your service, please remove the reference service from these entities.",
          serviceEntity.getIdentifier(), referredByEntities.size(),
          referredByEntities.size() > 1 ? "entities" : "entity"));
    }
  }

  private void setNameIfNotPresent(ServiceEntity requestService) {
    if (isEmpty(requestService.getName())) {
      requestService.setName(requestService.getIdentifier());
    }
  }

  private Criteria getServiceEqualityCriteria(@Valid ServiceEntity requestService, boolean deleted) {
    Criteria criteria = Criteria.where(ServiceEntityKeys.accountId)
                            .is(requestService.getAccountId())
                            .and(ServiceEntityKeys.orgIdentifier)
                            .is(requestService.getOrgIdentifier())
                            .and(ServiceEntityKeys.projectIdentifier)
                            .is(requestService.getProjectIdentifier())
                            .and(ServiceEntityKeys.identifier)
                            .is(requestService.getIdentifier())
                            .and(ServiceEntityKeys.deleted)
                            .is(deleted);
    if (requestService.getVersion() != null) {
      criteria.and(ServiceEntityKeys.version).is(requestService.getVersion());
    }
    return criteria;
  }

  @Override
  public Page<ServiceEntity> bulkCreate(String accountId, @NotNull List<ServiceEntity> serviceEntities) {
    try {
      validateTheServicesList(serviceEntities);
      populateDefaultNameIfNotPresent(serviceEntities);
      modifyServiceRequestBatch(serviceEntities);
      List<ServiceEntity> outputServiceEntitiesList = (List<ServiceEntity>) serviceRepository.saveAll(serviceEntities);
      for (ServiceEntity serviceEntity : serviceEntities) {
        publishEvent(serviceEntity.getAccountId(), serviceEntity.getOrgIdentifier(),
            serviceEntity.getProjectIdentifier(), serviceEntity.getIdentifier(),
            EventsFrameworkMetadataConstants.CREATE_ACTION);
      }
      return new PageImpl<>(outputServiceEntitiesList);
    } catch (DuplicateKeyException ex) {
      throw new DuplicateFieldException(
          getDuplicateServiceExistsErrorMessage(accountId, ex.getMessage()), USER_SRE, ex);
    } catch (Exception ex) {
      String serviceNames = serviceEntities.stream().map(ServiceEntity::getName).collect(Collectors.joining(","));
      log.info(
          "Encountered exception while saving the service entity records of [{}], with exception", serviceNames, ex);
      throw new UnexpectedException("Encountered exception while saving the service entity records.");
    }
  }

  @Override
  public List<ServiceEntity> getAllServices(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    return getAllServices(
        accountIdentifier, orgIdentifier, projectIdentifier, QUERY_PAGE_SIZE, true, new ArrayList<>());
  }

  @Override
  public List<ServiceEntity> getAllNonDeletedServices(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, List<String> sort) {
    return getAllServices(accountIdentifier, orgIdentifier, projectIdentifier, QUERY_PAGE_SIZE, false, sort);
  }

  @Override
  public List<ServiceEntity> getServices(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, List<String> serviceIdentifiers) {
    if (isEmpty(serviceIdentifiers)) {
      return emptyList();
    }
    Criteria criteria = Criteria.where(ServiceEntityKeys.accountId)
                            .is(accountIdentifier)
                            .and(ServiceEntityKeys.orgIdentifier)
                            .is(orgIdentifier)
                            .and(ServiceEntityKeys.projectIdentifier)
                            .is(projectIdentifier)
                            .and(ServiceEntityKeys.identifier)
                            .in(serviceIdentifiers);

    return serviceRepository.findAll(criteria);
  }

  @Override
  public boolean isServiceField(String fieldName, JsonNode serviceValue) {
    return YamlTypes.SERVICE_ENTITY.equals(fieldName) && serviceValue.isObject()
        && serviceValue.get(YamlTypes.SERVICE_REF) != null;
  }

  @Override
  public Integer findActiveServicesCountAtGivenTimestamp(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, long timestampInMs) {
    if (timestampInMs <= 0) {
      throw new InvalidRequestException("Invalid timestamp while fetching active services count : " + timestampInMs);
    }
    return serviceRepository
        .findActiveServiceCountAtGivenTimestamp(accountIdentifier, orgIdentifier, projectIdentifier, timestampInMs)
        .intValue();
  }

  @Override
  public String createServiceInputsYaml(String yaml, String serviceIdentifier) {
    return createServiceInputsYamlInternal(yaml, serviceIdentifier, null);
  }

  @Override
  public String createServiceInputsYamlGivenPrimaryArtifactRef(
      String serviceYaml, String serviceIdentifier, String primaryArtifactRef) {
    return createServiceInputsYamlInternal(serviceYaml, serviceIdentifier, primaryArtifactRef);
  }

  private String createServiceInputsYamlInternal(
      String serviceYaml, String serviceIdentifier, String primaryArtifactRef) {
    Map<String, Object> serviceInputs = new HashMap<>();

    try {
      YamlField serviceYamlField = YamlUtils.readTree(serviceYaml).getNode().getField(YamlTypes.SERVICE_ENTITY);
      if (serviceYamlField == null) {
        throw new YamlException(
            String.format("Yaml provided for service %s does not have service root field.", serviceIdentifier));
      }

      modifyServiceDefinitionNodeBeforeCreatingServiceInputs(serviceYamlField, serviceIdentifier, primaryArtifactRef);
      ObjectNode serviceNode = (ObjectNode) serviceYamlField.getNode().getCurrJsonNode();
      ObjectNode serviceDefinitionNode = serviceNode.retain(YamlTypes.SERVICE_DEFINITION);
      if (EmptyPredicate.isEmpty(serviceDefinitionNode)) {
        return null;
      }
      String serviceDefinition = serviceDefinitionNode.toString();
      String serviceDefinitionInputs = RuntimeInputFormHelper.createTemplateFromYaml(serviceDefinition);
      if (isEmpty(serviceDefinitionInputs)) {
        return serviceDefinitionInputs;
      }
      JsonNode serviceDefinitionInputNode = YamlUtils.readTree(serviceDefinitionInputs).getNode().getCurrJsonNode();
      serviceInputs.put(YamlTypes.SERVICE_INPUTS, serviceDefinitionInputNode);
      return YamlPipelineUtils.writeYamlString(serviceInputs);
    } catch (IOException e) {
      throw new InvalidRequestException(
          String.format("Error occurred while creating service inputs for service %s", serviceIdentifier), e);
    }
  }

  @Override
  public ArtifactSourcesResponseDTO getArtifactSourceInputs(String yaml, String serviceIdentifier) {
    try {
      YamlField serviceYamlField = YamlUtils.readTree(yaml).getNode().getField(YamlTypes.SERVICE_ENTITY);
      if (serviceYamlField == null) {
        throw new YamlException(
            String.format("Yaml provided for service %s does not have service root field.", serviceIdentifier));
      }

      YamlField primaryArtifactField = ServiceFilterHelper.getPrimaryArtifactNodeFromServiceYaml(serviceYamlField);
      if (primaryArtifactField == null) {
        return ArtifactSourcesResponseDTO.builder().build();
      }

      YamlField artifactSourcesField = primaryArtifactField.getNode().getField(YamlTypes.ARTIFACT_SOURCES);
      if (artifactSourcesField == null) {
        return ArtifactSourcesResponseDTO.builder().build();
      }
      List<String> artifactSourceIdentifiers = new ArrayList<>();
      Map<String, String> sourceIdentifierToSourceInputMap = new HashMap<>();

      List<YamlNode> artifactSources = artifactSourcesField.getNode().asArray();
      for (YamlNode artifactSource : artifactSources) {
        String sourceIdentifier = artifactSource.getIdentifier();
        artifactSourceIdentifiers.add(sourceIdentifier);
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode tempSourceNode = objectMapper.createObjectNode();
        // Adding root node because RuntimeInputFormHelper.createTemplateFromYaml() method requires root node.
        tempSourceNode.set(YamlTypes.ARTIFACT_SOURCES, artifactSource.getCurrJsonNode());
        String runtimeInputFormWithSourcesRootNode =
            RuntimeInputFormHelper.createTemplateFromYaml(tempSourceNode.toString());
        if (EmptyPredicate.isNotEmpty(runtimeInputFormWithSourcesRootNode)) {
          JsonNode runtimeInputFormNode =
              YamlUtils.readTree(runtimeInputFormWithSourcesRootNode).getNode().getCurrJsonNode();
          sourceIdentifierToSourceInputMap.put(sourceIdentifier,
              YamlPipelineUtils.writeYamlString(runtimeInputFormNode.get(YamlTypes.ARTIFACT_SOURCES)));
        }
      }

      return ArtifactSourcesResponseDTO.builder()
          .sourceIdentifiers(artifactSourceIdentifiers)
          .sourceIdentifierToSourceInputMap(sourceIdentifierToSourceInputMap)
          .build();
    } catch (IOException e) {
      throw new InvalidRequestException(
          String.format("Error occurred while creating service inputs for service %s", serviceIdentifier), e);
    }
  }

  @Override
  public ServiceInputsMergedResponseDto mergeServiceInputs(
      String accountId, String orgId, String projectId, String serviceId, String oldServiceInputsYaml) {
    Optional<ServiceEntity> serviceEntity = get(accountId, orgId, projectId, serviceId, false);
    if (!serviceEntity.isPresent()) {
      throw new NotFoundException(
          format("Service with identifier [%s] in project [%s], org [%s] not found", serviceId, projectId, orgId));
    }

    String serviceYaml = serviceEntity.get().getYaml();
    if (isEmpty(serviceYaml)) {
      return ServiceInputsMergedResponseDto.builder().mergedServiceInputsYaml("").serviceYaml("").build();
    }
    try {
      YamlNode primaryArtifactRefNode = null;
      if (isNotEmpty(oldServiceInputsYaml)) {
        YamlNode oldServiceInputsNode = YamlUtils.readTree(oldServiceInputsYaml).getNode();
        primaryArtifactRefNode = YamlNodeUtils.goToPathUsingFqn(
            oldServiceInputsNode, "serviceInputs.serviceDefinition.spec.artifacts.primary.primaryArtifactRef");
      }

      String newServiceInputsYaml = createServiceInputsYamlGivenPrimaryArtifactRef(
          serviceYaml, serviceId, primaryArtifactRefNode == null ? null : primaryArtifactRefNode.asText());
      return ServiceInputsMergedResponseDto.builder()
          .mergedServiceInputsYaml(InputSetMergeUtility.mergeInputs(oldServiceInputsYaml, newServiceInputsYaml))
          .serviceYaml(serviceYaml)
          .build();
    } catch (IOException ex) {
      throw new InvalidRequestException("Error occurred while merging old and new service inputs", ex);
    }
  }

  private void modifyServiceDefinitionNodeBeforeCreatingServiceInputs(
      YamlField serviceYamlField, String serviceIdentifier, String primaryArtifactRef) {
    YamlField primaryArtifactField = ServiceFilterHelper.getPrimaryArtifactNodeFromServiceYaml(serviceYamlField);
    if (primaryArtifactField == null) {
      return;
    }
    if (!primaryArtifactField.getNode().isObject()) {
      throw new InvalidRequestException(
          String.format("Primary field inside service %s should be an OBJECT node but was %s", serviceIdentifier,
              primaryArtifactField.getNode().getCurrJsonNode().getNodeType()));
    }

    YamlField primaryArtifactRefField = primaryArtifactField.getNode().getField(YamlTypes.PRIMARY_ARTIFACT_REF);
    YamlField artifactSourcesField = primaryArtifactField.getNode().getField(YamlTypes.ARTIFACT_SOURCES);
    if (primaryArtifactRefField == null || artifactSourcesField == null) {
      return;
    }

    if (!artifactSourcesField.getNode().isArray()) {
      throw new InvalidRequestException(
          String.format("Artifact sources inside service %s should be ARRAY node but was %s", serviceIdentifier,
              artifactSourcesField.getNode().getCurrJsonNode().getNodeType()));
    }

    String primaryArtifactRefValue = primaryArtifactRefField.getNode().asText();

    ObjectNode primaryArtifactObjectNode = (ObjectNode) primaryArtifactField.getNode().getCurrJsonNode();
    if (NGExpressionUtils.matchesInputSetPattern(primaryArtifactRefValue)) {
      if (EmptyPredicate.isNotEmpty(primaryArtifactRef)
          && !NGExpressionUtils.matchesInputSetPattern(primaryArtifactRef)) {
        primaryArtifactRefValue = primaryArtifactRef;
      } else {
        primaryArtifactObjectNode.remove(YamlTypes.ARTIFACT_SOURCES);
        primaryArtifactObjectNode.put(YamlTypes.ARTIFACT_SOURCES, "<+input>");
        return;
      }
    }

    if (EngineExpressionEvaluator.hasExpressions(primaryArtifactRefValue)) {
      throw new InvalidRequestException(
          String.format("Primary artifact ref cannot be an expression inside the service %s", serviceIdentifier));
    }

    ObjectMapper objectMapper = new ObjectMapper();
    ArrayNode filteredArtifactSourcesNode = objectMapper.createArrayNode();
    List<YamlNode> artifactSources = artifactSourcesField.getNode().asArray();
    for (YamlNode artifactSource : artifactSources) {
      String sourceIdentifier = artifactSource.getIdentifier();
      if (primaryArtifactRefValue.equals(sourceIdentifier)) {
        filteredArtifactSourcesNode.add(artifactSource.getCurrJsonNode());
        break;
      }
    }

    if (EmptyPredicate.isEmpty(filteredArtifactSourcesNode)) {
      throw new InvalidRequestException(
          String.format("Primary artifact ref value %s provided does not exist in sources in service %s",
              primaryArtifactRefValue, serviceIdentifier));
    }
    primaryArtifactObjectNode.set(YamlTypes.ARTIFACT_SOURCES, filteredArtifactSourcesNode);
  }

  @Override
  public boolean forceDeleteAllInProject(String accountId, String orgIdentifier, String projectIdentifier) {
    Criteria criteria = CoreCriteriaUtils.createCriteriaForGetList(accountId, orgIdentifier, projectIdentifier);
    return Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
      DeleteResult deleteResult = serviceRepository.deleteMany(criteria);
      if (!deleteResult.wasAcknowledged()) {
        throw new InvalidRequestException(String.format(
            "Services under Project[%s], Organization [%s] couldn't be deleted.", projectIdentifier, orgIdentifier));
      }
      return true;
    }));
  }

  @Override
  public YamlNode getYamlNodeForFqn(String accountId, String orgIdentifier, String projectIdentifier,
      @NotEmpty String serviceIdentifier, String fqn) {
    Optional<ServiceEntity> entity = get(accountId, orgIdentifier, projectIdentifier, serviceIdentifier, false);
    if (entity.isEmpty()) {
      throw new InvalidRequestException(format("Service: %s does not exist", serviceIdentifier));
    }

    int index = 0;
    String[] split = fqn.split("\\.");
    final String serviceDefinitionLimiter = "serviceDefinition";
    for (int i = 0; i < split.length; i++) {
      if (serviceDefinitionLimiter.equals(split[i])) {
        index = i;
        break;
      }
    }

    if (index == 0) {
      throw new InvalidRequestException(format("FQN must contain %s", serviceDefinitionLimiter));
    }

    List<String> splitPaths = List.of(split).subList(index, split.length);
    String fqnWithinServiceEntityYaml = String.join(".", splitPaths);

    YamlNode service;
    try {
      service = YamlNode.fromYamlPath(entity.get().fetchNonEmptyYaml(), "service");
    } catch (IOException e) {
      throw new InvalidRequestException("Service entity yaml must be rooted at \"service\"");
    }

    if (service == null) {
      throw new InvalidRequestException("Service entity yaml must be rooted at \"service\"");
    }

    YamlNode leafNode = YamlNodeUtils.goToPathUsingFqn(service, fqnWithinServiceEntityYaml);
    if (leafNode == null) {
      throw new InvalidRequestException(
          format("Unable to locate path %s within service yaml", fqnWithinServiceEntityYaml));
    }
    return leafNode;
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

  @VisibleForTesting
  String getDuplicateServiceExistsErrorMessage(String accountId, String exceptionString) {
    String errorMessageToBeReturned;
    try {
      JSONObject jsonObjectOfDuplicateKey = DuplicateKeyExceptionParser.getDuplicateKey(exceptionString);
      if (jsonObjectOfDuplicateKey != null) {
        String orgIdentifier = jsonObjectOfDuplicateKey.getString("orgIdentifier");
        String projectIdentifier = jsonObjectOfDuplicateKey.getString("projectIdentifier");
        String identifier = jsonObjectOfDuplicateKey.getString("identifier");
        errorMessageToBeReturned =
            getDuplicateServiceExistsErrorMessage(accountId, orgIdentifier, projectIdentifier, identifier);
      } else {
        errorMessageToBeReturned = "A Duplicate Service already exists";
      }
    } catch (Exception ex) {
      errorMessageToBeReturned = "A Duplicate Service already exists";
    }
    return errorMessageToBeReturned;
  }

  @VisibleForTesting
  List<ServiceEntity> getAllServices(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      Integer pageSize, boolean includeDeletedServices, List<String> sort) {
    List<ServiceEntity> serviceEntityList = new ArrayList<>();

    Criteria criteria = Criteria.where(ServiceEntityKeys.accountId)
                            .is(accountIdentifier)
                            .and(ServiceEntityKeys.orgIdentifier)
                            .is(orgIdentifier)
                            .and(ServiceEntityKeys.projectIdentifier)
                            .is(projectIdentifier);

    if (!includeDeletedServices) {
      criteria.and(ServiceEntityKeys.deleted).is(false);
    }

    int pageNum = 0;
    // Query in batches of 10k
    while (true) {
      Pageable pageRequest;
      if (isEmpty(sort)) {
        pageRequest = PageRequest.of(pageNum, pageSize, Sort.by(Sort.Direction.DESC, ServiceEntityKeys.createdAt));
      } else {
        pageRequest = PageUtils.getPageRequest(pageNum, pageSize, sort);
      }
      Page<ServiceEntity> pageResponse = serviceRepository.findAll(criteria, pageRequest);
      if (pageResponse.isEmpty()) {
        break;
      }
      serviceEntityList.addAll(pageResponse.getContent());
      pageNum += 1;
    }

    return serviceEntityList;
  }

  private void validateTheServicesList(List<ServiceEntity> serviceEntities) {
    if (isEmpty(serviceEntities)) {
      return;
    }
    serviceEntities.forEach(
        serviceEntity -> validatePresenceOfRequiredFields(serviceEntity.getAccountId(), serviceEntity.getIdentifier()));
  }

  private void populateDefaultNameIfNotPresent(List<ServiceEntity> serviceEntities) {
    if (isEmpty(serviceEntities)) {
      return;
    }
    serviceEntities.forEach(this::setNameIfNotPresent);
  }

  private void modifyServiceRequest(ServiceEntity requestService) {
    requestService.setName(requestService.getName().trim());
  }

  private void modifyServiceRequestBatch(List<ServiceEntity> serviceEntities) {
    if (isEmpty(serviceEntities)) {
      return;
    }
    serviceEntities.forEach(this::modifyServiceRequest);
  }

  boolean processQuietly(BooleanSupplier b) {
    try {
      b.getAsBoolean();
      // supplier processed
      return true;
    } catch (Exception ex) {
      log.error("failed to process entity deletion", ex);
      // ignore this
      return false;
    }
  }

  public Optional<ServiceEntity> getService(
      String accountId, String orgIdentifier, String projectIdentifier, String serviceIdentifier) {
    return serviceRepository.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifier(
        accountId, orgIdentifier, projectIdentifier, serviceIdentifier);
  }
}
