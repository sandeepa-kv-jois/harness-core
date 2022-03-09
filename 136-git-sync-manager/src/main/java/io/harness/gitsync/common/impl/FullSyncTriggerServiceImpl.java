/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.impl;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.eventsframework.EventsFrameworkConstants;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.producer.Message;
import io.harness.eventsframework.schemas.entity.EntityScopeInfo;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.gitsync.FullSyncEventRequest;
import io.harness.gitsync.common.dtos.TriggerFullSyncRequestDTO;
import io.harness.gitsync.common.dtos.TriggerFullSyncResponseDTO;
import io.harness.gitsync.common.helper.UserProfileHelper;
import io.harness.gitsync.common.service.FullSyncTriggerService;
import io.harness.gitsync.common.service.GitBranchService;
import io.harness.gitsync.core.fullsync.GitFullSyncConfigService;
import io.harness.gitsync.core.fullsync.entity.GitFullSyncJob;
import io.harness.gitsync.core.fullsync.service.FullSyncJobService;
import io.harness.gitsync.fullsync.dtos.GitFullSyncConfigDTO;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.protobuf.StringValue;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class FullSyncTriggerServiceImpl implements FullSyncTriggerService {
  private final Producer eventProducer;
  private final GitFullSyncConfigService gitFullSyncConfigService;
  private final FullSyncJobService fullSyncJobService;
  private final UserProfileHelper userProfileHelper;
  private final GitBranchService gitBranchService;

  @Inject
  public FullSyncTriggerServiceImpl(@Named(EventsFrameworkConstants.GIT_FULL_SYNC_STREAM)
                                    Producer fullSyncEventProducer, GitFullSyncConfigService gitFullSyncConfigService,
      FullSyncJobService fullSyncJobService, UserProfileHelper userProfileHelper, GitBranchService gitBranchService) {
    this.eventProducer = fullSyncEventProducer;
    this.gitFullSyncConfigService = gitFullSyncConfigService;
    this.fullSyncJobService = fullSyncJobService;
    this.userProfileHelper = userProfileHelper;
    this.gitBranchService = gitBranchService;
  }

  @Override
  public TriggerFullSyncResponseDTO triggerFullSync(
      String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    userProfileHelper.validateIfScmUserProfileIsSet(accountIdentifier);
    GitFullSyncConfigDTO gitFullSyncConfigDTO =
        gitFullSyncConfigService.get(accountIdentifier, orgIdentifier, projectIdentifier)
            .orElseThrow(()
                             -> new InvalidRequestException(
                                 "There is no configuration saved for performing full sync, please save and try again",
                                 WingsException.USER));
    validateBranch(accountIdentifier, orgIdentifier, projectIdentifier, gitFullSyncConfigDTO.getRepoIdentifier(),
        gitFullSyncConfigDTO.getBranch(), gitFullSyncConfigDTO.isNewBranch());
    Optional<GitFullSyncJob> fullSyncJob =
        fullSyncJobService.getRunningOrQueuedJob(accountIdentifier, orgIdentifier, projectIdentifier);
    if (fullSyncJob.isPresent()) {
      throw new InvalidRequestException("Last Sync is in progress");
    }
    TriggerFullSyncRequestDTO triggerFullSyncRequestDTO =
        TriggerFullSyncRequestDTO.builder()
            .branch(gitFullSyncConfigDTO.getBranch())
            .prTitle(gitFullSyncConfigDTO.getPrTitle())
            .createPR(gitFullSyncConfigDTO.isCreatePullRequest())
            .targetBranchForPR(gitFullSyncConfigDTO.getTargetBranch())
            .yamlGitConfigIdentifier(gitFullSyncConfigDTO.getRepoIdentifier())
            .isNewBranch(gitFullSyncConfigDTO.isNewBranch())
            .baseBranch(gitFullSyncConfigDTO.getBaseBranch())
            .rootFolder(gitFullSyncConfigDTO.getRootFolder())
            .build();
    final String messageId =
        sendEventForFullSync(accountIdentifier, orgIdentifier, projectIdentifier, triggerFullSyncRequestDTO);
    if (messageId == null) {
      return TriggerFullSyncResponseDTO.builder().isFullSyncTriggered(false).build();
    }
    return TriggerFullSyncResponseDTO.builder().isFullSyncTriggered(true).build();
  }

  private String sendEventForFullSync(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      TriggerFullSyncRequestDTO fullSyncRequest) {
    final EntityScopeInfo.Builder entityScopeInfoBuilder =
        EntityScopeInfo.newBuilder()
            .setAccountId(accountIdentifier)
            .setIdentifier(fullSyncRequest.getYamlGitConfigIdentifier());
    if (isNotEmpty(orgIdentifier)) {
      entityScopeInfoBuilder.setOrgId(StringValue.of(orgIdentifier));
    }
    if (isNotEmpty(projectIdentifier)) {
      entityScopeInfoBuilder.setProjectId(StringValue.of(projectIdentifier));
    }

    final FullSyncEventRequest.Builder builder = FullSyncEventRequest.newBuilder()
                                                     .setGitConfigScope(entityScopeInfoBuilder.build())
                                                     .setBranch(fullSyncRequest.getBranch())
                                                     .setCreatePr(fullSyncRequest.isCreatePR())
                                                     .setIsNewBranch(fullSyncRequest.isNewBranch())
                                                     .setRootFolder(fullSyncRequest.getRootFolder())
                                                     .setUserPrincipal(userProfileHelper.getUserPrincipal());

    if (fullSyncRequest.isCreatePR()) {
      builder.setTargetBranch(fullSyncRequest.getTargetBranchForPR()).setPrTitle(fullSyncRequest.getPrTitle());
    }

    if (fullSyncRequest.isNewBranch()) {
      builder.setBaseBranch(fullSyncRequest.getBaseBranch());
    }

    try {
      final String messageId = eventProducer.send(Message.newBuilder()
                                                      .putAllMetadata(ImmutableMap.of("accountId", accountIdentifier))
                                                      .setData(builder.build().toByteString())
                                                      .build());
      log.info("Produced full sync event with id [{}] for accountId [{}], orgId [{}] and projectId [{}]", messageId,
          accountIdentifier, orgIdentifier, projectIdentifier);
      return messageId;
    } catch (Exception e) {
      log.error("Event to trigger full sync failed for accountId [{}], orgId [{}] and projectId [{}]",
          accountIdentifier, orgIdentifier, projectIdentifier, e);
      return null;
    }
  }

  private void validateBranch(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String yamlGitConfigId, String branch, boolean isNewBranch) {
    boolean isBranchExists =
        gitBranchService.isBranchExists(accountIdentifier, orgIdentifier, projectIdentifier, yamlGitConfigId, branch);
    if (!isBranchExists && !isNewBranch) {
      throw new InvalidRequestException(String.format("Branch [%s] does not exist. Please check the config.", branch));
    } else if (isBranchExists && isNewBranch) {
      throw new InvalidRequestException(String.format("Branch [%s] already exist. Please check the config.", branch));
    }
  }
}
