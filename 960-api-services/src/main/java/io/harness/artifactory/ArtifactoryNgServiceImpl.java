/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.artifactory;

import static org.jfrog.artifactory.client.model.impl.PackageTypeImpl.docker;
import static org.jfrog.artifactory.client.model.impl.PackageTypeImpl.maven;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import software.wings.helpers.ext.jenkins.BuildDetails;
import software.wings.utils.RepositoryType;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class ArtifactoryNgServiceImpl implements ArtifactoryNgService {
  @Inject ArtifactoryClientImpl artifactoryClient;

  @Override
  public List<BuildDetails> getBuildDetails(
      ArtifactoryConfigRequest artifactoryConfig, String repositoryName, String artifactPath, int maxVersions) {
    return artifactoryClient.getBuildDetails(artifactoryConfig, repositoryName, artifactPath, maxVersions);
  }

  @Override
  public Map<String, String> getRepositories(ArtifactoryConfigRequest artifactoryConfig, String packageType) {
    RepositoryType repositoryType = RepositoryType.valueOf(packageType);

    switch (repositoryType) {
      case docker:
        return artifactoryClient.getRepositories(artifactoryConfig, Arrays.asList(docker));
      case maven:
        return artifactoryClient.getRepositories(artifactoryConfig, Arrays.asList(maven));
      case any:
      default:
        return artifactoryClient.getRepositories(artifactoryConfig, new ArrayList<>());
    }
  }

  @Override
  public InputStream downloadArtifacts(ArtifactoryConfigRequest artifactoryConfig, String repoKey,
      Map<String, String> metadata, String artifactPathMetadataKey, String artifactFileNameMetadataKey) {
    return artifactoryClient.downloadArtifacts(
        artifactoryConfig, repoKey, metadata, artifactPathMetadataKey, artifactFileNameMetadataKey);
  }
}
