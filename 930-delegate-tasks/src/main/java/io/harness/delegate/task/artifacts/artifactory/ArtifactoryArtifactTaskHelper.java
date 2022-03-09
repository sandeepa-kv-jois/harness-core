/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.artifactory;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.artifact.ArtifactMetadataKeys;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.task.artifacts.request.ArtifactTaskParameters;
import io.harness.delegate.task.artifacts.response.ArtifactTaskExecutionResponse;
import io.harness.delegate.task.artifacts.response.ArtifactTaskResponse;
import io.harness.eraro.ErrorCode;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;

import software.wings.utils.RepositoryFormat;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CDP)
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
@Singleton
public class ArtifactoryArtifactTaskHelper {
  private final ArtifactoryArtifactTaskHandler artifactoryArtifactTaskHandler;

  public ArtifactTaskResponse getArtifactCollectResponse(
      ArtifactTaskParameters artifactTaskParameters, LogCallback executionLogCallback) {
    ArtifactoryArtifactDelegateRequest attributes =
        (ArtifactoryArtifactDelegateRequest) artifactTaskParameters.getAttributes();
    String registryUrl = attributes.getArtifactoryConnectorDTO().getArtifactoryServerUrl();
    artifactoryArtifactTaskHandler.decryptRequestDTOs(attributes);
    ArtifactTaskResponse artifactTaskResponse;
    switch (artifactTaskParameters.getArtifactTaskType()) {
      case GET_LAST_SUCCESSFUL_BUILD:
        saveLogs(executionLogCallback, "Fetching Artifact details");
        artifactTaskResponse =
            getSuccessTaskResponse(artifactoryArtifactTaskHandler.getLastSuccessfulBuild(attributes));
        ArtifactoryArtifactDelegateResponse artifactoryArtifactDelegateResponse =
            (ArtifactoryArtifactDelegateResponse) (artifactTaskResponse.getArtifactTaskExecutionResponse()
                                                       .getArtifactDelegateResponses()
                                                       .size()
                        != 0
                    ? artifactTaskResponse.getArtifactTaskExecutionResponse().getArtifactDelegateResponses().get(0)
                    : ArtifactoryArtifactDelegateResponse.builder().build());
        String buildMetadataUrl = artifactoryArtifactDelegateResponse.getBuildDetails() != null
            ? artifactoryArtifactDelegateResponse.getBuildDetails().getBuildUrl()
            : null;
        String dockerPullCommand =
            (RepositoryFormat.docker.name().equals(artifactoryArtifactDelegateResponse.getRepositoryFormat())
                && artifactoryArtifactDelegateResponse.getBuildDetails() != null
                && artifactoryArtifactDelegateResponse.getBuildDetails().getMetadata() != null)
            ? "\nImage pull command: docker pull "
                + artifactoryArtifactDelegateResponse.getBuildDetails().getMetadata().get(ArtifactMetadataKeys.IMAGE)
            : null;
        saveLogs(executionLogCallback,
            "Fetched Artifact details"
                + "\ntype: Artifactory Artifact"
                + "\nbuild metadata url: " + buildMetadataUrl
                + "\nrepository: " + artifactoryArtifactDelegateResponse.getRepositoryName()
                + "\nartifactPath: " + artifactoryArtifactDelegateResponse.getArtifactPath()
                + "\ntag: " + artifactoryArtifactDelegateResponse.getTag()
                + "\nrepository type: " + artifactoryArtifactDelegateResponse.getRepositoryFormat()
                + (EmptyPredicate.isNotEmpty(dockerPullCommand) ? dockerPullCommand : ""));
        break;
      case GET_BUILDS:
        saveLogs(executionLogCallback, "Fetching artifact details");
        artifactTaskResponse = getSuccessTaskResponse(artifactoryArtifactTaskHandler.getBuilds(attributes));
        saveLogs(executionLogCallback,
            "Fetched " + artifactTaskResponse.getArtifactTaskExecutionResponse().getArtifactDelegateResponses().size()
                + " artifacts");
        break;
      case VALIDATE_ARTIFACT_SERVER:
        saveLogs(executionLogCallback, "Validating  Artifact Server");
        artifactTaskResponse =
            getSuccessTaskResponse(artifactoryArtifactTaskHandler.validateArtifactServer(attributes));
        saveLogs(executionLogCallback, "validated artifact server: " + registryUrl);
        break;
      default:
        saveLogs(executionLogCallback,
            "No corresponding Artifactory artifact task type [{}]: " + artifactTaskParameters.toString());
        log.error("No corresponding Artifactory artifact task type [{}]", artifactTaskParameters.toString());
        return ArtifactTaskResponse.builder()
            .commandExecutionStatus(CommandExecutionStatus.FAILURE)
            .errorMessage("There is no Artifactory artifact task type impl defined for - "
                + artifactTaskParameters.getArtifactTaskType().name())
            .errorCode(ErrorCode.INVALID_ARGUMENT)
            .build();
    }
    return artifactTaskResponse;
  }

  private ArtifactTaskResponse getSuccessTaskResponse(ArtifactTaskExecutionResponse taskExecutionResponse) {
    return ArtifactTaskResponse.builder()
        .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
        .artifactTaskExecutionResponse(taskExecutionResponse)
        .build();
  }

  private void saveLogs(LogCallback executionLogCallback, String message) {
    if (executionLogCallback != null) {
      executionLogCallback.saveExecutionLog(message);
    }
  }
  public ArtifactTaskResponse getArtifactCollectResponse(ArtifactTaskParameters artifactTaskParameters) {
    return getArtifactCollectResponse(artifactTaskParameters, null);
  }
}
