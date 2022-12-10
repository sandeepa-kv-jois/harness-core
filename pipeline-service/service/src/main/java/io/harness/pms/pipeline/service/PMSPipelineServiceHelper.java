/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.service;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.telemetry.Destination.AMPLITUDE;

import static java.lang.String.format;
import static org.springframework.data.mongodb.core.query.Criteria.where;

import io.harness.ModuleType;
import io.harness.NGResourceFilterConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.Scope;
import io.harness.data.structure.EmptyPredicate;
import io.harness.data.structure.HarnessStringUtils;
import io.harness.engine.governance.PolicyEvaluationFailureException;
import io.harness.exception.DuplicateFileImportException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.ngexception.InvalidFieldsDTO;
import io.harness.exception.ngexception.beans.yamlschema.YamlSchemaErrorDTO;
import io.harness.exception.ngexception.beans.yamlschema.YamlSchemaErrorWrapperDTO;
import io.harness.filter.FilterType;
import io.harness.filter.dto.FilterDTO;
import io.harness.filter.service.FilterService;
import io.harness.gitaware.dto.GitContextRequestParams;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitaware.helper.GitAwareEntityHelper;
import io.harness.gitsync.helpers.GitContextHelper;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitsync.interceptor.GitSyncBranchContext;
import io.harness.governance.GovernanceMetadata;
import io.harness.governance.PolicySetMetadata;
import io.harness.ng.core.common.beans.NGTag.NGTagKeys;
import io.harness.ng.core.template.TemplateMergeResponseDTO;
import io.harness.ng.core.template.exception.NGTemplateResolveExceptionV2;
import io.harness.pms.filter.creation.FilterCreatorMergeService;
import io.harness.pms.filter.creation.FilterCreatorMergeServiceResponse;
import io.harness.pms.filter.utils.ModuleInfoFilterUtils;
import io.harness.pms.gitsync.PmsGitSyncBranchContextGuard;
import io.harness.pms.instrumentaion.PipelineInstrumentationConstants;
import io.harness.pms.instrumentaion.PipelineInstrumentationUtils;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.PipelineEntity.PipelineEntityKeys;
import io.harness.pms.pipeline.PipelineEntityUtils;
import io.harness.pms.pipeline.PipelineFilterPropertiesDto;
import io.harness.pms.pipeline.PipelineImportRequestDTO;
import io.harness.pms.pipeline.governance.service.PipelineGovernanceService;
import io.harness.pms.pipeline.validation.PipelineValidationResponse;
import io.harness.pms.pipeline.validation.service.PipelineValidationService;
import io.harness.pms.yaml.PipelineVersion;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YAMLMetadataFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.repositories.pipeline.PMSPipelineRepository;
import io.harness.serializer.JsonUtils;
import io.harness.telemetry.TelemetryReporter;
import io.harness.utils.PmsFeatureFlagService;
import io.harness.yaml.validator.InvalidYamlException;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.query.Criteria;

@Singleton
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(PIPELINE)
public class PMSPipelineServiceHelper {
  @Inject private final FilterService filterService;
  @Inject private final FilterCreatorMergeService filterCreatorMergeService;
  @Inject private final PipelineValidationService pipelineValidationService;
  @Inject private final PipelineGovernanceService pipelineGovernanceService;
  @Inject private final PMSPipelineTemplateHelper pipelineTemplateHelper;
  @Inject private final PmsFeatureFlagService pmsFeatureFlagService;
  @Inject private final TelemetryReporter telemetryReporter;
  @Inject private final GitAwareEntityHelper gitAwareEntityHelper;
  @Inject private final PMSPipelineRepository pmsPipelineRepository;

  public static String PIPELINE_SAVE = "pipeline_save";
  public static String PIPELINE_SAVE_ACTION_TYPE = "action";
  public static String PIPELINE_NAME = "pipelineName";
  public static String ORG_ID = "orgId";
  public static String PROJECT_ID = "projectId";

