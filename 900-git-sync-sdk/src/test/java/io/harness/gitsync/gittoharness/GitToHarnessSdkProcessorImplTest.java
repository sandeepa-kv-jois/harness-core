/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.gittoharness;

import static io.harness.rule.OwnerRule.DEEPAK;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitsync.interceptor.GitSyncBranchContext;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;

public class GitToHarnessSdkProcessorImplTest extends CategoryTest {
  @InjectMocks GitToHarnessSdkProcessorImpl gitToHarnessSdkProcessor;

  @Before
  public void setup() {
    initMocks(this);
  }

  @Test
  @Owner(developers = DEEPAK)
  @Category(UnitTests.class)
  public void createGitEntityInfo() {
    String branch = "branch";
    String filePath = "filePath";
    String yamlGitConfigId = "yamlGitConfigId";
    String lastObjectId = "lastObjectId";
    String commitId = "commitId";
    String folderPath = "/folderPath/.harness/";
    GitSyncBranchContext gitEntityInfo = gitToHarnessSdkProcessor.createGitEntityInfo(
        branch, "/folderPath/.harness/filePath", yamlGitConfigId, lastObjectId, commitId);

    final GitEntityInfo gitBranchInfo = gitEntityInfo.getGitBranchInfo();
    assertThat(gitBranchInfo.getBranch()).isEqualTo(branch);
    assertThat(gitBranchInfo.getFilePath()).isEqualTo(filePath);
    assertThat(gitBranchInfo.getFolderPath()).isEqualTo(folderPath);
    assertThat(gitBranchInfo.getYamlGitConfigId()).isEqualTo(yamlGitConfigId);
    assertThat(gitBranchInfo.getLastObjectId()).isEqualTo(lastObjectId);
    assertThat(gitBranchInfo.isSyncFromGit()).isEqualTo(true);
    assertThat(gitBranchInfo.getCommitId()).isEqualTo(commitId);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = DEEPAK)
  @Category(UnitTests.class)
  public void test_createGitEntityInfoWhenFilePathDoesNotContainsHarness() {
    String branch = "branch";
    String yamlGitConfigId = "yamlGitConfigId";
    String lastObjectId = "lastObjectId";
    String commitId = "commitId";
    GitSyncBranchContext gitEntityInfo = gitToHarnessSdkProcessor.createGitEntityInfo(
        branch, "folderPath/filePath", yamlGitConfigId, lastObjectId, commitId);
  }

  @Test
  @Owner(developers = DEEPAK)
  @Category(UnitTests.class)
  public void test_createGitEntityInfoWhenFolderPathContainsHarness() {
    String branch = "branch";
    String yamlGitConfigId = "yamlGitConfigId";
    String lastObjectId = "lastObjectId";
    String commitId = "commitId";
    GitSyncBranchContext gitEntityInfo = gitToHarnessSdkProcessor.createGitEntityInfo(
        branch, "testharness/.harness/filePath", yamlGitConfigId, lastObjectId, commitId);
    final GitEntityInfo gitBranchInfo = gitEntityInfo.getGitBranchInfo();
    assertThat(gitBranchInfo).isNotNull();
  }

  @Test
  @Owner(developers = DEEPAK)
  @Category(UnitTests.class)
  public void test_createGitEntityInfoWhenFilePathContainsHarness() {
    String branch = "branch";
    String yamlGitConfigId = "yamlGitConfigId";
    String lastObjectId = "lastObjectId";
    String commitId = "commitId";
    GitSyncBranchContext gitEntityInfo = gitToHarnessSdkProcessor.createGitEntityInfo(
        branch, "folderPath/.harness/harness.yaml", yamlGitConfigId, lastObjectId, commitId);
    final GitEntityInfo gitBranchInfo = gitEntityInfo.getGitBranchInfo();
    assertThat(gitBranchInfo).isNotNull();
  }

  @Test
  @Owner(developers = DEEPAK)
  @Category(UnitTests.class)
  public void test_createGitEntityInfo_WithComplexPath() {
    String branch = "branch";
    String yamlGitConfigId = "yamlGitConfigId";
    String lastObjectId = "lastObjectId";
    String commitId = "commitId";
    GitSyncBranchContext gitEntityInfo = gitToHarnessSdkProcessor.createGitEntityInfo(
        branch, "testharness/pipelines/.harness/ci/cd/filePath", yamlGitConfigId, lastObjectId, commitId);
    final GitEntityInfo gitBranchInfo = gitEntityInfo.getGitBranchInfo();
    assertThat(gitBranchInfo).isNotNull();
    assertThat(gitBranchInfo.getFilePath()).isEqualTo("ci/cd/filePath");
    assertThat(gitBranchInfo.getFolderPath()).isEqualTo("testharness/pipelines/.harness/");
  }
}
