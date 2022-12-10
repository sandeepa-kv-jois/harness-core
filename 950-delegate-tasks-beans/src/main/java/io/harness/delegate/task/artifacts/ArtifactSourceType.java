/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts;

import static io.harness.delegate.task.artifacts.ArtifactSourceConstants.ACR_NAME;
import static io.harness.delegate.task.artifacts.ArtifactSourceConstants.AMAZON_S3_NAME;
import static io.harness.delegate.task.artifacts.ArtifactSourceConstants.AMI_ARTIFACTS_NAME;
import static io.harness.delegate.task.artifacts.ArtifactSourceConstants.AZURE_ARTIFACTS_NAME;
import static io.harness.delegate.task.artifacts.ArtifactSourceConstants.CUSTOM_ARTIFACT_NAME;
import static io.harness.delegate.task.artifacts.ArtifactSourceConstants.DOCKER_REGISTRY_NAME;
import static io.harness.delegate.task.artifacts.ArtifactSourceConstants.ECR_NAME;
import static io.harness.delegate.task.artifacts.ArtifactSourceConstants.GCR_NAME;
import static io.harness.delegate.task.artifacts.ArtifactSourceConstants.GITHUB_PACKAGES_NAME;
import static io.harness.delegate.task.artifacts.ArtifactSourceConstants.GOOGLE_ARTIFACT_REGISTRY_NAME;
import static io.harness.delegate.task.artifacts.ArtifactSourceConstants.JENKINS_NAME;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

public enum ArtifactSourceType {
  @JsonProperty(DOCKER_REGISTRY_NAME) DOCKER_REGISTRY(DOCKER_REGISTRY_NAME),
  @JsonProperty(GCR_NAME) GCR(GCR_NAME),
  @JsonProperty(ECR_NAME) ECR(ECR_NAME),
  @JsonProperty(ArtifactSourceConstants.NEXUS3_REGISTRY_NAME)
  NEXUS3_REGISTRY(ArtifactSourceConstants.NEXUS3_REGISTRY_NAME),
  @JsonProperty(ArtifactSourceConstants.NEXUS2_REGISTRY_NAME)
  NEXUS2_REGISTRY(ArtifactSourceConstants.NEXUS2_REGISTRY_NAME),
  @JsonProperty(ArtifactSourceConstants.ARTIFACTORY_REGISTRY_NAME)
  ARTIFACTORY_REGISTRY(ArtifactSourceConstants.ARTIFACTORY_REGISTRY_NAME),
  @JsonProperty(CUSTOM_ARTIFACT_NAME) CUSTOM_ARTIFACT(CUSTOM_ARTIFACT_NAME),
  @JsonProperty(ACR_NAME) ACR(ACR_NAME),
  @JsonProperty(JENKINS_NAME) JENKINS(JENKINS_NAME),
  @JsonProperty(AMAZON_S3_NAME) AMAZONS3(AMAZON_S3_NAME),
  @JsonProperty(GOOGLE_ARTIFACT_REGISTRY_NAME) GOOGLE_ARTIFACT_REGISTRY(GOOGLE_ARTIFACT_REGISTRY_NAME),
  @JsonProperty(GITHUB_PACKAGES_NAME) GITHUB_PACKAGES(GITHUB_PACKAGES_NAME),
  @JsonProperty(AZURE_ARTIFACTS_NAME) AZURE_ARTIFACTS(AZURE_ARTIFACTS_NAME),
  @JsonProperty(AMI_ARTIFACTS_NAME) AMI(AMI_ARTIFACTS_NAME);

  private final String displayName;

  ArtifactSourceType(String displayName) {
    this.displayName = displayName;
  }

  @JsonValue
  public String getDisplayName() {
    return displayName;
  }
  @Override
  public String toString() {
    return displayName;
  }

  @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
  public static ArtifactSourceType getArtifactSourceType(@JsonProperty("type") String displayName) {
    for (ArtifactSourceType sourceType : ArtifactSourceType.values()) {
      if (sourceType.displayName.equalsIgnoreCase(displayName)) {
        return sourceType;
      }
    }
    throw new IllegalArgumentException(String.format(
        "Invalid value:%s, the expected values are: %s", displayName, Arrays.toString(ArtifactSourceType.values())));
  }
}
