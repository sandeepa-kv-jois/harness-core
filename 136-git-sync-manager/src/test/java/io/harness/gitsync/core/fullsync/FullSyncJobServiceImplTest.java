/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.core.fullsync;

import static io.harness.rule.OwnerRule.BHAVYA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.gitsync.GitSyncTestBase;
import io.harness.gitsync.core.fullsync.entity.GitFullSyncJob;
import io.harness.gitsync.core.fullsync.impl.FullSyncJobServiceImpl;
import io.harness.repositories.fullSync.FullSyncJobRepository;
import io.harness.rule.Owner;
import io.harness.security.dto.UserPrincipal;

import com.google.inject.Inject;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PL)
public class FullSyncJobServiceImplTest extends GitSyncTestBase {
  public static final String YAML_GIT_CONFIG = "yamlGitConfig";
  public static final String BRANCH = "branch";
  public static final String PR_TITLE = "pr title";
  public static final String BASE_BRANCH = "baseBranch";
  public static final String ACCOUNT = "account";
  public static final String ORG = "org";
  public static final String PROJECT = "project";
  public static final String REPO_URL = "repo_url";
  public static final String MESSAGE_ID = "messageId";
  public static final String NAME = "name";
  public static final String EMAIL = "email";
  public static final String USER_NAME = "user_name";
  private UserPrincipal userPrincipal;
  private GitFullSyncJob gitFullSyncJob;
  @Inject private FullSyncJobRepository fullSyncJobRepository;
  @Inject private FullSyncJobServiceImpl fullSyncJobService;

  @Before
  public void setup() throws Exception {
    MockitoAnnotations.initMocks(this);
    userPrincipal = new UserPrincipal(NAME, EMAIL, USER_NAME, ACCOUNT);
  }

  private void save(GitFullSyncJob.SyncStatus syncStatus) {
    gitFullSyncJob = GitFullSyncJob.builder()
                         .accountIdentifier(ACCOUNT)
                         .orgIdentifier(ORG)
                         .projectIdentifier(PROJECT)
                         .yamlGitConfigIdentifier(YAML_GIT_CONFIG)
                         .syncStatus(syncStatus.name())
                         .messageId(MESSAGE_ID)
                         .retryCount(0)
                         .branch(BASE_BRANCH)
                         .prTitle(PR_TITLE)
                         .triggeredBy(userPrincipal)
                         .build();
    fullSyncJobService.save(gitFullSyncJob);
  }

  @Test
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  public void testGetRunningJob() {
    save(GitFullSyncJob.SyncStatus.RUNNING);
    Optional<GitFullSyncJob> savedJob = fullSyncJobService.getRunningJob(ACCOUNT, ORG, PROJECT);
    assertThat(savedJob.isPresent()).isEqualTo(true);
    assertThat(savedJob.get().getTriggeredBy().getUsername()).isEqualTo(USER_NAME);
  }
}
