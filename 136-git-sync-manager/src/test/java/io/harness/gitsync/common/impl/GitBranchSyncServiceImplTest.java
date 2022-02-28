/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.impl;

import static io.harness.rule.OwnerRule.MEET;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.git.YamlGitConfigDTO;
import io.harness.gitsync.GitSyncTestBase;
import io.harness.gitsync.common.service.ScmOrchestratorService;
import io.harness.product.ci.scm.proto.Commit;
import io.harness.rule.Owner;

import java.util.function.Function;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class GitBranchSyncServiceImplTest extends GitSyncTestBase {
  @InjectMocks GitBranchSyncServiceImpl gitBranchSyncService;
  @Mock ScmOrchestratorService scmOrchestratorService;
  @Mock YamlGitConfigDTO yamlGitConfigDTO;
  public static final String commitId = "commitId";
  public static final String accountIdentifier = "accountIdentifier";
  public static final String message = "message";
  @Before
  public void setup() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testGetCommitMessage() {
    assert (gitBranchSyncService.getCommitMessage(yamlGitConfigDTO, null, accountIdentifier)) == null;
    Commit commit = Commit.newBuilder().setMessage(message).build();
    when(scmOrchestratorService.processScmRequest(any(Function.class), any(), any(), any())).thenReturn(commit);
    assertThat(gitBranchSyncService.getCommitMessage(yamlGitConfigDTO, commitId, accountIdentifier)).isEqualTo(message);
  }
}
