/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.k8s.exception;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.KubernetesCliTaskRuntimeException;
import io.harness.exception.KubernetesTaskException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.WingsException;
import io.harness.exception.exceptionmanager.exceptionhandler.ExceptionHandler;
import io.harness.k8s.ProcessResponse;

import com.google.common.collect.ImmutableSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import org.zeroturnaround.exec.ProcessResult;

@OwnedBy(CDP)
public class KubernetesCliRuntimeExceptionHandler implements ExceptionHandler {
  private static final String CLIENT_TOOLS_DIRECTORY_NAME = "client-tools";
  private static final String STEADY_STATE_FAILURE_DEADLINE_ERROR = "exceeded its progress deadline";
  private static final String KUBECTL_APPLY_CONSOLE_ERROR = "Apply manifest failed with error:\n%s";
  private static final String KUBECTL_DRY_RUN_CONSOLE_ERROR = "Dry run manifest failed with error:\n%s";
  private static final String KUBECTL_STEADY_STATE_CONSOLE_ERROR = "Steady state check failed with error:\n%s";
  private static final String KUBECTL_SCALE_CONSOLE_ERROR = "Failed to scale resource(s) with error:\n%s";
  private static final String UNRESOLVED_VALUE = "<no value>";

  private static final String INVALID_RESOURCE_REGEX = "((\\S+) \"([^\"]*)\" is invalid:)";
  private static final String RESOURCE_NOT_FOUND_REGEX = ".* \"(.*?)\" not found.*";
  private static final String INVALID_TYPE_VALUE_REGEX = ".* ValidationError\\((.*?)\\).*invalid type.*";
  private static final String UNKNOWN_FIELD_REGEX = ".* ValidationError\\((.*?)\\).*unknown field \"(.*?)\".*";

  private static final Pattern INVALID_RESOURCES_PATTERN = Pattern.compile(INVALID_RESOURCE_REGEX, Pattern.MULTILINE);
  private static final Pattern RESOURCE_NOT_FOUND_ERROR_PATTERN =
      Pattern.compile(RESOURCE_NOT_FOUND_REGEX, Pattern.MULTILINE);
  private static final Pattern INVALID_TYPE_ERROR_PATTERN =
      Pattern.compile(INVALID_TYPE_VALUE_REGEX, Pattern.MULTILINE);
  private static final Pattern UNKNOWN_FIELD_ERROR_PATTERN = Pattern.compile(UNKNOWN_FIELD_REGEX, Pattern.MULTILINE);

  public static Set<Class<? extends Exception>> exceptions() {
    return ImmutableSet.of(KubernetesCliTaskRuntimeException.class);
  }

  @Override
  public WingsException handleException(Exception exception) {
    KubernetesCliTaskRuntimeException kubernetesTaskException = (KubernetesCliTaskRuntimeException) exception;
    String cliErrorMessage = kubernetesTaskException.getProcessResponse().getErrorMessage();

    // handle some common errors
    if (cliErrorMessage.contains(UNRESOLVED_VALUE)) {
      return getExplanationExceptionWithCommand(KubernetesExceptionHints.UNRESOLVED_MANIFEST_FIELD,
          KubernetesExceptionExplanation.UNRESOLVED_MANIFEST_FIELD,
          getExecutedCommandWithOutputWithExitCode(kubernetesTaskException), cliErrorMessage);
    }

    switch (kubernetesTaskException.getCommandType()) {
      case APPLY:
        return handleApplyManifestException(kubernetesTaskException);
      case SCALE:
        return handleScalingException(kubernetesTaskException);
      case DRY_RUN:
        return handleDryRunException(kubernetesTaskException);
      case STEADY_STATE_CHECK:
        return handleSteadyStateCheckFailure(kubernetesTaskException);
      default:
        return getExplanationException(KubernetesExceptionHints.GENERIC_CLI_FAILURE,
            getExecutedCommandWithOutputWithExitCode(kubernetesTaskException), cliErrorMessage);
    }
  }

