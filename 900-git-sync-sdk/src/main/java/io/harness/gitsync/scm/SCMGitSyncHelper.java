/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.scm;

import static io.harness.annotations.dev.HarnessTeam.DX;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.HarnessStringUtils.emptyIfNull;
import static io.harness.gitsync.interceptor.GitSyncConstants.DEFAULT;

import io.harness.EntityType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.eraro.ErrorCode;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.exception.ngexception.beans.ScmErrorMetadataDTO;
import io.harness.git.model.ChangeType;
import io.harness.gitsync.CreateFileRequest;
import io.harness.gitsync.CreateFileResponse;
import io.harness.gitsync.CreatePRRequest;
import io.harness.gitsync.CreatePRResponse;
import io.harness.gitsync.ErrorDetails;
import io.harness.gitsync.FileInfo;
import io.harness.gitsync.GetFileRequest;
import io.harness.gitsync.GetFileResponse;
import io.harness.gitsync.GetRepoUrlRequest;
import io.harness.gitsync.GetRepoUrlResponse;
import io.harness.gitsync.GitMetaData;
import io.harness.gitsync.HarnessToGitPushInfoServiceGrpc.HarnessToGitPushInfoServiceBlockingStub;
import io.harness.gitsync.PushFileResponse;
import io.harness.gitsync.UpdateFileRequest;
import io.harness.gitsync.UpdateFileResponse;
import io.harness.gitsync.common.beans.GitOperation;
import io.harness.gitsync.common.helper.CacheRequestMapper;
import io.harness.gitsync.common.helper.ChangeTypeMapper;
import io.harness.gitsync.common.helper.EntityTypeMapper;
import io.harness.gitsync.common.helper.GitSyncGrpcClientUtils;
import io.harness.gitsync.common.helper.GitSyncLogContextHelper;
import io.harness.gitsync.common.helper.ScopeIdentifierMapper;
import io.harness.gitsync.common.helper.UserPrincipalMapper;
import io.harness.gitsync.exceptions.GitSyncException;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitsync.persistance.GitSyncSdkService;
import io.harness.gitsync.scm.beans.SCMNoOpResponse;
import io.harness.gitsync.scm.beans.ScmCreateFileGitRequest;
import io.harness.gitsync.scm.beans.ScmCreateFileGitResponse;
import io.harness.gitsync.scm.beans.ScmCreatePRResponse;
import io.harness.gitsync.scm.beans.ScmErrorDetails;
import io.harness.gitsync.scm.beans.ScmGetFileResponse;
import io.harness.gitsync.scm.beans.ScmGetRepoUrlResponse;
import io.harness.gitsync.scm.beans.ScmGitMetaData;
import io.harness.gitsync.scm.beans.ScmPushResponse;
import io.harness.gitsync.scm.beans.ScmUpdateFileGitRequest;
import io.harness.gitsync.scm.beans.ScmUpdateFileGitResponse;
import io.harness.gitsync.scm.errorhandling.ScmErrorHandler;
import io.harness.gitsync.sdk.CacheResponse;
import io.harness.gitsync.sdk.CacheState;
import io.harness.impl.ScmResponseStatusUtils;
import io.harness.logging.MdcContextSetter;
import io.harness.manage.GlobalContextManager;
import io.harness.ng.core.EntityDetail;
import io.harness.ng.core.entitydetail.EntityDetailRestToProtoMapper;
import io.harness.security.Principal;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.dto.ServiceAccountPrincipal;
import io.harness.security.dto.ServicePrincipal;
import io.harness.security.dto.UserPrincipal;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.protobuf.StringValue;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

@Singleton
@Slf4j
@OwnedBy(DX)
public class SCMGitSyncHelper {
  @Inject private HarnessToGitPushInfoServiceBlockingStub harnessToGitPushInfoServiceBlockingStub;
  @Inject private EntityDetailRestToProtoMapper entityDetailRestToProtoMapper;
  @Inject GitSyncSdkService gitSyncSdkService;
  @Inject private ScmErrorHandler scmErrorHandler;

