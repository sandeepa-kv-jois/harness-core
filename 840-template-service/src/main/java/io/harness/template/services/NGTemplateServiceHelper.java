/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.template.services;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.encryption.Scope.ACCOUNT;
import static io.harness.encryption.Scope.ORG;
import static io.harness.encryption.Scope.PROJECT;
import static io.harness.springdata.SpringDataMongoUtils.populateInFilter;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.springframework.data.mongodb.core.query.Criteria.where;

import io.harness.NGResourceFilterConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.ExplanationException;
import io.harness.exception.HintException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.ScmException;
import io.harness.exception.ngexception.NGTemplateException;
import io.harness.filter.FilterType;
import io.harness.filter.dto.FilterDTO;
import io.harness.filter.service.FilterService;
import io.harness.git.model.ChangeType;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitsync.interceptor.GitSyncBranchContext;
import io.harness.gitsync.persistance.GitSyncSdkService;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.ng.core.common.beans.NGTag.NGTagKeys;
import io.harness.ng.core.mapper.TagMapper;
import io.harness.ng.core.template.ListingScope;
import io.harness.ng.core.template.TemplateListType;
import io.harness.repositories.NGTemplateRepository;
import io.harness.springdata.SpringDataMongoUtils;
import io.harness.template.TemplateFilterPropertiesDTO;
import io.harness.template.beans.TemplateFilterProperties;
import io.harness.template.entity.TemplateEntity;
import io.harness.template.entity.TemplateEntity.TemplateEntityKeys;
import io.harness.template.events.TemplateUpdateEventType;
import io.harness.template.gitsync.TemplateGitSyncBranchContextGuard;
import io.harness.template.utils.TemplateUtils;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;

