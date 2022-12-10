/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.scmerrorhandling.handlers.ado;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;

import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(PL)
public class ScmErrorExplanations {
  public static final String FILE_NOT_FOUND =
      "The requested file path<FILEPATH> doesn't exist in git. Possible reasons can be:\n"
      + "1. The requested file path doesn't exist for given branch<BRANCH> and repo<REPO>\n"
      + "2. The given branch<BRANCH> or repo<REPO> is invalid";
  public static final String INVALID_CONNECTOR_CREDS =
      "The credentials provided in the Azure connector<CONNECTOR> are invalid or have expired.";
  public static final String REPO_NOT_FOUND = "Provided Azure repository<REPO> does not exist or has been deleted.";
  public static final String MISSING_PERMISSION_CREDS_EXPLANATION =
      "The credentials provided in the Azure connector<CONNECTOR> do not have correct permissions.";
  public static final String WRONG_REPO_OR_BRANCH = "The provided branch<BRANCH> or the repo<REPO> are invalid.";
}
