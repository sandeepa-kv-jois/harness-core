/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.remote;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.MEENAKSHI;
import static io.harness.rule.OwnerRule.NISHANT;
import static io.harness.rule.OwnerRule.PIYUSH;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.api.NGEncryptedDataService;
import io.harness.ng.core.api.SecretCrudService;
import io.harness.ng.core.api.impl.SecretCrudServiceImpl;
import io.harness.ng.core.api.impl.SecretPermissionValidator;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.dto.SecretResourceFilterDTO;
import io.harness.ng.core.dto.secrets.SecretDTOV2;
import io.harness.ng.core.dto.secrets.SecretResponseWrapper;
import io.harness.rule.Owner;

import software.wings.service.impl.security.NGEncryptorService;

import io.dropwizard.jersey.validation.JerseyViolationException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.validation.ConstraintViolation;
import javax.validation.Validator;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

@OwnedBy(PL)
public class NGSecretResourceV2Test extends CategoryTest {
  private SecretCrudService ngSecretService;
  private SecretPermissionValidator secretPermissionValidator;
  private Validator validator;
  private NGEncryptedDataService encryptedDataService;
  private NGSecretResourceV2 ngSecretResourceV2;
  private NGEncryptorService ngEncryptorService;

  @Before
  public void setup() {
    ngSecretService = mock(SecretCrudServiceImpl.class);
    secretPermissionValidator = mock(SecretPermissionValidator.class);
    encryptedDataService = mock(NGEncryptedDataService.class);
    ngEncryptorService = mock(NGEncryptorService.class);
    validator = mock(Validator.class);
    ngSecretResourceV2 = new NGSecretResourceV2(
        ngSecretService, validator, encryptedDataService, secretPermissionValidator, ngEncryptorService);
  }

  @Test
  @Owner(developers = PIYUSH)
  @Category(UnitTests.class)
  public void testIfListSecretsPostCallReturnsSuccessfully() {
    List<Object> mockResponse = Collections.singletonList(getMockResponse());
    Page<Object> page = new PageImpl<>(mockResponse);

    doNothing().when(secretPermissionValidator).checkForAccessOrThrow(any(), any(), any(), any());
    doReturn(page)
        .when(ngSecretService)
        .list("Test", "TestOrg", "TestProj", null, null, false, null, 1, 10, null, false);
    ResponseDTO<PageResponse<SecretResponseWrapper>> list = ngSecretResourceV2.listSecrets(
        "Test", "TestOrg", "TestProj", SecretResourceFilterDTO.builder().identifiers(null).build(), 1, 10);
    assertThat(list.getData().getContent().size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void testListSecretsAvailableAllScopeForTrue() {
    List<Object> mockResponse = Collections.singletonList(getMockResponse());
    Page<Object> page = new PageImpl<>(mockResponse);
    doNothing().when(secretPermissionValidator).checkForAccessOrThrow(any(), any(), any(), any());
    doReturn(page)
        .when(ngSecretService)
        .list("Test", "TestOrg", "TestProj", null, null, false, null, 1, 10, null, true);
    ResponseDTO<PageResponse<SecretResponseWrapper>> list = ngSecretResourceV2.listSecrets("Test", "TestOrg",
        "TestProj",
        SecretResourceFilterDTO.builder().identifiers(null).includeAllSecretsAccessibleAtScope(true).build(), 1, 10);
    verify(ngSecretService).list("Test", "TestOrg", "TestProj", null, null, false, null, 1, 10, null, true);
  }
  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void testListSecretsAvailableAllScopeIfNotInFilterShouldBeFalse() {
    List<Object> mockResponse = Collections.singletonList(getMockResponse());
    Page<Object> page = new PageImpl<>(mockResponse);
    doNothing().when(secretPermissionValidator).checkForAccessOrThrow(any(), any(), any(), any());
    doReturn(page)
        .when(ngSecretService)
        .list("Test", "TestOrg", "TestProj", null, null, false, null, 1, 10, null, false);
    ResponseDTO<PageResponse<SecretResponseWrapper>> list = ngSecretResourceV2.listSecrets(
        "Test", "TestOrg", "TestProj", SecretResourceFilterDTO.builder().identifiers(null).build(), 1, 10);
    verify(ngSecretService).list("Test", "TestOrg", "TestProj", null, null, false, null, 1, 10, null, false);
  }
  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void testListV2ForSecretsAvailableAllScopeAsTrue() {
    List<Object> mockResponse = Collections.singletonList(getMockResponse());
    Page<Object> page = new PageImpl<>(mockResponse);
    doNothing().when(secretPermissionValidator).checkForAccessOrThrow(any(), any(), any(), any());
    doReturn(page)
        .when(ngSecretService)
        .list("Test", "TestOrg", "TestProj", null, null, false, null, 1, 10, null, true);
    ResponseDTO<PageResponse<SecretResponseWrapper>> list =
        ngSecretResourceV2.list("Test", "TestOrg", "TestProj", null, null, null, null, null, false, true, 1, 10);
    verify(ngSecretService).list("Test", "TestOrg", "TestProj", null, null, false, null, 1, 10, null, true);
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void testListV2ForSecretsAvailableAllScopeIfNotInQueryParamShouldBeFalse() {
    List<Object> mockResponse = Collections.singletonList(getMockResponse());
    Page<Object> page = new PageImpl<>(mockResponse);
    doNothing().when(secretPermissionValidator).checkForAccessOrThrow(any(), any(), any(), any());
    doReturn(page)
        .when(ngSecretService)
        .list("Test", "TestOrg", "TestProj", null, null, false, null, 1, 10, null, false);
    ResponseDTO<PageResponse<SecretResponseWrapper>> list =
        ngSecretResourceV2.list("Test", "TestOrg", "TestProj", null, null, null, null, null, false, false, 1, 10);
    verify(ngSecretService).list("Test", "TestOrg", "TestProj", null, null, false, null, 1, 10, null, false);
  }

  @NotNull
  private List<SecretResponseWrapper> getMockResponse() {
    List<SecretResponseWrapper> mockResponse = new ArrayList<>();
    SecretResponseWrapper secretResponseWrapper =
        SecretResponseWrapper.builder()
            .secret(SecretDTOV2.builder().identifier("Test").name("TestName").build())
            .build();
    mockResponse.add(secretResponseWrapper);
    return mockResponse;
  }

  @Test(expected = JerseyViolationException.class)
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testValidateRequestPayload() {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    String spec =
        "{\"secret\":{\"type\":\"SecretFile\",\"identifier\":\"test_identifier\",\"description\":\"\",\"tags\":{},"
        + "\"spec\":{\"secretManagerIdentifier\":\"harnessSecretManager\"}}}";
    ConstraintViolation<Object> mockviolation = mock(ConstraintViolation.class);
    Set<ConstraintViolation<Object>> violations = new HashSet<>();
    violations.add(mockviolation);
    when(validator.validate(any())).thenReturn(violations);
    ngSecretResourceV2.createSecretFile(accountIdentifier, orgIdentifier, projectIdentifier, false, null, spec);
  }
}
