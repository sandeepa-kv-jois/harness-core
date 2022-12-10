/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.impl;

import static io.harness.connector.ConnectivityStatus.FAILURE;
import static io.harness.connector.ConnectivityStatus.SUCCESS;
import static io.harness.encryption.Scope.ACCOUNT;
import static io.harness.rule.OwnerRule.ABHINAV;
import static io.harness.rule.OwnerRule.DEV_MITTAL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectivityStatus;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.ConnectorValidationResult;
import io.harness.connector.heartbeat.ConnectorValidationParamsProvider;
import io.harness.connector.heartbeat.ScmConnectorValidationParamsProvider;
import io.harness.connector.helper.EncryptionHelper;
import io.harness.connector.services.ConnectorService;
import io.harness.connector.task.ConnectorValidationHandler;
import io.harness.connector.task.git.GitCommandTaskHandler;
import io.harness.connector.task.git.GitDecryptionHelper;
import io.harness.connector.task.git.GitValidationHandler;
import io.harness.connector.validator.scmValidators.GitConfigAuthenticationInfoHelper;
import io.harness.connector.validator.scmValidators.GitConnectorValidator;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitHTTPAuthenticationDTO;
import io.harness.delegate.beans.git.GitCommandExecutionResponse;
import io.harness.encryption.SecretRefHelper;
import io.harness.rule.Owner;
import io.harness.service.DelegateGrpcClientWrapper;

