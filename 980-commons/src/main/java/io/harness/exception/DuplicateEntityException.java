/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.exception;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.eraro.ErrorCode.RESOURCE_ALREADY_EXISTS;

import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.Level;

import java.util.EnumSet;

@OwnedBy(PL)
public class DuplicateEntityException extends WingsException {
  private static final String MESSAGE_ARG = "message";

  public DuplicateEntityException(String message) {
    super(message, null, RESOURCE_ALREADY_EXISTS, Level.ERROR, null, null);
    super.param(MESSAGE_ARG, message);
  }

  public DuplicateEntityException(String message, EnumSet<WingsException.ReportTarget> reportTargets) {
    super(message, null, RESOURCE_ALREADY_EXISTS, Level.ERROR, reportTargets, null);
    super.param(MESSAGE_ARG, message);
  }
}