  public static void validatePresenceOfRequiredFields(Object... fields) {
    Lists.newArrayList(fields).forEach(field -> Objects.requireNonNull(field, "One of the required fields is null."));
  }

  public static Criteria getPipelineEqualityCriteria(String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, boolean deleted, Long version) {
    Criteria criteria = Criteria.where(PipelineEntityKeys.accountId)
                            .is(accountId)
                            .and(PipelineEntityKeys.orgIdentifier)
                            .is(orgIdentifier)
                            .and(PipelineEntityKeys.projectIdentifier)
                            .is(projectIdentifier)
                            .and(PipelineEntityKeys.identifier)
                            .is(pipelineIdentifier)
                            .and(PipelineEntityKeys.deleted)
                            .is(deleted);

    if (version != null) {
      criteria.and(PipelineEntityKeys.version).is(version);
    }

    return criteria;
  }

  public static Criteria buildCriteriaForRepoListing(
      String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    Criteria criteria = new Criteria();

    criteria.and(PipelineEntityKeys.accountId).is(accountIdentifier);
    criteria.and(PipelineEntityKeys.orgIdentifier).is(orgIdentifier);
    criteria.and(PipelineEntityKeys.projectIdentifier).is(projectIdentifier);

    return criteria;
  }

  public PipelineEntity updatePipelineInfo(PipelineEntity pipelineEntity, String pipelineVersion) throws IOException {
    switch (pipelineVersion) {
      case PipelineVersion.V1:
        return pipelineEntity;
      case PipelineVersion.V0:
        return updatePipelineInfoInternal(pipelineEntity);
      default:
        throw new IllegalStateException("version not supported");
    }
  }

  public void populateFilterUsingIdentifier(Criteria criteria, String accountIdentifier, String orgIdentifier,
      String projectIdentifier, @NotNull String filterIdentifier) {
    FilterDTO pipelineFilterDTO = filterService.get(
        accountIdentifier, orgIdentifier, projectIdentifier, filterIdentifier, FilterType.PIPELINESETUP);
    if (pipelineFilterDTO == null) {
      throw new InvalidRequestException("Could not find a pipeline filter with the identifier ");
    } else {
      populateFilter(criteria, (PipelineFilterPropertiesDto) pipelineFilterDTO.getFilterProperties());
    }
  }

  public static void populateFilter(Criteria criteria, @NotNull PipelineFilterPropertiesDto pipelineFilter) {
    if (EmptyPredicate.isNotEmpty(pipelineFilter.getName())) {
      criteria.and(PipelineEntityKeys.name).is(pipelineFilter.getName());
    }
    if (EmptyPredicate.isNotEmpty(pipelineFilter.getDescription())) {
      criteria.and(PipelineEntityKeys.description).is(pipelineFilter.getDescription());
    }
    if (EmptyPredicate.isNotEmpty(pipelineFilter.getPipelineTags())) {
      criteria.and(PipelineEntityKeys.tags).in(pipelineFilter.getPipelineTags());
    }
    if (EmptyPredicate.isNotEmpty(pipelineFilter.getPipelineIdentifiers())) {
      criteria.and(PipelineEntityKeys.identifier).in(pipelineFilter.getPipelineIdentifiers());
    }
    if (pipelineFilter.getModuleProperties() != null) {
      ModuleInfoFilterUtils.processNode(
          JsonUtils.readTree(pipelineFilter.getModuleProperties().toJson()), "filters", criteria);
    }
    if (EmptyPredicate.isNotEmpty(pipelineFilter.getRepoName())) {
      criteria.and(PipelineEntityKeys.repo).is(pipelineFilter.getRepoName());
    }
  }

