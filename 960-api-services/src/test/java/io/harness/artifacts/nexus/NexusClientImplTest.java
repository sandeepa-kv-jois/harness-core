/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.artifacts.nexus;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.exception.WingsException.USER;
import static io.harness.rule.OwnerRule.MLUKIC;
import static io.harness.rule.OwnerRule.SHIVAM;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.artifact.ArtifactMetadataKeys;
import io.harness.artifacts.beans.BuildDetailsInternal;
import io.harness.category.element.UnitTests;
import io.harness.concurrent.HTimeLimiter;
import io.harness.exception.ExplanationException;
import io.harness.exception.HintException;
import io.harness.exception.InvalidArtifactServerException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.NexusRegistryException;
import io.harness.nexus.NexusClientImpl;
import io.harness.nexus.NexusRequest;
import io.harness.nexus.NexusThreeClientImpl;
import io.harness.nexus.NexusTwoClientImpl;
import io.harness.rule.Owner;

import software.wings.utils.RepositoryFormat;

import com.google.inject.Inject;
import java.io.IOException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.powermock.core.classloader.annotations.PrepareForTest;

@PrepareForTest({HTimeLimiter.class})
@OwnedBy(HarnessTeam.CDP)
public class NexusClientImplTest extends CategoryTest {
  @Inject @InjectMocks private NexusClientImpl nexusClient;
  @Mock NexusThreeClientImpl nexusThreeService;
  @Mock NexusTwoClientImpl nexusTwoClient;
  private static String url;
  private static Map<String, List<BuildDetailsInternal>> buildDetailsData;
  private NexusRequest nexusConfig;
  private NexusRequest nexusThreeConfig;
  private String DEFAULT_NEXUS_URL;

