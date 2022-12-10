/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.servicenow;

import static io.harness.rule.OwnerRule.NAMANG;
import static io.harness.rule.OwnerRule.PRABU;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.beans.DecryptableEntity;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResourceClient;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.servicenow.ServiceNowAuthCredentialsDTO;
import io.harness.delegate.beans.connector.servicenow.ServiceNowAuthType;
import io.harness.delegate.beans.connector.servicenow.ServiceNowAuthenticationDTO;
import io.harness.delegate.beans.connector.servicenow.ServiceNowConnectorDTO;
import io.harness.delegate.beans.connector.servicenow.ServiceNowUserNamePasswordDTO;
import io.harness.delegate.task.servicenow.ServiceNowTaskNGParameters;
import io.harness.delegate.task.servicenow.ServiceNowTaskNGParameters.ServiceNowTaskNGParametersBuilder;
import io.harness.delegate.task.servicenow.ServiceNowTaskNGResponse;
import io.harness.encryption.SecretRefData;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.ServiceNowException;
import io.harness.ng.core.NGAccess;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.remote.client.NGRestUtils;
import io.harness.rule.Owner;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.serializer.KryoSerializer;
import io.harness.servicenow.ServiceNowImportSetResponseNG;
import io.harness.servicenow.ServiceNowImportSetTransformMapResult;
import io.harness.steps.servicenow.importset.ServiceNowImportSetOutcome;

