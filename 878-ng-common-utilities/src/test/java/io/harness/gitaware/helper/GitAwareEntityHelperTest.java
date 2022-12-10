/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitaware.helper;

import static io.harness.rule.OwnerRule.ADITHYA;
import static io.harness.rule.OwnerRule.NAMAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.EntityType;
import io.harness.beans.Scope;
import io.harness.category.element.UnitTests;
import io.harness.context.GlobalContext;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.gitaware.dto.GitContextRequestParams;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitsync.interceptor.GitSyncBranchContext;
import io.harness.gitsync.scm.SCMGitSyncHelper;
import io.harness.gitsync.scm.beans.ScmCreateFileGitResponse;
import io.harness.gitsync.scm.beans.ScmGetFileResponse;
import io.harness.gitsync.scm.beans.ScmGitMetaData;
import io.harness.gitsync.scm.beans.ScmUpdateFileGitResponse;
import io.harness.manage.GlobalContextManager;
import io.harness.persistence.gitaware.GitAware;
import io.harness.rule.Owner;

import lombok.Builder;
import lombok.Data;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class GitAwareEntityHelperTest extends CategoryTest {
  @InjectMocks GitAwareEntityHelper gitAwareEntityHelper;
  @Mock SCMGitSyncHelper scmGitSyncHelper;

  String accountId = "accountId";
  String orgId = "orgId";
  String projectId = "projectId";

  String connectorRef = "conn";
  String repoName = "repo";
  String filePath = "file/path";
  String data = "data: pipelineYaml";

  String branch = "dev";
  String commitId = "commit";
  String commitMsg = "commit message";

  ScmGitMetaData scmGitMetaData;
  GitContextRequestParams gitContextRequestParams;
  GitContextRequestParams __default__branchGitParams;
  Scope scope;

  String yamlFilePath = ".harness/test.yaml";
  EntityType entityType;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    scmGitMetaData =
        ScmGitMetaData.builder().repoName(repoName).branchName(branch).commitId(commitId).filePath(filePath).build();
    scope = Scope.of(accountId, orgId, projectId);
    gitContextRequestParams = GitContextRequestParams.builder()
                                  .connectorRef(connectorRef)
                                  .repoName(repoName)
                                  .filePath(yamlFilePath)
                                  .branchName(branch)
                                  .entityType(EntityType.PIPELINES)
                                  .build();
    entityType = EntityType.PIPELINES;

    __default__branchGitParams = GitContextRequestParams.builder()
                                     .connectorRef(connectorRef)
                                     .repoName(repoName)
                                     .filePath(yamlFilePath)
                                     .branchName("__default__")
                                     .entityType(entityType)
                                     .build();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testFetchEntityFromRemote() {
    ScmGetFileResponse getFileResponse =
        ScmGetFileResponse.builder().fileContent(data).gitMetaData(scmGitMetaData).build();
    doReturn(getFileResponse)
        .when(scmGitSyncHelper)
        .getFileByBranch(scope, repoName, branch, yamlFilePath, connectorRef, false, entityType, null);
    DummyGitAware dummyGitAware = DummyGitAware.builder().build();
    GitAware gitAware = gitAwareEntityHelper.fetchEntityFromRemote(dummyGitAware, scope, gitContextRequestParams, null);
    assertThat(gitAware.getData()).isEqualTo(data);
    ScmGitMetaData scmGitMetaDataInContext = GitAwareContextHelper.getScmGitMetaData();
    assertThat(scmGitMetaDataInContext.getRepoName()).isEqualTo(repoName);
    assertThat(scmGitMetaDataInContext.getBranchName()).isEqualTo(branch);
    assertThat(scmGitMetaDataInContext.getFilePath()).isEqualTo(filePath);
    assertThat(scmGitMetaDataInContext.getCommitId()).isEqualTo(commitId);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testFetchEntityFromRemoteWithBranchAs__Default__() {
    ScmGetFileResponse getFileResponse =
        ScmGetFileResponse.builder().fileContent(data).gitMetaData(scmGitMetaData).build();
    doReturn(getFileResponse)
        .when(scmGitSyncHelper)
        .getFileByBranch(scope, repoName, "", yamlFilePath, connectorRef, false, entityType, null);
    DummyGitAware dummyGitAware = DummyGitAware.builder().build();
    GitAware gitAware =
        gitAwareEntityHelper.fetchEntityFromRemote(dummyGitAware, scope, __default__branchGitParams, null);
    assertThat(gitAware.getData()).isEqualTo(data);
    ScmGitMetaData scmGitMetaDataInContext = GitAwareContextHelper.getScmGitMetaData();
    assertThat(scmGitMetaDataInContext.getRepoName()).isEqualTo(repoName);
    assertThat(scmGitMetaDataInContext.getBranchName()).isEqualTo(branch);
    assertThat(scmGitMetaDataInContext.getFilePath()).isEqualTo(filePath);
    assertThat(scmGitMetaDataInContext.getCommitId()).isEqualTo(commitId);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testFetchYAMLFromRemote() {
    ScmGetFileResponse getFileResponse =
        ScmGetFileResponse.builder().fileContent(data).gitMetaData(scmGitMetaData).build();
    doReturn(getFileResponse)
        .when(scmGitSyncHelper)
        .getFileByBranch(scope, repoName, branch, yamlFilePath, connectorRef, false, entityType, null);
    String data = gitAwareEntityHelper.fetchYAMLFromRemote(scope, gitContextRequestParams, null);
    assertThat(data).isEqualTo(data);
    ScmGitMetaData scmGitMetaDataInContext = GitAwareContextHelper.getScmGitMetaData();
    assertThat(scmGitMetaDataInContext.getRepoName()).isEqualTo(repoName);
    assertThat(scmGitMetaDataInContext.getBranchName()).isEqualTo(branch);
    assertThat(scmGitMetaDataInContext.getFilePath()).isEqualTo(filePath);
    assertThat(scmGitMetaDataInContext.getCommitId()).isEqualTo(commitId);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testFetchYAMLFromRemoteWithBranchAs__Default__() {
    ScmGetFileResponse getFileResponse =
        ScmGetFileResponse.builder().fileContent(data).gitMetaData(scmGitMetaData).build();
    doReturn(getFileResponse)
        .when(scmGitSyncHelper)
        .getFileByBranch(scope, repoName, "", yamlFilePath, connectorRef, false, entityType, null);
    String data = gitAwareEntityHelper.fetchYAMLFromRemote(scope, __default__branchGitParams, null);
    assertThat(data).isEqualTo(data);
    ScmGitMetaData scmGitMetaDataInContext = GitAwareContextHelper.getScmGitMetaData();
    assertThat(scmGitMetaDataInContext.getRepoName()).isEqualTo(repoName);
    assertThat(scmGitMetaDataInContext.getBranchName()).isEqualTo(branch);
    assertThat(scmGitMetaDataInContext.getFilePath()).isEqualTo(filePath);
    assertThat(scmGitMetaDataInContext.getCommitId()).isEqualTo(commitId);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testCreateEntityOnGit() {
    GitEntityInfo gitEntityInfo =
        GitEntityInfo.builder().branch(branch).commitMsg(commitMsg).filePath(yamlFilePath).build();
    setupGitContext(gitEntityInfo);
    DummyGitAware dummyGitAware =
        DummyGitAware.builder().connectorRef(connectorRef).repo(repoName).filePath(yamlFilePath).build();
    ScmCreateFileGitResponse createFileResponse =
        ScmCreateFileGitResponse.builder().gitMetaData(scmGitMetaData).build();
    doReturn(createFileResponse).when(scmGitSyncHelper).createFile(any(), any(), any());
    ScmCreateFileGitResponse entityOnGit = gitAwareEntityHelper.createEntityOnGit(dummyGitAware, data, scope);
    assertThat(entityOnGit).isEqualTo(createFileResponse);
    ScmGitMetaData scmGitMetaDataInContext = GitAwareContextHelper.getScmGitMetaData();
    assertThat(scmGitMetaDataInContext.getRepoName()).isEqualTo(repoName);
    assertThat(scmGitMetaDataInContext.getBranchName()).isEqualTo(branch);
    assertThat(scmGitMetaDataInContext.getFilePath()).isEqualTo(filePath);
    assertThat(scmGitMetaDataInContext.getCommitId()).isEqualTo(commitId);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testCreateEntityOnGitWithMissingFields() {
    DummyGitAware noConnectorRef =
        DummyGitAware.builder().connectorRef("__default__").repo(repoName).filePath(filePath).build();
    assertThatThrownBy(() -> gitAwareEntityHelper.createEntityOnGit(noConnectorRef, data, scope))
        .hasMessage("No Connector reference provided.");

    DummyGitAware noFilePath =
        DummyGitAware.builder().connectorRef("__default__").repo(repoName).filePath("__default__").build();
    assertThatThrownBy(() -> gitAwareEntityHelper.createEntityOnGit(noFilePath, data, scope))
        .hasMessage("No file path provided.");

    DummyGitAware noRepoName =
        DummyGitAware.builder().connectorRef("__default__").repo("__default__").filePath("__default__").build();
    assertThatThrownBy(() -> gitAwareEntityHelper.createEntityOnGit(noRepoName, data, scope))
        .hasMessage("No repo name provided.");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testUpdateEntityOnGit() {
    GitEntityInfo gitEntityInfo =
        GitEntityInfo.builder().branch(branch).commitMsg(commitMsg).filePath(yamlFilePath).build();
    setupGitContext(gitEntityInfo);
    DummyGitAware dummyGitAware =
        DummyGitAware.builder().connectorRef(connectorRef).repo(repoName).filePath(yamlFilePath).build();
    ScmUpdateFileGitResponse createFileResponse =
        ScmUpdateFileGitResponse.builder().gitMetaData(scmGitMetaData).build();
    doReturn(createFileResponse).when(scmGitSyncHelper).updateFile(any(), any(), any());
    ScmUpdateFileGitResponse entityOnGit = gitAwareEntityHelper.updateEntityOnGit(dummyGitAware, data, scope);
    assertThat(entityOnGit).isEqualTo(createFileResponse);
    ScmGitMetaData scmGitMetaDataInContext = GitAwareContextHelper.getScmGitMetaData();
    assertThat(scmGitMetaDataInContext.getRepoName()).isEqualTo(repoName);
    assertThat(scmGitMetaDataInContext.getBranchName()).isEqualTo(branch);
    assertThat(scmGitMetaDataInContext.getFilePath()).isEqualTo(filePath);
    assertThat(scmGitMetaDataInContext.getCommitId()).isEqualTo(commitId);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testUpdateEntityOnGitWithMissingFields() {
    DummyGitAware noConnectorRef =
        DummyGitAware.builder().connectorRef("__default__").repo(repoName).filePath(filePath).build();
    assertThatThrownBy(() -> gitAwareEntityHelper.updateEntityOnGit(noConnectorRef, data, scope))
        .hasMessage("No Connector reference provided.");

    DummyGitAware noFilePath =
        DummyGitAware.builder().connectorRef("__default__").repo(repoName).filePath("__default__").build();
    assertThatThrownBy(() -> gitAwareEntityHelper.updateEntityOnGit(noFilePath, data, scope))
        .hasMessage("No file path provided.");

    DummyGitAware noRepoName =
        DummyGitAware.builder().connectorRef("__default__").repo("__default__").filePath("__default__").build();
    assertThatThrownBy(() -> gitAwareEntityHelper.updateEntityOnGit(noRepoName, data, scope))
        .hasMessage("No repo name provided.");

    DummyGitAware noBranchName = DummyGitAware.builder()
                                     .connectorRef(connectorRef)
                                     .repo(repoName)
                                     .filePath(filePath)
                                     .branch("__default__")
                                     .build();
    assertThatThrownBy(() -> gitAwareEntityHelper.updateEntityOnGit(noBranchName, data, scope))
        .hasMessage("No branch provided for updating the file.");
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testValidateFilePath_whenFilePathHasInvalidExtension() {
    String filePath = ".harness/abc.py";
    try {
      gitAwareEntityHelper.validateFilePathHasCorrectExtension(filePath);
    } catch (Exception ex) {
      WingsException exception = ExceptionUtils.cause(InvalidRequestException.class, ex);
      assertThat(exception).isNotNull();
      assertThat(exception.getMessage())
          .isEqualTo(String.format(GitAwareEntityHelper.FILE_PATH_INVALID_EXTENSION_ERROR_FORMAT, filePath));
    }
  }

  @Data
  @Builder
  public static class DummyGitAware implements GitAware {
    String identifier;
    String connectorRef;
    String repo;
    String filePath;
    String data;
    String branch;

    @Override
    public void setData(String data) {
      this.data = data;
    }
  }

  private void setupGitContext(GitEntityInfo branchInfo) {
    if (!GlobalContextManager.isAvailable()) {
      GlobalContextManager.set(new GlobalContext());
    }
    GlobalContextManager.upsertGlobalContextRecord(GitSyncBranchContext.builder().gitBranchInfo(branchInfo).build());
  }
}
