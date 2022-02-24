/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.artifact.resources.artifactory.mappers;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.artifact.resources.artifactory.dtos.ArtifactoryArtifactBuildDetailsDTO;

import software.wings.helpers.ext.jenkins.BuildDetails;

import java.util.List;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(HarnessTeam.CDP)
public class ArtifactoryResourceMapper {
  public List<ArtifactoryArtifactBuildDetailsDTO> toArtifactoryArtifactBuildDetailsDTO(
      List<BuildDetails> buildDetails) {
    return buildDetails.stream()
        .map(b
            -> ArtifactoryArtifactBuildDetailsDTO.builder()
                   .artifactName(b.getNumber())
                   .artifactPath(b.getArtifactPath())
                   .build())
        .collect(Collectors.toList());
  }
}
