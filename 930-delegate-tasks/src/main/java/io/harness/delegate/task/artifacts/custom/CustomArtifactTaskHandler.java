/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.custom;

import io.harness.delegate.task.artifacts.DelegateArtifactTaskHandler;
import io.harness.delegate.task.artifacts.response.ArtifactTaskExecutionResponse;
import io.harness.delegate.task.shell.CustomArtifactScriptExecutionOnDelegateNG;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Singleton
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
public class CustomArtifactTaskHandler extends DelegateArtifactTaskHandler<CustomArtifactDelegateRequest> {
  @Inject private CustomArtifactScriptExecutionOnDelegateNG customArtifactScriptExecutionOnDelegateNG;
  @Inject private CustomArtifactService customArtifactService;

  @Override
  public void decryptRequestDTOs(CustomArtifactDelegateRequest dto) {}

  @Override
  public ArtifactTaskExecutionResponse getBuilds(CustomArtifactDelegateRequest attributesRequest) {
    return customArtifactService.getBuilds(attributesRequest);
  }

  @Override
  public ArtifactTaskExecutionResponse getLastSuccessfulBuild(CustomArtifactDelegateRequest attributesRequest) {
    return customArtifactService.getLastSuccessfulBuild(attributesRequest);
  }
}