  private WingsException handleApplyManifestException(KubernetesCliTaskRuntimeException kubernetesTaskException) {
    String cliErrorMessage = kubernetesTaskException.getProcessResponse().getErrorMessage();
    String consolidatedError = format(KUBECTL_APPLY_CONSOLE_ERROR, cliErrorMessage);

    if (cliErrorMessage.matches(KubernetesExceptionMessages.CHARACTER_LIMIT_ERROR)
        || cliErrorMessage.matches(KubernetesExceptionMessages.INVALID_CHARACTERS_ERROR)) {
      List<String> invalidResourceNames = getAllResourceNames(cliErrorMessage, INVALID_RESOURCES_PATTERN);
      String explanation =
          format(KubernetesExceptionExplanation.K8S_CHARACTER_ERROR, String.join("\n", invalidResourceNames));
      return getExplanationExceptionWithCommand(KubernetesExceptionHints.K8S_CHARACTER_ERROR, explanation,
          getExecutedCommandWithOutputWithExitCode(kubernetesTaskException), consolidatedError);
    }
    return getExplanationException(KubernetesExceptionHints.APPLY_MANIFEST_FAILED,
        getExecutedCommandWithOutputWithExitCode(kubernetesTaskException), consolidatedError);
  }

  private WingsException handleScalingException(KubernetesCliTaskRuntimeException kubernetesTaskException) {
    String cliErrorMessage = kubernetesTaskException.getProcessResponse().getErrorMessage();
    String consolidatedError = format(KUBECTL_SCALE_CONSOLE_ERROR, cliErrorMessage);

    if (cliErrorMessage.matches(RESOURCE_NOT_FOUND_REGEX)) {
      List<String> extractedValues = extractValuesFromFirstMatch(cliErrorMessage, RESOURCE_NOT_FOUND_ERROR_PATTERN, 1);
      String resourceName = extractedValues.isEmpty() ? "" : extractedValues.get(0);
      return getExplanationExceptionWithCommand(KubernetesExceptionHints.SCALE_CLI_FAILED,
          format(KubernetesExceptionExplanation.SCALE_CLI_FAILED, resourceName),
          getExecutedCommandWithOutputWithExitCode(kubernetesTaskException), consolidatedError);
    }
    return getExplanationException(KubernetesExceptionHints.SCALE_CLI_FAILED_GENERIC,
        getExecutedCommandWithOutputWithExitCode(kubernetesTaskException), consolidatedError);
  }

  private WingsException handleDryRunException(KubernetesCliTaskRuntimeException kubernetesTaskException) {
    String cliErrorMessage = kubernetesTaskException.getProcessResponse().getErrorMessage();
    String consolidatedError = format(KUBECTL_DRY_RUN_CONSOLE_ERROR, cliErrorMessage);

    if (cliErrorMessage.matches(INVALID_TYPE_VALUE_REGEX)) {
      List<String> extractedValues = extractValuesFromFirstMatch(cliErrorMessage, INVALID_TYPE_ERROR_PATTERN, 1);
      String invalidFieldName = extractedValues.isEmpty() ? "" : extractedValues.get(0);
      return getExplanationExceptionWithCommand(KubernetesExceptionHints.VALIDATION_FAILED_INVALID_TYPE,
          format(KubernetesExceptionExplanation.VALIDATION_FAILED_INVALID_TYPE, invalidFieldName),
          getExecutedCommandWithOutputWithExitCode(kubernetesTaskException), consolidatedError);
    }
    if (cliErrorMessage.matches(UNKNOWN_FIELD_REGEX)) {
      List<String> extractedValues = extractValuesFromFirstMatch(cliErrorMessage, UNKNOWN_FIELD_ERROR_PATTERN, 2);
      String unknownField = "";
      if (extractedValues.size() == 2) {
        unknownField = String.join(":", extractedValues);
      }
      return getExplanationExceptionWithCommand(KubernetesExceptionHints.VALIDATION_FAILED_UNKNOWN_FIELD,
          format(KubernetesExceptionExplanation.VALIDATION_FAILED_UNKNOWN_FIELD, unknownField),
          getExecutedCommandWithOutputWithExitCode(kubernetesTaskException), consolidatedError);
    }
    return getExplanationException(KubernetesExceptionHints.DRY_RUN_MANIFEST_FAILED,
        getExecutedCommandWithOutputWithExitCode(kubernetesTaskException), consolidatedError);
  }