  public void resolveTemplatesAndValidatePipelineEntity(PipelineEntity pipelineEntity) {
    long start = System.currentTimeMillis();
    GovernanceMetadata governanceMetadata = resolveTemplatesAndValidatePipeline(pipelineEntity);
    log.info("[PMS_PipelineService] validating pipeline took {}ms for projectId {}, orgId {}, accountId {}",
        System.currentTimeMillis() - start, pipelineEntity.getProjectIdentifier(), pipelineEntity.getOrgIdentifier(),
        pipelineEntity.getAccountIdentifier());
    if (governanceMetadata.getDeny()) {
      List<String> denyingRuleSetIds = governanceMetadata.getDetailsList()
                                           .stream()
                                           .filter(PolicySetMetadata::getDeny)
                                           .map(PolicySetMetadata::getIdentifier)
                                           .collect(Collectors.toList());
      throw new PolicyEvaluationFailureException(
          "Pipeline does not follow the Policies in these Policy Sets: " + denyingRuleSetIds.toString(),
          governanceMetadata, pipelineEntity.getYaml());
    }
  }

  @VisibleForTesting
  static void checkAndThrowMismatchInImportedPipelineMetadataInternal(String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, PipelineImportRequestDTO pipelineImportRequest, String importedPipeline) {
    YamlField pipelineYamlField;
    try {
      pipelineYamlField = YamlUtils.readTree(importedPipeline);
    } catch (IOException e) {
      String errorMessage = PipelineCRUDErrorResponse.errorMessageForNotAYAMLFile(
          GitAwareContextHelper.getBranchInRequest(), GitAwareContextHelper.getFilepathInRequest());
      throw PMSPipelineServiceHelper.buildInvalidYamlException(errorMessage, importedPipeline);
    }
    YamlField pipelineInnerField = pipelineYamlField.getNode().getField(YAMLFieldNameConstants.PIPELINE);
    if (pipelineInnerField == null) {
      String errorMessage = PipelineCRUDErrorResponse.errorMessageForNotAPipelineYAML(
          GitAwareContextHelper.getBranchInRequest(), GitAwareContextHelper.getFilepathInRequest());
      throw PMSPipelineServiceHelper.buildInvalidYamlException(errorMessage, importedPipeline);
    }

    Map<String, String> changedFields = new HashMap<>();

    String identifierFromGit = pipelineInnerField.getNode().getIdentifier();
    if (!pipelineIdentifier.equals(identifierFromGit)) {
      changedFields.put(YAMLMetadataFieldNameConstants.IDENTIFIER, identifierFromGit);
    }

    String nameFromGit = pipelineInnerField.getNode().getName();
    if (!pipelineImportRequest.getPipelineName().equals(nameFromGit)) {
      changedFields.put(YAMLMetadataFieldNameConstants.NAME, nameFromGit);
    }

    String orgIdentifierFromGit = pipelineInnerField.getNode().getStringValue(YAMLFieldNameConstants.ORG_IDENTIFIER);
    if (!orgIdentifier.equals(orgIdentifierFromGit)) {
      changedFields.put(YAMLMetadataFieldNameConstants.ORG_IDENTIFIER, orgIdentifierFromGit);
    }

    String projectIdentifierFromGit =
        pipelineInnerField.getNode().getStringValue(YAMLFieldNameConstants.PROJECT_IDENTIFIER);
    if (!projectIdentifier.equals(projectIdentifierFromGit)) {
      changedFields.put(YAMLMetadataFieldNameConstants.PROJECT_IDENTIFIER, projectIdentifierFromGit);
    }

    if (!changedFields.isEmpty()) {
      InvalidFieldsDTO invalidFields = InvalidFieldsDTO.builder().expectedValues(changedFields).build();
      throw new InvalidRequestException(
          "Requested metadata params do not match the values found in the YAML on Git for these fields: "
              + changedFields.keySet(),
          invalidFields);
    }
  }

  public GovernanceMetadata resolveTemplatesAndValidatePipeline(PipelineEntity pipelineEntity) {
    return resolveTemplatesAndValidatePipelineYaml(pipelineEntity, true);
  }