import com.google.inject.Inject;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import org.apache.groovy.util.Maps;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public class ServiceNowStepHelperServiceTest extends CategoryTest {
  private static final String STAGING_TABLE = "STAGING_TABLE";
  private static final String IMPORT_SET = "IMPORT_SET";
  private static final String RECORD_URL =
      "https://harness.service-now.com/api/now/table/incident/a639e9ccdb4651909e7c2a5913961911";
  private static final String TARGET_TABLE = "incident";
  private static final String TRANSFORM_MAP = "Testing 2 transform maps";
  private static final String STATUS = "inserted";
  private static final String DISPLAY_NAME = "number";
  private static final String DISPLAY_VALUE = "INC0083151";
  private static final String ERROR_STATUS = "error";
  private static final String ERROR_MESSAGE = "No transform entry or scripts are defined; Target record not found";

  private ServiceNowImportSetTransformMapResult normalResult;
  private ServiceNowImportSetTransformMapResult errorResult;
  private ServiceNowImportSetTransformMapResult errorResultWhenNoTransformMap;
  private ServiceNowImportSetTransformMapResult invalidResultWithoutStatus;
  private ServiceNowImportSetTransformMapResult invalidResultWithoutTransformMap;

  @Mock private ConnectorResourceClient connectorResourceClient;
  @Mock private SecretManagerClientService secretManagerClientService;
  @Mock private KryoSerializer kryoSerializer;
  @Inject @InjectMocks ServiceNowStepHelperServiceImpl serviceNowStepHelperService;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    normalResult = ServiceNowImportSetTransformMapResult.builder()
                       .targetRecordURL(RECORD_URL)
                       .targetTable(TARGET_TABLE)
                       .transformMap(TRANSFORM_MAP)
                       .status(STATUS)
                       .displayName(DISPLAY_NAME)
                       .displayValue(DISPLAY_VALUE)
                       .build();
    errorResult = ServiceNowImportSetTransformMapResult.builder()
                      .transformMap(TRANSFORM_MAP)
                      .targetTable(TARGET_TABLE)
                      .status(ERROR_STATUS)
                      .errorMessage(ERROR_MESSAGE)
                      .build();
    errorResultWhenNoTransformMap = ServiceNowImportSetTransformMapResult.builder()
                                        .transformMap("")
                                        .status(ERROR_STATUS)
                                        .errorMessage(ERROR_MESSAGE)
                                        .build();
    invalidResultWithoutStatus =
        ServiceNowImportSetTransformMapResult.builder().transformMap("").errorMessage(ERROR_MESSAGE).build();
    invalidResultWithoutTransformMap =
        ServiceNowImportSetTransformMapResult.builder().status(ERROR_STATUS).errorMessage(ERROR_MESSAGE).build();
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testPrepareImportSetStepResponse() throws Exception {
    ServiceNowTaskNGResponse serviceNowTaskNGResponse =
        ServiceNowTaskNGResponse.builder()
            .serviceNowImportSetResponseNG(ServiceNowImportSetResponseNG.builder()
                                               .importSet(IMPORT_SET)
                                               .stagingTable(STAGING_TABLE)
                                               .serviceNowImportSetTransformMapResultList(Arrays.asList(
                                                   normalResult, errorResult, errorResultWhenNoTransformMap))
                                               .build())
            .build();
    ServiceNowImportSetOutcome stepOutcome = (ServiceNowImportSetOutcome) serviceNowStepHelperService
                                                 .prepareImportSetStepResponse(() -> serviceNowTaskNGResponse)
                                                 .getStepOutcomes()
                                                 .iterator()
                                                 .next()
                                                 .getOutcome();
    assertThat(stepOutcome.getImportSetNumber()).isEqualTo(IMPORT_SET);
    assertThat(stepOutcome.getStagingTable()).isEqualTo(STAGING_TABLE);
    assertThat(stepOutcome.getTransformMapOutcomes().get(0).getTargetRecordURL()).isEqualTo(RECORD_URL);
    assertThat(stepOutcome.getTransformMapOutcomes().get(0).getTargetTable()).isEqualTo(TARGET_TABLE);
    assertThat(stepOutcome.getTransformMapOutcomes().get(0).getTransformMap()).isEqualTo(TRANSFORM_MAP);
    assertThat(stepOutcome.getTransformMapOutcomes().get(0).getStatus()).isEqualTo(STATUS);
    assertThat(stepOutcome.getTransformMapOutcomes().get(0).getDisplayName()).isEqualTo(DISPLAY_NAME);
    assertThat(stepOutcome.getTransformMapOutcomes().get(0).getDisplayValue()).isEqualTo(DISPLAY_VALUE);

    assertThat(stepOutcome.getTransformMapOutcomes().get(1).getTransformMap()).isEqualTo(TRANSFORM_MAP);
    assertThat(stepOutcome.getTransformMapOutcomes().get(1).getStatus()).isEqualTo(ERROR_STATUS);
    assertThat(stepOutcome.getTransformMapOutcomes().get(1).getErrorMessage()).isEqualTo(ERROR_MESSAGE);
    assertThat(stepOutcome.getTransformMapOutcomes().get(1).getTargetTable()).isEqualTo(TARGET_TABLE);

    assertThat(stepOutcome.getTransformMapOutcomes().get(2).getTransformMap()).isEqualTo("");
    assertThat(stepOutcome.getTransformMapOutcomes().get(2).getStatus()).isEqualTo(ERROR_STATUS);
    assertThat(stepOutcome.getTransformMapOutcomes().get(2).getErrorMessage())
        .isEqualTo(ERROR_MESSAGE + ", please ensure that transform map is defined corresponding to the staging table");

    // when import set is missing
    ServiceNowTaskNGResponse serviceNowTaskNGResponse1 =
        ServiceNowTaskNGResponse.builder()
            .serviceNowImportSetResponseNG(ServiceNowImportSetResponseNG.builder()
                                               .stagingTable(STAGING_TABLE)
                                               .serviceNowImportSetTransformMapResultList(Arrays.asList(
                                                   normalResult, errorResult, errorResultWhenNoTransformMap))
                                               .build())
            .build();
    assertThatThrownBy(() -> serviceNowStepHelperService.prepareImportSetStepResponse(() -> serviceNowTaskNGResponse1))
        .isInstanceOf(ServiceNowException.class);
    // when staging table is missing
    ServiceNowTaskNGResponse serviceNowTaskNGResponse2 =
        ServiceNowTaskNGResponse.builder()
            .serviceNowImportSetResponseNG(ServiceNowImportSetResponseNG.builder()
                                               .importSet(IMPORT_SET)
                                               .serviceNowImportSetTransformMapResultList(Arrays.asList(
                                                   normalResult, errorResult, errorResultWhenNoTransformMap))
                                               .build())
            .build();
    assertThatThrownBy(() -> serviceNowStepHelperService.prepareImportSetStepResponse(() -> serviceNowTaskNGResponse2))
        .isInstanceOf(ServiceNowException.class);
    // invalid transform mao response
    ServiceNowTaskNGResponse serviceNowTaskNGResponse3 =
        ServiceNowTaskNGResponse.builder()
            .serviceNowImportSetResponseNG(ServiceNowImportSetResponseNG.builder()
                                               .importSet(IMPORT_SET)
                                               .stagingTable(STAGING_TABLE)
                                               .serviceNowImportSetTransformMapResultList(
                                                   Collections.singletonList(invalidResultWithoutTransformMap))
                                               .build())
            .build();
    assertThatThrownBy(() -> serviceNowStepHelperService.prepareImportSetStepResponse(() -> serviceNowTaskNGResponse3))
        .isInstanceOf(ServiceNowException.class);

    ServiceNowTaskNGResponse serviceNowTaskNGResponse4 =
        ServiceNowTaskNGResponse.builder()
            .serviceNowImportSetResponseNG(
                ServiceNowImportSetResponseNG.builder()
                    .importSet(IMPORT_SET)
                    .stagingTable(STAGING_TABLE)
                    .serviceNowImportSetTransformMapResultList(Collections.singletonList(invalidResultWithoutStatus))
                    .build())
            .build();
    assertThatThrownBy(() -> serviceNowStepHelperService.prepareImportSetStepResponse(() -> serviceNowTaskNGResponse4))
        .isInstanceOf(ServiceNowException.class);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void testPrepareTaskRequest() {
    MockedStatic<NGRestUtils> mockStatic = Mockito.mockStatic(NGRestUtils.class);

    Optional<ConnectorDTO> connectorDTO =
        Optional.of(ConnectorDTO.builder()
                        .connectorInfo(ConnectorInfoDTO.builder()
                                           .connectorConfig(ServiceNowConnectorDTO.builder()
                                                                .username("USERNAME")
                                                                .serviceNowUrl("url")
                                                                .passwordRef(SecretRefData.builder().build())
                                                                .build())
                                           .build())
                        .build());
    Optional<ConnectorDTO> connectorDTO1 = Optional.of(
        ConnectorDTO.builder()
            .connectorInfo(ConnectorInfoDTO.builder().connectorConfig(AwsConnectorDTO.builder().build()).build())
            .build());
    Optional<ConnectorDTO> connectorDTO2 = Optional.of(
        ConnectorDTO.builder()
            .connectorInfo(
                ConnectorInfoDTO.builder()
                    .connectorConfig(ServiceNowConnectorDTO.builder()
                                         .username("username")
                                         .serviceNowUrl("url")
                                         .passwordRef(SecretRefData.builder().build())
                                         .auth(ServiceNowAuthenticationDTO.builder()
                                                   .authType(ServiceNowAuthType.USER_PASSWORD)
                                                   .credentials(ServiceNowUserNamePasswordDTO.builder()
                                                                    .username("username")
                                                                    .passwordRef(SecretRefData.builder().build())
                                                                    .build())
                                                   .build())
                                         .build())
                    .build())
            .build());
    ServiceNowTaskNGParametersBuilder paramsBuilder = ServiceNowTaskNGParameters.builder();
    Ambiance ambiance = Ambiance.newBuilder()
                            .putAllSetupAbstractions(Maps.of("accountId", "accountId", "projectIdentifier",
                                "projectIdentfier", "orgIdentifier", "orgIdentifier"))
                            .build();

    when(NGRestUtils.getResponse(any())).thenReturn(connectorDTO1);
    assertThatThrownBy(
        () -> serviceNowStepHelperService.prepareTaskRequest(paramsBuilder, ambiance, "connectorRef", null, ""))
        .isInstanceOf(InvalidRequestException.class);

    when(NGRestUtils.getResponse(null)).thenReturn(Optional.empty());
    assertThatThrownBy(
        () -> serviceNowStepHelperService.prepareTaskRequest(paramsBuilder, ambiance, "connectorRef", null, ""))
        .isInstanceOf(InvalidRequestException.class);

    when(NGRestUtils.getResponse(any())).thenReturn(connectorDTO);
    serviceNowStepHelperService.prepareTaskRequest(paramsBuilder, ambiance, "connectorRef", "10m", "");
    ArgumentCaptor<DecryptableEntity> requestArgumentCaptorForSecretService =
        ArgumentCaptor.forClass(DecryptableEntity.class);
    ArgumentCaptor<NGAccess> requestArgumentCaptorForNGAccess = ArgumentCaptor.forClass(NGAccess.class);
    verify(secretManagerClientService)
        .getEncryptionDetails(
            requestArgumentCaptorForNGAccess.capture(), requestArgumentCaptorForSecretService.capture());
    assertThat(requestArgumentCaptorForSecretService.getValue() instanceof ServiceNowConnectorDTO).isTrue();
    assertThat(requestArgumentCaptorForNGAccess.getValue()).isEqualTo(AmbianceUtils.getNgAccess(ambiance));
    when(NGRestUtils.getResponse(any())).thenReturn(connectorDTO2);
    serviceNowStepHelperService.prepareTaskRequest(paramsBuilder, ambiance, "connectorRef", "10m", "");
    verify(secretManagerClientService, times(2))
        .getEncryptionDetails(
            requestArgumentCaptorForNGAccess.capture(), requestArgumentCaptorForSecretService.capture());
    assertThat(requestArgumentCaptorForSecretService.getValue() instanceof ServiceNowAuthCredentialsDTO).isTrue();
    assertThat(requestArgumentCaptorForNGAccess.getValue()).isEqualTo(AmbianceUtils.getNgAccess(ambiance));
  }
}
