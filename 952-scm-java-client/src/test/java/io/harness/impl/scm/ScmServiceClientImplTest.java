/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.impl.scm;

import static io.harness.constants.Constants.SCM_CONFLICT_ERROR_CODE;
import static io.harness.constants.Constants.SCM_CONFLICT_ERROR_MESSAGE;
import static io.harness.constants.Constants.SCM_INTERNAL_SERVER_ERROR_CODE;
import static io.harness.constants.Constants.SCM_INTERNAL_SERVER_ERROR_MESSAGE;
import static io.harness.delegate.beans.connector.ConnectorType.BITBUCKET;
import static io.harness.delegate.beans.connector.ConnectorType.GITHUB;
import static io.harness.rule.OwnerRule.MEET;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.gitsync.GitFileDetails;
import io.harness.category.element.UnitTests;
import io.harness.constants.Constants;
import io.harness.delegate.beans.connector.scm.ScmConnector;
import io.harness.product.ci.scm.proto.Commit;
import io.harness.product.ci.scm.proto.CreateBranchResponse;
import io.harness.product.ci.scm.proto.CreateFileResponse;
import io.harness.product.ci.scm.proto.GetLatestCommitOnFileResponse;
import io.harness.product.ci.scm.proto.GetLatestCommitResponse;
import io.harness.product.ci.scm.proto.Provider;
import io.harness.product.ci.scm.proto.SCMGrpc;
import io.harness.product.ci.scm.proto.UpdateFileResponse;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@OwnedBy(HarnessTeam.PL)
@RunWith(PowerMockRunner.class)
@PrepareForTest({SCMGrpc.SCMBlockingStub.class, Provider.class})
public class ScmServiceClientImplTest extends CategoryTest {
  @InjectMocks ScmServiceClientImpl scmServiceClient;
  @Mock ScmGitProviderHelper scmGitProviderHelper;
  @Mock ScmGitProviderMapper scmGitProviderMapper;
  @Mock SCMGrpc.SCMBlockingStub scmBlockingStub;
  @Mock Provider gitProvider;
  @Mock ScmConnector scmConnector;
  Commit commit;
  GetLatestCommitResponse getLatestCommitResponse;
  CreateBranchResponse createBranchResponse;
  String slug = "slug";
  String error = "error";
  String branch = "branch";
  String filepath = "filepath";
  String commitId = "commitId";
  String commitMessage = "commitMessage";
  String baseBranchName = "baseBranchName";
  String sha = "sha";
  String fileContent = "fileContent";
  String userEmail = "userEmail";
  String userName = "userName";

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testCreateNewBranch() {
    commit = Commit.newBuilder().setSha(sha).build();
    getLatestCommitResponse = GetLatestCommitResponse.newBuilder().setCommit(commit).build();
    createBranchResponse = CreateBranchResponse.newBuilder().setStatus(422).setError(error).build();

    when(scmGitProviderHelper.getSlug(any())).thenReturn(slug);
    when(scmGitProviderMapper.mapToSCMGitProvider(any())).thenReturn(gitProvider);
    when(scmBlockingStub.getLatestCommit(any())).thenReturn(getLatestCommitResponse);
    when(scmBlockingStub.createBranch(any())).thenReturn(createBranchResponse);
    assertThatThrownBy(() -> scmServiceClient.createNewBranch(scmConnector, branch, baseBranchName, scmBlockingStub))
        .hasMessage(String.format("Action could not be completed. Possible reasons can be:\n"
                + "1. A branch with name %s already exists in the remote Git repository\n"
                + "2. The branch name %s is invalid\n",
            branch, branch));
  }

  @Test
  @Owner(developers = OwnerRule.MOHIT_GARG)
  @Category(UnitTests.class)
  public void testUpdateFileOpsPreChecksGithub() {
    when(scmConnector.getConnectorType()).thenReturn(GITHUB);
    Optional<UpdateFileResponse> response =
        scmServiceClient.runUpdateFileOpsPreChecks(scmConnector, scmBlockingStub, GitFileDetails.builder().build());
    assertThat(response.equals(Optional.empty())).isTrue();
  }