@Singleton
@AllArgsConstructor(access = AccessLevel.PUBLIC, onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(CDC)
public class NGTemplateServiceHelper {
  private final FilterService filterService;
  private final NGTemplateRepository templateRepository;
  private GitSyncSdkService gitSyncSdkService;
  private TemplateGitXService templateGitXService;

  public Optional<TemplateEntity> getTemplateOrThrowExceptionIfInvalid(String accountId, String orgIdentifier,
      String projectIdentifier, String templateIdentifier, String versionLabel, boolean deleted,
      boolean loadFromCache) {
    return getOrThrowExceptionIfInvalid(
        accountId, orgIdentifier, projectIdentifier, templateIdentifier, versionLabel, deleted, false, loadFromCache);
  }

  public Optional<TemplateEntity> getMetadataOrThrowExceptionIfInvalid(String accountId, String orgIdentifier,
      String projectIdentifier, String templateIdentifier, String versionLabel, boolean deleted) {
    return getOrThrowExceptionIfInvalid(
        accountId, orgIdentifier, projectIdentifier, templateIdentifier, versionLabel, deleted, true, false);
  }

  public Optional<TemplateEntity> getOrThrowExceptionIfInvalid(String accountId, String orgIdentifier,
      String projectIdentifier, String templateIdentifier, String versionLabel, boolean deleted,
      boolean getMetadataOnly, boolean loadFromCache) {
    try {
      Optional<TemplateEntity> optionalTemplate = getTemplate(accountId, orgIdentifier, projectIdentifier,
          templateIdentifier, versionLabel, deleted, getMetadataOnly, loadFromCache, false);
      if (optionalTemplate.isPresent() && optionalTemplate.get().isEntityInvalid()) {
        throw new NGTemplateException(
            "Invalid Template yaml cannot be used. Please correct the template version yaml.");
      }
      return optionalTemplate;
    } catch (NGTemplateException e) {
      throw new NGTemplateException(e.getMessage(), e);
    } catch (Exception e) {
      log.error(String.format("Error while retrieving template with identifier [%s] and versionLabel [%s]",
                    templateIdentifier, versionLabel),
          e);
      ScmException exception = TemplateUtils.getScmException(e);
      if (null != exception) {
        throw new InvalidRequestException(
            String.format("Error while retrieving template with identifier [%s] and versionLabel [%s]",
                templateIdentifier, versionLabel),
            e);
      } else {
        throw new InvalidRequestException(
            String.format("Error while retrieving template with identifier [%s] and versionLabel [%s]: %s",
                templateIdentifier, versionLabel, e.getMessage()));
      }
    }
  }

  public static void validatePresenceOfRequiredFields(Object... fields) {
    Lists.newArrayList(fields).forEach(field -> Objects.requireNonNull(field, "One of the required fields is null."));
  }

  public TemplateGitSyncBranchContextGuard getTemplateGitContextForGivenTemplate(
      TemplateEntity template, GitEntityInfo gitEntityInfo, String commitMsg) {
    GitEntityInfo gitEntityInfoForGivenTemplate = null;
    if (gitEntityInfo != null) {
      gitEntityInfoForGivenTemplate = gitEntityInfo.withCommitMsg(commitMsg)
                                          .withFilePath(template.getFilePath())
                                          .withFolderPath(template.getRootFolder())
                                          .withLastObjectId(template.getObjectIdOfYaml());
    }
    GitSyncBranchContext branchContext =
        GitSyncBranchContext.builder().gitBranchInfo(gitEntityInfoForGivenTemplate).build();
    return new TemplateGitSyncBranchContextGuard(branchContext, false);
  }

  public Criteria formCriteria(Criteria criteria, TemplateListType templateListType) {
    if (templateListType.equals(TemplateListType.LAST_UPDATED_TEMPLATE_TYPE)) {
      return criteria.and(TemplateEntityKeys.isLastUpdatedTemplate).is(true);
    } else if (templateListType.equals(TemplateListType.STABLE_TEMPLATE_TYPE)) {
      return criteria.and(TemplateEntityKeys.isStableTemplate).is(true);
    }
    return criteria;
  }

  public Criteria formCriteria(String accountId, String orgId, String projectId, String filterIdentifier,
      TemplateFilterPropertiesDTO filterProperties, boolean deleted, String searchTerm,
      Boolean includeAllTemplatesAccessibleAtScope) {
    Criteria criteria = new Criteria();
    criteria.and(TemplateEntityKeys.accountId).is(accountId);

    Criteria includeAllTemplatesCriteria = null;
    if (includeAllTemplatesAccessibleAtScope != null && includeAllTemplatesAccessibleAtScope) {
      includeAllTemplatesCriteria = getCriteriaToReturnAllTemplatesAccessible(orgId, projectId);
    } else {
      criteria.and(TemplateEntityKeys.orgIdentifier).is(orgId);
      criteria.and(TemplateEntityKeys.projectIdentifier).is(projectId);
    }

    if (filterProperties != null && filterProperties.getListingScope() != null) {
      ListingScope listingScope = filterProperties.getListingScope();
      if (listingScope.getAccountIdentifier() != null) {
        if (gitSyncSdkService.isGitSyncEnabled(listingScope.getAccountIdentifier(), listingScope.getOrgIdentifier(),
                listingScope.getProjectIdentifier())) {
          criteria.and("storeType").in(StoreType.INLINE.name(), null);
        } else if (!templateGitXService.isNewGitXEnabled(listingScope.getAccountIdentifier(),
                       listingScope.getOrgIdentifier(), listingScope.getProjectIdentifier())) {
          criteria.and("storeType").in(StoreType.INLINE.name(), null);
        }
      }
    }

    criteria.and(TemplateEntityKeys.deleted).is(deleted);

    if (EmptyPredicate.isNotEmpty(filterIdentifier) && filterProperties != null) {
      throw new InvalidRequestException("Can not apply both filter properties and saved filter together");
    } else if (EmptyPredicate.isNotEmpty(filterIdentifier) && filterProperties == null) {
      populateFilterUsingIdentifier(
          criteria, accountId, orgId, projectId, filterIdentifier, searchTerm, includeAllTemplatesCriteria);
    } else if (EmptyPredicate.isEmpty(filterIdentifier) && filterProperties != null) {
      NGTemplateServiceHelper.populateFilter(criteria, filterProperties, searchTerm, includeAllTemplatesCriteria);
    } else {
      List<Criteria> criteriaList = new ArrayList<>();
      if (includeAllTemplatesCriteria != null) {
        criteriaList.add(includeAllTemplatesCriteria);
      }
      Criteria searchTermCriteria = getSearchTermCriteria(searchTerm);
      if (searchTermCriteria != null) {
        criteriaList.add(searchTermCriteria);
      }
      if (criteriaList.size() != 0) {
        criteria.andOperator(criteriaList.toArray(new Criteria[0]));
      }
    }
    return criteria;
  }

  public Criteria formCriteria(String accountId, String orgId, String projectId, String filterIdentifier,
      boolean deleted, TemplateFilterProperties filterProperties, String searchTerm,
      Boolean includeAllTemplatesAccessibleAtScope) {
    Criteria criteria = new Criteria();
    criteria.and(TemplateEntityKeys.accountId).is(accountId);

    Criteria includeAllTemplatesCriteria = null;
    if (includeAllTemplatesAccessibleAtScope != null && includeAllTemplatesAccessibleAtScope) {
      includeAllTemplatesCriteria = getCriteriaToReturnAllTemplatesAccessible(orgId, projectId);
    } else {
      criteria.and(TemplateEntityKeys.orgIdentifier).is(orgId);
      criteria.and(TemplateEntityKeys.projectIdentifier).is(projectId);
    }

    criteria.and(TemplateEntityKeys.deleted).is(deleted);

    if (EmptyPredicate.isNotEmpty(filterIdentifier) && filterProperties != null) {
      throw new InvalidRequestException("Can not apply both filter properties and saved filter together");
    } else if (EmptyPredicate.isNotEmpty(filterIdentifier) && filterProperties == null) {
      populateFilterUsingIdentifier(
          criteria, accountId, orgId, projectId, filterIdentifier, searchTerm, includeAllTemplatesCriteria);
    } else if (EmptyPredicate.isEmpty(filterIdentifier) && filterProperties != null) {
      NGTemplateServiceHelper.populateFilter(criteria, filterProperties, searchTerm, includeAllTemplatesCriteria);
    } else {
      List<Criteria> criteriaList = new ArrayList<>();
      if (includeAllTemplatesCriteria != null) {
        criteriaList.add(includeAllTemplatesCriteria);
      }
      Criteria searchTermCriteria = getSearchTermCriteria(searchTerm);
      if (searchTermCriteria != null) {
        criteriaList.add(searchTermCriteria);
      }
      if (criteriaList.size() != 0) {
        criteria.andOperator(criteriaList.toArray(new Criteria[0]));
      }
    }
    return criteria;
  }

  public Criteria formCriteriaForRepoListing(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      boolean includeAllTemplatesAccessibleAtScope) {
    Criteria criteria = new Criteria();
    criteria.and(TemplateEntityKeys.accountId).is(accountIdentifier);

    Criteria includeAllTemplatesCriteria = null;
    if (includeAllTemplatesAccessibleAtScope) {
      includeAllTemplatesCriteria = getCriteriaToReturnAllTemplatesAccessible(orgIdentifier, projectIdentifier);
    } else {
      if (EmptyPredicate.isNotEmpty(orgIdentifier)) {
        criteria.and(TemplateEntityKeys.orgIdentifier).is(orgIdentifier);
        if (EmptyPredicate.isNotEmpty(projectIdentifier)) {
          criteria.and(TemplateEntityKeys.projectIdentifier).is(projectIdentifier);
        } else {
          criteria.and(TemplateEntityKeys.projectIdentifier).exists(false);
        }
      } else {
        criteria.and(TemplateEntityKeys.orgIdentifier).exists(false);
        criteria.and(TemplateEntityKeys.projectIdentifier).exists(false);
      }
    }
    List<Criteria> criteriaList = new ArrayList<>();
    if (includeAllTemplatesCriteria != null) {
      criteriaList.add(includeAllTemplatesCriteria);
    }
    if (criteriaList.size() != 0) {
      criteria.andOperator(criteriaList.toArray(new Criteria[0]));
    }
    return criteria;
  }

  private void populateFilterUsingIdentifier(Criteria criteria, String accountIdentifier, String orgIdentifier,
      String projectIdentifier, @NotNull String filterIdentifier, String searchTerm,
      Criteria includeAllTemplatesCriteria) {
    FilterDTO pipelineFilterDTO =
        filterService.get(accountIdentifier, orgIdentifier, projectIdentifier, filterIdentifier, FilterType.TEMPLATE);
    if (pipelineFilterDTO == null) {
      throw new InvalidRequestException("Could not find a Template filter with the identifier ");
    } else {
      populateFilter(criteria, (TemplateFilterPropertiesDTO) pipelineFilterDTO.getFilterProperties(), searchTerm,
          includeAllTemplatesCriteria);
    }
  }

  private static void populateFilter(Criteria criteria, @NotNull TemplateFilterPropertiesDTO templateFilter,
      String searchTerm, Criteria includeAllTemplatesCriteria) {
    populateInFilter(criteria, TemplateEntityKeys.identifier, templateFilter.getTemplateIdentifiers());
    List<Criteria> criteriaList = new ArrayList<>();

    if (includeAllTemplatesCriteria != null) {
      criteriaList.add(includeAllTemplatesCriteria);
    }
    Criteria nameFilter = getCaseInsensitiveFilter(TemplateEntityKeys.name, templateFilter.getTemplateNames());
    if (nameFilter != null) {
      criteriaList.add(nameFilter);
    }
    Criteria descriptionFilter = getDescriptionFilter(templateFilter.getDescription());
    if (descriptionFilter != null) {
      criteriaList.add(descriptionFilter);
    }
    Criteria searchTermCriteria = getSearchTermCriteria(searchTerm);
    if (searchTermCriteria != null) {
      criteriaList.add(searchTermCriteria);
    }
    if (criteriaList.size() != 0) {
      criteria.andOperator(criteriaList.toArray(new Criteria[0]));
    }
    addRepoFilter(criteria, templateFilter.getRepoName());
    populateTagsFilter(criteria, templateFilter.getTags());
    populateInFilter(criteria, TemplateEntityKeys.templateEntityType, templateFilter.getTemplateEntityTypes());
    populateInFilter(criteria, TemplateEntityKeys.childType, templateFilter.getChildTypes());
  }

  private static void populateFilter(Criteria criteria, @NotNull TemplateFilterProperties templateFilter,
      String searchTerm, Criteria includeAllTemplatesCriteria) {
    populateInFilter(criteria, TemplateEntityKeys.identifier, templateFilter.getTemplateIdentifiers());
    List<Criteria> criteriaList = new ArrayList<>();

    if (includeAllTemplatesCriteria != null) {
      criteriaList.add(includeAllTemplatesCriteria);
    }
    Criteria nameFilter = getCaseInsensitiveFilter(TemplateEntityKeys.name, templateFilter.getTemplateNames());
    if (nameFilter != null) {
      criteriaList.add(nameFilter);
    }
    Criteria descriptionFilter = getDescriptionFilter(templateFilter.getDescription());
    if (descriptionFilter != null) {
      criteriaList.add(descriptionFilter);
    }
    Criteria searchTermCriteria = getSearchTermCriteria(searchTerm);
    if (searchTermCriteria != null) {
      criteriaList.add(searchTermCriteria);
    }
    if (criteriaList.size() != 0) {
      criteria.andOperator(criteriaList.toArray(new Criteria[0]));
    }
    addRepoFilter(criteria, templateFilter.getRepoName());
    populateTagsFilter(criteria, templateFilter.getTags());
    populateInFilter(criteria, TemplateEntityKeys.templateEntityType, templateFilter.getTemplateEntityTypes());
    populateInFilter(criteria, TemplateEntityKeys.childType, templateFilter.getChildTypes());
  }

  private static void addRepoFilter(Criteria criteria, String repoName) {
    if (EmptyPredicate.isNotEmpty(repoName)) {
      criteria.and(TemplateEntityKeys.repo).is(repoName);
    }
  }

  private static Criteria getSearchTermCriteria(String searchTerm) {
    if (EmptyPredicate.isNotEmpty(searchTerm)) {
      Criteria searchCriteria = new Criteria();
      searchCriteria.orOperator(where(TemplateEntityKeys.identifier)
                                    .regex(searchTerm, NGResourceFilterConstants.CASE_INSENSITIVE_MONGO_OPTIONS),
          where(TemplateEntityKeys.name).regex(searchTerm, NGResourceFilterConstants.CASE_INSENSITIVE_MONGO_OPTIONS),
          where(TemplateEntityKeys.versionLabel)
              .regex(searchTerm, NGResourceFilterConstants.CASE_INSENSITIVE_MONGO_OPTIONS),
          where(TemplateEntityKeys.description)
              .regex(searchTerm, NGResourceFilterConstants.CASE_INSENSITIVE_MONGO_OPTIONS),
          where(TemplateEntityKeys.tags + "." + NGTagKeys.key)
              .regex(searchTerm, NGResourceFilterConstants.CASE_INSENSITIVE_MONGO_OPTIONS),
          where(TemplateEntityKeys.tags + "." + NGTagKeys.value)
              .regex(searchTerm, NGResourceFilterConstants.CASE_INSENSITIVE_MONGO_OPTIONS));

      return searchCriteria;
    }
    return null;
  }

  private static Criteria getCaseInsensitiveFilter(String fieldName, List<String> values) {
    if (isNotEmpty(values)) {
      List<Criteria> criteriaForCaseInsensitive =
          values.stream()
              .map(value -> where(fieldName).regex(value, NGResourceFilterConstants.CASE_INSENSITIVE_MONGO_OPTIONS))
              .collect(Collectors.toList());
      return new Criteria().orOperator(criteriaForCaseInsensitive.toArray(new Criteria[0]));
    }
    return null;
  }

  private static Criteria getDescriptionFilter(String description) {
    if (isBlank(description)) {
      return null;
    }
    String[] descriptionsWords = description.split(" ");
    if (isNotEmpty(descriptionsWords)) {
      String pattern = SpringDataMongoUtils.getPatternForMatchingAnyOneOf(Arrays.asList(descriptionsWords));
      return where(TemplateEntityKeys.description)
          .regex(pattern, NGResourceFilterConstants.CASE_INSENSITIVE_MONGO_OPTIONS);
    }
    return null;
  }

  private static void populateTagsFilter(Criteria criteria, Map<String, String> tags) {
    if (isEmpty(tags)) {
      return;
    }
    criteria.and(TemplateEntityKeys.tags).in(TagMapper.convertToList(tags));
  }

  private static void populateTagsFilter(Criteria criteria, List<NGTag> tags) {
    if (isEmpty(tags)) {
      return;
    }
    criteria.and(TemplateEntityKeys.tags).in(tags.stream().map(NGTag::getValue).collect(Collectors.toList()));
  }

  private Criteria getCriteriaToReturnAllTemplatesAccessible(String orgIdentifier, String projectIdentifier) {
    if (EmptyPredicate.isNotEmpty(projectIdentifier)) {
      return new Criteria().orOperator(Criteria.where(TemplateEntityKeys.templateScope)
                                           .is(PROJECT)
                                           .and(TemplateEntityKeys.projectIdentifier)
                                           .is(projectIdentifier),
          Criteria.where(TemplateEntityKeys.templateScope)
              .is(ORG)
              .and(TemplateEntityKeys.orgIdentifier)
              .is(orgIdentifier),
          Criteria.where(TemplateEntityKeys.templateScope).is(ACCOUNT));
    } else if (EmptyPredicate.isNotEmpty(orgIdentifier)) {
      return new Criteria().orOperator(Criteria.where(TemplateEntityKeys.templateScope)
                                           .is(ORG)
                                           .and(TemplateEntityKeys.orgIdentifier)
                                           .is(orgIdentifier),
          Criteria.where(TemplateEntityKeys.templateScope).is(ACCOUNT));
    } else {
      return Criteria.where(TemplateEntityKeys.templateScope).is(ACCOUNT);
    }
  }

  public Optional<TemplateEntity> getTemplate(String accountId, String orgIdentifier, String projectIdentifier,
      String templateIdentifier, String versionLabel, boolean deleted, boolean getMetadataOnly, boolean loadFromCache,
      boolean loadFromFallbackBranch) {
    if (EmptyPredicate.isEmpty(versionLabel)) {
      return getStableTemplate(accountId, orgIdentifier, projectIdentifier, templateIdentifier, deleted,
          getMetadataOnly, loadFromCache, loadFromFallbackBranch);

    } else {
      return getTemplateWithVersionLabel(accountId, orgIdentifier, projectIdentifier, templateIdentifier, versionLabel,
          deleted, getMetadataOnly, loadFromCache, loadFromFallbackBranch);
    }
  }

  public Optional<TemplateEntity> getStableTemplate(String accountId, String orgIdentifier, String projectIdentifier,
      String templateIdentifier, boolean deleted, boolean getMetadataOnly, boolean loadFromCache,
      boolean loadFromFallbackBranch) {
    if (isOldGitSync(accountId, orgIdentifier, projectIdentifier)) {
      return templateRepository
          .findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifierAndIsStableAndDeletedNotForOldGitSync(
              accountId, orgIdentifier, projectIdentifier, templateIdentifier, !deleted);
    } else {
      return templateRepository
          .findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifierAndIsStableAndDeletedNot(accountId,
              orgIdentifier, projectIdentifier, templateIdentifier, !deleted, getMetadataOnly, loadFromCache,
              loadFromFallbackBranch);
    }
  }

  public Optional<TemplateEntity> getTemplateWithVersionLabel(String accountId, String orgIdentifier,
      String projectIdentifier, String templateIdentifier, String versionLabel, boolean deleted,
      boolean getMetadataOnly, boolean loadFromCache, boolean loadFromFallbackBranch) {
    if (isOldGitSync(accountId, orgIdentifier, projectIdentifier)) {
      return templateRepository
          .findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifierAndVersionLabelAndDeletedNotForOldGitSync(
              accountId, orgIdentifier, projectIdentifier, templateIdentifier, versionLabel, !deleted);
    } else {
      return templateRepository
          .findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifierAndVersionLabelAndDeletedNot(accountId,
              orgIdentifier, projectIdentifier, templateIdentifier, versionLabel, !deleted, getMetadataOnly,
              loadFromCache, loadFromFallbackBranch);
    }
  }

  public Optional<TemplateEntity> getLastUpdatedTemplate(String accountId, String orgIdentifier,
      String projectIdentifier, String templateIdentifier, boolean getMetadataOnly) {
    if (isOldGitSync(accountId, orgIdentifier, projectIdentifier)) {
      return templateRepository
          .findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifierAndIsLastUpdatedAndDeletedNotForOldGitSync(
              accountId, orgIdentifier, projectIdentifier, templateIdentifier, true);
    } else {
      return templateRepository
          .findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifierAndIsLastUpdatedAndDeletedNot(
              accountId, orgIdentifier, projectIdentifier, templateIdentifier, true, getMetadataOnly);
    }
  }

  public boolean isOldGitSync(TemplateEntity templateEntity) {
    return gitSyncSdkService.isGitSyncEnabled(
        templateEntity.getAccountId(), templateEntity.getOrgIdentifier(), templateEntity.getProjectIdentifier());
  }

  public boolean isOldGitSync(String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    return gitSyncSdkService.isGitSyncEnabled(accountIdentifier, orgIdentifier, projectIdentifier);
  }

  public TemplateEntity makeTemplateUpdateCall(TemplateEntity templateToUpdate, TemplateEntity oldTemplateEntity,
      ChangeType changeType, String comments, TemplateUpdateEventType templateUpdateEventType, boolean skipAudits) {
    return makeUpdateCall(
        templateToUpdate, oldTemplateEntity, changeType, comments, templateUpdateEventType, skipAudits, false);
  }

  public TemplateEntity makeTemplateUpdateInDB(TemplateEntity templateToUpdate, TemplateEntity oldTemplateEntity,
      ChangeType changeType, String comments, TemplateUpdateEventType templateUpdateEventType, boolean skipAudits) {
    return makeUpdateCall(
        templateToUpdate, oldTemplateEntity, changeType, comments, templateUpdateEventType, skipAudits, true);
  }

  private TemplateEntity makeUpdateCall(TemplateEntity templateToUpdate, TemplateEntity oldTemplateEntity,
      ChangeType changeType, String comments, TemplateUpdateEventType templateUpdateEventType, boolean skipAudits,
      boolean makeOnlyDbUpdate) {
    try {
      TemplateEntity updatedTemplate;
      if (isOldGitSync(templateToUpdate)) {
        updatedTemplate = templateRepository.updateTemplateYamlForOldGitSync(
            templateToUpdate, oldTemplateEntity, changeType, comments, templateUpdateEventType, skipAudits);
      } else {
        if (makeOnlyDbUpdate) {
          updatedTemplate = templateRepository.updateTemplateInDb(
              templateToUpdate, oldTemplateEntity, changeType, comments, templateUpdateEventType, skipAudits);
        } else {
          updatedTemplate = templateRepository.updateTemplateYaml(
              templateToUpdate, oldTemplateEntity, changeType, comments, templateUpdateEventType, skipAudits);
        }
      }
      if (updatedTemplate == null) {
        throw new InvalidRequestException(format(
            "Unexpected exception occurred while updating template with identifier [%s] and versionLabel [%s], under Project[%s], Organization [%s] could not be updated.",
            templateToUpdate.getIdentifier(), templateToUpdate.getVersionLabel(),
            templateToUpdate.getProjectIdentifier(), templateToUpdate.getOrgIdentifier()));
      }
      return updatedTemplate;
    } catch (ExplanationException | HintException | ScmException e) {
      log.error(
          String.format(
              "Unexpected exception occurred while updating template [%s] and versionLabel [%s], under Project[%s], Organization [%s]",
              templateToUpdate.getIdentifier(), templateToUpdate.getVersionLabel(),
              templateToUpdate.getProjectIdentifier(), templateToUpdate.getOrgIdentifier()),
          e);
      throw e;
    } catch (Exception e) {
      log.error(
          String.format(
              "Unexpected exception occurred while updating template with identifier [%s] and versionLabel [%s], under Project[%s], Organization [%s]",
              templateToUpdate.getIdentifier(), templateToUpdate.getVersionLabel(),
              templateToUpdate.getProjectIdentifier(), templateToUpdate.getOrgIdentifier()),
          e);
      throw new InvalidRequestException(String.format(
          "Unexpected exception occurred while updating template with identifier [%s] and versionLabel [%s], under Project[%s], Organization [%s] : %s",
          templateToUpdate.getIdentifier(), templateToUpdate.getVersionLabel(), templateToUpdate.getProjectIdentifier(),
          templateToUpdate.getOrgIdentifier(), e.getMessage()));
    }
  }

  public Page<TemplateEntity> listTemplate(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      Criteria criteria, Pageable pageable, boolean getDistinctFromBranches) {
    if (isOldGitSync(accountIdentifier, orgIdentifier, projectIdentifier)) {
      if (Boolean.TRUE.equals(getDistinctFromBranches)
          && gitSyncSdkService.isGitSyncEnabled(accountIdentifier, orgIdentifier, projectIdentifier)) {
        return templateRepository.findAll(
            criteria, pageable, accountIdentifier, orgIdentifier, projectIdentifier, true);
      }
      return templateRepository.findAll(criteria, pageable, accountIdentifier, orgIdentifier, projectIdentifier, false);
    } else {
      return templateRepository.findAll(accountIdentifier, orgIdentifier, projectIdentifier, criteria, pageable);
    }
  }

  public boolean deleteTemplate(String accountId, String orgIdentifier, String projectIdentifier,
      String templateIdentifier, TemplateEntity templateToDelete, String versionLabel, String comments) {
    try {
      if (isOldGitSync(templateToDelete)) {
        templateRepository.hardDeleteTemplateForOldGitSync(templateToDelete, comments);
      } else {
        templateRepository.deleteTemplate(templateToDelete, comments);
      }
      return true;
    } catch (Exception e) {
      String errorMessage = format(
          "Template with identifier [%s] and versionLabel [%s], under Project[%s], Organization [%s], Account [%s], couldn't be deleted : %s",
          templateIdentifier, versionLabel, projectIdentifier, orgIdentifier, accountId, e.getMessage());
      log.error(errorMessage, e);
      return false;
    }
  }

  public String getComment(String operationType, String templateIdentifier, String commitMessage) {
    if (isNotEmpty(commitMessage)) {
      return commitMessage;
    } else {
      return String.format(
          "[HARNESS]: Template with template identifier [%s] has been [%s]", templateIdentifier, operationType);
    }
  }
}
