/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.artifact.resources.artifactory.service;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.artifact.resources.artifactory.dtos.ArtifactoryArtifactBuildDetailsDTO;
import io.harness.cdng.artifact.resources.artifactory.dtos.ArtifactoryRepoDetailsDTO;

import java.util.List;
import lombok.NonNull;

@OwnedBy(HarnessTeam.CDP)
public interface ArtifactoryResourceService {
  ArtifactoryRepoDetailsDTO getRepositories(@NonNull String repositoryType, @NonNull IdentifierRef connectorRef,
      @NonNull String orgIdentifier, @NonNull String projectIdentifier);

  List<ArtifactoryArtifactBuildDetailsDTO> getBuildDetails(@NonNull String repositoryName, @NonNull String filePath,
      int maxVersions, @NonNull IdentifierRef connectorRef, @NonNull String orgIdentifier,
      @NonNull String projectIdentifier);
}