  @Test
  @Owner(developers = OwnerRule.MOHIT_GARG)
  @Category(UnitTests.class)
  public void testUpdateFileOpsPreChecksBitbucket() {
    String currentCommitId = "DUMMY_COMMIT_ID";
    String newCommitId = "NEW_COMMIT_ID";
    when(scmConnector.getConnectorType()).thenReturn(BITBUCKET);
    when(scmGitProviderMapper.mapToSCMGitProvider(any(), eq(true))).thenReturn(Provider.newBuilder().build());
    when(scmGitProviderHelper.getSlug(any())).thenReturn(slug);
    when(scmBlockingStub.getLatestCommitOnFile(any()))
        .thenReturn(GetLatestCommitOnFileResponse.newBuilder().setCommitId(newCommitId).build());
    // conflict commit id case
    Optional<UpdateFileResponse> response = scmServiceClient.runUpdateFileOpsPreChecks(scmConnector, scmBlockingStub,
        GitFileDetails.builder().commitId(currentCommitId).branch(branch).filePath(filepath).build());
    assertThat(response).isNotNull();
    assertThat(response.get().getStatus() == SCM_CONFLICT_ERROR_CODE).isTrue();
    assertThat(response.get().getError().equals(SCM_CONFLICT_ERROR_MESSAGE)).isTrue();
    assertThat(response.get().getCommitId().equals(newCommitId)).isTrue();

    // non-conflict commit id case
    when(scmBlockingStub.getLatestCommitOnFile(any()))
        .thenReturn(GetLatestCommitOnFileResponse.newBuilder().setCommitId(currentCommitId).build());
    response = scmServiceClient.runUpdateFileOpsPreChecks(scmConnector, scmBlockingStub,
        GitFileDetails.builder().commitId(currentCommitId).branch(branch).filePath(filepath).build());
    assertThat(response).isNotNull();
    assertThat(response.equals(Optional.empty())).isTrue();
  }

  @Test
  @Owner(developers = OwnerRule.MOHIT_GARG)
  @Category(UnitTests.class)
  public void testEmptyCommitIdWhenCreateFile() {
    when(scmGitProviderMapper.mapToSCMGitProvider(any(), eq(true))).thenReturn(Provider.newBuilder().build());
    when(scmGitProviderHelper.getSlug(any())).thenReturn(slug);
    when(scmBlockingStub.createFile(any()))
        .thenReturn(CreateFileResponse.newBuilder().setStatus(200).setCommitId("").build());
    CreateFileResponse createFileResponse =
        scmServiceClient.createFile(scmConnector, getGitFileDetailsDefault(), scmBlockingStub);
    assertThat(createFileResponse).isNotNull();
    assertThat(createFileResponse.getStatus() == SCM_INTERNAL_SERVER_ERROR_CODE).isTrue();
    assertThat(createFileResponse.getError().equals(SCM_INTERNAL_SERVER_ERROR_MESSAGE)).isTrue();
  }

  @Test
  @Owner(developers = OwnerRule.MOHIT_GARG)
  @Category(UnitTests.class)
  public void testEmptyCommitIdWhenUpdateFile() {
    when(scmGitProviderMapper.mapToSCMGitProvider(any(), eq(true))).thenReturn(Provider.newBuilder().build());
    when(scmGitProviderHelper.getSlug(any())).thenReturn(slug);
    when(scmBlockingStub.updateFile(any()))
        .thenReturn(UpdateFileResponse.newBuilder().setStatus(200).setCommitId("").build());
    UpdateFileResponse updateFileResponse =
        scmServiceClient.updateFile(scmConnector, getGitFileDetailsDefault(), scmBlockingStub);
    assertThat(updateFileResponse).isNotNull();
    assertThat(updateFileResponse.getStatus() == SCM_INTERNAL_SERVER_ERROR_CODE).isTrue();
    assertThat(updateFileResponse.getError().equals(SCM_INTERNAL_SERVER_ERROR_MESSAGE)).isTrue();
  }

  @Test
  @Owner(developers = OwnerRule.MOHIT_GARG)
  @Category(UnitTests.class)
  public void testUpdateFileDuringConflict() {
    String conflictCommitId = "conflictCommitId";
    when(scmConnector.getConnectorType()).thenReturn(BITBUCKET);
    when(scmGitProviderMapper.mapToSCMGitProvider(any(), eq(true))).thenReturn(Provider.newBuilder().build());
    when(scmGitProviderHelper.getSlug(any())).thenReturn(slug);
    when(scmBlockingStub.getLatestCommitOnFile(any()))
        .thenReturn(GetLatestCommitOnFileResponse.newBuilder().setCommitId(conflictCommitId).build());
    UpdateFileResponse updateFileResponse =
        scmServiceClient.updateFile(scmConnector, getGitFileDetailsDefault(), scmBlockingStub);
    assertThat(updateFileResponse).isNotNull();
    assertThat(updateFileResponse.equals(getErrorUpdateFileResponse(conflictCommitId))).isTrue();
  }

  private GitFileDetails getGitFileDetailsDefault() {
    return GitFileDetails.builder()
        .filePath(filepath)
        .branch(branch)
        .commitId(commitId)
        .commitMessage(commitMessage)
        .fileContent(fileContent)
        .oldFileSha(sha)
        .userEmail(userEmail)
        .userName(userName)
        .build();
  }

  private UpdateFileResponse getErrorUpdateFileResponse(String commitId) {
    return UpdateFileResponse.newBuilder()
        .setStatus(Constants.SCM_CONFLICT_ERROR_CODE)
        .setError(Constants.SCM_CONFLICT_ERROR_MESSAGE)
        .setCommitId(commitId)
        .build();
  }
}
