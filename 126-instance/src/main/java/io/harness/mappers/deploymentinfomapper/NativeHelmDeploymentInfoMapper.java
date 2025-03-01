/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.mappers.deploymentinfomapper;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.dtos.deploymentinfo.NativeHelmDeploymentInfoDTO;
import io.harness.entities.deploymentinfo.NativeHelmDeploymentInfo;

import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.CDP)
@UtilityClass
public class NativeHelmDeploymentInfoMapper {
  public NativeHelmDeploymentInfoDTO toDTO(NativeHelmDeploymentInfo nativeHelmDeploymentInfo) {
    return NativeHelmDeploymentInfoDTO.builder()
        .namespaces(nativeHelmDeploymentInfo.getNamespaces())
        .releaseName(nativeHelmDeploymentInfo.getReleaseName())
        .helmChartInfo(nativeHelmDeploymentInfo.getHelmChartInfo())
        .helmVersion(nativeHelmDeploymentInfo.getHelmVersion())
        .build();
  }

  public NativeHelmDeploymentInfo toEntity(NativeHelmDeploymentInfoDTO nativeHelmDeploymentInfoDTO) {
    return NativeHelmDeploymentInfo.builder()
        .namespaces(nativeHelmDeploymentInfoDTO.getNamespaces())
        .releaseName(nativeHelmDeploymentInfoDTO.getReleaseName())
        .helmChartInfo(nativeHelmDeploymentInfoDTO.getHelmChartInfo())
        .helmVersion(nativeHelmDeploymentInfoDTO.getHelmVersion())
        .build();
  }
}