  public ScmPushResponse pushToGit(
      GitEntityInfo gitBranchInfo, String yaml, ChangeType changeType, EntityDetail entityDetail) {
    if (gitBranchInfo.isSyncFromGit()) {
      return getResponseInG2H(gitBranchInfo, entityDetail);
    }

    final FileInfo fileInfo = getFileInfo(gitBranchInfo, yaml, changeType, entityDetail);
    final PushFileResponse pushFileResponse =
        GitSyncGrpcClientUtils.retryAndProcessException(harnessToGitPushInfoServiceBlockingStub::pushFile, fileInfo);
    try {
      checkForError(pushFileResponse);
    } catch (WingsException e) {
      throwDifferentExceptionInCaseOfChangeTypeAdd(gitBranchInfo, changeType, e);
    }
    return ScmGitUtils.createScmPushResponse(yaml, gitBranchInfo, pushFileResponse, entityDetail, changeType);
  }

  public ScmGetFileResponse getFileByBranch(Scope scope, String repoName, String branchName, String filePath,
      String connectorRef, boolean loadFromCache, EntityType entityType, Map<String, String> contextMap) {
    contextMap =
        GitSyncLogContextHelper.setContextMap(scope, repoName, branchName, filePath, GitOperation.GET_FILE, contextMap);
    try (GlobalContextManager.GlobalContextGuard guard = GlobalContextManager.ensureGlobalContextGuard();
         MdcContextSetter ignore1 = new MdcContextSetter(contextMap)) {
      final GetFileRequest getFileRequest =
          GetFileRequest.newBuilder()
              .setRepoName(repoName)
              .setConnectorRef(connectorRef)
              .setBranchName(Strings.nullToEmpty(branchName))
              .setFilePath(filePath)
              .setCacheRequestParams(CacheRequestMapper.getCacheRequest(loadFromCache))
              .putAllContextMap(contextMap)
              .setEntityType(EntityTypeMapper.getEntityType(entityType))
              .setScopeIdentifiers(ScopeIdentifierMapper.getScopeIdentifiersFromScope(scope))
              .setPrincipal(getPrincipal())
              .build();
      final GetFileResponse getFileResponse = GitSyncGrpcClientUtils.retryAndProcessException(
          harnessToGitPushInfoServiceBlockingStub::getFile, getFileRequest);

      ScmGitMetaData scmGitMetaData = getScmGitMetaData(getFileResponse);
      if (isFailureResponse(getFileResponse.getStatusCode())) {
        log.error("Git SDK getFile Failure: {}", getFileResponse);
        if (isEmpty(scmGitMetaData.getBranchName())) {
          scmGitMetaData.setRepoName(getFileRequest.getBranchName());
        }
        scmErrorHandler.processAndThrowException(getFileResponse.getStatusCode(),
            getScmErrorDetailsFromGitProtoResponse(getFileResponse.getError()), scmGitMetaData);
      }
      return ScmGetFileResponse.builder()
          .fileContent(getFileResponse.getFileContent())
          .gitMetaData(scmGitMetaData)
          .build();
    }
  }

