/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.impl.scm;

import static io.harness.rule.OwnerRule.MEET;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.scm.ScmConnector;
import io.harness.product.ci.scm.proto.Commit;
import io.harness.product.ci.scm.proto.CreateBranchResponse;
import io.harness.product.ci.scm.proto.GetLatestCommitResponse;
import io.harness.product.ci.scm.proto.Provider;
import io.harness.product.ci.scm.proto.SCMGrpc;
import io.harness.rule.Owner;

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
  String baseBranchName = "baseBranchName";
  String sha = "sha";

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
}
