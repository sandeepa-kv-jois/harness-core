/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.nexus;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.nexus.NexusHelper.isRequestSuccessful;
import static io.harness.nexus.NexusHelper.isSuccessful;

import static java.util.Collections.emptyMap;

import io.harness.annotations.dev.OwnedBy;
import io.harness.artifact.ArtifactMetadataKeys;
import io.harness.artifact.ArtifactUtilities;
import io.harness.artifacts.beans.BuildDetailsInternal;
import io.harness.delegate.beans.artifact.ArtifactFileMetadataInternal;
import io.harness.exception.InvalidArtifactServerException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.NexusRegistryException;
import io.harness.exception.WingsException;
import io.harness.nexus.model.Asset;
import io.harness.nexus.model.Nexus3ComponentResponse;
import io.harness.nexus.model.Nexus3Repository;
import io.harness.nexus.model.Nexus3TokenResponse;

import software.wings.utils.RepositoryFormat;

import com.google.common.collect.Lists;
import com.google.inject.Singleton;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Credentials;
import org.apache.commons.lang3.StringUtils;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.converter.jackson.JacksonConverterFactory;

@OwnedBy(CDC)
@Singleton
@Slf4j
public class NexusThreeClientImpl {
  private static final List<String> IGNORE_EXTENSIONS = Lists.newArrayList("pom", "sha1", "sha256", "sha512", "md5");
  private static final String REPO_PORT_REGEX = "^[\\d]+$";

  public Map<String, String> getRepositories(NexusRequest nexusConfig, String repositoryFormat) throws IOException {
    log.info("Retrieving repositories");
    NexusThreeRestClient nexusThreeRestClient = getNexusThreeClient(nexusConfig);
    Response<List<Nexus3Repository>> response;
    if (nexusConfig.isHasCredentials()) {
      response =
          nexusThreeRestClient
              .listRepositories(Credentials.basic(nexusConfig.getUsername(), new String(nexusConfig.getPassword())))
              .execute();
    } else {
      response = nexusThreeRestClient.listRepositories().execute();
    }

    if (isSuccessful(response)) {
      if (isNotEmpty(response.body())) {
        log.info("Retrieving {} repositories success", repositoryFormat);
        final Map<String, String> repositories;
        if (repositoryFormat == null) {
          repositories =
              response.body().stream().collect(Collectors.toMap(Nexus3Repository::getName, Nexus3Repository::getName));
        } else {
          final String filterBy = repositoryFormat.equals(RepositoryFormat.maven.name()) ? "maven2" : repositoryFormat;
          repositories = response.body()
                             .stream()
                             .filter(o -> o.getFormat().equals(filterBy))
                             .collect(Collectors.toMap(Nexus3Repository::getName, Nexus3Repository::getName));
        }
        log.info("Retrieved repositories are {}", repositories.values());
        return repositories;
      } else {
        throw NestedExceptionUtils.hintWithExplanationException(
            "Check if the connector details - URL & credentials are correct",
            "No repositories were found for the connector",
            new InvalidArtifactServerException("Failed to fetch the repositories", WingsException.USER));
      }
    }
    log.info("No repositories found returning empty map");
    return emptyMap();
  }

  public NexusThreeRestClient getNexusThreeClient(NexusRequest nexusConfig) {
    return NexusHelper.getRetrofit(nexusConfig, JacksonConverterFactory.create()).create(NexusThreeRestClient.class);
  }

  public NexusThreeRestClient getNexusThreeClient(NexusRequest nexusConfig, String artifactoDownloadUrl) {
    return NexusHelper
        .getRetrofit(artifactoDownloadUrl, nexusConfig.isCertValidationRequired(), JacksonConverterFactory.create())
        .create(NexusThreeRestClient.class);
  }

  public boolean isServerValid(NexusRequest nexusConfig) throws IOException {
    log.info("Validate if nexus is running by retrieving repositories");
    NexusThreeRestClient nexusThreeRestClient = getNexusThreeClient(nexusConfig);
    Response<List<Nexus3Repository>> response;
    if (nexusConfig.isHasCredentials()) {
      response =
          nexusThreeRestClient
              .listRepositories(Credentials.basic(nexusConfig.getUsername(), new String(nexusConfig.getPassword())))
              .execute();
    } else {
      response = nexusThreeRestClient.listRepositories().execute();
    }
    if (response == null) {
      return false;
    }

    if (response.code() == 404) {
      throw NestedExceptionUtils.hintWithExplanationException(
          "Check if the Nexus URL & Nexus version are correct. Nexus URLs are different for different Nexus versions",
          "The Nexus URL or the version for the connector is incorrect",
          new InvalidArtifactServerException("Invalid Nexus connector details"));
    }
    return NexusHelper.isSuccessful(response);
  }