  public ScmCreateFileGitResponse createFile(
      Scope scope, ScmCreateFileGitRequest gitRequest, Map<String, String> contextMap) {
    contextMap = GitSyncLogContextHelper.setContextMap(scope, gitRequest.getRepoName(), gitRequest.getBranchName(),
        gitRequest.getFilePath(), GitOperation.CREATE_FILE, contextMap);
    try (GlobalContextManager.GlobalContextGuard guard = GlobalContextManager.ensureGlobalContextGuard();
         MdcContextSetter ignore1 = new MdcContextSetter(contextMap)) {
      final CreateFileRequest createFileRequest =
          CreateFileRequest.newBuilder()
              .setRepoName(gitRequest.getRepoName())
              .setFilePath(gitRequest.getFilePath())
              .setBranchName(gitRequest.getBranchName())
              .setConnectorRef(gitRequest.getConnectorRef())
              .setFileContent(gitRequest.getFileContent())
              .setIsCommitToNewBranch(gitRequest.isCommitToNewBranch())
              .setCommitMessage(gitRequest.getCommitMessage())
              .setScopeIdentifiers(ScopeIdentifierMapper.getScopeIdentifiersFromScope(scope))
              .putAllContextMap(contextMap)
              .setBaseBranchName((gitRequest.isCommitToNewBranch()) ? gitRequest.getBaseBranch() : "")
              .setPrincipal(getPrincipal())
              .build();

      final CreateFileResponse createFileResponse = GitSyncGrpcClientUtils.retryAndProcessException(
          harnessToGitPushInfoServiceBlockingStub::createFile, createFileRequest);

      if (isFailureResponse(createFileResponse.getStatusCode())) {
        log.error("Git SDK createFile Failure: {}", createFileResponse);
        scmErrorHandler.processAndThrowException(createFileResponse.getStatusCode(),
            getScmErrorDetailsFromGitProtoResponse(createFileResponse.getError()),
            getScmGitMetaDataFromGitProtoResponse(createFileResponse.getGitMetaData()));
      }

      return ScmCreateFileGitResponse.builder()
          .gitMetaData(getScmGitMetaDataFromGitProtoResponse(createFileResponse.getGitMetaData()))
          .build();
    }
  }

  public ScmUpdateFileGitResponse updateFile(
      Scope scope, ScmUpdateFileGitRequest gitRequest, Map<String, String> contextMap) {
    contextMap = GitSyncLogContextHelper.setContextMap(scope, gitRequest.getRepoName(), gitRequest.getBranchName(),
        gitRequest.getFilePath(), GitOperation.UPDATE_FILE, contextMap);
    try (GlobalContextManager.GlobalContextGuard guard = GlobalContextManager.ensureGlobalContextGuard();
         MdcContextSetter ignore1 = new MdcContextSetter(contextMap)) {
      final UpdateFileRequest updateFileRequest =
          UpdateFileRequest.newBuilder()
              .setRepoName(gitRequest.getRepoName())
              .setFilePath(gitRequest.getFilePath())
              .setBranchName(gitRequest.getBranchName())
              .setConnectorRef(gitRequest.getConnectorRef())
              .setFileContent(gitRequest.getFileContent())
              .setIsCommitToNewBranch(gitRequest.isCommitToNewBranch())
              .setCommitMessage(gitRequest.getCommitMessage())
              .setScopeIdentifiers(ScopeIdentifierMapper.getScopeIdentifiersFromScope(scope))
              .putAllContextMap(contextMap)
              .setBaseBranchName((gitRequest.isCommitToNewBranch()) ? gitRequest.getBaseBranch() : "")
              .setOldCommitId(emptyIfNull(gitRequest.getOldCommitId()))
              .setOldFileSha(emptyIfNull(gitRequest.getOldFileSha()))
              .setPrincipal(getPrincipal())
              .build();

      final UpdateFileResponse updateFileResponse = GitSyncGrpcClientUtils.retryAndProcessException(
          harnessToGitPushInfoServiceBlockingStub::updateFile, updateFileRequest);

      if (isFailureResponse(updateFileResponse.getStatusCode())) {
        log.error("Git SDK updateFile Failure: {}", updateFileResponse);
        scmErrorHandler.processAndThrowException(updateFileResponse.getStatusCode(),
            getScmErrorDetailsFromGitProtoResponse(updateFileResponse.getError()),
            getScmGitMetaDataFromGitProtoResponse(updateFileResponse.getGitMetaData()));
      }

      return ScmUpdateFileGitResponse.builder()
          .gitMetaData(getScmGitMetaDataFromGitProtoResponse(updateFileResponse.getGitMetaData()))
          .build();
    }
  }