  public GovernanceMetadata resolveTemplatesAndValidatePipeline(
      PipelineEntity pipelineEntity, boolean throwExceptionIfGovernanceRulesFails) {
    try {
      GitEntityInfo gitEntityInfo = GitContextHelper.getGitEntityInfo();
      if (gitEntityInfo != null && gitEntityInfo.isNewBranch()) {
        GitSyncBranchContext gitSyncBranchContext =
            GitSyncBranchContext.builder()
                .gitBranchInfo(GitEntityInfo.builder()
                                   .branch(gitEntityInfo.getBaseBranch())
                                   .yamlGitConfigId(gitEntityInfo.getYamlGitConfigId())
                                   .build())
                .build();
        try (PmsGitSyncBranchContextGuard ignored = new PmsGitSyncBranchContextGuard(gitSyncBranchContext, true)) {
          return resolveTemplatesAndValidatePipelineYaml(pipelineEntity, throwExceptionIfGovernanceRulesFails);
        }
      } else {
        return resolveTemplatesAndValidatePipelineYaml(pipelineEntity, throwExceptionIfGovernanceRulesFails);
      }
    } catch (io.harness.yaml.validator.InvalidYamlException ex) {
      ex.setYaml(pipelineEntity.getData());
      throw ex;
    } catch (NGTemplateResolveExceptionV2 ex) {
      throw ex;
    } catch (Exception ex) {
      YamlSchemaErrorWrapperDTO errorWrapperDTO =
          YamlSchemaErrorWrapperDTO.builder()
              .schemaErrors(Collections.singletonList(
                  YamlSchemaErrorDTO.builder().message(ex.getMessage()).fqn("$.pipeline").build()))
              .build();
      throw new io.harness.yaml.validator.InvalidYamlException(
          HarnessStringUtils.emptyIfNull(ex.getMessage()), ex, errorWrapperDTO, pipelineEntity.getData());
    }
  }

  public GovernanceMetadata validatePipeline(PipelineEntity pipelineEntity,
      TemplateMergeResponseDTO templateMergeResponseDTO, boolean throwExceptionIfGovernanceRulesFails) {
    try {
      GitEntityInfo gitEntityInfo = GitContextHelper.getGitEntityInfo();
      if (gitEntityInfo != null && gitEntityInfo.isNewBranch()) {
        GitSyncBranchContext gitSyncBranchContext =
            GitSyncBranchContext.builder()
                .gitBranchInfo(GitEntityInfo.builder()
                                   .branch(gitEntityInfo.getBaseBranch())
                                   .yamlGitConfigId(gitEntityInfo.getYamlGitConfigId())
                                   .build())
                .build();
        try (PmsGitSyncBranchContextGuard ignored = new PmsGitSyncBranchContextGuard(gitSyncBranchContext, true)) {
          return validateYaml(pipelineEntity, templateMergeResponseDTO, throwExceptionIfGovernanceRulesFails)
              .getGovernanceMetadata();
        }
      } else {
        return validateYaml(pipelineEntity, templateMergeResponseDTO, throwExceptionIfGovernanceRulesFails)
            .getGovernanceMetadata();
      }
    } catch (io.harness.yaml.validator.InvalidYamlException ex) {
      ex.setYaml(pipelineEntity.getData());
      throw ex;
    } catch (NGTemplateResolveExceptionV2 ex) {
      throw ex;
    } catch (Exception ex) {
      YamlSchemaErrorWrapperDTO errorWrapperDTO =
          YamlSchemaErrorWrapperDTO.builder()
              .schemaErrors(Collections.singletonList(
                  YamlSchemaErrorDTO.builder().message(ex.getMessage()).fqn("$.pipeline").build()))
              .build();
      throw new io.harness.yaml.validator.InvalidYamlException(
          HarnessStringUtils.emptyIfNull(ex.getMessage()), ex, errorWrapperDTO, pipelineEntity.getData());
    }
  }

