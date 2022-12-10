/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.template.services;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.rule.OwnerRule.ABHINAV_MITTAL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.TemplateServiceTestBase;
import io.harness.account.AccountClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.encryption.Scope;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.JsonSchemaException;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.template.TemplateEntityType;
import io.harness.pipeline.yamlschema.PipelineYamlSchemaServiceClient;
import io.harness.pms.yaml.YamlSchemaResponse;
import io.harness.remote.client.NGRestUtils;
import io.harness.rule.Owner;
import io.harness.template.entity.TemplateEntity;
import io.harness.template.helpers.TemplateYamlSchemaMergeHelper;
import io.harness.yaml.schema.YamlSchemaProvider;
import io.harness.yaml.validator.InvalidYamlException;
import io.harness.yaml.validator.YamlSchemaValidator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Objects;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.mockito.stubbing.Answer;
import retrofit2.Call;

@OwnedBy(CDC)
public class NGTemplateSchemaServiceImplTest extends TemplateServiceTestBase {
  @Spy @InjectMocks NGTemplateSchemaServiceImpl ngTemplateSchemaService;

  @Mock TemplateYamlSchemaMergeHelper templateYamlSchemaMergeHelper;

  @Mock PipelineYamlSchemaServiceClient pipelineYamlSchemaServiceClient;
  @Mock AccountClient accountClient;

  @Mock YamlSchemaProvider yamlSchemaProvider;

  @Mock YamlSchemaValidator yamlSchemaValidator;
  private final String ACCOUNT_ID = RandomStringUtils.randomAlphanumeric(6);
  private final String ORG_IDENTIFIER = "orgId";
  private final String PROJ_IDENTIFIER = "projId";
  private final String TEMPLATE_IDENTIFIER = "template1";
  private final String TEMPLATE_VERSION_LABEL = "version1";
  private final String TEMPLATE_CHILD_TYPE = "ShellScript";

  private String yaml;

  TemplateEntity pipelineTemplateEntity;
  TemplateEntity stepTemplateEntity;

