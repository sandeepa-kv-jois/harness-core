/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.mappers.instanceinfo;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.dtos.instanceinfo.AzureSshWinrmInstanceInfoDTO;
import io.harness.entities.instanceinfo.AzureSshWinrmInstanceInfo;

import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.CDP)
@UtilityClass
public class AzureSshWinrmInstanceInfoMapper {
  public AzureSshWinrmInstanceInfoDTO toDTO(AzureSshWinrmInstanceInfo instanceInfo) {
    return AzureSshWinrmInstanceInfoDTO.builder()
        .host(instanceInfo.getHost())
        .serviceType(instanceInfo.getServiceType())
        .infrastructureKey(instanceInfo.getInfrastructureKey())
        .build();
  }

  public AzureSshWinrmInstanceInfo toEntity(AzureSshWinrmInstanceInfoDTO instanceInfoDTO) {
    return AzureSshWinrmInstanceInfo.builder()
        .host(instanceInfoDTO.getHost())
        .serviceType(instanceInfoDTO.getServiceType())
        .infrastructureKey(instanceInfoDTO.getInfrastructureKey())
        .build();
  }
}
