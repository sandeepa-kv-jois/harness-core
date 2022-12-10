/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.gcr;

import static io.harness.rule.OwnerRule.vivekveman;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

import io.harness.CategoryTest;
import io.harness.artifacts.beans.BuildDetailsInternal;
import io.harness.artifacts.gcr.beans.GcrInternalConfig;
import io.harness.artifacts.gcr.service.GcrApiService;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorCredentialDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpCredentialType;
import io.harness.delegate.beans.connector.gcpconnector.GcpManualDetailsDTO;
import io.harness.delegate.task.artifacts.response.ArtifactTaskExecutionResponse;
import io.harness.delegate.task.gcp.helpers.GcpHelperService;
import io.harness.encryption.SecretRefData;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.runtime.GcpClientRuntimeException;
import io.harness.rule.Owner;
import io.harness.security.encryption.SecretDecryptionService;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class GcrArtifactTaskHandlerTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @InjectMocks GcrArtifactTaskHandler gcrArtifactTaskHandler;
  @Mock GcpHelperService gcpHelperService;
  @Mock GcrApiService gcrApiService;
  @Mock SecretDecryptionService secretDecryptionService;

  private static final String TEST_PROJECT_ID = "project-a";
  private static final String TEST_ACCESS_TOKEN = String.format("{\"access_token\": \"%s\"}", TEST_PROJECT_ID);
  private final char[] serviceAccountKeyFileContent =
      String.format("{\"project_id\": \"%s\"}", TEST_PROJECT_ID).toCharArray();

  private GoogleCredential googleCredential;

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testGetLastSuccessfulBuildFromRegex() throws IOException {
    googleCredential = new GoogleCredential();
    BuildDetailsInternal buildDetailsInternal = BuildDetailsInternal.builder().number("version").build();
    GcrInternalConfig garInternalConfig;
    garInternalConfig =
        GcrInternalConfig.builder().basicAuthHeader("Bearer Auth").registryHostname("registryHostname").build();
    doReturn(buildDetailsInternal)
        .when(gcrApiService)
        .getLastSuccessfulBuildFromRegex(garInternalConfig, "imagePath", "v.*");
    doReturn(googleCredential).when(gcpHelperService).getGoogleCredential(serviceAccountKeyFileContent, false);
    doReturn("Bearer Auth").when(gcpHelperService).getBasicAuthHeader(serviceAccountKeyFileContent, false);
    GcrArtifactDelegateRequest gcrDelegateRequest =
        GcrArtifactDelegateRequest.builder()
            .gcpConnectorDTO(
                GcpConnectorDTO.builder()
                    .credential(
                        GcpConnectorCredentialDTO.builder()
                            .gcpCredentialType(GcpCredentialType.MANUAL_CREDENTIALS)
                            .config(
                                GcpManualDetailsDTO.builder()
                                    .secretKeyRef(
                                        SecretRefData.builder().decryptedValue(serviceAccountKeyFileContent).build())
                                    .build())
                            .build())
                    .build())
            .tagRegex("v.*")
            .registryHostname("registryHostname")
            .imagePath("imagePath")
            .build();
    ArtifactTaskExecutionResponse lastSuccessfulBuild =
        gcrArtifactTaskHandler.getLastSuccessfulBuild(gcrDelegateRequest);
    assertThat(lastSuccessfulBuild).isNotNull();
    assertThat(lastSuccessfulBuild.getArtifactDelegateResponses().size()).isEqualTo(1);
    assertThat(lastSuccessfulBuild.getArtifactDelegateResponses().get(0))
        .isInstanceOf(GcrArtifactDelegateResponse.class);
    GcrArtifactDelegateResponse attributes =
        (GcrArtifactDelegateResponse) lastSuccessfulBuild.getArtifactDelegateResponses().get(0);
    assertThat(attributes.getTag()).isEqualTo("version");
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testGetLastSuccessfulBuildVerify() throws IOException {
    googleCredential = new GoogleCredential();
    BuildDetailsInternal buildDetailsInternal = BuildDetailsInternal.builder().number("version").build();
    GcrInternalConfig garInternalConfig;
    garInternalConfig =
        GcrInternalConfig.builder().basicAuthHeader("Bearer Auth").registryHostname("registryHostname").build();
    doReturn(buildDetailsInternal).when(gcrApiService).verifyBuildNumber(garInternalConfig, "imagePath", "version");
    doReturn(googleCredential).when(gcpHelperService).getGoogleCredential(serviceAccountKeyFileContent, false);
    doReturn("Bearer Auth").when(gcpHelperService).getBasicAuthHeader(serviceAccountKeyFileContent, false);
    GcrArtifactDelegateRequest gcrDelegateRequest =
        GcrArtifactDelegateRequest.builder()
            .gcpConnectorDTO(
                GcpConnectorDTO.builder()
                    .credential(
                        GcpConnectorCredentialDTO.builder()
                            .gcpCredentialType(GcpCredentialType.MANUAL_CREDENTIALS)
                            .config(
                                GcpManualDetailsDTO.builder()
                                    .secretKeyRef(
                                        SecretRefData.builder().decryptedValue(serviceAccountKeyFileContent).build())
                                    .build())
                            .build())
                    .build())
            .tag("version")
            .registryHostname("registryHostname")
            .imagePath("imagePath")
            .build();
    ArtifactTaskExecutionResponse lastSuccessfulBuild =
        gcrArtifactTaskHandler.getLastSuccessfulBuild(gcrDelegateRequest);
    assertThat(lastSuccessfulBuild).isNotNull();
    assertThat(lastSuccessfulBuild.getArtifactDelegateResponses().size()).isEqualTo(1);
    assertThat(lastSuccessfulBuild.getArtifactDelegateResponses().get(0))
        .isInstanceOf(GcrArtifactDelegateResponse.class);
    GcrArtifactDelegateResponse attributes =
        (GcrArtifactDelegateResponse) lastSuccessfulBuild.getArtifactDelegateResponses().get(0);
    assertThat(attributes.getTag()).isEqualTo("version");
  }
  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testgetBuilds() throws IOException {
    googleCredential = new GoogleCredential();

    BuildDetailsInternal buildDetailsInternal = BuildDetailsInternal.builder().number("version").build();

    List<BuildDetailsInternal> ls = new ArrayList<>();
    ls.add(buildDetailsInternal);
    GcrInternalConfig garInternalConfig;

    garInternalConfig =
        GcrInternalConfig.builder().basicAuthHeader("Bearer Auth").registryHostname("registryHostname").build();
    doReturn(ls).when(gcrApiService).getBuilds(garInternalConfig, "imagePath", 10000);

    doReturn(googleCredential).when(gcpHelperService).getGoogleCredential(serviceAccountKeyFileContent, false);

    doReturn("Bearer Auth").when(gcpHelperService).getBasicAuthHeader(serviceAccountKeyFileContent, false);

    GcrArtifactDelegateRequest gcrDelegateRequest =
        GcrArtifactDelegateRequest.builder()
            .gcpConnectorDTO(
                GcpConnectorDTO.builder()
                    .credential(
                        GcpConnectorCredentialDTO.builder()
                            .gcpCredentialType(GcpCredentialType.MANUAL_CREDENTIALS)
                            .config(
                                GcpManualDetailsDTO.builder()
                                    .secretKeyRef(
                                        SecretRefData.builder().decryptedValue(serviceAccountKeyFileContent).build())
                                    .build())
                            .build())
                    .build())
            .registryHostname("registryHostname")
            .imagePath("imagePath")
            .build();

    ArtifactTaskExecutionResponse builds = gcrArtifactTaskHandler.getBuilds(gcrDelegateRequest);

    assertThat(builds).isNotNull();
    assertThat(builds.getArtifactDelegateResponses().size()).isEqualTo(1);
    assertThat(builds.getArtifactDelegateResponses().get(0)).isInstanceOf(GcrArtifactDelegateResponse.class);
    GcrArtifactDelegateResponse attributes = (GcrArtifactDelegateResponse) builds.getArtifactDelegateResponses().get(0);
    assertThat(attributes.getTag()).isEqualTo("version");
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testValidateArtifactServer() throws IOException {
    googleCredential = new GoogleCredential();

    GcrInternalConfig garInternalConfig;

    garInternalConfig =
        GcrInternalConfig.builder().basicAuthHeader("Bearer Auth").registryHostname("registryHostname").build();
    doReturn(true).when(gcrApiService).validateCredentials(garInternalConfig, "imagePath");

    doReturn(googleCredential).when(gcpHelperService).getGoogleCredential(serviceAccountKeyFileContent, false);

    doReturn("Bearer Auth").when(gcpHelperService).getBasicAuthHeader(serviceAccountKeyFileContent, false);

    GcrArtifactDelegateRequest gcrDelegateRequest =
        GcrArtifactDelegateRequest.builder()
            .gcpConnectorDTO(
                GcpConnectorDTO.builder()
                    .credential(
                        GcpConnectorCredentialDTO.builder()
                            .gcpCredentialType(GcpCredentialType.MANUAL_CREDENTIALS)
                            .config(
                                GcpManualDetailsDTO.builder()
                                    .secretKeyRef(
                                        SecretRefData.builder().decryptedValue(serviceAccountKeyFileContent).build())
                                    .build())
                            .build())
                    .build())
            .registryHostname("registryHostname")
            .imagePath("imagePath")
            .build();

    ArtifactTaskExecutionResponse response = gcrArtifactTaskHandler.validateArtifactServer(gcrDelegateRequest);

    assertThat(response).isNotNull();
    assertThat(response.isArtifactServerValid()).isEqualTo(true);
  }
  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testValidateArtifactImage() throws IOException {
    googleCredential = new GoogleCredential();

    GcrInternalConfig garInternalConfig;

    garInternalConfig =
        GcrInternalConfig.builder().basicAuthHeader("Bearer Auth").registryHostname("registryHostname").build();
    doReturn(true).when(gcrApiService).verifyImageName(garInternalConfig, "imagePath");

    doReturn(googleCredential).when(gcpHelperService).getGoogleCredential(serviceAccountKeyFileContent, false);

    doReturn("Bearer Auth").when(gcpHelperService).getBasicAuthHeader(serviceAccountKeyFileContent, false);

    GcrArtifactDelegateRequest gcrDelegateRequest =
        GcrArtifactDelegateRequest.builder()
            .gcpConnectorDTO(
                GcpConnectorDTO.builder()
                    .credential(
                        GcpConnectorCredentialDTO.builder()
                            .gcpCredentialType(GcpCredentialType.MANUAL_CREDENTIALS)
                            .config(
                                GcpManualDetailsDTO.builder()
                                    .secretKeyRef(
                                        SecretRefData.builder().decryptedValue(serviceAccountKeyFileContent).build())
                                    .build())
                            .build())
                    .build())
            .registryHostname("registryHostname")
            .imagePath("imagePath")
            .build();

    ArtifactTaskExecutionResponse response = gcrArtifactTaskHandler.validateArtifactImage(gcrDelegateRequest);

    assertThat(response).isNotNull();
    assertThat(response.isArtifactSourceValid()).isEqualTo(true);
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testGetLastSuccessfulBuildFromRegexException() throws IOException {
    doThrow(new IOException("io exception"))
        .when(gcpHelperService)
        .getBasicAuthHeader(serviceAccountKeyFileContent, false);
    GcrArtifactDelegateRequest gcrDelegateRequest =
        GcrArtifactDelegateRequest.builder()
            .gcpConnectorDTO(
                GcpConnectorDTO.builder()
                    .credential(
                        GcpConnectorCredentialDTO.builder()
                            .gcpCredentialType(GcpCredentialType.MANUAL_CREDENTIALS)
                            .config(
                                GcpManualDetailsDTO.builder()
                                    .secretKeyRef(
                                        SecretRefData.builder().decryptedValue(serviceAccountKeyFileContent).build())
                                    .build())
                            .build())
                    .build())
            .tagRegex("v.*")
            .registryHostname("registryHostname")
            .imagePath("imagePath")
            .build();

    assertThatThrownBy(() -> gcrArtifactTaskHandler.getLastSuccessfulBuild(gcrDelegateRequest))
        .isInstanceOf(GcpClientRuntimeException.class)
        .hasMessage("io exception");
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testgetBuildsException() throws IOException {
    doThrow(new IOException("io exception"))
        .when(gcpHelperService)
        .getBasicAuthHeader(serviceAccountKeyFileContent, false);

    GcrArtifactDelegateRequest gcrDelegateRequest =
        GcrArtifactDelegateRequest.builder()
            .gcpConnectorDTO(
                GcpConnectorDTO.builder()
                    .credential(
                        GcpConnectorCredentialDTO.builder()
                            .gcpCredentialType(GcpCredentialType.MANUAL_CREDENTIALS)
                            .config(
                                GcpManualDetailsDTO.builder()
                                    .secretKeyRef(
                                        SecretRefData.builder().decryptedValue(serviceAccountKeyFileContent).build())
                                    .build())
                            .build())
                    .build())
            .registryHostname("registryHostname")
            .imagePath("imagePath")
            .build();

    assertThatThrownBy(() -> gcrArtifactTaskHandler.getBuilds(gcrDelegateRequest))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Could not get basic auth header - io exception");
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testValidateArtifactServerException() throws IOException {
    doThrow(new IOException("io exception"))
        .when(gcpHelperService)
        .getBasicAuthHeader(serviceAccountKeyFileContent, false);

    GcrArtifactDelegateRequest gcrDelegateRequest =
        GcrArtifactDelegateRequest.builder()
            .gcpConnectorDTO(
                GcpConnectorDTO.builder()
                    .credential(
                        GcpConnectorCredentialDTO.builder()
                            .gcpCredentialType(GcpCredentialType.MANUAL_CREDENTIALS)
                            .config(
                                GcpManualDetailsDTO.builder()
                                    .secretKeyRef(
                                        SecretRefData.builder().decryptedValue(serviceAccountKeyFileContent).build())
                                    .build())
                            .build())
                    .build())
            .registryHostname("registryHostname")
            .imagePath("imagePath")
            .build();

    assertThatThrownBy(() -> gcrArtifactTaskHandler.validateArtifactServer(gcrDelegateRequest))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Could not get basic auth header - io exception");
  }
  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testValidateArtifactImageException() throws IOException {
    doThrow(new IOException("io exception"))
        .when(gcpHelperService)
        .getBasicAuthHeader(serviceAccountKeyFileContent, false);

    GcrArtifactDelegateRequest gcrDelegateRequest =
        GcrArtifactDelegateRequest.builder()
            .gcpConnectorDTO(
                GcpConnectorDTO.builder()
                    .credential(
                        GcpConnectorCredentialDTO.builder()
                            .gcpCredentialType(GcpCredentialType.MANUAL_CREDENTIALS)
                            .config(
                                GcpManualDetailsDTO.builder()
                                    .secretKeyRef(
                                        SecretRefData.builder().decryptedValue(serviceAccountKeyFileContent).build())
                                    .build())
                            .build())
                    .build())
            .registryHostname("registryHostname")
            .imagePath("imagePath")
            .build();

    assertThatThrownBy(() -> gcrArtifactTaskHandler.validateArtifactImage(gcrDelegateRequest))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Could not get basic auth header - io exception");
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testdecryptRequestDTOs() throws IOException {
    doReturn(null).when(secretDecryptionService).decrypt(any(), any());
    GcrArtifactDelegateRequest gcrDelegateRequest =
        GcrArtifactDelegateRequest.builder()
            .gcpConnectorDTO(
                GcpConnectorDTO.builder()
                    .credential(
                        GcpConnectorCredentialDTO.builder()
                            .gcpCredentialType(GcpCredentialType.MANUAL_CREDENTIALS)
                            .config(
                                GcpManualDetailsDTO.builder()
                                    .secretKeyRef(
                                        SecretRefData.builder().decryptedValue(serviceAccountKeyFileContent).build())
                                    .build())
                            .build())
                    .build())
            .tag("version")
            .registryHostname("registryHostname")
            .imagePath("imagePath")
            .build();

    gcrArtifactTaskHandler.decryptRequestDTOs(gcrDelegateRequest);
  }
}
