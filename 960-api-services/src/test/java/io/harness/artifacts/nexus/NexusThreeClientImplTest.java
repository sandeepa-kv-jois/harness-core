package io.harness.artifacts.nexus;

import static io.harness.rule.OwnerRule.MLUKIC;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.fail;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.artifact.ArtifactMetadataKeys;
import io.harness.artifacts.beans.BuildDetailsInternal;
import io.harness.category.element.UnitTests;
import io.harness.exception.ExplanationException;
import io.harness.exception.HintException;
import io.harness.exception.InvalidArtifactServerException;
import io.harness.exception.NexusRegistryException;
import io.harness.nexus.NexusRequest;
import io.harness.nexus.NexusThreeClientImpl;
import io.harness.rule.Owner;

import software.wings.utils.RepositoryFormat;

import com.github.tomakehurst.wiremock.core.Options;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(HarnessTeam.CDP)
public class NexusThreeClientImplTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @Rule
  public WireMockRule wireMockRule =
      new WireMockRule(WireMockConfiguration.wireMockConfig()
                           .usingFilesUnderDirectory("960-api-services/src/test/resources")
                           .port(Options.DYNAMIC_PORT),
          false);
  @InjectMocks NexusThreeClientImpl nexusThreeService;

  private static String url;

  @Before
  public void before() {
    MockitoAnnotations.initMocks(this);
    url = "http://localhost:" + wireMockRule.port();
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testGetRepositories() {
    NexusRequest nexusConfig = NexusRequest.builder()
                                   .nexusUrl(url)
                                   .username("username")
                                   .password("password".toCharArray())
                                   .hasCredentials(true)
                                   .artifactRepositoryUrl(url)
                                   .version("3.x")
                                   .build();

    wireMockRule.stubFor(get(urlEqualTo("/service/rest/v1/repositories"))
                             .willReturn(aResponse().withStatus(200).withBody("["
                                 + "{\n"
                                 + "        \"name\": \"docker-test1\",\n"
                                 + "        \"format\": \"docker\",\n"
                                 + "        \"type\": \"hosted\",\n"
                                 + "        \"url\": \"https://nexus.dev.harness.io/repository/docker-test1\",\n"
                                 + "        \"attributes\": {}\n"
                                 + "    },"
                                 + "{\n"
                                 + "        \"name\": \"docker-test2\",\n"
                                 + "        \"format\": \"docker\",\n"
                                 + "        \"type\": \"hosted\",\n"
                                 + "        \"url\": \"https://nexus.dev.harness.io/repository/docker-test2\",\n"
                                 + "        \"attributes\": {}\n"
                                 + "    }"
                                 + "]")));
    try {
      Map<String, String> response = nexusThreeService.getRepositories(nexusConfig, RepositoryFormat.docker.name());

      assertThat(response).isNotNull();
      assertThat(response).size().isEqualTo(2);
    } catch (Exception e) {
      fail("This point should not have been reached!", e);
    }
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testIsServerValid() {
    NexusRequest nexusConfig = NexusRequest.builder()
                                   .nexusUrl(url)
                                   .username("username")
                                   .password("password".toCharArray())
                                   .hasCredentials(true)
                                   .artifactRepositoryUrl(url)
                                   .version("3.x")
                                   .build();

    wireMockRule.stubFor(get(urlEqualTo("/service/rest/v1/repositories"))
                             .willReturn(aResponse().withStatus(200).withBody("["
                                 + "{\n"
                                 + "        \"name\": \"docker-test1\",\n"
                                 + "        \"format\": \"docker\",\n"
                                 + "        \"type\": \"hosted\",\n"
                                 + "        \"url\": \"https://nexus.dev.harness.io/repository/docker-test1\",\n"
                                 + "        \"attributes\": {}\n"
                                 + "    },"
                                 + "{\n"
                                 + "        \"name\": \"docker-test2\",\n"
                                 + "        \"format\": \"docker\",\n"
                                 + "        \"type\": \"hosted\",\n"
                                 + "        \"url\": \"https://nexus.dev.harness.io/repository/docker-test2\",\n"
                                 + "        \"attributes\": {}\n"
                                 + "    }"
                                 + "]")));
    try {
      boolean response = nexusThreeService.isServerValid(nexusConfig);

      assertThat(response).isNotNull();
      assertThat(response).isEqualTo(true);
    } catch (Exception e) {
      fail("This point should not have been reached!", e);
    }

    wireMockRule.stubFor(get(urlEqualTo("/service/rest/v1/repositories")).willReturn(aResponse().withStatus(404)));
    try {
      assertThatThrownBy(() -> nexusThreeService.isServerValid(nexusConfig))
          .isInstanceOf(HintException.class)
          .getCause()
          .isInstanceOf(ExplanationException.class)
          .getCause()
          .isInstanceOf(InvalidArtifactServerException.class);
    } catch (Exception e) {
      fail("This point should not have been reached!", e);
    }
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testGetArtifactsVersions() {
    NexusRequest nexusConfig = NexusRequest.builder()
                                   .nexusUrl(url)
                                   .username("username")
                                   .password("password".toCharArray())
                                   .hasCredentials(true)
                                   .artifactRepositoryUrl(url)
                                   .version("3.x")
                                   .build();

    wireMockRule.stubFor(
        get(urlEqualTo("/service/rest/v1/search?repository=todolist&name=todolist&format=docker"))
            .willReturn(aResponse().withStatus(200).withBody("{\n"
                + "    \"items\": [\n"
                + "{\n"
                + "            \"id\": \"dG9kb2xpc3Q6NzFhZmVhNTQwZTIzZGRlNTdiODg3MThiYzBmNWY3M2Q\",\n"
                + "            \"repository\": \"todolist\",\n"
                + "            \"format\": \"docker\",\n"
                + "            \"group\": null,\n"
                + "            \"name\": \"todolist\",\n"
                + "            \"version\": \"latest2\",\n"
                + "            \"assets\": [\n"
                + "                {\n"
                + "                    \"downloadUrl\": \"https://nexus3.dev.harness.io/repository/todolist/v2/todolist/manifests/latest2\",\n"
                + "                    \"path\": \"v2/todolist/manifests/latest2\",\n"
                + "                    \"id\": \"dG9kb2xpc3Q6OTEyZDBmZTdiODE5MjM5MjcxODliMGYyNmQxMDE3NTQ\",\n"
                + "                    \"repository\": \"todolist\",\n"
                + "                    \"format\": \"docker\",\n"
                + "                    \"checksum\": {\n"
                + "                        \"sha1\": \"0d0793de2da200fd2a821357adffe89438bbc9be\",\n"
                + "                        \"sha256\": \"90659bf80b44ce6be8234e6ff90a1ac34acbeb826903b02cfa0da11c82cbc042\"\n"
                + "                    }\n"
                + "                }\n"
                + "            ]\n"
                + "        },\n"
                + "        {\n"
                + "            \"id\": \"dG9kb2xpc3Q6OTNiOWI5ZWI5YTdlY2IwNjMyMDJhMTYwMzhmMTZkODk\",\n"
                + "            \"repository\": \"todolist\",\n"
                + "            \"format\": \"docker\",\n"
                + "            \"group\": null,\n"
                + "            \"name\": \"todolist\",\n"
                + "            \"version\": \"a1new\",\n"
                + "            \"assets\": [\n"
                + "                {\n"
                + "                    \"downloadUrl\": \"https://nexus3.dev.harness.io/repository/todolist/v2/todolist/manifests/a1new\",\n"
                + "                    \"path\": \"v2/todolist/manifests/a1new\",\n"
                + "                    \"id\": \"dG9kb2xpc3Q6N2Y2Mzc5ZDMyZjhkZDc4ZmRjMWY0MTM4NDI0M2JmOTE\",\n"
                + "                    \"repository\": \"todolist\",\n"
                + "                    \"format\": \"docker\",\n"
                + "                    \"checksum\": {\n"
                + "                        \"sha1\": \"0d0793de2da200fd2a821357adffe89438bbc9be\",\n"
                + "                        \"sha256\": \"90659bf80b44ce6be8234e6ff90a1ac34acbeb826903b02cfa0da11c82cbc042\"\n"
                + "                    }\n"
                + "                }\n"
                + "            ]\n"
                + "        }\n"
                + "],\n"
                + "\"continuationToken\": null\n"
                + "}")));
    try {
      List<BuildDetailsInternal> response = nexusThreeService.getArtifactsVersions(
          nexusConfig, "todolist", null, "todolist", RepositoryFormat.docker.name());

      assertThat(response).isNotNull();
      assertThat(response).size().isEqualTo(2);
    } catch (Exception e) {
      fail("This point should not have been reached!", e);
    }

    assertThatThrownBy(()
                           -> nexusThreeService.getArtifactsVersions(
                               nexusConfig, "todolist", "abcd", "todolist", RepositoryFormat.docker.name()))
        .isInstanceOf(HintException.class);
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testGetBuildDetails() {
    NexusRequest nexusConfig = NexusRequest.builder()
                                   .nexusUrl(url)
                                   .username("username")
                                   .password("password".toCharArray())
                                   .hasCredentials(true)
                                   .artifactRepositoryUrl(url)
                                   .version("3.x")
                                   .build();

    wireMockRule.stubFor(
        get(urlEqualTo("/service/rest/v1/search?repository=todolist&name=todolist&format=docker&version=latest2"))
            .willReturn(aResponse().withStatus(200).withBody("{\n"
                + "    \"items\": [\n"
                + "{\n"
                + "            \"id\": \"dG9kb2xpc3Q6NzFhZmVhNTQwZTIzZGRlNTdiODg3MThiYzBmNWY3M2Q\",\n"
                + "            \"repository\": \"todolist\",\n"
                + "            \"format\": \"docker\",\n"
                + "            \"group\": null,\n"
                + "            \"name\": \"todolist\",\n"
                + "            \"version\": \"latest2\",\n"
                + "            \"assets\": [\n"
                + "                {\n"
                + "                    \"downloadUrl\": \"https://nexus3.dev.harness.io/repository/todolist/v2/todolist/manifests/latest2\",\n"
                + "                    \"path\": \"v2/todolist/manifests/latest2\",\n"
                + "                    \"id\": \"dG9kb2xpc3Q6OTEyZDBmZTdiODE5MjM5MjcxODliMGYyNmQxMDE3NTQ\",\n"
                + "                    \"repository\": \"todolist\",\n"
                + "                    \"format\": \"docker\",\n"
                + "                    \"checksum\": {\n"
                + "                        \"sha1\": \"0d0793de2da200fd2a821357adffe89438bbc9be\",\n"
                + "                        \"sha256\": \"90659bf80b44ce6be8234e6ff90a1ac34acbeb826903b02cfa0da11c82cbc042\"\n"
                + "                    }\n"
                + "                }\n"
                + "            ]\n"
                + "        }\n"
                + "],\n"
                + "\"continuationToken\": null"
                + "}")));

    try {
      List<BuildDetailsInternal> response = nexusThreeService.getBuildDetails(
          nexusConfig, "todolist", null, "todolist", RepositoryFormat.docker.name(), "latest2");

      assertThat(response).isNotNull();
      assertThat(response).size().isEqualTo(1);
      assertThat(response.get(0).getMetadata().get(ArtifactMetadataKeys.IMAGE))
          .isEqualTo("localhost:" + wireMockRule.port() + "/todolist:latest2");
    } catch (Exception e) {
      fail("This point should not have been reached!", e);
    }

    assertThatThrownBy(()
                           -> nexusThreeService.getBuildDetails(
                               nexusConfig, "todolist", "abcd", "todolist", RepositoryFormat.docker.name(), "latest2"))
        .isInstanceOf(HintException.class);
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testVerifyArtifactManifestUrl() {
    NexusRequest nexusConfig = NexusRequest.builder()
                                   .nexusUrl(url)
                                   .username("username")
                                   .password("password".toCharArray())
                                   .hasCredentials(true)
                                   .artifactRepositoryUrl(url)
                                   .version("3.x")
                                   .build();

    String mockManifestUrl = url + "/repository/testrepo/v2/testimage/manifests/latest";

    wireMockRule.stubFor(
        get(urlEqualTo("/repository/testrepo/v2/testimage/manifests/latest")).willReturn(aResponse().withStatus(200)));

    wireMockRule.stubFor(get(urlEqualTo("/v2/token"))
                             .willReturn(aResponse().withStatus(200).withBody(
                                 "{\"token\":\"DockerToken.35a31844-fd58-313d-8399-9cf2d00068a9\"}")));

    boolean response = nexusThreeService.verifyArtifactManifestUrl(nexusConfig, mockManifestUrl);
    assertThat(response).isNotNull();
    assertThat(response).isEqualTo(true);

    String mockManifestUrl2 = url + "/repository/testrepo2/v2/testimage/manifests/latest";
    wireMockRule.stubFor(
        get(urlEqualTo("/repository/testrepo2/v2/testimage/manifests/latest")).willReturn(aResponse().withStatus(404)));

    assertThatThrownBy(() -> nexusThreeService.verifyArtifactManifestUrl(nexusConfig, mockManifestUrl2))
        .isInstanceOf(HintException.class)
        .getCause()
        .isInstanceOf(ExplanationException.class)
        .getCause()
        .isInstanceOf(NexusRegistryException.class);
  }
}