  public List<BuildDetailsInternal> getArtifactsVersions(
      NexusRequest nexusConfig, String repository, String port, String artifactName, String repositoryFormat) {
    if (isNotEmpty(port) && !port.matches(REPO_PORT_REGEX)) {
      throw NestedExceptionUtils.hintWithExplanationException(
          "Check repository port field for Nexus artifact configuration.",
          String.format("Repository port [%s] field must only contain numeric characters.", port),
          new NexusRegistryException("Invalid Nexus artifact configuration details"));
    }
    log.info("Retrieving artifact versions(tags)");
    NexusThreeRestClient nexusThreeRestClient = getNexusThreeClient(nexusConfig);
    final Call<Nexus3ComponentResponse> request;
    if (nexusConfig.isHasCredentials()) {
      request = nexusThreeRestClient.search(
          Credentials.basic(nexusConfig.getUsername(), new String(nexusConfig.getPassword())), repository, artifactName,
          repositoryFormat, null);
    } else {
      request = nexusThreeRestClient.search(repository, artifactName, repositoryFormat, null);
    }

    Response<Nexus3ComponentResponse> response = executeRequest(request);
    List<BuildDetailsInternal> result = processComponentResponse(
        request, response, nexusConfig, repository, port, artifactName, repositoryFormat, null);

    if (isEmpty(result)) {
      throw NestedExceptionUtils.hintWithExplanationException("Please check your artifact configuration.",
          String.format("Failed to retrieve artifact tags by API call '%s %s' and got response code '%s'",
              request.request().method(), request.request().url(), response.code()),
          new NexusRegistryException(
              String.format("No tags found for artifact [repositoryFormat=%s, repository=%s, artifact=%s].",
                  repositoryFormat, repository, artifactName)));
    }

    return result;
  }

  private List<ArtifactFileMetadataInternal> getArtifactMetadata(List<Asset> assets, String repoId) {
    List<ArtifactFileMetadataInternal> artifactFileMetadataInternals = new ArrayList<>();
    if (isEmpty(assets)) {
      return artifactFileMetadataInternals;
    }
    for (Asset item : assets) {
      String url = item.getDownloadUrl();
      String artifactFileName = url.substring(url.lastIndexOf('/') + 1);
      String artifactPath = item.getPath();
      if (IGNORE_EXTENSIONS.stream().anyMatch(artifactFileName::endsWith)) {
        continue;
      }
      if (!item.getRepository().equals(repoId)) {
        url = url.replace(item.getRepository(), repoId);
      }
      artifactFileMetadataInternals.add(
          ArtifactFileMetadataInternal.builder().fileName(artifactFileName).imagePath(artifactPath).url(url).build());
    }
    return artifactFileMetadataInternals;
  }

  private String getArtifactDownloadUrl(
      List<ArtifactFileMetadataInternal> artifactFileMetadataInternals, String extension, String classifier) {
    String defaultUrl = artifactFileMetadataInternals.get(0).getUrl();
    String url = null;
    if (StringUtils.isNoneBlank(extension, classifier)) {
      url = artifactFileMetadataInternals.stream()
                .filter(meta -> meta.getFileName().endsWith(extension) && meta.getFileName().contains(classifier))
                .map(ArtifactFileMetadataInternal::getUrl)
                .findFirst()
                .orElse(null);
    }
    return StringUtils.isNotBlank(url) ? url : defaultUrl;
  }

  private String getArtifactImagePath(
      List<ArtifactFileMetadataInternal> artifactFileMetadataInternals, String extension, String classifier) {
    String defaultArtifactPath = artifactFileMetadataInternals.get(0).getImagePath();
    String artifactPath = null;
    if (StringUtils.isNoneBlank(extension, classifier)) {
      artifactPath =
          artifactFileMetadataInternals.stream()
              .filter(meta -> meta.getFileName().endsWith(extension) && meta.getFileName().contains(classifier))
              .map(ArtifactFileMetadataInternal::getImagePath)
              .findFirst()
              .orElse(null);
    }
    return StringUtils.isNotBlank(artifactPath) ? artifactPath : defaultArtifactPath;
  }

  public List<BuildDetailsInternal> getBuildDetails(NexusRequest nexusConfig, String repository, String port,
      String artifactName, String repositoryFormat, String tag) {
    if (isNotEmpty(port) && !port.matches(REPO_PORT_REGEX)) {
      throw NestedExceptionUtils.hintWithExplanationException(
          "Please check repository port field in your Nexus artifact configuration.",
          String.format("Repository port [%s] field must only contain numeric characters.", port),
          new NexusRegistryException(
              String.format("Repository port has an invalid value.", repositoryFormat, repository, artifactName, tag)));
    }
    log.info("Retrieving artifact details");
    NexusThreeRestClient nexusThreeRestClient = getNexusThreeClient(nexusConfig);
    final Call<Nexus3ComponentResponse> request;
    if (nexusConfig.isHasCredentials()) {
      request = nexusThreeRestClient.getArtifact(
          Credentials.basic(nexusConfig.getUsername(), new String(nexusConfig.getPassword())), repository, artifactName,
          repositoryFormat, tag, null);
    } else {
      request = nexusThreeRestClient.getArtifact(repository, artifactName, repositoryFormat, tag, null);
    }

    Response<Nexus3ComponentResponse> response = executeRequest(request);
    List<BuildDetailsInternal> result =
        processComponentResponse(request, response, nexusConfig, repository, port, artifactName, repositoryFormat, tag);

    if (isEmpty(result)) {
      throw NestedExceptionUtils.hintWithExplanationException("Please check your artifact configuration.",
          String.format("Failed to retrieve artifact metadata with API call '%s %s' and got response code '%s'",
              request.request().method(), request.request().url(), response.code()),
          new NexusRegistryException(
              String.format("Artifact [repositoryFormat=%s, repository=%s, artifact=%s, tag=%s] was not found.",
                  repositoryFormat, repository, artifactName, tag)));
    }

    return result;
  }