  public ScmCreatePRResponse createPullRequest(Scope scope, String repoName, String connectorRef, String sourceBranch,
      String targetBranch, String title, Map<String, String> contextMap) {
    final CreatePRRequest createPRRequest =
        CreatePRRequest.newBuilder()
            .setRepoName(repoName)
            .setConnectorRef(connectorRef)
            .setScopeIdentifiers(ScopeIdentifierMapper.getScopeIdentifiersFromScope(scope))
            .setSourceBranch(sourceBranch)
            .setTargetBranch(targetBranch)
            .setTitle(title)
            .putAllContextMap(contextMap)
            .setPrincipal(getPrincipal())
            .build();

    final CreatePRResponse createPRResponse = GitSyncGrpcClientUtils.retryAndProcessException(
        harnessToGitPushInfoServiceBlockingStub::createPullRequest, createPRRequest);

    if (isFailureResponse(createPRResponse.getStatusCode())) {
      log.error("Git SDK createPullRequest Failure: {}", createPRResponse);
      scmErrorHandler.processAndThrowException(createPRResponse.getStatusCode(),
          getScmErrorDetailsFromGitProtoResponse(createPRResponse.getError()), ScmGitMetaData.builder().build());
    }

    return ScmCreatePRResponse.builder().prNumber(createPRResponse.getPrNumber()).build();
  }

  public ScmGetRepoUrlResponse getRepoUrl(
      Scope scope, String repoName, String connectorRef, Map<String, String> contextMap) {
    contextMap = GitSyncLogContextHelper.setContextMap(scope, repoName, "", "", GitOperation.GET_REPO_URL, contextMap);
    try (GlobalContextManager.GlobalContextGuard guard = GlobalContextManager.ensureGlobalContextGuard();
         MdcContextSetter ignore1 = new MdcContextSetter(contextMap)) {
      final GetRepoUrlRequest getRepoUrlRequest =
          GetRepoUrlRequest.newBuilder()
              .setRepoName(repoName)
              .setConnectorRef(connectorRef)
              .setScopeIdentifiers(ScopeIdentifierMapper.getScopeIdentifiersFromScope(scope))
              .putAllContextMap(contextMap)
              .build();

      final GetRepoUrlResponse getRepoUrlResponse = GitSyncGrpcClientUtils.retryAndProcessException(
          harnessToGitPushInfoServiceBlockingStub::getRepoUrl, getRepoUrlRequest);

      if (isFailureResponse(getRepoUrlResponse.getStatusCode())) {
        log.error("Git SDK getRepoUrl Failure: {}", getRepoUrlResponse);
        scmErrorHandler.processAndThrowException(getRepoUrlResponse.getStatusCode(),
            getScmErrorDetailsFromGitProtoResponse(getRepoUrlResponse.getError()), ScmGitMetaData.builder().build());
      }

      return ScmGetRepoUrlResponse.builder().repoUrl(getRepoUrlResponse.getRepoUrl()).build();
    }
  }

  @VisibleForTesting
  protected void throwDifferentExceptionInCaseOfChangeTypeAdd(
      GitEntityInfo gitBranchInfo, ChangeType changeType, WingsException e) {
    if (changeType.equals(ChangeType.ADD)) {
      final WingsException cause = ExceptionUtils.cause(ErrorCode.SCM_CONFLICT_ERROR, e);
      if (cause != null) {
        throw new InvalidRequestException(String.format(
            "A file with name %s already exists in the remote Git repository", gitBranchInfo.getFilePath()));
      }
    }
    throw e;
  }