  GovernanceMetadata resolveTemplatesAndValidatePipelineYaml(
      PipelineEntity pipelineEntity, boolean throwExceptionIfGovernanceRulesFails) {
    switch (pipelineEntity.getHarnessVersion()) {
      case PipelineVersion.V1:
        return GovernanceMetadata.newBuilder().setDeny(false).build();
      case PipelineVersion.V0:
        boolean getMergedTemplateWithTemplateReferences =
            pmsFeatureFlagService.isEnabled(pipelineEntity.getAccountId(), FeatureName.OPA_PIPELINE_GOVERNANCE);
        // Apply all the templateRefs(if any) then check for schema validation.
        TemplateMergeResponseDTO templateMergeResponseDTO = pipelineTemplateHelper.resolveTemplateRefsInPipeline(
            pipelineEntity, getMergedTemplateWithTemplateReferences);
        // Add Template Module Info temporarily to Pipeline Entity
        pipelineEntity.setTemplateModules(pipelineTemplateHelper.getTemplatesModuleInfo(templateMergeResponseDTO));
        return validateYaml(pipelineEntity, templateMergeResponseDTO, throwExceptionIfGovernanceRulesFails)
            .getGovernanceMetadata();
      default:
        throw new IllegalStateException("version not supported");
    }
  }

  PipelineValidationResponse validateYaml(PipelineEntity pipelineEntity,
      TemplateMergeResponseDTO templateMergeResponseDTO, boolean throwExceptionIfGovernanceRulesFails) {
    String accountId = pipelineEntity.getAccountId();
    String orgIdentifier = pipelineEntity.getOrgIdentifier();
    String projectIdentifier = pipelineEntity.getProjectIdentifier();
    String resolveTemplateRefsInPipeline = templateMergeResponseDTO.getMergedPipelineYaml();

    if (throwExceptionIfGovernanceRulesFails) {
      return pipelineValidationService.validateYamlAndGovernanceRules(accountId, orgIdentifier, projectIdentifier,
          resolveTemplateRefsInPipeline, templateMergeResponseDTO.getMergedPipelineYamlWithTemplateRef(),
          pipelineEntity);
    }
    return pipelineValidationService.validateYamlAndGetGovernanceMetadata(accountId, orgIdentifier, projectIdentifier,
        resolveTemplateRefsInPipeline, templateMergeResponseDTO.getMergedPipelineYamlWithTemplateRef(), pipelineEntity);
  }

