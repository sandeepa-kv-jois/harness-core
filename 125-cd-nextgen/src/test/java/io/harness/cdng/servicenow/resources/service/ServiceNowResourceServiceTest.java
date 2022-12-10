/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.servicenow.resources.service;

import static io.harness.rule.OwnerRule.NAMANG;
import static io.harness.rule.OwnerRule.PRABU;
import static io.harness.rule.OwnerRule.vivekveman;

import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.beans.DecryptableEntity;
import io.harness.beans.DelegateTaskRequest;
import io.harness.beans.IdentifierRef;
import io.harness.category.element.UnitTests;
import io.harness.common.NGTaskType;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.servicenow.ServiceNowAuthCredentialsDTO;
import io.harness.delegate.beans.connector.servicenow.ServiceNowAuthType;
import io.harness.delegate.beans.connector.servicenow.ServiceNowAuthenticationDTO;
import io.harness.delegate.beans.connector.servicenow.ServiceNowConnectorDTO;
import io.harness.delegate.beans.connector.servicenow.ServiceNowUserNamePasswordDTO;
import io.harness.delegate.task.servicenow.ServiceNowTaskNGParameters;
import io.harness.delegate.task.servicenow.ServiceNowTaskNGResponse;
import io.harness.encryption.SecretRefData;
import io.harness.rule.Owner;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.service.DelegateGrpcClientWrapper;
import io.harness.servicenow.ServiceNowActionNG;
import io.harness.servicenow.ServiceNowFieldNG;
import io.harness.servicenow.ServiceNowFieldSchemaNG;
import io.harness.servicenow.ServiceNowFieldTypeNG;
import io.harness.servicenow.ServiceNowStagingTable;
import io.harness.servicenow.ServiceNowTemplate;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class ServiceNowResourceServiceTest extends CategoryTest {
  private static final String ACCOUNT_ID = "accountId";
  private static final String ORG_IDENTIFIER = "orgIdentifier";
  private static final String PROJECT_IDENTIFIER = "projectIdentifier";
  private static final String IDENTIFIER = "identifier";
  private static final String TEMPLATE_NAME = "TEMPLATE_NAME";
  private static final IdentifierRef identifierRef = IdentifierRef.builder()
                                                         .accountIdentifier(ACCOUNT_ID)
                                                         .identifier(IDENTIFIER)
                                                         .projectIdentifier(PROJECT_IDENTIFIER)
                                                         .orgIdentifier(ORG_IDENTIFIER)
                                                         .build();

  @Mock ConnectorService connectorService;
  @Mock SecretManagerClientService secretManagerClientService;
  @Mock DelegateGrpcClientWrapper delegateGrpcClientWrapper;

  @InjectMocks @Inject ServiceNowResourceServiceImpl serviceNowResourceService;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    when(connectorService.get(any(), any(), any(), any())).thenReturn(Optional.of(getConnector(false)));
    when(secretManagerClientService.getEncryptionDetails(any(), any()))
        .thenReturn(Lists.newArrayList(EncryptedDataDetail.builder().build()));
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testGetIssueCreateMeta() {
    List<ServiceNowFieldNG> serviceNowFieldNGList =
        Arrays.asList(ServiceNowFieldNG.builder().name("name1").key("key1").internalType("string").build(),
            ServiceNowFieldNG.builder().name("name2").key("key2").internalType("unknown_xyz").build());
    List<ServiceNowFieldNG> serviceNowFieldNGListExpected =
        Arrays.asList(ServiceNowFieldNG.builder()
                          .name("name1")
                          .key("key1")
                          .internalType(null)
                          .schema(ServiceNowFieldSchemaNG.builder()
                                      .array(false)
                                      .customType(null)
                                      .typeStr("string")
                                      .type(ServiceNowFieldTypeNG.STRING)
                                      .build())
                          .build(),
            ServiceNowFieldNG.builder()
                .name("name2")
                .key("key2")
                .internalType(null)
                .schema(ServiceNowFieldSchemaNG.builder()
                            .array(false)
                            .customType(null)
                            .typeStr(null)
                            .type(ServiceNowFieldTypeNG.UNKNOWN)
                            .build())
                .build());
    when(delegateGrpcClientWrapper.executeSyncTask(any()))
        .thenReturn(ServiceNowTaskNGResponse.builder().serviceNowFieldNGList(serviceNowFieldNGList).build());
    List<ServiceNowFieldNG> serviceNowFieldNGListReturn = serviceNowResourceService.getIssueCreateMetadata(
        identifierRef, ORG_IDENTIFIER, PROJECT_IDENTIFIER, "CHANGE_REQUEST");
    assertTrue(serviceNowFieldNGListReturn.size() == serviceNowFieldNGListExpected.size()
        && serviceNowFieldNGListReturn.containsAll(serviceNowFieldNGListExpected)
        && serviceNowFieldNGListExpected.containsAll(serviceNowFieldNGListReturn));
    ArgumentCaptor<DelegateTaskRequest> requestArgumentCaptor = ArgumentCaptor.forClass(DelegateTaskRequest.class);
    verify(delegateGrpcClientWrapper).executeSyncTask(requestArgumentCaptor.capture());
    assertThat(requestArgumentCaptor.getValue().getTaskType()).isEqualTo(NGTaskType.SERVICENOW_TASK_NG.name());
    assertThat(requestArgumentCaptor.getValue().getAccountId()).isEqualTo(ACCOUNT_ID);
    ServiceNowTaskNGParameters parameters =
        (ServiceNowTaskNGParameters) requestArgumentCaptor.getValue().getTaskParameters();
    assertThat(parameters.getAction()).isEqualTo(ServiceNowActionNG.GET_TICKET_CREATE_METADATA);
    assertThat(parameters.getTicketType()).isEqualTo("CHANGE_REQUEST");
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testGetIssueCreateMetaWhenDelegateResponseNotHaveType() {
    ServiceNowFieldNG field1 = ServiceNowFieldNG.builder().name("name1").key("key1").build();
    ServiceNowFieldNG field2 = ServiceNowFieldNG.builder().name("name2").key("key2").build();
    List<ServiceNowFieldNG> serviceNowFieldNGList = Arrays.asList(field1, field2);
    List<ServiceNowFieldNG> serviceNowFieldNGListExpected = Arrays.asList(
        ServiceNowFieldNG.builder()
            .name("name1")
            .key("key1")
            .internalType(null)
            .schema(ServiceNowFieldSchemaNG.builder().array(false).customType(null).typeStr(null).type(null).build())
            .build(),
        ServiceNowFieldNG.builder()
            .name("name2")
            .key("key2")
            .internalType(null)
            .schema(ServiceNowFieldSchemaNG.builder().array(false).customType(null).typeStr(null).type(null).build())
            .build());
    when(delegateGrpcClientWrapper.executeSyncTask(any()))
        .thenReturn(ServiceNowTaskNGResponse.builder().serviceNowFieldNGList(serviceNowFieldNGList).build());
    List<ServiceNowFieldNG> serviceNowFieldNGListReturn = serviceNowResourceService.getIssueCreateMetadata(
        identifierRef, ORG_IDENTIFIER, PROJECT_IDENTIFIER, "CHANGE_REQUEST");
    assertTrue(serviceNowFieldNGListReturn.size() == serviceNowFieldNGListExpected.size()
        && serviceNowFieldNGListReturn.containsAll(serviceNowFieldNGListExpected)
        && serviceNowFieldNGListExpected.containsAll(serviceNowFieldNGListReturn));
    ArgumentCaptor<DelegateTaskRequest> requestArgumentCaptor = ArgumentCaptor.forClass(DelegateTaskRequest.class);
    verify(delegateGrpcClientWrapper).executeSyncTask(requestArgumentCaptor.capture());
    assertThat(requestArgumentCaptor.getValue().getTaskType()).isEqualTo(NGTaskType.SERVICENOW_TASK_NG.name());
    assertThat(requestArgumentCaptor.getValue().getAccountId()).isEqualTo(ACCOUNT_ID);
    ServiceNowTaskNGParameters parameters =
        (ServiceNowTaskNGParameters) requestArgumentCaptor.getValue().getTaskParameters();
    assertThat(parameters.getAction()).isEqualTo(ServiceNowActionNG.GET_TICKET_CREATE_METADATA);
    assertThat(parameters.getTicketType()).isEqualTo("CHANGE_REQUEST");
  }

  private ConnectorResponseDTO getConnector(boolean updatedYAML) {
    if (updatedYAML) {
      ConnectorInfoDTO connectorInfoDTO =
          ConnectorInfoDTO.builder()
              .connectorType(ConnectorType.SERVICENOW)
              .connectorConfig(ServiceNowConnectorDTO.builder()
                                   .serviceNowUrl("url")
                                   .username("username")
                                   .passwordRef(SecretRefData.builder().build())
                                   .auth(ServiceNowAuthenticationDTO.builder()
                                             .authType(ServiceNowAuthType.USER_PASSWORD)
                                             .credentials(ServiceNowUserNamePasswordDTO.builder()
                                                              .username("username")
                                                              .passwordRef(SecretRefData.builder().build())
                                                              .build())
                                             .build())
                                   .build())
              .build();
      return ConnectorResponseDTO.builder().connector(connectorInfoDTO).build();
    }
    ConnectorInfoDTO connectorInfoDTO = ConnectorInfoDTO.builder()
                                            .connectorType(ConnectorType.SERVICENOW)
                                            .connectorConfig(ServiceNowConnectorDTO.builder()
                                                                 .serviceNowUrl("url")
                                                                 .username("username")
                                                                 .passwordRef(SecretRefData.builder().build())
                                                                 .build())
                                            .build();
    return ConnectorResponseDTO.builder().connector(connectorInfoDTO).build();
  }
  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testGetMetadata() {
    List<ServiceNowFieldNG> serviceNowFieldNGList =
        Arrays.asList(ServiceNowFieldNG.builder().name("name1").key("key1").build(),
            ServiceNowFieldNG.builder().name("name2").key("key2").build());
    when(delegateGrpcClientWrapper.executeSyncTask(any()))
        .thenReturn(ServiceNowTaskNGResponse.builder().serviceNowFieldNGList(serviceNowFieldNGList).build());
    assertThat(serviceNowResourceService.getMetadata(identifierRef, ORG_IDENTIFIER, PROJECT_IDENTIFIER, "CHANGE_TASK"))
        .isEqualTo(serviceNowFieldNGList);
    ArgumentCaptor<DelegateTaskRequest> requestArgumentCaptor = ArgumentCaptor.forClass(DelegateTaskRequest.class);
    verify(delegateGrpcClientWrapper).executeSyncTask(requestArgumentCaptor.capture());
    ServiceNowTaskNGParameters parameters =
        (ServiceNowTaskNGParameters) requestArgumentCaptor.getValue().getTaskParameters();
    assertThat(parameters.getAction()).isEqualTo(ServiceNowActionNG.GET_METADATA);
    assertThat(parameters.getTicketType()).isEqualTo("CHANGE_TASK");
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testgetTemplateList() {
    List<ServiceNowTemplate> serviceNowFieldNGList1 =
        Arrays.asList(ServiceNowTemplate.builder().name("name1").sys_id("key1").build(),
            ServiceNowTemplate.builder().name("name2").sys_id("key2").build());
    when(delegateGrpcClientWrapper.executeSyncTask(any()))
        .thenReturn(ServiceNowTaskNGResponse.builder().serviceNowTemplateList(serviceNowFieldNGList1).build());
    assertThat(serviceNowResourceService.getTemplateList(
                   identifierRef, ORG_IDENTIFIER, PROJECT_IDENTIFIER, 0, 0, TEMPLATE_NAME, "CHANGE_TASK"))
        .isEqualTo(serviceNowFieldNGList1);
    ArgumentCaptor<DelegateTaskRequest> requestArgumentCaptor = ArgumentCaptor.forClass(DelegateTaskRequest.class);
    verify(delegateGrpcClientWrapper).executeSyncTask(requestArgumentCaptor.capture());
    ServiceNowTaskNGParameters parameters =
        (ServiceNowTaskNGParameters) requestArgumentCaptor.getValue().getTaskParameters();
    assertThat(parameters.getAction()).isEqualTo(ServiceNowActionNG.GET_TEMPLATE);
    assertThat(parameters.getTicketType()).isEqualTo("CHANGE_TASK");
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testGetStagingTableList() {
    List<ServiceNowStagingTable> serviceNowStagingTableList =
        Arrays.asList(ServiceNowStagingTable.builder().name("name1").label("label1").build(),
            ServiceNowStagingTable.builder().name("name2").label("label2").build());
    when(delegateGrpcClientWrapper.executeSyncTask(any()))
        .thenReturn(ServiceNowTaskNGResponse.builder().serviceNowStagingTableList(serviceNowStagingTableList).build());
    assertThat(serviceNowResourceService.getStagingTableList(identifierRef, ORG_IDENTIFIER, PROJECT_IDENTIFIER))
        .isEqualTo(serviceNowStagingTableList);
    ArgumentCaptor<DelegateTaskRequest> requestArgumentCaptor = ArgumentCaptor.forClass(DelegateTaskRequest.class);
    ArgumentCaptor<DecryptableEntity> requestArgumentCaptorForSecretService =
        ArgumentCaptor.forClass(DecryptableEntity.class);
    verify(delegateGrpcClientWrapper).executeSyncTask(requestArgumentCaptor.capture());
    ServiceNowTaskNGParameters parameters =
        (ServiceNowTaskNGParameters) requestArgumentCaptor.getValue().getTaskParameters();
    verify(secretManagerClientService).getEncryptionDetails(any(), requestArgumentCaptorForSecretService.capture());
    assertThat(requestArgumentCaptorForSecretService.getValue() instanceof ServiceNowConnectorDTO).isTrue();
    assertThat(parameters.getAction()).isEqualTo(ServiceNowActionNG.GET_IMPORT_SET_STAGING_TABLES);
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testGetStagingTableListWithUpdatedConnectorFlow() {
    List<ServiceNowStagingTable> serviceNowStagingTableList =
        Arrays.asList(ServiceNowStagingTable.builder().name("name1").label("label1").build(),
            ServiceNowStagingTable.builder().name("name2").label("label2").build());
    when(connectorService.get(any(), any(), any(), any())).thenReturn(Optional.of(getConnector(true)));
    when(delegateGrpcClientWrapper.executeSyncTask(any()))
        .thenReturn(ServiceNowTaskNGResponse.builder().serviceNowStagingTableList(serviceNowStagingTableList).build());
    assertThat(serviceNowResourceService.getStagingTableList(identifierRef, ORG_IDENTIFIER, PROJECT_IDENTIFIER))
        .isEqualTo(serviceNowStagingTableList);
    ArgumentCaptor<DelegateTaskRequest> requestArgumentCaptor = ArgumentCaptor.forClass(DelegateTaskRequest.class);
    ArgumentCaptor<DecryptableEntity> requestArgumentCaptorForSecretService =
        ArgumentCaptor.forClass(DecryptableEntity.class);
    verify(delegateGrpcClientWrapper).executeSyncTask(requestArgumentCaptor.capture());
    ServiceNowTaskNGParameters parameters =
        (ServiceNowTaskNGParameters) requestArgumentCaptor.getValue().getTaskParameters();
    verify(secretManagerClientService).getEncryptionDetails(any(), requestArgumentCaptorForSecretService.capture());
    assertThat(requestArgumentCaptorForSecretService.getValue() instanceof ServiceNowAuthCredentialsDTO).isTrue();
    assertThat(parameters.getAction()).isEqualTo(ServiceNowActionNG.GET_IMPORT_SET_STAGING_TABLES);
  }
}
