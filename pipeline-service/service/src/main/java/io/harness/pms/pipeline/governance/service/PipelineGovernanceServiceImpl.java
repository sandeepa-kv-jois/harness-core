/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.governance.service;

import static io.harness.pms.contracts.governance.ExpansionPlacementStrategy.APPEND;

import io.harness.beans.FeatureName;
import io.harness.data.structure.EmptyPredicate;
import io.harness.engine.GovernanceService;
import io.harness.exception.InvalidRequestException;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitsync.scm.beans.ScmGitMetaData;
import io.harness.governance.GovernanceMetadata;
import io.harness.governance.PolicySetMetadata;
import io.harness.opaclient.model.OpaConstants;
import io.harness.opaclient.model.PipelineGovernanceGitConfig;
import io.harness.pms.contracts.governance.ExpansionRequestMetadata;
import io.harness.pms.contracts.governance.ExpansionResponseBatch;
import io.harness.pms.contracts.governance.ExpansionResponseProto;
import io.harness.pms.gitsync.PmsGitSyncHelper;
import io.harness.pms.governance.ExpansionRequest;
import io.harness.pms.governance.ExpansionRequestsExtractor;
import io.harness.pms.governance.ExpansionsMerger;
import io.harness.pms.governance.JsonExpander;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.serializer.JsonUtils;
import io.harness.utils.PmsFeatureFlagService;

import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
public class PipelineGovernanceServiceImpl implements PipelineGovernanceService {
  @Inject private final JsonExpander jsonExpander;
  @Inject private final ExpansionRequestsExtractor expansionRequestsExtractor;
  @Inject PmsFeatureFlagService pmsFeatureFlagService;
  @Inject private final PmsGitSyncHelper gitSyncHelper;

  @Inject private final GovernanceService governanceService;

  @Override
  public GovernanceMetadata validateGovernanceRules(
      String accountId, String orgIdentifier, String projectIdentifier, String yamlWithResolvedTemplates) {
    if (!pmsFeatureFlagService.isEnabled(accountId, FeatureName.OPA_PIPELINE_GOVERNANCE)) {
      return GovernanceMetadata.newBuilder().setDeny(false).build();
    }
    String expandedPipelineJSON = fetchExpandedPipelineJSONFromYaml(
        accountId, orgIdentifier, projectIdentifier, yamlWithResolvedTemplates, false);
    return governanceService.evaluateGovernancePolicies(expandedPipelineJSON, accountId, orgIdentifier,
        projectIdentifier, OpaConstants.OPA_EVALUATION_ACTION_PIPELINE_SAVE, "");
  }

  @Override
  public GovernanceMetadata validateGovernanceRulesAndThrowExceptionIfDenied(
      String accountId, String orgIdentifier, String projectIdentifier, String yamlWithResolvedTemplates) {
    GovernanceMetadata governanceMetadata =
        validateGovernanceRules(accountId, orgIdentifier, projectIdentifier, yamlWithResolvedTemplates);
    if (governanceMetadata.getDeny()) {
      List<String> denyingPolicySetIds = governanceMetadata.getDetailsList()
                                             .stream()
                                             .filter(PolicySetMetadata::getDeny)
                                             .map(PolicySetMetadata::getIdentifier)
                                             .collect(Collectors.toList());
      // todo: see if this can be changed to PolicyEvaluationFailureException, probably yes
      throw new InvalidRequestException(
          "Pipeline does not follow the Policies in these Policy Sets: " + denyingPolicySetIds);
    }
    return governanceMetadata;
  }

  @Override
  public String fetchExpandedPipelineJSONFromYaml(
      String accountId, String orgIdentifier, String projectIdentifier, String pipelineYaml, boolean isExecution) {
    if (!pmsFeatureFlagService.isEnabled(accountId, FeatureName.OPA_PIPELINE_GOVERNANCE)) {
      return pipelineYaml;
    }
    long start = System.currentTimeMillis();
    ExpansionRequestMetadata expansionRequestMetadata =
        getRequestMetadata(accountId, orgIdentifier, projectIdentifier, pipelineYaml);

    Set<ExpansionRequest> expansionRequests = expansionRequestsExtractor.fetchExpansionRequests(pipelineYaml);
    Set<ExpansionResponseBatch> expansionResponseBatches =
        jsonExpander.fetchExpansionResponses(expansionRequests, expansionRequestMetadata);

    // During Execution more details can be added to Final expandedYaml via below method
    if (isExecution) {
      addGitDetailsToExpandedYaml(expansionResponseBatches);
    }

    String mergeExpansions = ExpansionsMerger.mergeExpansions(pipelineYaml, expansionResponseBatches);
    log.info("[PMS_GOVERNANCE] Pipeline Json Expansion took {}ms for projectId {}, orgId {}, accountId {}",
        System.currentTimeMillis() - start, projectIdentifier, orgIdentifier, accountId);
    return mergeExpansions;
  }

  ExpansionRequestMetadata getRequestMetadata(
      String accountId, String orgIdentifier, String projectIdentifier, String pipelineYaml) {
    ByteString gitSyncBranchContextBytes = gitSyncHelper.getGitSyncBranchContextBytesThreadLocal();
    ExpansionRequestMetadata.Builder expansionRequestMetadataBuilder =
        ExpansionRequestMetadata.newBuilder()
            .setAccountId(accountId)
            .setOrgId(orgIdentifier)
            .setProjectId(projectIdentifier)
            .setYaml(ByteString.copyFromUtf8(pipelineYaml));
    if (gitSyncBranchContextBytes != null) {
      expansionRequestMetadataBuilder.setGitSyncBranchContext(gitSyncBranchContextBytes);
    }
    return expansionRequestMetadataBuilder.build();
  }

  void addGitDetailsToExpandedYaml(Set<ExpansionResponseBatch> expansionResponseBatches) {
    ScmGitMetaData scmGitMetaData = GitAwareContextHelper.getScmGitMetaData();
    if (checkIfRemotePipeline(scmGitMetaData)) {
      // Adding GitConfig to expanded Yaml
      expansionResponseBatches.add(getGitDetailsAsExecutionResponse(scmGitMetaData));
    }
  }

  boolean checkIfRemotePipeline(ScmGitMetaData scmGitMetaData) {
    return !EmptyPredicate.isEmpty(scmGitMetaData.getBranchName());
  }

  ExpansionResponseBatch getGitDetailsAsExecutionResponse(ScmGitMetaData scmGitMetaData) {
    PipelineGovernanceGitConfig pipelineGovernanceGitConfig = getPipelineGovernanceGitConfigInfo(scmGitMetaData);

    String gitDetailsJson = JsonUtils.asJson(pipelineGovernanceGitConfig);
    ExpansionResponseProto gitConfig = ExpansionResponseProto.newBuilder()
                                           .setFqn(YAMLFieldNameConstants.PIPELINE)
                                           .setKey(PipelineGovernanceGitConfig.GIT_CONFIG)
                                           .setValue(gitDetailsJson)
                                           .setSuccess(true)
                                           .setPlacement(APPEND)
                                           .build();

    return ExpansionResponseBatch.newBuilder()
        .addAllExpansionResponseProto(Collections.singletonList(gitConfig))
        .build();
  }

  PipelineGovernanceGitConfig getPipelineGovernanceGitConfigInfo(ScmGitMetaData scmGitMetaData) {
    return PipelineGovernanceGitConfig.builder()
        .branch(scmGitMetaData.getBranchName())
        .filePath(scmGitMetaData.getFilePath())
        .repoName(scmGitMetaData.getRepoName())
        .build();
  }
}
