/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.citasks.vm.helper;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.ci.pod.ImageDetailsWithConnector;
import io.harness.delegate.beans.ci.vm.VmTaskExecutionResponse;
import io.harness.delegate.beans.ci.vm.runner.ExecuteStepRequest;
import io.harness.delegate.beans.ci.vm.runner.ExecuteStepResponse;
import io.harness.delegate.task.citasks.cik8handler.ImageCredentials;
import io.harness.delegate.task.citasks.cik8handler.ImageSecretBuilder;
import io.harness.delegate.task.citasks.cik8handler.SecretSpecBuilder;
import io.harness.k8s.model.ImageDetails;
import io.harness.logging.CommandExecutionStatus;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import retrofit2.Response;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.CI)
public class StepExecutionHelper {
  @Inject private ImageSecretBuilder imageSecretBuilder;
  @Inject private HttpHelper httpHelper;
  @Inject private SecretSpecBuilder secretSpecBuilder;
  public static final String IMAGE_PATH_SPLIT_REGEX = ":";

  public ExecuteStepRequest.ImageAuth getImageAuth(String image, ConnectorDetails imageConnector) {
    if (!StringUtils.isEmpty(image)) {
      ImageDetails imageInfo = getImageInfo(image);
      ImageDetailsWithConnector.builder().imageDetails(imageInfo).imageConnectorDetails(imageConnector).build();
      ImageCredentials imageCredentials = imageSecretBuilder.getImageCredentials(
          ImageDetailsWithConnector.builder().imageConnectorDetails(imageConnector).imageDetails(imageInfo).build());
      if (imageCredentials != null) {
        return ExecuteStepRequest.ImageAuth.builder()
            .address(imageCredentials.getRegistryUrl())
            .password(imageCredentials.getPassword())
            .username(imageCredentials.getUserName())
            .build();
      }
    }
    return null;
  }

  public VmTaskExecutionResponse callRunnerForStepExecution(ExecuteStepRequest request) {
    try {
      Response<ExecuteStepResponse> response = httpHelper.executeStepWithRetries(request);
      if (!response.isSuccessful()) {
        return VmTaskExecutionResponse.builder().commandExecutionStatus(CommandExecutionStatus.FAILURE).build();
      }

      if (isEmpty(response.body().getError())) {
        return VmTaskExecutionResponse.builder()
            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .outputVars(response.body().getOutputs())
            .build();
      } else {
        return VmTaskExecutionResponse.builder()
            .commandExecutionStatus(CommandExecutionStatus.FAILURE)
            .errorMessage(response.body().getError())
            .build();
      }
    } catch (Exception e) {
      log.error("Failed to execute step in runner", e);
      return VmTaskExecutionResponse.builder()
          .commandExecutionStatus(CommandExecutionStatus.FAILURE)
          .errorMessage(e.toString())
          .build();
    }
  }

  private ImageDetails getImageInfo(String image) {
    String tag = "";
    String name = image;

    if (image.contains(IMAGE_PATH_SPLIT_REGEX)) {
      String[] subTokens = image.split(IMAGE_PATH_SPLIT_REGEX);
      if (subTokens.length > 1) {
        tag = subTokens[subTokens.length - 1];
        String[] nameparts = Arrays.copyOf(subTokens, subTokens.length - 1);
        name = String.join(IMAGE_PATH_SPLIT_REGEX, nameparts);
      }
    }

    return ImageDetails.builder().name(name).tag(tag).build();
  }

  public List<ExecuteStepRequest.VolumeMount> getVolumeMounts(Map<String, String> volToMountPath) {
    List<ExecuteStepRequest.VolumeMount> volumeMounts = new ArrayList<>();
    if (isEmpty(volToMountPath)) {
      return volumeMounts;
    }

    for (Map.Entry<String, String> entry : volToMountPath.entrySet()) {
      volumeMounts.add(ExecuteStepRequest.VolumeMount.builder().name(entry.getKey()).path(entry.getValue()).build());
    }
    return volumeMounts;
  }
}
