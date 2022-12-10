/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.azure.exception;

import static io.harness.azure.model.AzureConstants.DEPLOY_TO_SLOT;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.FailureType;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.WingsException;
import io.harness.exception.exceptionmanager.exceptionhandler.ExceptionHandler;
import io.harness.exception.ngexception.AzureAppServiceTaskException;
import io.harness.exception.runtime.azure.AzureAppServicesDeployArtifactFileException;
import io.harness.exception.runtime.azure.AzureAppServicesDeploymentSlotNotFoundException;
import io.harness.exception.runtime.azure.AzureAppServicesRuntimeException;
import io.harness.exception.runtime.azure.AzureAppServicesSlotSteadyStateException;
import io.harness.exception.runtime.azure.AzureAppServicesWebAppNotFoundException;
import io.harness.reflection.ReflectionUtils;

import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.UncheckedTimeoutException;
import java.net.SocketTimeoutException;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Request;
import retrofit2.HttpException;

@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class AzureAppServicesRuntimeExceptionHandler implements ExceptionHandler {
  private static final Pattern PORT_DIDNT_RESPOND_PATTERN =
      Pattern.compile("Container ([A-z-_0-9]+) didn't respond to HTTP pings on port: (\\d+)");
  private static final Pattern STOPPING_SITE_CONTAINER_PATTERN =
      Pattern.compile("Stopping site ([A-z-_0-9]+) because it failed during startup");

  private static final String HINT_WEBAPP_NOT_FOUND =
      "Check to make sure web app '%s' exists under resource group '%s'";
  private static final String HINT_DEPLOYMENT_SLOT_NOT_FOUND =
      "Check to make sure you have provided correct deployment slot '%s' for web app '%s'";
  private static final String HINT_INVALID_ARTIFACT_TYPE = "Check if deployed artifact '%s' is packaged '%s' file";
  private static final String HINT_CONFIGURE_WEBSISTES_PORT =
      "If container listens to a different port than 80 or 8080 configure WEBSISTES_PORT in application settings";
  private static final String HINT_CHECK_CONTAINER_LOGS = "Check container logs for more details";
  private static final String HINT_CONTAINER_FAILED_DURING_STARTUP =
      "Verify docker image configuration and credentials";
  private static final String HINT_CONFIGURE_TIMEOUT =
      "If deployment is taking more time than configured timeout of %d minutes increase step timeout";
  private static final String HINT_CONNECTION_ISSUE_KUDU_SERVICE =
      "Check if application kudu service is available from delegate. For example if your application URL is http://mysite.azurewebsites.net/ then  kudu service URL would be https://mysite.scm.azurewebsites.net/";
  private static final String HINT_GENERIC_ACTION_STEADY_STATE_FAILED =
      "Check slot deployment logs for more details and application configuration";

  private static final String EXPLANATION_WEBAPP_NOT_FOUND =
      "Unable to find web app with name '%s' in resource group '%s'";
  private static final String EXPLANATION_DEPLOYMENT_SLOT_NOT_FOUND =
      "Unable to find deployment slot '%s' for web app '%s', resource group '%s' and subscription '%s'";
  private static final String EXPLANATION_DEPLOY_ARTIFACT_REQUEST_FAILED =
      "HTTP request %s %s failed with error code '%d' while uploading artifact file '%s'";
  private static final String EXPLANATION_PORT_DIDNT_RESPOND = "Container %s didn't response to HTTP pings on port: %s";
  private static final String EXPLANATION_CONTAINER_FAILS_START = "Container failed to start with error log: %s";
  private static final String EXPLANATION_STOPPING_CONTAINER =
      "Site container was stopped because it failed during startup";
  private static final String EXPLANATION_STEADY_STATE_CHECK_TIMEOUT =
      "Configured timeout of %d minutes expired for operation %s";
  private static final String EXPLANATION_GENERIC_ARTIFACT_DEPLOY_FAILED =
      "Failed to upload artifact file to Kudu service";
  private static final String EXPLANATION_CONNECTION_ISSUE_KUDU_SERVICE =
      "Timed out while trying to connect to application kudu service to upload artifact file";
  private static final String EXPLANATION_GENERIC_ACTION_STEADY_STATE_FAILED =
      "Operation %s failed while waiting for operation to complete";

  public static Set<Class<? extends Exception>> exceptions() {
    return ImmutableSet.<Class<? extends Exception>>builder().add(AzureAppServicesRuntimeException.class).build();
  }

  @Override
  public WingsException handleException(Exception exception) {
    if (exception instanceof AzureAppServicesWebAppNotFoundException) {
      AzureAppServicesWebAppNotFoundException notFoundException = (AzureAppServicesWebAppNotFoundException) exception;
      return NestedExceptionUtils.hintWithExplanationException(
          format(HINT_WEBAPP_NOT_FOUND, notFoundException.getWebAppName(), notFoundException.getResourceGroup()),
          format(EXPLANATION_WEBAPP_NOT_FOUND, notFoundException.getWebAppName(), notFoundException.getResourceGroup()),
          new AzureAppServiceTaskException(exception.getMessage()));
    } else if (exception instanceof AzureAppServicesDeploymentSlotNotFoundException) {
      AzureAppServicesDeploymentSlotNotFoundException slotNotFoundException =
          (AzureAppServicesDeploymentSlotNotFoundException) exception;
      return NestedExceptionUtils.hintWithExplanationException(
          format(HINT_DEPLOYMENT_SLOT_NOT_FOUND, slotNotFoundException.getSlotName(),
              slotNotFoundException.getWebAppName()),
          format(EXPLANATION_DEPLOYMENT_SLOT_NOT_FOUND, slotNotFoundException.getSlotName(),
              slotNotFoundException.getWebAppName(), slotNotFoundException.getResourceGroup(),
              slotNotFoundException.getSubscriptionId()),
          new AzureAppServiceTaskException(exception.getMessage()));
    } else if (exception instanceof AzureAppServicesDeployArtifactFileException) {
      AzureAppServicesDeployArtifactFileException artifactFileException =
          (AzureAppServicesDeployArtifactFileException) exception;
      return mapDeployArtifactFileException(artifactFileException);
    } else if (exception instanceof AzureAppServicesSlotSteadyStateException) {
      AzureAppServicesSlotSteadyStateException slotSteadyStateException =
          (AzureAppServicesSlotSteadyStateException) exception;
      return mapSlotSteadyStateException(slotSteadyStateException);
    }

    return new AzureAppServiceTaskException(exception.getMessage());
  }

  private WingsException mapDeployArtifactFileException(
      AzureAppServicesDeployArtifactFileException artifactFileException) {
    HttpException retrofitHttpException = ExceptionUtils.cause(HttpException.class, artifactFileException.getCause());
    if (retrofitHttpException != null) {
      resetExceptionCause(artifactFileException);
      if (retrofitHttpException.response() != null && retrofitHttpException.response().raw() != null) {
        Request request = retrofitHttpException.response().raw().request();
        String url = request.url().toString();
        String method = request.method();
        int errorCode = retrofitHttpException.code();
        return NestedExceptionUtils.hintWithExplanationException(
            format(HINT_INVALID_ARTIFACT_TYPE, artifactFileException.getArtifactFilePath(),
                artifactFileException.getArtifactType()),
            format(EXPLANATION_DEPLOY_ARTIFACT_REQUEST_FAILED, method, url, errorCode,
                artifactFileException.getArtifactFilePath()),
            new AzureAppServiceTaskException(artifactFileException.getMessage()));
      }
    }

    SocketTimeoutException socketTimeoutException =
        ExceptionUtils.cause(SocketTimeoutException.class, artifactFileException);
    if (socketTimeoutException != null) {
      resetExceptionCause(artifactFileException);
      return NestedExceptionUtils.hintWithExplanationException(HINT_CONNECTION_ISSUE_KUDU_SERVICE,
          EXPLANATION_CONNECTION_ISSUE_KUDU_SERVICE,
          new AzureAppServiceTaskException(artifactFileException.getMessage(), FailureType.TIMEOUT));
    }

    return NestedExceptionUtils.hintWithExplanationException(
        format(HINT_INVALID_ARTIFACT_TYPE, artifactFileException.getArtifactFilePath(),
            artifactFileException.getArtifactType()),
        artifactFileException.getCause() != null ? artifactFileException.getCause().getMessage()
                                                 : EXPLANATION_GENERIC_ARTIFACT_DEPLOY_FAILED,
        new AzureAppServiceTaskException(artifactFileException.getMessage()));
  }

  private WingsException mapSlotSteadyStateException(
      AzureAppServicesSlotSteadyStateException slotSteadyStateException) {
    if (slotSteadyStateException.getCause() != null
        && slotSteadyStateException.getCause() instanceof UncheckedTimeoutException) {
      resetExceptionCause(slotSteadyStateException);
      return NestedExceptionUtils.hintWithExplanationException(
          format(HINT_CONFIGURE_TIMEOUT, slotSteadyStateException.getTimeoutIntervalInMin()),
          format(EXPLANATION_STEADY_STATE_CHECK_TIMEOUT, slotSteadyStateException.getTimeoutIntervalInMin(),
              slotSteadyStateException.getAction()),
          new AzureAppServiceTaskException(slotSteadyStateException.getMessage(), FailureType.TIMEOUT));
    }

    if (DEPLOY_TO_SLOT.equals(slotSteadyStateException.getAction())) {
      return mapContainerSlotSteadyStateException(slotSteadyStateException);
    } else {
      return mapActionBasedSlotSteadyStateException(slotSteadyStateException);
    }
  }

  private WingsException mapContainerSlotSteadyStateException(
      AzureAppServicesSlotSteadyStateException slotSteadyStateException) {
    Matcher portErrorMatcher = PORT_DIDNT_RESPOND_PATTERN.matcher(slotSteadyStateException.getMessage());
    if (portErrorMatcher.find()) {
      String containerName = portErrorMatcher.group(1);
      String port = portErrorMatcher.group(2);
      return NestedExceptionUtils.hintWithExplanationException(HINT_CONFIGURE_WEBSISTES_PORT,
          format(EXPLANATION_PORT_DIDNT_RESPOND, containerName, port),
          new AzureAppServiceTaskException(slotSteadyStateException.getMessage()));
    }

    Matcher stoppingContainerMatcher = STOPPING_SITE_CONTAINER_PATTERN.matcher(slotSteadyStateException.getMessage());
    if (stoppingContainerMatcher.find()) {
      return NestedExceptionUtils.hintWithExplanationException(HINT_CONTAINER_FAILED_DURING_STARTUP,
          EXPLANATION_STOPPING_CONTAINER, new AzureAppServiceTaskException(slotSteadyStateException.getMessage()));
    }

    return NestedExceptionUtils.hintWithExplanationException(HINT_CHECK_CONTAINER_LOGS,
        format(EXPLANATION_CONTAINER_FAILS_START, slotSteadyStateException.getMessage()),
        new AzureAppServiceTaskException(slotSteadyStateException.getMessage()));
  }

  private WingsException mapActionBasedSlotSteadyStateException(
      AzureAppServicesSlotSteadyStateException slotSteadyStateException) {
    return NestedExceptionUtils.hintWithExplanationException(HINT_GENERIC_ACTION_STEADY_STATE_FAILED,
        format(EXPLANATION_GENERIC_ACTION_STEADY_STATE_FAILED, slotSteadyStateException.getAction()),
        new AzureAppServiceTaskException(slotSteadyStateException.getMessage()));
  }

  private void resetExceptionCause(Throwable exception) {
    try {
      ReflectionUtils.setObjectField(ReflectionUtils.getFieldByName(exception.getClass(), "cause"), exception, null);
    } catch (Exception e) {
      log.error("Failed to reset exception cause", e);
    }
  }
}