  public Criteria formCriteria(String accountId, String orgId, String projectId, String filterIdentifier,
      PipelineFilterPropertiesDto filterProperties, boolean deleted, String module, String searchTerm) {
    Criteria criteria = new Criteria();
    if (isNotEmpty(accountId)) {
      criteria.and(PipelineEntityKeys.accountId).is(accountId);
    }
    if (isNotEmpty(orgId)) {
      criteria.and(PipelineEntityKeys.orgIdentifier).is(orgId);
    }
    if (isNotEmpty(projectId)) {
      criteria.and(PipelineEntityKeys.projectIdentifier).is(projectId);
    }

    criteria.and(PipelineEntityKeys.deleted).is(deleted);

    if (EmptyPredicate.isNotEmpty(filterIdentifier) && filterProperties != null) {
      throw new InvalidRequestException("Can not apply both filter properties and saved filter together");
    } else if (EmptyPredicate.isNotEmpty(filterIdentifier) && filterProperties == null) {
      populateFilterUsingIdentifier(criteria, accountId, orgId, projectId, filterIdentifier);
    } else if (EmptyPredicate.isEmpty(filterIdentifier) && filterProperties != null) {
      PMSPipelineServiceHelper.populateFilter(criteria, filterProperties);
    }

    Criteria moduleCriteria = new Criteria();
    if (EmptyPredicate.isNotEmpty(module)) {
      // Add approval stage criteria to check for the pipelines containing the given module and the approval stage.
      Criteria approvalStageCriteria =
          Criteria.where(format("%s.%s.stageTypes", PipelineEntityKeys.filters, ModuleType.PMS.name().toLowerCase()))
              .exists(true);
      for (ModuleType moduleType : ModuleType.values()) {
        if (moduleType.isInternal()) {
          continue;
        }
        // This query ensures that only pipelines containing approval stage are visible.
        approvalStageCriteria.and(format("%s.%s", PipelineEntityKeys.filters, moduleType.name().toLowerCase()))
            .exists(false);
      }
      // Check for pipeline with no filters also - empty pipeline or pipelines with only approval stage
      // criteria = { "$or": [ { "filters": {} } , { "filters.MODULE": { $exists: true } } ] }
      moduleCriteria.orOperator(where(PipelineEntityKeys.filters).is(new Document()),
          where(format("%s.%s", PipelineEntityKeys.filters, module)).exists(true), approvalStageCriteria);
    }

    Criteria searchCriteria = new Criteria();
    if (EmptyPredicate.isNotEmpty(searchTerm)) {
      searchCriteria.orOperator(where(PipelineEntityKeys.identifier)
                                    .regex(searchTerm, NGResourceFilterConstants.CASE_INSENSITIVE_MONGO_OPTIONS),
          where(PipelineEntityKeys.name).regex(searchTerm, NGResourceFilterConstants.CASE_INSENSITIVE_MONGO_OPTIONS),
          where(PipelineEntityKeys.tags + "." + NGTagKeys.key)
              .regex(searchTerm, NGResourceFilterConstants.CASE_INSENSITIVE_MONGO_OPTIONS),
          where(PipelineEntityKeys.tags + "." + NGTagKeys.value)
              .regex(searchTerm, NGResourceFilterConstants.CASE_INSENSITIVE_MONGO_OPTIONS));
    }

    criteria.andOperator(moduleCriteria, searchCriteria);

    return criteria;
  }

  // TODO(Brijesh): Make this async.
  public void sendPipelineSaveTelemetryEvent(PipelineEntity entity, String actionType) {
    try {
      HashMap<String, Object> properties = new HashMap<>();
      properties.put(PIPELINE_NAME, entity.getName());
      properties.put(ORG_ID, entity.getOrgIdentifier());
      properties.put(PROJECT_ID, entity.getProjectIdentifier());
      properties.put(PIPELINE_SAVE_ACTION_TYPE, actionType);
      properties.put(PipelineInstrumentationConstants.MODULE_NAME,
          PipelineEntityUtils.getModuleNameFromPipelineEntity(entity, "cd"));
      properties.put(PipelineInstrumentationConstants.STAGE_TYPES, PipelineInstrumentationUtils.getStageTypes(entity));
      telemetryReporter.sendTrackEvent(PIPELINE_SAVE, null, entity.getAccountId(), properties,
          Collections.singletonMap(AMPLITUDE, true), io.harness.telemetry.Category.GLOBAL);
    } catch (Exception ex) {
      log.error(
          format(
              "Exception while sending telemetry event for pipeline save. accountId: %s, orgId: %s, projectId: %s, pipelineId: %s",
              entity.getAccountIdentifier(), entity.getOrgIdentifier(), entity.getProjectIdentifier(),
              entity.getIdentifier()),
          ex);
    }
  }

  public static InvalidYamlException buildInvalidYamlException(String errorMessage, String pipelineYaml) {
    YamlSchemaErrorWrapperDTO errorWrapperDTO =
        YamlSchemaErrorWrapperDTO.builder()
            .schemaErrors(
                Collections.singletonList(YamlSchemaErrorDTO.builder().message(errorMessage).fqn("$.pipeline").build()))
            .build();
    InvalidYamlException invalidYamlException = new InvalidYamlException(errorMessage, errorWrapperDTO);
    invalidYamlException.setYaml(pipelineYaml);
    return invalidYamlException;
  }

