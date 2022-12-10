/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.logstreaming;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.expression.SecretString.SECRET_MASK;

import static org.apache.commons.lang3.StringUtils.replaceEach;

import io.harness.logging.LogSanitizerHelper;

import java.util.ArrayList;
import java.util.Set;
import lombok.Builder;

@Builder
public class LogStreamingSanitizer {
  private final Set<String> secrets;
  public void sanitizeLogMessage(LogLine logLine) {
    String sanitizedLogMessage = logLine.getMessage();
    if (!isEmpty(secrets)) {
      ArrayList<String> secretMasks = new ArrayList<>();
      ArrayList<String> secretValues = new ArrayList<>();
      for (String secret : secrets) {
        secretMasks.add(SECRET_MASK);
        secretValues.add(secret);

        addSecretMasksWithQuotesRemoved(secret, secretMasks, secretValues);
      }
      sanitizedLogMessage = replaceEach(
          logLine.getMessage(), secretValues.toArray(new String[] {}), secretMasks.toArray(new String[] {}));
    }

    // JWT mask
    sanitizedLogMessage = LogSanitizerHelper.sanitizeJWT(sanitizedLogMessage);

    logLine.setMessage(sanitizedLogMessage);
  }

  private void addSecretMasksWithQuotesRemoved(
      String secret, ArrayList<String> secretMasks, ArrayList<String> secretValues) {
    String secretWithDoubleQuoteRemoved = secret.replaceAll("\"", "");
    if (!secretWithDoubleQuoteRemoved.equals(secret)) {
      secretMasks.add(SECRET_MASK);
      secretValues.add(secretWithDoubleQuoteRemoved);
    }
    String secretWithSingleQuoteRemoved = secret.replaceAll("\'", "");
    if (!secretWithSingleQuoteRemoved.equals(secret)) {
      secretMasks.add(SECRET_MASK);
      secretValues.add(secretWithSingleQuoteRemoved);
    }
  }
}
