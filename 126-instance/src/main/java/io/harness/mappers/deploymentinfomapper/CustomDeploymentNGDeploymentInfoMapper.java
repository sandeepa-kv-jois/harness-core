/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.mappers.deploymentinfomapper;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.dtos.deploymentinfo.CustomDeploymentNGDeploymentInfoDTO;
import io.harness.entities.deploymentinfo.CustomDeploymentNGDeploymentInfo;

import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.CDP)
@UtilityClass
public class CustomDeploymentNGDeploymentInfoMapper {
  public CustomDeploymentNGDeploymentInfoDTO toDTO(CustomDeploymentNGDeploymentInfo customDeploymentNGDeploymentInfo) {
    return CustomDeploymentNGDeploymentInfoDTO.builder()
        .instanceFetchScript(customDeploymentNGDeploymentInfo.getInstanceFetchScript())
        .infratructureKey(customDeploymentNGDeploymentInfo.getInfratructureKey())
        .artifactBuildNum(customDeploymentNGDeploymentInfo.getArtifactBuildNum())
        .artifactName(customDeploymentNGDeploymentInfo.getArtifactName())
        .artifactSourceName(customDeploymentNGDeploymentInfo.getArtifactSourceName())
        .artifactStreamId(customDeploymentNGDeploymentInfo.getArtifactStreamId())
        .scriptOutput(customDeploymentNGDeploymentInfo.getScriptOutput())
        .tags(customDeploymentNGDeploymentInfo.getTags())
        .build();
  }

  public CustomDeploymentNGDeploymentInfo toEntity(
      CustomDeploymentNGDeploymentInfoDTO customDeploymentNGDeploymentInfoDTO) {
    return CustomDeploymentNGDeploymentInfo.builder()
        .instanceFetchScript(customDeploymentNGDeploymentInfoDTO.getInstanceFetchScript())
        .artifactBuildNum(customDeploymentNGDeploymentInfoDTO.getArtifactBuildNum())
        .artifactName(customDeploymentNGDeploymentInfoDTO.getArtifactName())
        .artifactSourceName(customDeploymentNGDeploymentInfoDTO.getArtifactSourceName())
        .artifactStreamId(customDeploymentNGDeploymentInfoDTO.getArtifactStreamId())
        .infratructureKey(customDeploymentNGDeploymentInfoDTO.getInfratructureKey())
        .scriptOutput(customDeploymentNGDeploymentInfoDTO.getScriptOutput())
        .tags(customDeploymentNGDeploymentInfoDTO.getTags())
        .build();
  }
}
