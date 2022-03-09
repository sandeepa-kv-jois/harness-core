/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.envGroup.mappers;

import static io.harness.NGConstants.HARNESS_BLUE;
import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.ng.core.mapper.TagMapper.convertToMap;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.envGroup.beans.EnvironmentGroupEntity;
import io.harness.ng.core.envGroup.dto.EnvironmentGroupResponse;
import io.harness.ng.core.envGroup.dto.EnvironmentGroupResponseDTO;

import java.util.Optional;
import lombok.experimental.UtilityClass;

@OwnedBy(PIPELINE)
@UtilityClass
public class EnvironmentGroupMapper {
  public EnvironmentGroupResponseDTO writeDTO(EnvironmentGroupEntity envGroup) {
    return EnvironmentGroupResponseDTO.builder()
        .accountId(envGroup.getAccountId())
        .orgIdentifier(envGroup.getOrgIdentifier())
        .projectIdentifier(envGroup.getProjectIdentifier())
        .identifier(envGroup.getIdentifier())
        .name(envGroup.getName())
        .color(Optional.ofNullable(envGroup.getColor()).orElse(HARNESS_BLUE))
        .description(envGroup.getDescription())
        .deleted(envGroup.getDeleted())
        .tags(convertToMap(envGroup.getTags()))
        .version(envGroup.getVersion())
        .build();
  }

  public EnvironmentGroupResponse toResponseWrapper(EnvironmentGroupEntity envGroup) {
    return EnvironmentGroupResponse.builder()
        .environment(writeDTO(envGroup))
        .createdAt(envGroup.getCreatedAt())
        .lastModifiedAt(envGroup.getLastModifiedAt())
        .build();
  }
}
