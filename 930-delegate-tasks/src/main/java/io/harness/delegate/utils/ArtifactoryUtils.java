/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.utils;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.encoding.EncodingUtils.encodeBase64;
import static io.harness.delegate.task.ssh.exception.SshExceptionConstants.EMPTY_ARTIFACT_PATH;
import static io.harness.delegate.task.ssh.exception.SshExceptionConstants.EMPTY_ARTIFACT_PATH_EXPLANATION;
import static io.harness.delegate.task.ssh.exception.SshExceptionConstants.EMPTY_ARTIFACT_PATH_HINT;
import static io.harness.logging.LogLevel.ERROR;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.artifactory.ArtifactoryConfigRequest;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryConnectorDTO;
import io.harness.delegate.task.artifactory.ArtifactoryRequestMapper;
import io.harness.delegate.task.ssh.artifact.ArtifactoryArtifactDelegateConfig;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.runtime.SshCommandExecutionException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.security.encryption.SecretDecryptionService;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@UtilityClass
@Slf4j
@OwnedBy(CDP)
public class ArtifactoryUtils {
  public static ArtifactoryConfigRequest getArtifactConfigRequest(
      ArtifactoryArtifactDelegateConfig artifactoryArtifactConfig, LogCallback logCallback,
      SecretDecryptionService secretDecryptionService, ArtifactoryRequestMapper artifactoryRequestMapper) {
    if (EmptyPredicate.isEmpty(artifactoryArtifactConfig.getArtifactPath())) {
      logCallback.saveExecutionLog(
          "artifactPath or artifactPathFilter is blank", ERROR, CommandExecutionStatus.FAILURE);
      throw NestedExceptionUtils.hintWithExplanationException(EMPTY_ARTIFACT_PATH_HINT,
          format(EMPTY_ARTIFACT_PATH_EXPLANATION, artifactoryArtifactConfig.getIdentifier()),
          new SshCommandExecutionException(EMPTY_ARTIFACT_PATH));
    }
    ArtifactoryConnectorDTO artifactoryConnectorDTO =
        (ArtifactoryConnectorDTO) artifactoryArtifactConfig.getConnectorDTO().getConnectorConfig();
    secretDecryptionService.decrypt(
        artifactoryConnectorDTO.getAuth().getCredentials(), artifactoryArtifactConfig.getEncryptedDataDetails());
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(
        artifactoryConnectorDTO, artifactoryArtifactConfig.getEncryptedDataDetails());

    return artifactoryRequestMapper.toArtifactoryRequest(artifactoryConnectorDTO);
  }

  public static ArtifactoryConfigRequest getArtifactConfigRequest(
      ArtifactoryArtifactDelegateConfig artifactoryArtifactConfig, SecretDecryptionService secretDecryptionService,
      ArtifactoryRequestMapper artifactoryRequestMapper) {
    return getArtifactConfigRequest(artifactoryArtifactConfig, null, secretDecryptionService, artifactoryRequestMapper);
  }

  public static String getArtifactFileName(String artifactPath) {
    String artifactFileName = artifactPath;
    int lastIndexOfSlash = artifactFileName.lastIndexOf('/');
    if (lastIndexOfSlash > 0) {
      artifactFileName = artifactFileName.substring(lastIndexOfSlash + 1);
      log.info("Got filename: " + artifactFileName);
    }
    return artifactFileName;
  }

  public static String getAuthHeader(ArtifactoryConfigRequest artifactoryConfigRequest) {
    String authHeader = null;
    if (artifactoryConfigRequest.isHasCredentials()) {
      String pair = artifactoryConfigRequest.getUsername() + ":" + new String(artifactoryConfigRequest.getPassword());
      authHeader = "Basic " + encodeBase64(pair);
    }
    return authHeader;
  }

  public static String getArtifactoryUrl(ArtifactoryConfigRequest artifactoryConfigRequest, String artifactPath) {
    String url = artifactoryConfigRequest.getArtifactoryUrl().trim();
    if (!url.endsWith("/")) {
      url += "/";
    }
    return url + artifactPath;
  }
}
