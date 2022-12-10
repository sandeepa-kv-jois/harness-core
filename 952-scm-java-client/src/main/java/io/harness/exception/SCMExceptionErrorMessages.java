/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.exception;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.PL)
@UtilityClass
public class SCMExceptionErrorMessages {
  public final String FILE_NOT_FOUND_ERROR = "File not found";
  public final String CREATE_PULL_REQUEST_FAILURE = "Issue while creating pull request";
  public final String REPOSITORY_NOT_FOUND_ERROR = "Git repository not found";
  public final String BRANCH_NOT_FOUND_ERROR = "Git branch not found";

  public final String AZURE_REPOSITORY_OR_BRANCH_NOT_FOUND_ERROR = "Azure repository or branch not found";
  public final String BITBUCKET_REPOSITORY_OR_BRANCH_NOT_FOUND_ERROR = "Bitbucket repository or branch not found";
}
