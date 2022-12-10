/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.outbox;

import static io.harness.ng.core.ResourceConstants.INFRASTRUCTURE_ID;
import static io.harness.ng.core.ResourceConstants.SERVICE_OVERRIDE_NAME;
import static io.harness.ng.core.ResourceConstants.STATUS;
import static io.harness.ng.core.events.EnvironmentUpdatedEvent.Status.CREATED;
import static io.harness.ng.core.events.EnvironmentUpdatedEvent.Status.DELETED;
import static io.harness.ng.core.events.EnvironmentUpdatedEvent.Status.UPDATED;
import static io.harness.ng.core.utils.NGYamlUtils.getYamlString;
import static io.harness.rule.OwnerRule.BRIJESH;
import static io.harness.rule.OwnerRule.NAMAN_TALAYCHA;

import static io.serializer.HObjectMapper.NG_DEFAULT_OBJECT_MAPPER;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertNull;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.apache.commons.lang3.RandomStringUtils.randomNumeric;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.ModuleType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.audit.Action;
import io.harness.audit.beans.AuditEntry;
import io.harness.audit.client.api.AuditClientService;
import io.harness.category.element.UnitTests;
import io.harness.context.GlobalContext;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.mappers.EnvironmentMapper;
import io.harness.ng.core.events.EnvironmentCreateEvent;
import io.harness.ng.core.events.EnvironmentDeleteEvent;
import io.harness.ng.core.events.EnvironmentUpdatedEvent;
import io.harness.ng.core.events.EnvironmentUpsertEvent;
import io.harness.ng.core.events.OutboxEventConstants;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity;
import io.harness.ng.core.serviceoverride.beans.NGServiceOverridesEntity;
import io.harness.outbox.OutboxEvent;
import io.harness.rule.Owner;
import io.harness.security.SourcePrincipalContextData;
import io.harness.security.dto.Principal;
import io.harness.security.dto.UserPrincipal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;

@OwnedBy(HarnessTeam.PIPELINE)
public class EnvironmentEventHandlerTest extends CategoryTest {
  private ObjectMapper objectMapper;
  private AuditClientService auditClientService;
  private EnvironmentEventHandler environmentEventHandler;