  public String importPipelineFromRemote(String accountId, String orgIdentifier, String projectIdentifier) {
    GitEntityInfo gitEntityInfo = GitAwareContextHelper.getGitRequestParamsInfo();
    Scope scope = Scope.of(accountId, orgIdentifier, projectIdentifier);
    GitContextRequestParams gitContextRequestParams = GitContextRequestParams.builder()
                                                          .branchName(gitEntityInfo.getBranch())
                                                          .connectorRef(gitEntityInfo.getConnectorRef())
                                                          .filePath(gitEntityInfo.getFilePath())
                                                          .repoName(gitEntityInfo.getRepoName())
                                                          .build();
    return gitAwareEntityHelper.fetchYAMLFromRemote(scope, gitContextRequestParams, Collections.emptyMap());
  }

  public static void checkAndThrowMismatchInImportedPipelineMetadata(String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, PipelineImportRequestDTO pipelineImportRequest, String importedPipeline,
      String pipelineVersion) {
    if (EmptyPredicate.isEmpty(importedPipeline)) {
      String errorMessage = PipelineCRUDErrorResponse.errorMessageForEmptyYamlOnGit(
          orgIdentifier, projectIdentifier, pipelineIdentifier, GitAwareContextHelper.getBranchInRequest());
      throw PMSPipelineServiceHelper.buildInvalidYamlException(errorMessage, importedPipeline);
    }
    // TODO (prashant) : Check with the team
    switch (pipelineVersion) {
      case PipelineVersion.V1:
        return;
      default:
        checkAndThrowMismatchInImportedPipelineMetadataInternal(
            orgIdentifier, projectIdentifier, pipelineIdentifier, pipelineImportRequest, importedPipeline);
    }
  }

  public String getRepoUrlAndCheckForFileUniqueness(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String pipelineIdentifier, Boolean isForceImport) {
    GitEntityInfo gitEntityInfo = GitAwareContextHelper.getGitRequestParamsInfo();
    String repoURL = gitAwareEntityHelper.getRepoUrl(accountIdentifier, orgIdentifier, projectIdentifier);

    if (Boolean.TRUE.equals(isForceImport)) {
      log.info("Importing YAML forcefully with Pipeline Id: {}, RepoURl: {}, FilePath: {}", pipelineIdentifier, repoURL,
          gitEntityInfo.getFilePath());
    } else if (isAlreadyImported(accountIdentifier, repoURL, gitEntityInfo.getFilePath())) {
      String error = "The Requested YAML with Pipeline Id: " + pipelineIdentifier + ", RepoURl: " + repoURL
          + ", FilePath: " + gitEntityInfo.getFilePath() + " has already been imported.";
      throw new DuplicateFileImportException(error);
    }
    return repoURL;
  }

  private boolean isAlreadyImported(String accountIdentifier, String repoURL, String filePath) {
    Long totalInstancesOfYAML = pmsPipelineRepository.countFileInstances(accountIdentifier, repoURL, filePath);
    return totalInstancesOfYAML > 0;
  }

  private PipelineEntity updatePipelineInfoInternal(PipelineEntity pipelineEntity) throws IOException {
    FilterCreatorMergeServiceResponse filtersAndStageCount = filterCreatorMergeService.getPipelineInfo(pipelineEntity);
    PipelineEntity newEntity = pipelineEntity.withStageCount(filtersAndStageCount.getStageCount())
                                   .withStageNames(filtersAndStageCount.getStageNames());
    newEntity.getFilters().clear();
    try {
      if (isNotEmpty(filtersAndStageCount.getFilters())) {
        filtersAndStageCount.getFilters().forEach(
            (key, value)
                -> newEntity.getFilters().put(key, isNotEmpty(value) ? Document.parse(value) : Document.parse("{}")));
      }

      if (isNotEmpty(pipelineEntity.getTemplateModules())) {
        for (String module : pipelineEntity.getTemplateModules()) {
          if (!newEntity.getFilters().containsKey(module)) {
            newEntity.getFilters().put(module, Document.parse("{}"));
          }
        }
      }
    } catch (Exception e) {
      log.error("Unable to parse the Filter value", e);
    }
    return newEntity;
  }
}