  private FileInfo getFileInfo(
      GitEntityInfo gitBranchInfo, String yaml, ChangeType changeType, EntityDetail entityDetail) {
    FileInfo.Builder builder = FileInfo.newBuilder()
                                   .setPrincipal(getPrincipal())
                                   .setAccountId(entityDetail.getEntityRef().getAccountIdentifier())
                                   .setBranch(gitBranchInfo.getBranch())
                                   .setEntityDetail(entityDetailRestToProtoMapper.createEntityDetailDTO(entityDetail))
                                   .setChangeType(ChangeTypeMapper.toProto(changeType))
                                   .setFilePath(gitBranchInfo.getFilePath())
                                   .setFolderPath(gitBranchInfo.getFolderPath())
                                   .setIsNewBranch(gitBranchInfo.isNewBranch())
                                   .setCommitMsg(StringValue.of(gitBranchInfo.getCommitMsg()))
                                   .setYamlGitConfigId(gitBranchInfo.getYamlGitConfigId())
                                   .putAllContextMap(MDC.getCopyOfContextMap())
                                   .setYaml(emptyIfNull(yaml));

    if (gitBranchInfo.getIsFullSyncFlow() != null) {
      builder.setIsFullSyncFlow(gitBranchInfo.getIsFullSyncFlow());
    } else {
      builder.setIsFullSyncFlow(false);
    }

    if (gitBranchInfo.getBaseBranch() != null) {
      builder.setBaseBranch(StringValue.of(gitBranchInfo.getBaseBranch()));
    }

    if (gitBranchInfo.getLastObjectId() != null) {
      builder.setOldFileSha(StringValue.of(gitBranchInfo.getLastObjectId()));
    }

    if (gitBranchInfo.getResolvedConflictCommitId() != null
        && !gitBranchInfo.getResolvedConflictCommitId().equals(DEFAULT)) {
      builder.setCommitId(gitBranchInfo.getResolvedConflictCommitId());
    }

    return builder.build();
  }

  private SCMNoOpResponse getResponseInG2H(GitEntityInfo gitBranchInfo, EntityDetail entityDetail) {
    final boolean defaultBranch = gitSyncSdkService.isDefaultBranch(entityDetail.getEntityRef().getAccountIdentifier(),
        entityDetail.getEntityRef().getOrgIdentifier(), entityDetail.getEntityRef().getProjectIdentifier());
    return SCMNoOpResponse.builder()
        .filePath(gitBranchInfo.getFilePath())
        .pushToDefaultBranch(defaultBranch)
        .objectId(gitBranchInfo.getLastObjectId())
        .yamlGitConfigId(gitBranchInfo.getYamlGitConfigId())
        .branch(gitBranchInfo.getBranch())
        .folderPath(gitBranchInfo.getFolderPath())
        .commitId(gitBranchInfo.getCommitId())
        .build();
  }

  @VisibleForTesting
  protected void checkForError(PushFileResponse pushFileResponse) {
    if (pushFileResponse.getStatus() != 1) {
      final String errorMessage =
          isNotEmpty(pushFileResponse.getError()) ? pushFileResponse.getError() : "Error in doing git push";
      throw new GitSyncException(errorMessage);
    }
    try {
      ScmResponseStatusUtils.checkScmResponseStatusAndThrowException(
          pushFileResponse.getScmResponseCode(), pushFileResponse.getError());
    } catch (WingsException ex) {
      ex.setMetadata(ScmErrorMetadataDTO.builder().conflictCommitId(pushFileResponse.getCommitId()).build());
      throw ex;
    }
  }

