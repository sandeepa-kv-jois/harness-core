/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.mappers;

import io.harness.artifacts.beans.BuildDetailsInternal;
import io.harness.artifacts.gar.beans.GarInternalConfig;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.delegate.task.artifacts.gar.GarDelegateRequest;
import io.harness.delegate.task.artifacts.gar.GarDelegateResponse;

import lombok.experimental.UtilityClass;

@UtilityClass
public class GarRequestResponseMapper {
  public static GarInternalConfig toGarInternalConfig(GarDelegateRequest request, String bearerToken) {
    return GarInternalConfig.builder()
        .bearerToken(bearerToken)
        .project(request.getProject())
        .repositoryName(request.getRepositoryName())
        .region(request.getRegion())
        .pkg(request.getPkg())
        .maxBuilds(request.getMaxBuilds())
        .build();
  }
  public static GarDelegateResponse toGarResponse(
      BuildDetailsInternal buildDetailsInternal, GarDelegateRequest request) {
    return GarDelegateResponse.builder()
        .buildDetails(ArtifactBuildDetailsMapper.toBuildDetailsNG(buildDetailsInternal))
        .sourceType(ArtifactSourceType.GOOGLE_ARTIFACT_REGISTRY)
        .version(buildDetailsInternal.getNumber())
        .build();
  }
}
