/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.utils;

import static io.harness.rule.OwnerRule.MOHIT_GARG;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.gitsync.helper.GitEntityFilePath;
import io.harness.gitsync.helper.GitSyncFilePathUtils;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PL)
public class GitSyncFilePathUtilsTest extends CategoryTest {
  @Test
  @Owner(developers = MOHIT_GARG)
  @Category(UnitTests.class)
  public void testFilePath() {
    String completePath = "testFolder/.harness/test.yaml";
    GitEntityFilePath gitEntityFilePath = GitSyncFilePathUtils.getRootFolderAndFilePath(completePath);
    assertThat(gitEntityFilePath.getRootFolder()).isEqualTo("/testFolder/.harness/");
    assertThat(gitEntityFilePath.getFilePath()).isEqualTo("test.yaml");

    completePath = "testFolder/folder-temp/.harness/harness/test.yaml";
    gitEntityFilePath = GitSyncFilePathUtils.getRootFolderAndFilePath(completePath);
    assertThat(gitEntityFilePath.getRootFolder()).isEqualTo("/testFolder/folder-temp/.harness/");
    assertThat(gitEntityFilePath.getFilePath()).isEqualTo("harness/test.yaml");
  }
}
