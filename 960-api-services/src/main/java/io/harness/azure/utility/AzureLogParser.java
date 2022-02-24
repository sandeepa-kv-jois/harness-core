/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.azure.utility;

import static io.harness.azure.model.AzureConstants.TIME_PATTERN;
import static io.harness.azure.model.AzureConstants.TIME_STAMP_REGEX;
import static io.harness.azure.model.AzureConstants.deploymentLogPattern;
import static io.harness.azure.model.AzureConstants.failureContainerLogPattern;

import io.harness.azure.model.AzureConstants;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.validation.constraints.NotNull;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

public class AzureLogParser {
  DateTimeFormatter formatter = DateTimeFormat.forPattern(TIME_PATTERN).withZoneUTC();

  private static final Pattern welcomeLogPattern =
      Pattern.compile("Welcome, you are now connected to log-streaming service.*", Pattern.CASE_INSENSITIVE);
  private static final Pattern timestampPattern = Pattern.compile(TIME_STAMP_REGEX, Pattern.CASE_INSENSITIVE);

  public boolean checkIsSuccessDeployment(String log) {
    Matcher deploymentLogMatcher = deploymentLogPattern.matcher(log);
    Matcher containerLogMatcher = AzureConstants.containerSuccessPattern.matcher(log);
    Matcher tomcatLogMatcher = AzureConstants.tomcatSuccessPattern.matcher(log);
    return deploymentLogMatcher.find() || containerLogMatcher.find() || tomcatLogMatcher.find();
  }

  public boolean checkIsWelcomeLog(String log) {
    Matcher matcher = welcomeLogPattern.matcher(log);
    return matcher.find();
  }

  public String removeTimestamp(@NotNull String log) {
    return log.replaceAll(TIME_STAMP_REGEX, "");
  }

  public boolean shouldLog(String log) {
    return !checkIsWelcomeLog(log) && !log.isEmpty();
  }

  public boolean checkIfFailed(String log) {
    Matcher matcher = failureContainerLogPattern.matcher(log);
    return matcher.find();
  }

  public Optional<DateTime> parseTime(String log) {
    Matcher timestampMatcher = timestampPattern.matcher(log);
    if (timestampMatcher.find()) {
      String timestamp = timestampMatcher.group();
      timestamp = timestamp.trim();
      return Optional.of(formatter.parseDateTime(timestamp));
    }

    return Optional.empty();
  }
}