  private WingsException handleSteadyStateCheckFailure(KubernetesCliTaskRuntimeException kubernetesTaskException) {
    String cliErrorMessage = kubernetesTaskException.getProcessResponse().getErrorMessage();
    String consolidatedError = format(KUBECTL_STEADY_STATE_CONSOLE_ERROR, cliErrorMessage);

    if (cliErrorMessage.contains(STEADY_STATE_FAILURE_DEADLINE_ERROR)) {
      return getExplanationExceptionWithCommand(KubernetesExceptionHints.WAIT_FOR_STEADY_STATE_FAILED,
          KubernetesExceptionExplanation.WAIT_FOR_STEADY_STATE_FAILED,
          getExecutedCommandWithOutputWithExitCode(kubernetesTaskException), consolidatedError);
    }

    return getExplanationException(KubernetesExceptionHints.WAIT_FOR_STEADY_STATE_FAILED,
        getExecutedCommandWithOutputWithExitCode(kubernetesTaskException), consolidatedError);
  }

  private String getExecutedCommandWithOutputWithExitCode(KubernetesCliTaskRuntimeException exception) {
    ProcessResponse processResponse = exception.getProcessResponse();
    ProcessResult processResult = processResponse.getProcessResult();
    String kubectlPath = getRelativeKubectlClientPath(processResponse.getKubectlPath());
    if (isNotEmpty(processResult.outputUTF8())) {
      return format(KubernetesExceptionExplanation.FAILED_COMMAND_WITH_EXITCODE_AND_OUTPUT,
          processResponse.getPrintableCommand(), processResult.getExitValue(), processResult.outputUTF8(), kubectlPath);
    }
    return format(KubernetesExceptionExplanation.FAILED_COMMAND_WITH_EXITCODE, processResponse.getPrintableCommand(),
        processResult.getExitValue(), kubectlPath);
  }

  private List<String> extractValuesFromFirstMatch(String errorMessage, Pattern pattern, int valuesToExtract) {
    Matcher matcher = pattern.matcher(errorMessage);
    if (matcher.find()) {
      List<String> values = new ArrayList<>(matcher.groupCount());
      for (int i = 1; i <= matcher.groupCount() && i <= valuesToExtract; i++) {
        values.add(matcher.group(i));
      }
      return values;
    }
    return Collections.emptyList();
  }

  private List<String> getAllResourceNames(String errorMessage, Pattern pattern) {
    Matcher matcher = pattern.matcher(errorMessage);
    List<String> values = new ArrayList<>();
    while (matcher.find()) {
      values.add(matcher.group(2) + "/" + matcher.group(3));
    }
    return values;
  }

  private WingsException getExplanationException(String hint, String explanation, String errorMessage) {
    return NestedExceptionUtils.hintWithExplanationException(
        hint, explanation, new KubernetesTaskException(errorMessage));
  }

  private WingsException getExplanationExceptionWithCommand(
      String hint, String explanation, String command, String errorMessage) {
    return NestedExceptionUtils.hintWithExplanationAndCommandException(
        hint, explanation, command, new KubernetesTaskException(errorMessage));
  }

  private String getRelativeKubectlClientPath(String kubectlPath) {
    if (!StringUtils.isEmpty(kubectlPath) && kubectlPath.contains(CLIENT_TOOLS_DIRECTORY_NAME)) {
      return kubectlPath.substring(kubectlPath.indexOf(CLIENT_TOOLS_DIRECTORY_NAME));
    }
    return kubectlPath;
  }
}
