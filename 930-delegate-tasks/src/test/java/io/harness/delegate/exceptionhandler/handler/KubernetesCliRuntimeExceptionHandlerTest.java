/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.exceptionhandler.handler;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.configuration.KubernetesCliCommandType.APPLY;
import static io.harness.configuration.KubernetesCliCommandType.DELETE;
import static io.harness.configuration.KubernetesCliCommandType.DRY_RUN;
import static io.harness.configuration.KubernetesCliCommandType.SCALE;
import static io.harness.configuration.KubernetesCliCommandType.STEADY_STATE_CHECK;
import static io.harness.rule.OwnerRule.ABHINAV2;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.task.k8s.exception.KubernetesCliRuntimeExceptionHandler;
import io.harness.delegate.task.k8s.exception.KubernetesExceptionHints;
import io.harness.exception.ExplanationException;
import io.harness.exception.HintException;
import io.harness.exception.KubernetesCliTaskRuntimeException;
import io.harness.exception.KubernetesTaskException;
import io.harness.exception.WingsException;
import io.harness.k8s.ProcessResponse;
import io.harness.rule.Owner;

import java.nio.charset.StandardCharsets;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.zeroturnaround.exec.ProcessOutput;
import org.zeroturnaround.exec.ProcessResult;

@OwnedBy(CDP)
public class KubernetesCliRuntimeExceptionHandlerTest extends CategoryTest {
  private KubernetesCliRuntimeExceptionHandler exceptionHandler = new KubernetesCliRuntimeExceptionHandler();

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void verifyHandledExceptions() {
    assertThat(KubernetesCliRuntimeExceptionHandler.exceptions())
        .containsExactly(KubernetesCliTaskRuntimeException.class);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void handleUnknownFieldInDryRunException() {
    KubernetesCliTaskRuntimeException exception =
        new KubernetesCliTaskRuntimeException(createProcessResponse(CliErrorMessages.UNKNOWN_FIELD_ERROR), DRY_RUN);
    WingsException handledException = exceptionHandler.handleException(exception);

    assertThat(handledException).isInstanceOf(HintException.class);
    assertThat(handledException.getMessage()).isEqualTo(KubernetesExceptionHints.VALIDATION_FAILED_UNKNOWN_FIELD);
    assertThat(handledException.getCause()).isInstanceOf(ExplanationException.class);
    assertThat(handledException.getCause().getMessage()).contains("Unknown field [Deployment.metadata:xyz]");
    assertThat(handledException.getCause().getCause()).isInstanceOf(ExplanationException.class);
    assertThat(handledException.getCause().getCause().getCause()).isInstanceOf(KubernetesTaskException.class);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void handleInvalidTypeInDryRunException() {
    KubernetesCliTaskRuntimeException exception =
        new KubernetesCliTaskRuntimeException(createProcessResponse(CliErrorMessages.INVALID_TYPE_VALUE), DRY_RUN);
    WingsException handledException = exceptionHandler.handleException(exception);
    assertThat(handledException).isInstanceOf(HintException.class);
    assertThat(handledException.getMessage()).isEqualTo(KubernetesExceptionHints.VALIDATION_FAILED_INVALID_TYPE);
    assertThat(handledException.getCause()).isInstanceOf(ExplanationException.class);
    assertThat(handledException.getCause().getMessage()).contains("Invalid type value for [Deployment.spec.replicas]");
    assertThat(handledException.getCause().getCause()).isInstanceOf(ExplanationException.class);
    assertThat(handledException.getCause().getCause().getCause()).isInstanceOf(KubernetesTaskException.class);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void handleDryRunException() {
    KubernetesCliTaskRuntimeException exception =
        new KubernetesCliTaskRuntimeException(createProcessResponse(CliErrorMessages.DUMMY_MESSAGE), DRY_RUN);
    WingsException handledException = exceptionHandler.handleException(exception);
    assertThat(handledException).isInstanceOf(HintException.class);
    assertThat(handledException.getMessage()).isEqualTo(KubernetesExceptionHints.DRY_RUN_MANIFEST_FAILED);
    assertThat(handledException.getCause()).isInstanceOf(ExplanationException.class);
    assertThat(handledException.getCause().getCause()).isInstanceOf(KubernetesTaskException.class);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void handleSteadyStateProgressDeadlineFailure() {
    KubernetesCliTaskRuntimeException exception = new KubernetesCliTaskRuntimeException(
        createProcessResponse(CliErrorMessages.STEADY_STATE_PROGRESS_DEADLINE_ERROR), STEADY_STATE_CHECK);
    WingsException handledException = exceptionHandler.handleException(exception);
    assertThat(handledException).isInstanceOf(HintException.class);
    assertThat(handledException.getMessage()).isEqualTo(KubernetesExceptionHints.WAIT_FOR_STEADY_STATE_FAILED);
    assertThat(handledException.getCause()).isInstanceOf(ExplanationException.class);
    assertThat(handledException.getCause().getMessage()).contains("Resources failed to reach steady state");
    assertThat(handledException.getCause().getCause()).isInstanceOf(ExplanationException.class);
    assertThat(handledException.getCause().getCause().getCause()).isInstanceOf(KubernetesTaskException.class);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void handleSteadyStateException() {
    KubernetesCliTaskRuntimeException exception = new KubernetesCliTaskRuntimeException(
        createProcessResponse(CliErrorMessages.DUMMY_MESSAGE), STEADY_STATE_CHECK);
    WingsException handledException = exceptionHandler.handleException(exception);
    assertThat(handledException).isInstanceOf(HintException.class);
    assertThat(handledException.getMessage()).isEqualTo(KubernetesExceptionHints.WAIT_FOR_STEADY_STATE_FAILED);
    assertThat(handledException.getCause()).isInstanceOf(ExplanationException.class);
    assertThat(handledException.getCause().getCause()).isInstanceOf(KubernetesTaskException.class);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void handleScaleMissingResourceException() {
    KubernetesCliTaskRuntimeException exception =
        new KubernetesCliTaskRuntimeException(createProcessResponse(CliErrorMessages.SCALE_MISSING_RESOURCE), SCALE);
    WingsException handledException = exceptionHandler.handleException(exception);
    assertThat(handledException).isInstanceOf(HintException.class);
    assertThat(handledException.getMessage()).isEqualTo(KubernetesExceptionHints.SCALE_CLI_FAILED);
    assertThat(handledException.getCause()).isInstanceOf(ExplanationException.class);
    assertThat(handledException.getCause().getMessage()).contains("Failed to scale resource [abc]");
    assertThat(handledException.getCause().getCause()).isInstanceOf(ExplanationException.class);
    assertThat(handledException.getCause().getCause().getCause()).isInstanceOf(KubernetesTaskException.class);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void handleScaleException() {
    KubernetesCliTaskRuntimeException exception =
        new KubernetesCliTaskRuntimeException(createProcessResponse(CliErrorMessages.DUMMY_MESSAGE), SCALE);
    WingsException handledException = exceptionHandler.handleException(exception);
    assertThat(handledException).isInstanceOf(HintException.class);
    assertThat(handledException.getMessage()).isEqualTo(KubernetesExceptionHints.SCALE_CLI_FAILED_GENERIC);
    assertThat(handledException.getCause()).isInstanceOf(ExplanationException.class);
    assertThat(handledException.getCause().getCause()).isInstanceOf(KubernetesTaskException.class);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void handleInvalidCharactersError() {
    KubernetesCliTaskRuntimeException exception =
        new KubernetesCliTaskRuntimeException(createProcessResponse(CliErrorMessages.INVALID_CHARACTERS_ERROR), APPLY);
    WingsException handledException = exceptionHandler.handleException(exception);
    assertThat(handledException).isInstanceOf(HintException.class);
    assertThat(handledException.getMessage()).isEqualTo(KubernetesExceptionHints.K8S_CHARACTER_ERROR);
    assertThat(handledException.getCause()).isInstanceOf(ExplanationException.class);
    assertThat(handledException.getCause().getMessage()).contains("Deployment/Pqr");
    assertThat(handledException.getCause().getCause()).isInstanceOf(ExplanationException.class);
    assertThat(handledException.getCause().getCause().getCause()).isInstanceOf(KubernetesTaskException.class);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void handleApplyException() {
    KubernetesCliTaskRuntimeException exception =
        new KubernetesCliTaskRuntimeException(createProcessResponse(CliErrorMessages.DUMMY_MESSAGE), APPLY);
    WingsException handledException = exceptionHandler.handleException(exception);
    assertThat(handledException).isInstanceOf(HintException.class);
    assertThat(handledException.getMessage()).isEqualTo(KubernetesExceptionHints.APPLY_MANIFEST_FAILED);
    assertThat(handledException.getCause()).isInstanceOf(ExplanationException.class);
    assertThat(handledException.getCause().getCause()).isInstanceOf(KubernetesTaskException.class);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void handleGenericException() {
    KubernetesCliTaskRuntimeException exception =
        new KubernetesCliTaskRuntimeException(createProcessResponse(CliErrorMessages.DUMMY_MESSAGE), DELETE);
    WingsException handledException = exceptionHandler.handleException(exception);
    assertThat(handledException).isInstanceOf(HintException.class);
    assertThat(handledException.getMessage()).isEqualTo(KubernetesExceptionHints.GENERIC_CLI_FAILURE);
    assertThat(handledException.getCause()).isInstanceOf(ExplanationException.class);
    assertThat(handledException.getCause().getCause()).isInstanceOf(KubernetesTaskException.class);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void handleMultipleNamingExceptions() {
    KubernetesCliTaskRuntimeException exception =
        new KubernetesCliTaskRuntimeException(createProcessResponse(CliErrorMessages.MULTIPLE_NAMING_ERRORS), APPLY);
    WingsException handledException = exceptionHandler.handleException(exception);
    assertThat(handledException).isInstanceOf(HintException.class);
    assertThat(handledException.getMessage()).isEqualTo(KubernetesExceptionHints.K8S_CHARACTER_ERROR);
    assertThat(handledException.getCause()).isInstanceOf(ExplanationException.class);
    assertThat(handledException.getCause().getMessage()).contains("Secret/InvalidSecret");
    assertThat(handledException.getCause().getMessage()).contains("ConfigMap/InvalidConfigmap");
    assertThat(handledException.getCause().getMessage()).contains("Service/InvalidService");
    assertThat(handledException.getCause().getCause()).isInstanceOf(ExplanationException.class);
    assertThat(handledException.getCause().getCause().getCause()).isInstanceOf(KubernetesTaskException.class);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void handleUnresolvedFieldError() {
    KubernetesCliTaskRuntimeException exception =
        new KubernetesCliTaskRuntimeException(createProcessResponse(CliErrorMessages.UNRESOLVED_VALUE_ERROR), APPLY);
    WingsException handledException = exceptionHandler.handleException(exception);
    assertThat(handledException).isInstanceOf(HintException.class);
    assertThat(handledException.getMessage()).isEqualTo(KubernetesExceptionHints.UNRESOLVED_MANIFEST_FIELD);
    assertThat(handledException.getCause()).isInstanceOf(ExplanationException.class);
    assertThat(handledException.getCause().getCause()).isInstanceOf(ExplanationException.class);
    assertThat(handledException.getCause().getCause().getCause()).isInstanceOf(KubernetesTaskException.class);
  }

  private ProcessResponse createProcessResponse(String cliErrorMessage) {
    return ProcessResponse.builder()
        .processResult(new ProcessResult(1, new ProcessOutput("process failed.".getBytes(StandardCharsets.UTF_8))))
        .errorMessage(cliErrorMessage)
        .build();
  }
  static class CliErrorMessages {
    private CliErrorMessages() {}
    static final String DUMMY_MESSAGE = "Some random error message";
    static final String UNKNOWN_FIELD_ERROR =
        "error validating data: ValidationError(Deployment.metadata): unknown field \"xyz\" in io.k8s.apimachinery.pkg.apis.meta.v1.ObjectMeta; if you choose to ignore these errors, turn validation off with --validate=false";
    static final String INVALID_TYPE_VALUE =
        "error validating data: ValidationError(Deployment.spec.replicas): invalid type for io.k8s.api.apps.v1.DeploymentSpec.replicas: got \"string\", expected \"integer\"; if you choose to ignore these errors, turn validation off with --validate=false";
    static final String STEADY_STATE_PROGRESS_DEADLINE_ERROR =
        "error: deployment \"abc\" exceeded its progress deadline";
    static final String SCALE_MISSING_RESOURCE = "deployments.apps \"abc\" not found";
    static final String INVALID_CHARACTERS_ERROR =
        "The Deployment \"Pqr\" is invalid: metadata.name: Invalid value: \"Pqr\": a DNS-1123 subdomain must consist of lower case alphanumeric characters, '-' or '.', and must start and end with an alphanumeric character (e.g. 'example.com', regex used for validation is '[a-z0-9]([-a-z0-9]*[a-z0-9])?(\\.[a-z0-9]([-a-z0-9]*[a-z0-9])?)*')";
    static final String MULTIPLE_NAMING_ERRORS =
        "Error from server (Invalid): error when creating \"manifests.yaml\": Secret \"InvalidSecret\" is invalid: metadata.name: Invalid value: \"InvalidSecret\": a DNS-1123 subdomain must consist of lower case alphanumeric characters, '-' or '.', and must start and end with an alphanumeric character (e.g. 'example.com', regex used for validation is '[a-z0-9]([-a-z0-9]*[a-z0-9])?(\\.[a-z0-9]([-a-z0-9]*[a-z0-9])?)*')Error from server (Invalid): error when creating \"manifests.yaml\": ConfigMap \"InvalidConfigmap\" is invalid: metadata.name: Invalid value: \"InvalidConfigmap\": a DNS-1123 subdomain must consist of lower case alphanumeric characters, '-' or '.', and must start and end with an alphanumeric character (e.g. 'example.com', regex used for validation is '[a-z0-9]([-a-z0-9]*[a-z0-9])?(\\.[a-z0-9]([-a-z0-9]*[a-z0-9])?)*')Error from server (Invalid): error when creating \"manifests.yaml\": Service \"InvalidService\" is invalid: [metadata.name: Invalid value: \"InvalidService\": a DNS-1035 label must consist of lower case alphanumeric characters or '-', start with an alphabetic character, and end with an alphanumeric character (e.g. 'my-name',  or 'abc-123', regex used for validation is '[a-z]([-a-z0-9]*[a-z0-9])?')";
    static final String UNRESOLVED_VALUE_ERROR =
        "Error from server (Invalid): error when creating \"manifests.yaml\": Secret \"<no value>-1\" is invalid: metadata.name: Invalid value: \"<no value>-1\": a DNS-1123 subdomain must consist of lower case alphanumeric characters, '-' or '.', and must start and end with an alphanumeric character (e.g. 'example.com', regex used for validation is '[a-z0-9]([-a-z0-9]*[a-z0-9])?(\\.[a-z0-9]([-a-z0-9]*[a-z0-9])?)*')";
  }
}
