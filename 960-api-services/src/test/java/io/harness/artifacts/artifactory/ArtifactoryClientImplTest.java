package io.harness.artifacts.artifactory;

import static io.harness.rule.OwnerRule.MLUKIC;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.artifactory.ArtifactoryClientImpl;
import io.harness.artifactory.ArtifactoryConfigRequest;
import io.harness.artifacts.beans.BuildDetailsInternal;
import io.harness.category.element.UnitTests;
import io.harness.exception.ArtifactoryRegistryException;
import io.harness.exception.ArtifactoryServerException;
import io.harness.exception.ExplanationException;
import io.harness.exception.HintException;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;

import software.wings.utils.RepositoryFormat;

import com.github.tomakehurst.wiremock.core.Options;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(HarnessTeam.CDP)
public class ArtifactoryClientImplTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @Rule
  public WireMockRule wireMockRule =
      new WireMockRule(WireMockConfiguration.wireMockConfig()
                           .usingFilesUnderDirectory("960-api-services/src/test/resources")
                           .port(Options.DYNAMIC_PORT),
          false);
  @InjectMocks private ArtifactoryClientImpl artifactoryClient;

  private static String url;

  @Before
  public void before() {
    MockitoAnnotations.initMocks(this);
    url = "http://localhost:" + wireMockRule.port();
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testValidateArtifactServer() {
    ArtifactoryConfigRequest artifactoryConfigRequest = ArtifactoryConfigRequest.builder()
                                                            .artifactoryUrl(url)
                                                            .username("username")
                                                            .password("password".toCharArray())
                                                            .hasCredentials(true)
                                                            .artifactRepositoryUrl(url)
                                                            .build();

    wireMockRule.stubFor(get(urlEqualTo("/api/repositories/")).willReturn(aResponse().withStatus(200)));

    boolean response = artifactoryClient.validateArtifactServer(artifactoryConfigRequest);
    assertThat(response).isEqualTo(true);

    wireMockRule.stubFor(get(urlEqualTo("/api/repositories/")).willReturn(aResponse().withStatus(400)));

    assertThatThrownBy(() -> artifactoryClient.validateArtifactServer(artifactoryConfigRequest))
        .isInstanceOf(HintException.class)
        .getCause()
        .isInstanceOf(ExplanationException.class)
        .getCause()
        .isInstanceOf(ArtifactoryServerException.class);
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testIsRunning() {
    ArtifactoryConfigRequest artifactoryConfigRequest = ArtifactoryConfigRequest.builder()
                                                            .artifactoryUrl(url)
                                                            .username("username")
                                                            .password("password".toCharArray())
                                                            .hasCredentials(true)
                                                            .artifactRepositoryUrl(url)
                                                            .build();

    wireMockRule.stubFor(get(urlEqualTo("/api/repositories/")).willReturn(aResponse().withStatus(200)));

    boolean response = artifactoryClient.isRunning(artifactoryConfigRequest);
    assertThat(response).isEqualTo(true);

    wireMockRule.stubFor(get(urlEqualTo("/api/repositories/")).willReturn(aResponse().withStatus(407)));

    assertThatThrownBy(() -> artifactoryClient.validateArtifactServer(artifactoryConfigRequest))
        .isInstanceOf(HintException.class)
        .getCause()
        .isInstanceOf(ExplanationException.class)
        .getCause()
        .isInstanceOf(InvalidRequestException.class);

    wireMockRule.stubFor(get(urlEqualTo("/api/repositories/")).willReturn(aResponse().withStatus(404)));

    assertThatThrownBy(() -> artifactoryClient.validateArtifactServer(artifactoryConfigRequest))
        .isInstanceOf(HintException.class)
        .getCause()
        .isInstanceOf(ExplanationException.class)
        .getCause()
        .isInstanceOf(ArtifactoryServerException.class);
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testGetArtifactsDetails() {
    ArtifactoryConfigRequest artifactoryConfigRequest = ArtifactoryConfigRequest.builder()
                                                            .artifactoryUrl(url)
                                                            .username("username")
                                                            .password("password".toCharArray())
                                                            .hasCredentials(true)
                                                            .artifactRepositoryUrl(url)
                                                            .build();

    wireMockRule.stubFor(get(urlEqualTo("/api/docker/testrepo/v2/nginx/tags/list"))
                             .willReturn(aResponse().withStatus(200).withBody("{\n"
                                 + "  \"name\" : \"nginx\",\n"
                                 + "  \"tags\" : [ \"1.15\", \"latest\", \"1.20\", \"1.21\" ]\n"
                                 + "}")));

    wireMockRule.stubFor(
        get(urlEqualTo("/api/docker/testrepo/v2/nginx2/tags/list")).willReturn(aResponse().withStatus(200)));

    List<BuildDetailsInternal> response = artifactoryClient.getArtifactsDetails(
        artifactoryConfigRequest, "testrepo", "nginx", RepositoryFormat.docker.name(), 10000);
    assertThat(response.size()).isNotNull();
    assertThat(response.size()).isEqualTo(4);

    assertThatThrownBy(()
                           -> artifactoryClient.getArtifactsDetails(
                               artifactoryConfigRequest, "testrepo", "nginx2", RepositoryFormat.docker.name(), 10000))
        .isInstanceOf(HintException.class)
        .getCause()
        .isInstanceOf(ExplanationException.class)
        .getCause()
        .isInstanceOf(ArtifactoryRegistryException.class);
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testVerifyArtifactManifestUrl() {
    ArtifactoryConfigRequest artifactoryConfigRequest = ArtifactoryConfigRequest.builder()
                                                            .artifactoryUrl(url)
                                                            .username("username")
                                                            .password("password".toCharArray())
                                                            .hasCredentials(true)
                                                            .artifactRepositoryUrl(url)
                                                            .build();

    String artifactManifestUrl = url + "/artifactory/testrepo/nginx/latest/manifest.json";

    wireMockRule.stubFor(
        get(urlEqualTo("/artifactory/testrepo/nginx/latest/manifest.json")).willReturn(aResponse().withStatus(200)));

    boolean response = artifactoryClient.verifyArtifactManifestUrl(artifactoryConfigRequest, artifactManifestUrl);
    assertThat(response).isNotNull();
    assertThat(response).isEqualTo(true);

    String artifactManifestUrl2 = url + "/artifactory/testrepo2/nginx/latest/manifest.json";

    wireMockRule.stubFor(
        get(urlEqualTo("/artifactory/testrepo2/nginx/latest/manifest.json")).willReturn(aResponse().withStatus(404)));

    assertThatThrownBy(
        () -> artifactoryClient.verifyArtifactManifestUrl(artifactoryConfigRequest, artifactManifestUrl2))
        .isInstanceOf(HintException.class)
        .getCause()
        .isInstanceOf(ExplanationException.class)
        .getCause()
        .isInstanceOf(ArtifactoryRegistryException.class);
  }
}
