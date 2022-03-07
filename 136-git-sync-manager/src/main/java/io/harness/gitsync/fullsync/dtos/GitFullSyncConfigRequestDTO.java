/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.fullsync.dtos;

import io.harness.gitsync.sdk.GitSyncApiConstants;

import io.swagger.v3.oas.annotations.media.Schema;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@Schema(name = "GitFullSyncConfigRequest", description = "Details required to trigger Git Full Sync.")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class GitFullSyncConfigRequestDTO {
  @Schema(description =
              "Name of the branch to which the entities will be pushed and from which pull request will be created.")
  @NotNull
  String branch;
  @Schema(description = GitSyncApiConstants.REPOID_PARAM_MESSAGE) @NotNull String repoIdentifier;
  @Schema(description = "Path of the root folder inside which the entities will be pushed.") @NotNull String rootFolder;
  @Schema(description = "Either true to create a new branch, or false to push entities to a existing branch."
          + "Default: false.")
  boolean isNewBranch;
  @Schema(description = "Name of the branch from which new branch will be forked out.") String baseBranch;
  @Schema(description = "If true a pull request will be created from branch to target branch."
          + "Default: false.")
  boolean createPullRequest;
  @Schema(description = "Name of the branch to which pull request will be merged.") String targetBranch;
  @Schema(description = "Title of the pull request.") String prTitle;
}