  @Before
  public void before() {
    MockitoAnnotations.initMocks(this);

    url = "http://localhost:1234";

    buildDetailsData = new HashMap<>();

    List<BuildDetailsInternal> bdiList = new ArrayList<>();
    String repoUrl = "nexus.harness.io:8001";
    String imageName = "superApp";
    bdiList.add(createBuildDetails(repoUrl, null, imageName, "1.0"));
    bdiList.add(createBuildDetails(repoUrl, null, imageName, "2.0"));
    bdiList.add(createBuildDetails(repoUrl, null, imageName, "3.0"));
    buildDetailsData.put("bdi1", bdiList);

    bdiList = new ArrayList<>();
    repoUrl = "nexus.harness.io:8002";
    imageName = "super/duper/app";
    bdiList.add(createBuildDetails(repoUrl, null, imageName, "2.4.1"));
    bdiList.add(createBuildDetails(repoUrl, null, imageName, "2.4.2"));
    bdiList.add(createBuildDetails(repoUrl, null, imageName, "2.5"));
    bdiList.add(createBuildDetails(repoUrl, null, imageName, "2.5.3"));
    buildDetailsData.put("bdi2", bdiList);

    bdiList = new ArrayList<>();
    repoUrl = "nexus.harness.io:8002";
    imageName = "extra/megaapp";
    bdiList.add(createBuildDetails(repoUrl, null, imageName, "a4"));
    bdiList.add(createBuildDetails(repoUrl, null, imageName, "b23"));
    bdiList.add(createBuildDetails(repoUrl, null, imageName, "latest"));
    bdiList.add(createBuildDetails(repoUrl, null, imageName, "basic"));
    buildDetailsData.put("bdi3", bdiList);
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testGetRepositories() {
    Map<String, String> mockResponse = new HashMap<>();
    mockResponse.put("repo1", "repo1");
    mockResponse.put("repo2", "repo2");
    mockResponse.put("repo3", "repo3");

    /** nexus 2.x connector with unknown repo format */
    NexusRequest nexusConfig1 = NexusRequest.builder()
                                    .nexusUrl(url)
                                    .username("username")
                                    .password("password".toCharArray())
                                    .hasCredentials(true)
                                    .artifactRepositoryUrl(url)
                                    .version("2.x")
                                    .build();

    try (MockedStatic<HTimeLimiter> hTimeLimiterMockedStatic = mockStatic(HTimeLimiter.class)) {
      hTimeLimiterMockedStatic.when(() -> HTimeLimiter.callInterruptible21(any(), any(), any()))
          .thenReturn(mockResponse);
      Map<String, String> response = nexusClient.getRepositories(nexusConfig1);
      assertThat(response).isNotNull();
      assertThat(response).size().isEqualTo(3);
    } catch (HintException ex) {
      assertThat(ex.getMessage()).isEqualTo("Nexus 3.x requires that a repository format is correct");
    }

    /** nexus 2.x connector with docker repo format */
    NexusRequest nexusConfig2 = NexusRequest.builder()
                                    .nexusUrl(url)
                                    .username("username")
                                    .password("password".toCharArray())
                                    .hasCredentials(true)
                                    .artifactRepositoryUrl(url)
                                    .version("2.x")
                                    .build();

    try (MockedStatic<HTimeLimiter> hTimeLimiterMockedStatic = mockStatic(HTimeLimiter.class)) {
      hTimeLimiterMockedStatic.when(() -> HTimeLimiter.callInterruptible21(any(), any(), any()))
          .thenThrow(NestedExceptionUtils.hintWithExplanationException("Nexus 2.x does not support docker artifacts",
              "The version for the connector should probably be 3.x and not 2.x",
              new InvalidArtifactServerException("Nexus 2.x does not support docker artifact type", USER)));
      nexusClient.getRepositories(nexusConfig2, RepositoryFormat.docker.name());
    } catch (Exception e) {
      assertThat(e)
          .isInstanceOf(HintException.class)
          .getCause()
          .isInstanceOf(ExplanationException.class)
          .getCause()
          .isInstanceOf(InvalidArtifactServerException.class);
    }

    /** nexus 3.x connector with unknown repo format */
    NexusRequest nexusConfig3 = NexusRequest.builder()
                                    .nexusUrl(url)
                                    .username("username")
                                    .password("password".toCharArray())
                                    .hasCredentials(true)
                                    .artifactRepositoryUrl(url)
                                    .version("3.x")
                                    .build();

    try (MockedStatic<HTimeLimiter> hTimeLimiterMockedStatic = mockStatic(HTimeLimiter.class)) {
      hTimeLimiterMockedStatic.when(() -> HTimeLimiter.callInterruptible21(any(), any(), any()))
          .thenThrow(NestedExceptionUtils.hintWithExplanationException(
              "Nexus 3.x requires that a repository format is correct",
              "Ensure that a right repository format is chosen",
              new InvalidRequestException("Not supported for nexus 3.x", USER)));
      nexusClient.getRepositories(nexusConfig3);
    } catch (Exception e) {
      assertThat(e)
          .isInstanceOf(HintException.class)
          .getCause()
          .isInstanceOf(ExplanationException.class)
          .getCause()
          .isInstanceOf(InvalidRequestException.class);
    }

    /** nexus 3.x connector with docker repo format */
    NexusRequest nexusConfig4 = NexusRequest.builder()
                                    .nexusUrl(url)
                                    .username("username")
                                    .password("password".toCharArray())
                                    .hasCredentials(true)
                                    .artifactRepositoryUrl(url)
                                    .version("3.x")
                                    .build();

    try (MockedStatic<HTimeLimiter> hTimeLimiterMockedStatic = mockStatic(HTimeLimiter.class)) {
      hTimeLimiterMockedStatic.when(() -> HTimeLimiter.callInterruptible21(any(), any(), any()))
          .thenReturn(mockResponse);
      Map<String, String> response = nexusClient.getRepositories(nexusConfig4, RepositoryFormat.docker.name());
      assertThat(response).isNotNull();
      assertThat(response).size().isEqualTo(3);
    } catch (Exception e) {
      fail("This point should not have been reached!", e);
    }
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetRepositoriesException() {
    Map<String, String> mockResponse = new HashMap<>();
    mockResponse.put("repo1", "repo1");
    mockResponse.put("repo2", "repo2");
    mockResponse.put("repo3", "repo3");

    /** nexus 2.x connector with unknown repo format */
    NexusRequest nexusConfig1 = NexusRequest.builder()
                                    .nexusUrl(url)
                                    .username("username")
                                    .password("password".toCharArray())
                                    .hasCredentials(true)
                                    .artifactRepositoryUrl(url)
                                    .version("2.x")
                                    .build();

    try (MockedStatic<HTimeLimiter> hTimeLimiterMockedStatic = mockStatic(HTimeLimiter.class)) {
      hTimeLimiterMockedStatic.when(() -> HTimeLimiter.callInterruptible21(any(), any(), any()))
          .thenReturn(mockResponse);
      when(nexusTwoClient.getRepositories(nexusConfig, "maven")).thenThrow(IOException.class);
      Map<String, String> response = nexusClient.getRepositories(nexusConfig1);
      assertThat(response).isNotNull();
      assertThat(response).size().isEqualTo(3);
    } catch (HintException ex) {
      assertThat(ex.getMessage()).isEqualTo("Nexus 3.x requires that a repository format is correct");
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetRepositoriesExceptionForNexus3() {
    Map<String, String> mockResponse = new HashMap<>();
    mockResponse.put("repo1", "repo1");
    mockResponse.put("repo2", "repo2");
    mockResponse.put("repo3", "repo3");

    /** nexus 3.x connector with unknown repo format */
    NexusRequest nexusConfig1 = NexusRequest.builder()
                                    .nexusUrl(url)
                                    .username("username")
                                    .password("password".toCharArray())
                                    .hasCredentials(true)
                                    .artifactRepositoryUrl(url)
                                    .version("3.x")
                                    .build();

    try (MockedStatic<HTimeLimiter> hTimeLimiterMockedStatic = mockStatic(HTimeLimiter.class)) {
      hTimeLimiterMockedStatic.when(() -> HTimeLimiter.callInterruptible21(any(), any(), any()))
          .thenReturn(mockResponse);
      when(nexusThreeService.getRepositories(nexusConfig1, "maven")).thenThrow(IOException.class);
      Map<String, String> response = nexusClient.getRepositories(nexusConfig1);
      assertThat(response).isNotNull();
      assertThat(response).size().isEqualTo(3);
    } catch (HintException ex) {
      assertThat(ex.getMessage()).isEqualTo("Nexus 3.x requires that a repository format is correct");
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testIsRunning() {
    NexusRequest nexusConfig = NexusRequest.builder()
                                   .nexusUrl(url)
                                   .username("username")
                                   .password("password".toCharArray())
                                   .hasCredentials(true)
                                   .artifactRepositoryUrl(url)
                                   .version("3.x")
                                   .build();

    try {
      doReturn(true).when(nexusThreeService).isServerValid(nexusConfig);
    } catch (IOException e) {
      fail("Not expected the IOException to occur", e);
    }

    boolean response = nexusClient.isRunning(nexusConfig);
    assertThat(response).isEqualTo(true);
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testIsRunningException() {
    NexusRequest nexusConfig = NexusRequest.builder()
                                   .nexusUrl(url)
                                   .username("username")
                                   .password("password".toCharArray())
                                   .hasCredentials(true)
                                   .artifactRepositoryUrl(url)
                                   .version("3.x")
                                   .build();

    try {
      when(nexusThreeService.isServerValid(nexusConfig)).thenThrow(UnknownHostException.class);
    } catch (HintException | IOException ex) {
      assertThat(ex.getMessage()).isEqualTo("Check if the Nexus URL & version are correct");
    }
    try {
      boolean response = nexusClient.isRunning(nexusConfig);
    } catch (HintException ex) {
      assertThat(ex.getMessage()).isEqualTo("Check if the Nexus URL & version are correct");
    }
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testGetArtifactsVersions() {
    NexusRequest nexusConfig1 = NexusRequest.builder()
                                    .nexusUrl(url)
                                    .username("username")
                                    .password("password".toCharArray())
                                    .hasCredentials(true)
                                    .artifactRepositoryUrl(url)
                                    .version("2.x")
                                    .build();

    assertThatThrownBy(
        () -> nexusClient.getArtifactsVersions(nexusConfig1, "test1", null, "superApp", RepositoryFormat.docker.name()))
        .isInstanceOf(HintException.class);

    NexusRequest nexusConfig2 = NexusRequest.builder()
                                    .nexusUrl(url)
                                    .username("username")
                                    .password("password".toCharArray())
                                    .hasCredentials(true)
                                    .artifactRepositoryUrl(url)
                                    .version("3.x")
                                    .build();

    doReturn(buildDetailsData.get("bdi1"))
        .when(nexusThreeService)
        .getArtifactsVersions(nexusConfig2, "test1", null, "superApp", RepositoryFormat.docker.name());

    List<BuildDetailsInternal> response =
        nexusClient.getArtifactsVersions(nexusConfig2, "test1", null, "superApp", RepositoryFormat.docker.name());

    assertThat(response).isNotNull();
    assertThat(response).size().isEqualTo(3);
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetArtifactsVersionsException() {
    NexusRequest nexusConfig1 = NexusRequest.builder()
                                    .nexusUrl(url)
                                    .username("username")
                                    .password("password".toCharArray())
                                    .hasCredentials(true)
                                    .artifactRepositoryUrl(url)
                                    .version("2.x")
                                    .build();

    assertThatThrownBy(
        () -> nexusClient.getArtifactsVersions(nexusConfig1, "test1", null, "superApp", RepositoryFormat.docker.name()))
        .isInstanceOf(HintException.class);

    NexusRequest nexusConfig2 = NexusRequest.builder()
                                    .nexusUrl(url)
                                    .username("username")
                                    .password("password".toCharArray())
                                    .hasCredentials(true)
                                    .artifactRepositoryUrl(url)
                                    .version("3.x")
                                    .build();

    when(
        nexusThreeService.getArtifactsVersions(nexusConfig2, "test1", null, "superApp", RepositoryFormat.docker.name()))
        .thenThrow(NexusRegistryException.class);

    try {
      List<BuildDetailsInternal> response =
          nexusClient.getArtifactsVersions(nexusConfig2, "test1", null, "superApp", RepositoryFormat.docker.name());
    } catch (NexusRegistryException | HintException ex) {
      assertThat(ex).isInstanceOf(NexusRegistryException.class);
    }
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testGetBuildDetails() {
    NexusRequest nexusConfig1 = NexusRequest.builder()
                                    .nexusUrl(url)
                                    .username("username")
                                    .password("password".toCharArray())
                                    .hasCredentials(true)
                                    .artifactRepositoryUrl(url)
                                    .version("2.x")
                                    .build();

    assertThatThrownBy(()
                           -> nexusClient.getBuildDetails(
                               nexusConfig1, "test1", null, "superApp", RepositoryFormat.docker.name(), "tag1"))
        .isInstanceOf(HintException.class);

    NexusRequest nexusConfig2 = NexusRequest.builder()
                                    .nexusUrl(url)
                                    .username("username")
                                    .password("password".toCharArray())
                                    .hasCredentials(true)
                                    .artifactRepositoryUrl(url)
                                    .version("3.x")
                                    .build();

    doReturn(buildDetailsData.get("bdi1"))
        .when(nexusThreeService)
        .getBuildDetails(nexusConfig2, "test1", null, "superApp", RepositoryFormat.docker.name(), "1.0");

    List<BuildDetailsInternal> response =
        nexusClient.getBuildDetails(nexusConfig2, "test1", null, "superApp", RepositoryFormat.docker.name(), "1.0");
    assertThat(response).isNotNull();
    assertThat(response).size().isEqualTo(3);
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetBuildDetailsForMaven() {
    NexusRequest nexusConfig1 = NexusRequest.builder()
                                    .nexusUrl(url)
                                    .username("username")
                                    .password("password".toCharArray())
                                    .hasCredentials(true)
                                    .artifactRepositoryUrl(url)
                                    .version("2.x")
                                    .build();

    doReturn(buildDetailsData.get("bdi1"))
        .when(nexusTwoClient)
        .getVersions(nexusConfig1, "test1", "groupId", "superApp", "war", "1.0");

    List<BuildDetailsInternal> response =
        nexusClient.getArtifactsVersions(nexusConfig1, "test1", "groupId", "superApp", "war", "1.0");

    assertThat(response).isNotNull();
    assertThat(response).size().isEqualTo(3);

    NexusRequest nexusConfig2 = NexusRequest.builder()
                                    .nexusUrl(url)
                                    .username("username")
                                    .password("password".toCharArray())
                                    .hasCredentials(true)
                                    .artifactRepositoryUrl(url)
                                    .version("3.x")
                                    .build();

    doReturn(buildDetailsData.get("bdi1"))
        .when(nexusThreeService)
        .getVersions(nexusConfig2, "test1", "groupId", "superApp", "war", "1.0");

    response = nexusClient.getArtifactsVersions(nexusConfig2, "test1", "groupId", "superApp", "war", "1.0");
    assertThat(response).isNotNull();
    assertThat(response).size().isEqualTo(3);
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetBuildDetailsForNPM() throws IOException {
    NexusRequest nexusConfig1 = NexusRequest.builder()
                                    .nexusUrl(url)
                                    .username("username")
                                    .password("password".toCharArray())
                                    .hasCredentials(true)
                                    .artifactRepositoryUrl(url)
                                    .version("2.x")
                                    .build();

    doReturn(buildDetailsData.get("bdi1"))
        .when(nexusTwoClient)
        .getVersions("npm", nexusConfig1, "npm", "", Collections.emptySet());

    List<BuildDetailsInternal> response = nexusClient.getArtifactsVersions(nexusConfig1, "npm", "npm", "");

    assertThat(response).isNotNull();
    assertThat(response).size().isEqualTo(3);

    NexusRequest nexusConfig2 = NexusRequest.builder()
                                    .nexusUrl(url)
                                    .username("username")
                                    .password("password".toCharArray())
                                    .hasCredentials(true)
                                    .artifactRepositoryUrl(url)
                                    .version("3.x")
                                    .build();

    doReturn(buildDetailsData.get("bdi1")).when(nexusThreeService).getPackageVersions(nexusConfig2, "npm", "");

    response = nexusClient.getArtifactsVersions(nexusConfig2, "npm", "npm", "");
    assertThat(response).isNotNull();
    assertThat(response).size().isEqualTo(3);
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetBuildDetailsForRaw() throws IOException {
    NexusRequest nexusConfig1 = NexusRequest.builder()
                                    .nexusUrl(url)
                                    .username("username")
                                    .password("password".toCharArray())
                                    .hasCredentials(true)
                                    .artifactRepositoryUrl(url)
                                    .version("3.x")
                                    .build();

    doReturn(buildDetailsData.get("bdi1"))
        .when(nexusThreeService)
        .getPackageNamesBuildDetails(nexusConfig1, "RAW", "test");

    List<BuildDetailsInternal> response = nexusClient.getPackageNames(nexusConfig1, "RAW", "test");

    assertThat(response).isNotNull();
    assertThat(response).size().isEqualTo(3);

    NexusRequest nexusConfig2 = NexusRequest.builder()
                                    .nexusUrl(url)
                                    .username("username")
                                    .password("password".toCharArray())
                                    .hasCredentials(true)
                                    .artifactRepositoryUrl(url)
                                    .version("2.x")
                                    .build();

    doReturn(buildDetailsData.get("bdi1")).when(nexusThreeService).getPackageVersions(nexusConfig2, "npm", "");

    response = nexusClient.getArtifactsVersions(nexusConfig2, "npm", "npm", "");
    assertThat(response).isNotNull();
    assertThat(response).size().isEqualTo(0);
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetPackageName() throws IOException {
    NexusRequest nexusConfig1 = NexusRequest.builder()
                                    .nexusUrl(url)
                                    .username("username")
                                    .password("password".toCharArray())
                                    .hasCredentials(true)
                                    .artifactRepositoryUrl(url)
                                    .version("2.x")
                                    .build();

    assertThatThrownBy(() -> nexusClient.getPackageNames(nexusConfig1, "RAW", "test"))
        .isInstanceOf(HintException.class);
  }

  private BuildDetailsInternal createBuildDetails(String repoUrl, String port, String imageName, String tag) {
    return BuildDetailsInternal.builder()
        .number(tag)
        .metadata(createBuildMetadata(repoUrl, port, imageName, tag))
        .build();
  }

  private String generateArtifactPullUrl(String hostname, String port, String imagePath, String imageTag) {
    return hostname + (isEmpty(port) ? "" : ":" + port) + "/" + imagePath + ":" + imageTag;
  }

  private Map<String, String> createBuildMetadata(String hostname, String port, String imagePath, String imageTag) {
    Map<String, String> metadata = new HashMap<>();
    metadata.put(ArtifactMetadataKeys.IMAGE, generateArtifactPullUrl(hostname, port, imagePath, imageTag));
    metadata.put(ArtifactMetadataKeys.TAG, imageTag);
    return metadata;
  }
}