import java.util.Map;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.DX)
public class GitConnectorValidatorTest extends CategoryTest {
  public static final String ACCOUNT_ID = "ACCOUNT_ID";
  @Mock DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  @Mock EncryptionHelper encryptionHelper;
  @Mock GitDecryptionHelper gitDecryptionHelper;
  @Mock private ConnectorService connectorService;
  @Mock private GitCommandTaskHandler gitCommandTaskHandler;
  @Mock GitConfigAuthenticationInfoHelper gitConfigAuthenticationInfoHelper;
  @Mock private Map<String, ConnectorValidationHandler> connectorTypeToConnectorValidationHandlerMap;
  @Mock private Map<String, ConnectorValidationParamsProvider> connectorValidationParamsProviderMap;
  @InjectMocks GitConnectorValidator gitConnectorValidator;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    when(connectorService.get(any(), any(), any(), any()))
        .thenReturn(Optional.of(ConnectorResponseDTO.builder()
                                    .connector(ConnectorInfoDTO.builder()
                                                   .connectorType(ConnectorType.GIT)
                                                   .identifier("identifier")
                                                   .projectIdentifier("projectIdentifier")
                                                   .orgIdentifier("orgIdentifier")
                                                   .build())
                                    .build()));
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void testConnectorValidationForFailedResponse() {
    GitConfigDTO gitConfig = GitConfigDTO.builder()
                                 .gitAuth(GitHTTPAuthenticationDTO.builder()
                                              .passwordRef(SecretRefHelper.createSecretRef(ACCOUNT + "abcd"))
                                              .username("username")
                                              .build())
                                 .gitConnectionType(GitConnectionType.REPO)
                                 .branchName("branchName")
                                 .url("url")
                                 .gitAuthType(GitAuthType.HTTP)
                                 .build();
    GitCommandExecutionResponse gitResponse =
        GitCommandExecutionResponse.builder()
            .connectorValidationResult(ConnectorValidationResult.builder().status(FAILURE).build())
            .build();
    doReturn(gitResponse).when(delegateGrpcClientWrapper).executeSyncTask(any());
    when(encryptionHelper.getEncryptionDetail(any(), any(), any(), any())).thenReturn(null);
    ConnectorValidationResult connectorValidationResult =
        gitConnectorValidator.validate(gitConfig, ACCOUNT_ID, null, null, null);
    verify(delegateGrpcClientWrapper, times(1)).executeSyncTask(any());
    assertThat(connectorValidationResult.getStatus()).isEqualTo(FAILURE);
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void testConnectorValidationForSuccessfulResponse() {
    GitConfigDTO gitConfig =
        GitConfigDTO.builder()
            .gitAuth(GitHTTPAuthenticationDTO.builder()
                         .passwordRef(SecretRefHelper.createSecretRef(ACCOUNT.getYamlRepresentation() + ".abcd"))
                         .username("username")
                         .build())
            .gitAuthType(GitAuthType.HTTP)
            .gitConnectionType(GitConnectionType.REPO)
            .branchName("branchName")
            .url("url")
            .build();
    GitCommandExecutionResponse gitResponse =
        GitCommandExecutionResponse.builder()
            .connectorValidationResult(ConnectorValidationResult.builder().status(SUCCESS).build())
            .build();
    when(encryptionHelper.getEncryptionDetail(any(), any(), any(), any())).thenReturn(null);

    doReturn(gitResponse).when(delegateGrpcClientWrapper).executeSyncTask(any());
    ConnectorValidationResult connectorValidationResult =
        gitConnectorValidator.validate(gitConfig, ACCOUNT_ID, null, null, null);
    verify(delegateGrpcClientWrapper, times(1)).executeSyncTask(any());
    assertThat(connectorValidationResult.getStatus()).isEqualTo(SUCCESS);
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void testConnectorViaDelegate() {
    GitConfigDTO gitConfig =
        GitConfigDTO.builder()
            .gitAuth(GitHTTPAuthenticationDTO.builder()
                         .passwordRef(SecretRefHelper.createSecretRef(ACCOUNT.getYamlRepresentation() + ".abcd"))
                         .username("username")
                         .build())
            .gitAuthType(GitAuthType.HTTP)
            .gitConnectionType(GitConnectionType.REPO)
            .branchName("branchName")
            .url("url")
            .executeOnDelegate(true)
            .build();
    GitCommandExecutionResponse gitResponse =
        GitCommandExecutionResponse.builder()
            .connectorValidationResult(ConnectorValidationResult.builder().status(SUCCESS).build())
            .build();
    when(encryptionHelper.getEncryptionDetail(any(), any(), any(), any())).thenReturn(null);

    doReturn(gitResponse).when(delegateGrpcClientWrapper).executeSyncTask(any());
    ConnectorValidationResult connectorValidationResult =
        gitConnectorValidator.validate(gitConfig, ACCOUNT_ID, null, null, null);
    verify(delegateGrpcClientWrapper, times(1)).executeSyncTask(any());
    assertThat(connectorValidationResult.getStatus()).isEqualTo(SUCCESS);
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testConnectorViaManager() {
    GitConfigDTO gitConfig =
        GitConfigDTO.builder()
            .gitAuth(GitHTTPAuthenticationDTO.builder()
                         .passwordRef(SecretRefHelper.createSecretRef(ACCOUNT.getYamlRepresentation() + ".abcd"))
                         .username("username")
                         .build())
            .gitAuthType(GitAuthType.HTTP)
            .gitConnectionType(GitConnectionType.REPO)
            .branchName("branchName")
            .url("url")
            .executeOnDelegate(false)
            .build();

    GitValidationHandler gitValidationHandler = mock(GitValidationHandler.class);
    on(gitValidationHandler).set("gitCommandTaskHandler", gitCommandTaskHandler);
    on(gitValidationHandler).set("gitDecryptionHelper", gitDecryptionHelper);
    when(gitValidationHandler.validate(any(), any())).thenCallRealMethod();
    when(connectorTypeToConnectorValidationHandlerMap.get(Matchers.eq("Git"))).thenReturn(gitValidationHandler);

    ConnectorValidationResult connectorValidationResult =
        ConnectorValidationResult.builder().status(ConnectivityStatus.SUCCESS).build();

    when(gitCommandTaskHandler.validateGitCredentials(any(), any(), any(), any()))
        .thenReturn(connectorValidationResult);
    ScmConnectorValidationParamsProvider scmConnectorValidationParamsProvider =
        new ScmConnectorValidationParamsProvider();

    on(scmConnectorValidationParamsProvider)
        .set("gitConfigAuthenticationInfoHelper", gitConfigAuthenticationInfoHelper);
    when(connectorValidationParamsProviderMap.get(Matchers.eq("Git"))).thenReturn(scmConnectorValidationParamsProvider);

    ConnectorValidationResult validationResult = gitConnectorValidator.validate(
        gitConfig, "accountIdentifier", "orgIdentifier", "projectIdentifier", "identifier");
    assertThat(validationResult.getStatus()).isEqualTo(ConnectivityStatus.SUCCESS);
  }
}