  private Principal getPrincipal() {
    final io.harness.security.dto.Principal sourcePrincipal = SourcePrincipalContextBuilder.getSourcePrincipal();
    if (sourcePrincipal == null) {
      throw new InvalidRequestException("Principal cannot be null");
    }
    final Principal.Builder principalBuilder = Principal.newBuilder();
    switch (sourcePrincipal.getType()) {
      case USER:
        UserPrincipal userPrincipalFromContext = (UserPrincipal) sourcePrincipal;
        return principalBuilder.setUserPrincipal(UserPrincipalMapper.toProto(userPrincipalFromContext)).build();
      case SERVICE:
        final ServicePrincipal servicePrincipalFromContext = (ServicePrincipal) sourcePrincipal;
        final io.harness.security.ServicePrincipal servicePrincipal =
            io.harness.security.ServicePrincipal.newBuilder().setName(servicePrincipalFromContext.getName()).build();
        return principalBuilder.setServicePrincipal(servicePrincipal).build();
      case SERVICE_ACCOUNT:
        final ServiceAccountPrincipal serviceAccountPrincipalFromContext = (ServiceAccountPrincipal) sourcePrincipal;
        final io.harness.security.ServiceAccountPrincipal serviceAccountPrincipal =
            io.harness.security.ServiceAccountPrincipal.newBuilder()
                .setName(StringValue.of(serviceAccountPrincipalFromContext.getName()))
                .setEmail(StringValue.of(serviceAccountPrincipalFromContext.getEmail()))
                .setUserName(StringValue.of(serviceAccountPrincipalFromContext.getUsername()))
                .build();
        return principalBuilder.setServiceAccountPrincipal(serviceAccountPrincipal).build();
      default:
        throw new InvalidRequestException("Principal type not set.");
    }
  }

  private ScmGitMetaData getScmGitMetaDataFromGitProtoResponse(GitMetaData gitMetaData) {
    return ScmGitMetaData.builder()
        .blobId(gitMetaData.getBlobId())
        .branchName(gitMetaData.getBranchName())
        .repoName(gitMetaData.getRepoName())
        .filePath(gitMetaData.getFilePath())
        .commitId(gitMetaData.getCommitId())
        .fileUrl(gitMetaData.getFileUrl())
        .repoUrl(gitMetaData.getRepoUrl())
        .build();
  }

  private ScmGitMetaData getScmGitMetaData(GetFileResponse getFileResponse) {
    if (getFileResponse.hasCacheResponse()) {
      return getScmGitMetaDataFromGitProtoResponse(
          getFileResponse.getGitMetaData(), getFileResponse.getCacheResponse());
    } else {
      return getScmGitMetaDataFromGitProtoResponse(getFileResponse.getGitMetaData());
    }
  }

  private ScmGitMetaData getScmGitMetaDataFromGitProtoResponse(
      GitMetaData gitMetaData, io.harness.gitsync.CacheResponseParams cacheResponse) {
    return ScmGitMetaData.builder()
        .blobId(gitMetaData.getBlobId())
        .branchName(gitMetaData.getBranchName())
        .repoName(gitMetaData.getRepoName())
        .filePath(gitMetaData.getFilePath())
        .commitId(gitMetaData.getCommitId())
        .fileUrl(gitMetaData.getFileUrl())
        .repoUrl(gitMetaData.getRepoUrl())
        .cacheResponse(getCacheResponseFromGitProtoResponse(cacheResponse))
        .build();
  }

  private CacheResponse getCacheResponseFromGitProtoResponse(io.harness.gitsync.CacheResponseParams cacheResponse) {
    return CacheResponse.builder()
        .cacheState(getCacheStateFromGitProtoResponse(cacheResponse.getCacheState()))
        .lastUpdatedAt(cacheResponse.getLastUpdateAt())
        .ttlLeft(cacheResponse.getTtlLeft())
        .build();
  }

  private CacheState getCacheStateFromGitProtoResponse(io.harness.gitsync.CacheState cacheState) {
    if (io.harness.gitsync.CacheState.STALE_CACHE.equals(cacheState)) {
      return CacheState.STALE_CACHE;
    } else if (io.harness.gitsync.CacheState.VALID_CACHE.equals(cacheState)) {
      return CacheState.VALID_CACHE;
    }
    return CacheState.UNKNOWN;
  }

  private ScmErrorDetails getScmErrorDetailsFromGitProtoResponse(ErrorDetails errorDetails) {
    return ScmErrorDetails.builder()
        .errorMessage(errorDetails.getErrorMessage())
        .explanationMessage(errorDetails.getExplanationMessage())
        .hintMessage(errorDetails.getHintMessage())
        .build();
  }

  private boolean isFailureResponse(int statusCode) {
    return statusCode >= 300;
  }
}