  @Before
  public void setup() {
    objectMapper = NG_DEFAULT_OBJECT_MAPPER;
    auditClientService = mock(AuditClientService.class);
    environmentEventHandler = spy(new EnvironmentEventHandler(auditClientService));
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testCreate() throws JsonProcessingException {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    Environment environment = Environment.builder()
                                  .accountId(accountIdentifier)
                                  .projectIdentifier(projectIdentifier)
                                  .orgIdentifier(orgIdentifier)
                                  .identifier(identifier)
                                  .build();
    EnvironmentCreateEvent environmentCreateEvent = EnvironmentCreateEvent.builder()
                                                        .accountIdentifier(accountIdentifier)
                                                        .orgIdentifier(orgIdentifier)
                                                        .projectIdentifier(projectIdentifier)
                                                        .environment(environment)
                                                        .build();

    GlobalContext globalContext = new GlobalContext();
    Principal principal =
        new UserPrincipal(randomAlphabetic(10), randomAlphabetic(10), randomAlphabetic(10), randomAlphabetic(10));
    SourcePrincipalContextData sourcePrincipalContextData =
        SourcePrincipalContextData.builder().principal(principal).build();
    globalContext.upsertGlobalContextRecord(sourcePrincipalContextData);
    String eventData = objectMapper.writeValueAsString(environmentCreateEvent);

    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .id(randomAlphabetic(10))
                                  .blocked(false)
                                  .eventType(OutboxEventConstants.ENVIRONMENT_CREATED)
                                  .eventData(eventData)
                                  .resource(environmentCreateEvent.getResource())
                                  .resourceScope(environmentCreateEvent.getResourceScope())
                                  .globalContext(globalContext)
                                  .createdAt(Long.valueOf(randomNumeric(6)))
                                  .build();

    String newYaml = getYamlString(EnvironmentMapper.toNGEnvironmentConfig(environment));
    final ArgumentCaptor<AuditEntry> auditEntryArgumentCaptor = ArgumentCaptor.forClass(AuditEntry.class);
    when(auditClientService.publishAudit(any(), any(), any())).thenReturn(true);
    environmentEventHandler.handle(outboxEvent);
    verify(auditClientService, times(1)).publishAudit(auditEntryArgumentCaptor.capture(), any(), any());
    AuditEntry auditEntry = auditEntryArgumentCaptor.getValue();
    assertAuditEntry(accountIdentifier, orgIdentifier, projectIdentifier, identifier, auditEntry, outboxEvent);
    assertEquals(Action.CREATE, auditEntry.getAction());
    assertNull(auditEntry.getOldYaml());
    assertEquals(newYaml, auditEntry.getNewYaml());
  }
  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testDelete() throws JsonProcessingException {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    Environment environment = Environment.builder()
                                  .accountId(accountIdentifier)
                                  .projectIdentifier(projectIdentifier)
                                  .orgIdentifier(orgIdentifier)
                                  .identifier(identifier)
                                  .build();
    EnvironmentDeleteEvent environmentDeleteEvent = EnvironmentDeleteEvent.builder()
                                                        .accountIdentifier(accountIdentifier)
                                                        .orgIdentifier(orgIdentifier)
                                                        .projectIdentifier(projectIdentifier)
                                                        .environment(environment)
                                                        .build();

    GlobalContext globalContext = new GlobalContext();
    Principal principal =
        new UserPrincipal(randomAlphabetic(10), randomAlphabetic(10), randomAlphabetic(10), randomAlphabetic(10));
    SourcePrincipalContextData sourcePrincipalContextData =
        SourcePrincipalContextData.builder().principal(principal).build();
    globalContext.upsertGlobalContextRecord(sourcePrincipalContextData);
    String eventData = objectMapper.writeValueAsString(environmentDeleteEvent);

    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .id(randomAlphabetic(10))
                                  .blocked(false)
                                  .eventType(OutboxEventConstants.ENVIRONMENT_DELETED)
                                  .eventData(eventData)
                                  .resource(environmentDeleteEvent.getResource())
                                  .resourceScope(environmentDeleteEvent.getResourceScope())
                                  .globalContext(globalContext)
                                  .createdAt(Long.valueOf(randomNumeric(6)))
                                  .build();

    String oldYaml = getYamlString(EnvironmentMapper.toNGEnvironmentConfig(environment));
    final ArgumentCaptor<AuditEntry> auditEntryArgumentCaptor = ArgumentCaptor.forClass(AuditEntry.class);
    when(auditClientService.publishAudit(any(), any(), any())).thenReturn(true);
    environmentEventHandler.handle(outboxEvent);
    verify(auditClientService, times(1)).publishAudit(auditEntryArgumentCaptor.capture(), any(), any());
    AuditEntry auditEntry = auditEntryArgumentCaptor.getValue();
    assertAuditEntry(accountIdentifier, orgIdentifier, projectIdentifier, identifier, auditEntry, outboxEvent);
    assertEquals(Action.DELETE, auditEntry.getAction());
    assertNull(auditEntry.getNewYaml());
    assertEquals(oldYaml, auditEntry.getOldYaml());
  }
  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testUpdate() throws JsonProcessingException {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    Environment newEnvironment = Environment.builder()
                                     .accountId(accountIdentifier)
                                     .projectIdentifier(projectIdentifier)
                                     .orgIdentifier(orgIdentifier)
                                     .identifier(identifier)
                                     .build();
    Environment oldEnvironment = Environment.builder()
                                     .accountId(accountIdentifier)
                                     .projectIdentifier(projectIdentifier)
                                     .orgIdentifier(orgIdentifier)
                                     .identifier(identifier)
                                     .build();
    EnvironmentUpdatedEvent environmentUpdatedEvent =
        EnvironmentUpdatedEvent.builder()
            .accountIdentifier(accountIdentifier)
            .orgIdentifier(orgIdentifier)
            .projectIdentifier(projectIdentifier)
            .resourceType(EnvironmentUpdatedEvent.ResourceType.ENVIRONMENT)
            .status(UPDATED)
            .newEnvironment(newEnvironment)
            .oldEnvironment(oldEnvironment)
            .build();

    GlobalContext globalContext = new GlobalContext();
    Principal principal =
        new UserPrincipal(randomAlphabetic(10), randomAlphabetic(10), randomAlphabetic(10), randomAlphabetic(10));
    SourcePrincipalContextData sourcePrincipalContextData =
        SourcePrincipalContextData.builder().principal(principal).build();
    globalContext.upsertGlobalContextRecord(sourcePrincipalContextData);
    String eventData = objectMapper.writeValueAsString(environmentUpdatedEvent);

    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .id(randomAlphabetic(10))
                                  .blocked(false)
                                  .eventType(OutboxEventConstants.ENVIRONMENT_UPDATED)
                                  .eventData(eventData)
                                  .resource(environmentUpdatedEvent.getResource())
                                  .resourceScope(environmentUpdatedEvent.getResourceScope())
                                  .globalContext(globalContext)
                                  .createdAt(Long.valueOf(randomNumeric(6)))
                                  .build();

    String newYaml = getYamlString(EnvironmentMapper.toNGEnvironmentConfig(newEnvironment));
    String oldYaml = getYamlString(EnvironmentMapper.toNGEnvironmentConfig(oldEnvironment));
    final ArgumentCaptor<AuditEntry> auditEntryArgumentCaptor = ArgumentCaptor.forClass(AuditEntry.class);
    when(auditClientService.publishAudit(any(), any(), any())).thenReturn(true);
    environmentEventHandler.handle(outboxEvent);
    verify(auditClientService, times(1)).publishAudit(auditEntryArgumentCaptor.capture(), any(), any());
    AuditEntry auditEntry = auditEntryArgumentCaptor.getValue();
    assertAuditEntry(accountIdentifier, orgIdentifier, projectIdentifier, identifier, auditEntry, outboxEvent);
    assertEquals(Action.UPDATE, auditEntry.getAction());
    assertEquals(newYaml, auditEntry.getNewYaml());
    assertEquals(oldYaml, auditEntry.getOldYaml());
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testUpdateEnvCreateServiceOverride() throws JsonProcessingException {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    String environmentRef = randomAlphabetic(10);
    String serviceRef = randomAlphabetic(10);
    NGServiceOverridesEntity newServiceOverridesEntity = NGServiceOverridesEntity.builder()
                                                             .accountId(accountIdentifier)
                                                             .projectIdentifier(projectIdentifier)
                                                             .orgIdentifier(orgIdentifier)
                                                             .environmentRef(environmentRef)
                                                             .serviceRef(serviceRef)
                                                             .yaml("yaml")
                                                             .build();
    EnvironmentUpdatedEvent environmentUpdatedEvent =
        EnvironmentUpdatedEvent.builder()
            .accountIdentifier(accountIdentifier)
            .orgIdentifier(orgIdentifier)
            .projectIdentifier(projectIdentifier)
            .resourceType(EnvironmentUpdatedEvent.ResourceType.SERVICE_OVERRIDE)
            .status(EnvironmentUpdatedEvent.Status.CREATED)
            .newServiceOverridesEntity(newServiceOverridesEntity)
            .build();

    GlobalContext globalContext = new GlobalContext();
    Principal principal =
        new UserPrincipal(randomAlphabetic(10), randomAlphabetic(10), randomAlphabetic(10), randomAlphabetic(10));
    SourcePrincipalContextData sourcePrincipalContextData =
        SourcePrincipalContextData.builder().principal(principal).build();
    globalContext.upsertGlobalContextRecord(sourcePrincipalContextData);
    String eventData = objectMapper.writeValueAsString(environmentUpdatedEvent);

    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .id(randomAlphabetic(10))
                                  .blocked(false)
                                  .eventType(OutboxEventConstants.ENVIRONMENT_UPDATED)
                                  .eventData(eventData)
                                  .resource(environmentUpdatedEvent.getResource())
                                  .resourceScope(environmentUpdatedEvent.getResourceScope())
                                  .globalContext(globalContext)
                                  .createdAt(Long.valueOf(randomNumeric(6)))
                                  .build();

    String newYaml = newServiceOverridesEntity.getYaml();
    final ArgumentCaptor<AuditEntry> auditEntryArgumentCaptor = ArgumentCaptor.forClass(AuditEntry.class);
    when(auditClientService.publishAudit(any(), any(), any())).thenReturn(true);
    environmentEventHandler.handle(outboxEvent);
    verify(auditClientService, times(1)).publishAudit(auditEntryArgumentCaptor.capture(), any(), any());
    AuditEntry auditEntry = auditEntryArgumentCaptor.getValue();
    assertAuditEntry(accountIdentifier, orgIdentifier, projectIdentifier, environmentRef, auditEntry, outboxEvent);
    assertEquals(Action.UPDATE, auditEntry.getAction());
    assertEquals(serviceRef + " Override", auditEntry.getResource().getLabels().get(SERVICE_OVERRIDE_NAME));
    assertEquals(CREATED.name(), auditEntry.getResource().getLabels().get(STATUS));
    assertEquals(newYaml, auditEntry.getNewYaml());
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testUpdateEnvDeleteServiceOverride() throws JsonProcessingException {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    String environmentRef = randomAlphabetic(10);
    String serviceRef = randomAlphabetic(10);

    NGServiceOverridesEntity oldServiceOverridesEntity = NGServiceOverridesEntity.builder()
                                                             .accountId(accountIdentifier)
                                                             .projectIdentifier(projectIdentifier)
                                                             .orgIdentifier(orgIdentifier)
                                                             .environmentRef(environmentRef)
                                                             .serviceRef(serviceRef)
                                                             .yaml("oldYaml")
                                                             .build();
    EnvironmentUpdatedEvent environmentUpdatedEvent =
        EnvironmentUpdatedEvent.builder()
            .accountIdentifier(accountIdentifier)
            .orgIdentifier(orgIdentifier)
            .projectIdentifier(projectIdentifier)
            .resourceType(EnvironmentUpdatedEvent.ResourceType.SERVICE_OVERRIDE)
            .status(DELETED)
            .oldServiceOverridesEntity(oldServiceOverridesEntity)
            .build();

    GlobalContext globalContext = new GlobalContext();
    Principal principal =
        new UserPrincipal(randomAlphabetic(10), randomAlphabetic(10), randomAlphabetic(10), randomAlphabetic(10));
    SourcePrincipalContextData sourcePrincipalContextData =
        SourcePrincipalContextData.builder().principal(principal).build();
    globalContext.upsertGlobalContextRecord(sourcePrincipalContextData);
    String eventData = objectMapper.writeValueAsString(environmentUpdatedEvent);

    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .id(randomAlphabetic(10))
                                  .blocked(false)
                                  .eventType(OutboxEventConstants.ENVIRONMENT_UPDATED)
                                  .eventData(eventData)
                                  .resource(environmentUpdatedEvent.getResource())
                                  .resourceScope(environmentUpdatedEvent.getResourceScope())
                                  .globalContext(globalContext)
                                  .createdAt(Long.valueOf(randomNumeric(6)))
                                  .build();

    String oldYaml = oldServiceOverridesEntity.getYaml();
    final ArgumentCaptor<AuditEntry> auditEntryArgumentCaptor = ArgumentCaptor.forClass(AuditEntry.class);
    when(auditClientService.publishAudit(any(), any(), any())).thenReturn(true);
    environmentEventHandler.handle(outboxEvent);
    verify(auditClientService, times(1)).publishAudit(auditEntryArgumentCaptor.capture(), any(), any());
    AuditEntry auditEntry = auditEntryArgumentCaptor.getValue();
    assertAuditEntry(accountIdentifier, orgIdentifier, projectIdentifier, environmentRef, auditEntry, outboxEvent);
    assertEquals(Action.UPDATE, auditEntry.getAction());
    assertEquals(serviceRef + " Override", auditEntry.getResource().getLabels().get(SERVICE_OVERRIDE_NAME));
    assertEquals(DELETED.name(), auditEntry.getResource().getLabels().get(STATUS));
    assertEquals(oldYaml, auditEntry.getOldYaml());
  }
  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testUpdateEnvUpdateServiceOverride() throws JsonProcessingException {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    String environmentRef = randomAlphabetic(10);
    String serviceRef = randomAlphabetic(10);
    NGServiceOverridesEntity newServiceOverridesEntity = NGServiceOverridesEntity.builder()
                                                             .accountId(accountIdentifier)
                                                             .projectIdentifier(projectIdentifier)
                                                             .orgIdentifier(orgIdentifier)
                                                             .environmentRef(environmentRef)
                                                             .serviceRef(serviceRef)
                                                             .yaml("newYaml")
                                                             .build();
    NGServiceOverridesEntity oldServiceOverridesEntity = NGServiceOverridesEntity.builder()
                                                             .accountId(accountIdentifier)
                                                             .projectIdentifier(projectIdentifier)
                                                             .orgIdentifier(orgIdentifier)
                                                             .environmentRef(environmentRef)
                                                             .serviceRef(serviceRef)
                                                             .yaml("oldYaml")
                                                             .build();
    EnvironmentUpdatedEvent environmentUpdatedEvent =
        EnvironmentUpdatedEvent.builder()
            .accountIdentifier(accountIdentifier)
            .orgIdentifier(orgIdentifier)
            .projectIdentifier(projectIdentifier)
            .resourceType(EnvironmentUpdatedEvent.ResourceType.SERVICE_OVERRIDE)
            .status(UPDATED)
            .newServiceOverridesEntity(newServiceOverridesEntity)
            .oldServiceOverridesEntity(oldServiceOverridesEntity)
            .build();

    GlobalContext globalContext = new GlobalContext();
    Principal principal =
        new UserPrincipal(randomAlphabetic(10), randomAlphabetic(10), randomAlphabetic(10), randomAlphabetic(10));
    SourcePrincipalContextData sourcePrincipalContextData =
        SourcePrincipalContextData.builder().principal(principal).build();
    globalContext.upsertGlobalContextRecord(sourcePrincipalContextData);
    String eventData = objectMapper.writeValueAsString(environmentUpdatedEvent);

    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .id(randomAlphabetic(10))
                                  .blocked(false)
                                  .eventType(OutboxEventConstants.ENVIRONMENT_UPDATED)
                                  .eventData(eventData)
                                  .resource(environmentUpdatedEvent.getResource())
                                  .resourceScope(environmentUpdatedEvent.getResourceScope())
                                  .globalContext(globalContext)
                                  .createdAt(Long.valueOf(randomNumeric(6)))
                                  .build();

    String newYaml = newServiceOverridesEntity.getYaml();
    String oldYaml = oldServiceOverridesEntity.getYaml();
    final ArgumentCaptor<AuditEntry> auditEntryArgumentCaptor = ArgumentCaptor.forClass(AuditEntry.class);
    when(auditClientService.publishAudit(any(), any(), any())).thenReturn(true);
    environmentEventHandler.handle(outboxEvent);
    verify(auditClientService, times(1)).publishAudit(auditEntryArgumentCaptor.capture(), any(), any());
    AuditEntry auditEntry = auditEntryArgumentCaptor.getValue();
    assertAuditEntry(accountIdentifier, orgIdentifier, projectIdentifier, environmentRef, auditEntry, outboxEvent);
    assertEquals(Action.UPDATE, auditEntry.getAction());
    assertEquals(serviceRef + " Override", auditEntry.getResource().getLabels().get(SERVICE_OVERRIDE_NAME));
    assertEquals(UPDATED.name(), auditEntry.getResource().getLabels().get(STATUS));
    assertEquals(newYaml, auditEntry.getNewYaml());
    assertEquals(oldYaml, auditEntry.getOldYaml());
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testUpdateEnvCreateInfrastructure() throws JsonProcessingException {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    String environmentRef = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    InfrastructureEntity newInfrastructure = InfrastructureEntity.builder()
                                                 .accountId(accountIdentifier)
                                                 .projectIdentifier(projectIdentifier)
                                                 .orgIdentifier(orgIdentifier)
                                                 .envIdentifier(environmentRef)
                                                 .identifier(identifier)
                                                 .yaml("newYaml")
                                                 .build();
    EnvironmentUpdatedEvent environmentUpdatedEvent =
        EnvironmentUpdatedEvent.builder()
            .accountIdentifier(accountIdentifier)
            .orgIdentifier(orgIdentifier)
            .projectIdentifier(projectIdentifier)
            .resourceType(EnvironmentUpdatedEvent.ResourceType.INFRASTRUCTURE)
            .status(EnvironmentUpdatedEvent.Status.CREATED)
            .newInfrastructureEntity(newInfrastructure)
            .build();

    GlobalContext globalContext = new GlobalContext();
    Principal principal =
        new UserPrincipal(randomAlphabetic(10), randomAlphabetic(10), randomAlphabetic(10), randomAlphabetic(10));
    SourcePrincipalContextData sourcePrincipalContextData =
        SourcePrincipalContextData.builder().principal(principal).build();
    globalContext.upsertGlobalContextRecord(sourcePrincipalContextData);
    String eventData = objectMapper.writeValueAsString(environmentUpdatedEvent);

    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .id(randomAlphabetic(10))
                                  .blocked(false)
                                  .eventType(OutboxEventConstants.ENVIRONMENT_UPDATED)
                                  .eventData(eventData)
                                  .resource(environmentUpdatedEvent.getResource())
                                  .resourceScope(environmentUpdatedEvent.getResourceScope())
                                  .globalContext(globalContext)
                                  .createdAt(Long.valueOf(randomNumeric(6)))
                                  .build();

    String newYaml = newInfrastructure.getYaml();
    final ArgumentCaptor<AuditEntry> auditEntryArgumentCaptor = ArgumentCaptor.forClass(AuditEntry.class);
    when(auditClientService.publishAudit(any(), any(), any())).thenReturn(true);
    environmentEventHandler.handle(outboxEvent);
    verify(auditClientService, times(1)).publishAudit(auditEntryArgumentCaptor.capture(), any(), any());
    AuditEntry auditEntry = auditEntryArgumentCaptor.getValue();
    assertAuditEntry(accountIdentifier, orgIdentifier, projectIdentifier, environmentRef, auditEntry, outboxEvent);
    assertEquals(Action.UPDATE, auditEntry.getAction());
    assertEquals(identifier, auditEntry.getResource().getLabels().get(INFRASTRUCTURE_ID));
    assertEquals(CREATED.name(), auditEntry.getResource().getLabels().get(STATUS));
    assertEquals(newYaml, auditEntry.getNewYaml());
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testUpdateEnvUpdateInfrastructure() throws JsonProcessingException {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    String environmentRef = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    InfrastructureEntity newInfrastructure = InfrastructureEntity.builder()
                                                 .accountId(accountIdentifier)
                                                 .projectIdentifier(projectIdentifier)
                                                 .orgIdentifier(orgIdentifier)
                                                 .envIdentifier(environmentRef)
                                                 .identifier(identifier)
                                                 .yaml("newYaml")
                                                 .build();
    InfrastructureEntity oldInfrastructure = InfrastructureEntity.builder()
                                                 .accountId(accountIdentifier)
                                                 .projectIdentifier(projectIdentifier)
                                                 .orgIdentifier(orgIdentifier)
                                                 .envIdentifier(environmentRef)
                                                 .identifier(identifier)
                                                 .yaml("oldYaml")
                                                 .build();
    EnvironmentUpdatedEvent environmentUpdatedEvent =
        EnvironmentUpdatedEvent.builder()
            .accountIdentifier(accountIdentifier)
            .orgIdentifier(orgIdentifier)
            .projectIdentifier(projectIdentifier)
            .resourceType(EnvironmentUpdatedEvent.ResourceType.INFRASTRUCTURE)
            .status(UPDATED)
            .newInfrastructureEntity(newInfrastructure)
            .oldInfrastructureEntity(oldInfrastructure)
            .build();

    GlobalContext globalContext = new GlobalContext();
    Principal principal =
        new UserPrincipal(randomAlphabetic(10), randomAlphabetic(10), randomAlphabetic(10), randomAlphabetic(10));
    SourcePrincipalContextData sourcePrincipalContextData =
        SourcePrincipalContextData.builder().principal(principal).build();
    globalContext.upsertGlobalContextRecord(sourcePrincipalContextData);
    String eventData = objectMapper.writeValueAsString(environmentUpdatedEvent);

    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .id(randomAlphabetic(10))
                                  .blocked(false)
                                  .eventType(OutboxEventConstants.ENVIRONMENT_UPDATED)
                                  .eventData(eventData)
                                  .resource(environmentUpdatedEvent.getResource())
                                  .resourceScope(environmentUpdatedEvent.getResourceScope())
                                  .globalContext(globalContext)
                                  .createdAt(Long.valueOf(randomNumeric(6)))
                                  .build();

    String newYaml = newInfrastructure.getYaml();
    String oldYaml = oldInfrastructure.getYaml();
    final ArgumentCaptor<AuditEntry> auditEntryArgumentCaptor = ArgumentCaptor.forClass(AuditEntry.class);
    when(auditClientService.publishAudit(any(), any(), any())).thenReturn(true);
    environmentEventHandler.handle(outboxEvent);
    verify(auditClientService, times(1)).publishAudit(auditEntryArgumentCaptor.capture(), any(), any());
    AuditEntry auditEntry = auditEntryArgumentCaptor.getValue();
    assertAuditEntry(accountIdentifier, orgIdentifier, projectIdentifier, environmentRef, auditEntry, outboxEvent);
    assertEquals(Action.UPDATE, auditEntry.getAction());
    assertEquals(identifier, auditEntry.getResource().getLabels().get(INFRASTRUCTURE_ID));
    assertEquals(UPDATED.name(), auditEntry.getResource().getLabels().get(STATUS));
    assertEquals(newYaml, auditEntry.getNewYaml());
    assertEquals(oldYaml, auditEntry.getOldYaml());
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testUpdateEnvDeleteInfrastructure() throws JsonProcessingException {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    String environmentRef = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    InfrastructureEntity oldInfrastructure = InfrastructureEntity.builder()
                                                 .accountId(accountIdentifier)
                                                 .projectIdentifier(projectIdentifier)
                                                 .orgIdentifier(orgIdentifier)
                                                 .envIdentifier(environmentRef)
                                                 .identifier(identifier)
                                                 .yaml("oldYaml")
                                                 .build();
    EnvironmentUpdatedEvent environmentUpdatedEvent =
        EnvironmentUpdatedEvent.builder()
            .accountIdentifier(accountIdentifier)
            .orgIdentifier(orgIdentifier)
            .projectIdentifier(projectIdentifier)
            .resourceType(EnvironmentUpdatedEvent.ResourceType.INFRASTRUCTURE)
            .status(DELETED)
            .oldInfrastructureEntity(oldInfrastructure)
            .build();

    GlobalContext globalContext = new GlobalContext();
    Principal principal =
        new UserPrincipal(randomAlphabetic(10), randomAlphabetic(10), randomAlphabetic(10), randomAlphabetic(10));
    SourcePrincipalContextData sourcePrincipalContextData =
        SourcePrincipalContextData.builder().principal(principal).build();
    globalContext.upsertGlobalContextRecord(sourcePrincipalContextData);
    String eventData = objectMapper.writeValueAsString(environmentUpdatedEvent);

    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .id(randomAlphabetic(10))
                                  .blocked(false)
                                  .eventType(OutboxEventConstants.ENVIRONMENT_UPDATED)
                                  .eventData(eventData)
                                  .resource(environmentUpdatedEvent.getResource())
                                  .resourceScope(environmentUpdatedEvent.getResourceScope())
                                  .globalContext(globalContext)
                                  .createdAt(Long.valueOf(randomNumeric(6)))
                                  .build();

    String oldYaml = oldInfrastructure.getYaml();
    final ArgumentCaptor<AuditEntry> auditEntryArgumentCaptor = ArgumentCaptor.forClass(AuditEntry.class);
    when(auditClientService.publishAudit(any(), any(), any())).thenReturn(true);
    environmentEventHandler.handle(outboxEvent);
    verify(auditClientService, times(1)).publishAudit(auditEntryArgumentCaptor.capture(), any(), any());
    AuditEntry auditEntry = auditEntryArgumentCaptor.getValue();
    assertAuditEntry(accountIdentifier, orgIdentifier, projectIdentifier, environmentRef, auditEntry, outboxEvent);
    assertEquals(Action.UPDATE, auditEntry.getAction());
    assertEquals(identifier, auditEntry.getResource().getLabels().get(INFRASTRUCTURE_ID));
    assertEquals(DELETED.name(), auditEntry.getResource().getLabels().get(STATUS));
    assertEquals(oldYaml, auditEntry.getOldYaml());
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testUpsert() throws JsonProcessingException {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    Environment environment = Environment.builder()
                                  .accountId(accountIdentifier)
                                  .projectIdentifier(projectIdentifier)
                                  .orgIdentifier(orgIdentifier)
                                  .identifier(identifier)
                                  .build();
    EnvironmentUpsertEvent environmentUpsertEvent = EnvironmentUpsertEvent.builder()
                                                        .accountIdentifier(accountIdentifier)
                                                        .orgIdentifier(orgIdentifier)
                                                        .projectIdentifier(projectIdentifier)
                                                        .environment(environment)
                                                        .build();

    GlobalContext globalContext = new GlobalContext();
    Principal principal =
        new UserPrincipal(randomAlphabetic(10), randomAlphabetic(10), randomAlphabetic(10), randomAlphabetic(10));
    SourcePrincipalContextData sourcePrincipalContextData =
        SourcePrincipalContextData.builder().principal(principal).build();
    globalContext.upsertGlobalContextRecord(sourcePrincipalContextData);
    String eventData = objectMapper.writeValueAsString(environmentUpsertEvent);

    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .id(randomAlphabetic(10))
                                  .blocked(false)
                                  .eventType(OutboxEventConstants.ENVIRONMENT_UPSERTED)
                                  .eventData(eventData)
                                  .resource(environmentUpsertEvent.getResource())
                                  .resourceScope(environmentUpsertEvent.getResourceScope())
                                  .globalContext(globalContext)
                                  .createdAt(Long.valueOf(randomNumeric(6)))
                                  .build();

    String newYaml = getYamlString(EnvironmentMapper.toNGEnvironmentConfig(environment));
    final ArgumentCaptor<AuditEntry> auditEntryArgumentCaptor = ArgumentCaptor.forClass(AuditEntry.class);
    when(auditClientService.publishAudit(any(), any(), any())).thenReturn(true);
    environmentEventHandler.handle(outboxEvent);
    verify(auditClientService, times(1)).publishAudit(auditEntryArgumentCaptor.capture(), any(), any());
    AuditEntry auditEntry = auditEntryArgumentCaptor.getValue();
    assertAuditEntry(accountIdentifier, orgIdentifier, projectIdentifier, identifier, auditEntry, outboxEvent);
    assertEquals(Action.UPSERT, auditEntry.getAction());
    assertNull(auditEntry.getOldYaml());
    assertEquals(newYaml, auditEntry.getNewYaml());
  }

  private void assertAuditEntry(String accountId, String orgIdentifier, String projectIdentifier, String identifier,
      AuditEntry auditEntry, OutboxEvent outboxEvent) {
    assertNotNull(auditEntry);
    assertEquals(accountId, auditEntry.getResourceScope().getAccountIdentifier());
    assertEquals(orgIdentifier, auditEntry.getResourceScope().getOrgIdentifier());
    assertEquals(projectIdentifier, auditEntry.getResourceScope().getProjectIdentifier());
    assertEquals(auditEntry.getInsertId(), outboxEvent.getId());
    assertEquals(identifier, auditEntry.getResource().getIdentifier());
    assertEquals(ModuleType.CORE, auditEntry.getModule());
    assertEquals(outboxEvent.getCreatedAt().longValue(), auditEntry.getTimestamp());
  }
}