  private Response executeRequest(Call request) {
    try {
      return request.execute();
    } catch (IOException e) {
      throw NestedExceptionUtils.hintWithExplanationException(
          "Nexus registry might not be online. Please check Nexus connector configuration and verify that URL is valid.",
          String.format("Failed to execute API call '%s %s'", request.request().method(), request.request().url()),
          new NexusRegistryException(e.getMessage()));
    }
  }

  private List<BuildDetailsInternal> processComponentResponse(Call request, Response<Nexus3ComponentResponse> response,
      NexusRequest nexusConfig, String repository, String port, String artifactName, String repositoryFormat,
      String tag) {
    List<BuildDetailsInternal> components = new ArrayList<>();
    if (isRequestSuccessful(request, response)) {
      if (isNotEmpty(response.body().getItems())) {
        for (Nexus3ComponentResponse.Component component : response.body().getItems()) {
          List<ArtifactFileMetadataInternal> artifactFileMetadataInternals =
              getArtifactMetadata(component.getAssets(), repository);
          String versionDownloadUrl = null;
          String artifactPath = null;
          String actualTag = isEmpty(tag) ? component.getVersion() : tag;
          if (isNotEmpty(artifactFileMetadataInternals)) {
            versionDownloadUrl = getArtifactDownloadUrl(artifactFileMetadataInternals, null, null);
            artifactPath = getArtifactImagePath(artifactFileMetadataInternals, null, null);
          }

          String repoName = ArtifactUtilities.getNexusRepositoryNameNG(
              nexusConfig.getNexusUrl(), port, nexusConfig.getArtifactRepositoryUrl(), artifactName);
          log.info("Retrieving docker tags for repository {} imageName {} ", repository, artifactName);
          Map<String, String> metadata = new HashMap<>();
          metadata.put(ArtifactMetadataKeys.IMAGE, repoName + ":" + actualTag);
          metadata.put(ArtifactMetadataKeys.TAG, actualTag);
          metadata.put(ArtifactMetadataKeys.ARTIFACT_MANIFEST_URL,
              ArtifactUtilities.getNexusRegistryUrlNG(
                  nexusConfig.getNexusUrl(), port, nexusConfig.getArtifactRepositoryUrl())
                  + "/repository/" + ArtifactUtilities.trimSlashforwardChars(repository) + "/"
                  + ArtifactUtilities.trimSlashforwardChars(artifactPath));

          BuildDetailsInternal buildDetailsInternal = BuildDetailsInternal.builder()
                                                          .number(component.getVersion())
                                                          .metadata(metadata)
                                                          .buildUrl(versionDownloadUrl)
                                                          .artifactPath(artifactPath)
                                                          .build();

          components.add(buildDetailsInternal);
        }
      }
    }
    return components;
  }

  public boolean verifyArtifactManifestUrl(NexusRequest nexusConfig, String artifactManifestUrl) {
    NexusThreeRestClient nexusThreeRestClient = getNexusThreeClient(
        nexusConfig, ArtifactUtilities.getBaseUrl(ArtifactUtilities.getHostname(artifactManifestUrl)));
    String authorization;

    if (nexusConfig.isHasCredentials()) {
      authorization = Credentials.basic(nexusConfig.getUsername(), new String(nexusConfig.getPassword()));
    } else {
      final Call<Nexus3TokenResponse> tokenRequest = nexusThreeRestClient.getAnonymousAccessToken();
      final Response<Nexus3TokenResponse> tokenResponse = executeRequest(tokenRequest);
      authorization = "Bearer " + tokenResponse.body().getToken();
    }

    HttpURLConnection connection = null;
    try {
      connection = (HttpURLConnection) new URL(artifactManifestUrl).openConnection();
      connection.setRequestMethod("GET");
      connection.setConnectTimeout(15000);
      connection.setReadTimeout(15000);
      connection.setRequestProperty("Authorization", authorization);
      if (connection.getResponseCode() == 200) {
        return true;
      }
    } catch (IOException e) {
      log.error("Failed to pull artifact manifest", e);
    } finally {
      if (connection != null) {
        connection.disconnect();
      }
    }

    throw NestedExceptionUtils.hintWithExplanationException(
        "Please verify your Nexus artifact repository URL field or repository port.",
        String.format("Can not pull the artifact manifest. Check was performed with API call '%s %s'", "GET",
            artifactManifestUrl),
        new NexusRegistryException("Could not retrieve artifact manifest."));
  }
}