  private String readFile(String filename) {
    ClassLoader classLoader = getClass().getClassLoader();
    try {
      return Resources.toString(Objects.requireNonNull(classLoader.getResource(filename)), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new InvalidRequestException("Could not read resource file: " + filename);
    }
  }

  private JsonNode readJsonFile(String filename) {
    ClassLoader classLoader = getClass().getClassLoader();
    ObjectMapper objectMapper = new ObjectMapper();
    try {
      return objectMapper.readTree(
          Resources.toString(Objects.requireNonNull(classLoader.getResource(filename)), StandardCharsets.UTF_8));
    } catch (IOException e) {
      throw new InvalidRequestException("Could not read resource file: " + filename);
    }
  }

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.openMocks(this);
    String filename = "template.yaml";
    yaml = readFile(filename);

    on(ngTemplateSchemaService).set("yamlSchemaProvider", yamlSchemaProvider);
    on(ngTemplateSchemaService).set("yamlSchemaValidator", yamlSchemaValidator);
    on(ngTemplateSchemaService).set("allowedParallelStages", 1);

    MockedStatic<TemplateYamlSchemaMergeHelper> templateYamlSchemaMergeHelperMockedStatic =
        Mockito.mockStatic(TemplateYamlSchemaMergeHelper.class);
    when(TemplateYamlSchemaMergeHelper.isFeatureFlagEnabled(any(), anyString(), any())).thenReturn(true);
    templateYamlSchemaMergeHelperMockedStatic
        .when(() -> TemplateYamlSchemaMergeHelper.mergeYamlSchema(any(), any(), any(), any()))
        .thenAnswer((Answer<Void>) invocation -> null);
    when(yamlSchemaProvider.getYamlSchema(any(), any(), any(), any())).thenReturn(readJsonFile("template-schema.json"));

    pipelineTemplateEntity = TemplateEntity.builder()
                                 .accountId(ACCOUNT_ID)
                                 .orgIdentifier(ORG_IDENTIFIER)
                                 .projectIdentifier(PROJ_IDENTIFIER)
                                 .identifier(TEMPLATE_IDENTIFIER)
                                 .name(TEMPLATE_IDENTIFIER)
                                 .versionLabel(TEMPLATE_VERSION_LABEL)
                                 .yaml(yaml)
                                 .templateEntityType(TemplateEntityType.STEP_TEMPLATE)
                                 .childType(TEMPLATE_CHILD_TYPE)
                                 .fullyQualifiedIdentifier("account_id/orgId/projId/template1/version1/")
                                 .templateScope(Scope.PROJECT)
                                 .build();

    stepTemplateEntity = TemplateEntity.builder()
                             .accountId(ACCOUNT_ID)
                             .orgIdentifier(ORG_IDENTIFIER)
                             .projectIdentifier(PROJ_IDENTIFIER)
                             .identifier(TEMPLATE_IDENTIFIER)
                             .name(TEMPLATE_IDENTIFIER)
                             .versionLabel(TEMPLATE_VERSION_LABEL)
                             .yaml(yaml)
                             .templateEntityType(TemplateEntityType.STEP_TEMPLATE)
                             .childType(TEMPLATE_CHILD_TYPE)
                             .fullyQualifiedIdentifier("account_id/orgId/projId/template1/version1/")
                             .templateScope(Scope.PROJECT)
                             .build();
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void getTemplateSchemaWithTemplateChildTypeNull() {
    assertThat(ngTemplateSchemaService.getTemplateSchema(
                   ACCOUNT_ID, PROJ_IDENTIFIER, ORG_IDENTIFIER, Scope.PROJECT, null, TemplateEntityType.STAGE_TEMPLATE))
        .isEqualTo(readJsonFile("template-schema.json"));
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void getTemplateSchemaWithException() {
    Call<ResponseDTO<YamlSchemaResponse>> requestCall = mock(Call.class);
    doReturn(requestCall).when(pipelineYamlSchemaServiceClient).getYamlSchema(any(), any(), any(), any(), any(), any());
    try (MockedStatic<NGRestUtils> mockStatic = Mockito.mockStatic(NGRestUtils.class)) {
      mockStatic.when(() -> NGRestUtils.getResponse(requestCall)).thenThrow(new JsonSchemaException("Exception"));

      assertThatThrownBy(() -> {
        ngTemplateSchemaService.getTemplateSchema(
            ACCOUNT_ID, PROJ_IDENTIFIER, ORG_IDENTIFIER, Scope.PROJECT, null, TemplateEntityType.PIPELINE_TEMPLATE);
      }).isInstanceOf(JsonSchemaException.class);
    }
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testValidateStepSchemaBy() throws Exception {
    when(yamlSchemaValidator.validate(anyString(), anyString(), anyBoolean(), anyInt(), anyString()))
        .thenReturn(Collections.emptySet());
    Call<ResponseDTO<YamlSchemaResponse>> requestCall = mock(Call.class);
    doReturn(requestCall).when(pipelineYamlSchemaServiceClient).getYamlSchema(any(), any(), any(), any(), any(), any());
    try (MockedStatic<NGRestUtils> mockStatic = Mockito.mockStatic(NGRestUtils.class)) {
      YamlSchemaResponse yamlSchemaResponse =
          YamlSchemaResponse.builder().schema(null).schemaErrorResponse(null).build();
      mockStatic.when(() -> NGRestUtils.getResponse(requestCall)).thenReturn(yamlSchemaResponse);
      ngTemplateSchemaService.validateYamlSchemaInternal(stepTemplateEntity);
      verify(yamlSchemaValidator, times(1)).validate(anyString(), anyString(), anyBoolean(), anyInt(), anyString());
    }
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testValidatePipelineSchema() throws IOException {
    when(yamlSchemaValidator.validate(anyString(), anyString(), anyBoolean(), anyInt(), anyString()))
        .thenReturn(Collections.emptySet());
    Call<ResponseDTO<YamlSchemaResponse>> requestCall = mock(Call.class);
    doReturn(requestCall).when(pipelineYamlSchemaServiceClient).getYamlSchema(any(), any(), any(), any(), any(), any());
    try (MockedStatic<NGRestUtils> mockStatic = Mockito.mockStatic(NGRestUtils.class)) {
      YamlSchemaResponse yamlSchemaResponse =
          YamlSchemaResponse.builder().schema(null).schemaErrorResponse(null).build();
      mockStatic.when(() -> NGRestUtils.getResponse(requestCall)).thenReturn(yamlSchemaResponse);
      ngTemplateSchemaService.validateYamlSchemaInternal(stepTemplateEntity);
      verify(yamlSchemaValidator, times(1)).validate(anyString(), anyString(), anyBoolean(), anyInt(), anyString());
    }
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testInvalidValidatePipelineSchema() throws IOException {
    when(yamlSchemaValidator.validate(anyString(), anyString(), anyBoolean(), anyInt(), anyString()))
        .thenThrow(new InvalidYamlException("msg", null));
    Call<ResponseDTO<YamlSchemaResponse>> requestCall = mock(Call.class);
    doReturn(requestCall).when(pipelineYamlSchemaServiceClient).getYamlSchema(any(), any(), any(), any(), any(), any());
    try (MockedStatic<NGRestUtils> mockStatic = Mockito.mockStatic(NGRestUtils.class)) {
      YamlSchemaResponse yamlSchemaResponse =
          YamlSchemaResponse.builder().schema(null).schemaErrorResponse(null).build();
      mockStatic.when(() -> NGRestUtils.getResponse(requestCall)).thenReturn(yamlSchemaResponse);
      assertThatThrownBy(() -> ngTemplateSchemaService.validateYamlSchemaInternal(stepTemplateEntity))
          .isInstanceOf(InvalidYamlException.class);
    }
  }
}
