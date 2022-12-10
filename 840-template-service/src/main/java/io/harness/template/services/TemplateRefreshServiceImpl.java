/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.template.services;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_NESTS;
import static io.harness.template.resources.NGTemplateResource.TEMPLATE;

import io.harness.NgAutoLogContextForMethod;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.git.model.ChangeType;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.logging.AutoLogContext;
import io.harness.ng.core.template.TemplateResponseDTO;
import io.harness.ng.core.template.refresh.ErrorNodeSummary;
import io.harness.ng.core.template.refresh.ValidateTemplateInputsResponseDTO;
import io.harness.ng.core.template.refresh.YamlDiffResponseDTO;
import io.harness.ng.core.template.refresh.YamlFullRefreshResponseDTO;
import io.harness.template.beans.PermissionTypes;
import io.harness.template.entity.TemplateEntity;
import io.harness.template.entity.TemplateEntityGetResponse;
import io.harness.template.helpers.TemplateInputsRefreshHelper;
import io.harness.template.helpers.TemplateInputsValidator;
import io.harness.template.mappers.NGTemplateDtoMapper;
import io.harness.template.utils.TemplateUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.Stack;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Singleton
@Slf4j
@OwnedBy(CDC)
public class TemplateRefreshServiceImpl implements TemplateRefreshService {
  private TemplateInputsRefreshHelper templateInputsRefreshHelper;
  private NGTemplateService templateService;
  private TemplateInputsValidator templateInputsValidator;

  private AccessControlClient accessControlClient;

  @Override
  public void refreshAndUpdateTemplate(
      String accountId, String orgId, String projectId, String templateIdentifier, String versionLabel) {
    TemplateEntityGetResponse templateEntityGetResponse =
        getTemplate(accountId, orgId, projectId, templateIdentifier, versionLabel);
    TemplateEntity template = templateEntityGetResponse.getTemplateEntity();
    TemplateUtils.setupGitParentEntityDetails(
        accountId, orgId, projectId, template.getRepo(), template.getConnectorRef());
    String yaml = template.getYaml();
    updateTemplate(accountId, orgId, projectId, templateIdentifier, versionLabel, yaml);
  }

  private void updateTemplate(
      String accountId, String orgId, String projectId, String templateIdentifier, String versionLabel, String yaml) {
    String refreshedYaml = refreshLinkedTemplateInputs(accountId, orgId, projectId, yaml);
    TemplateEntity templateEntity = NGTemplateDtoMapper.toTemplateEntity(
        accountId, orgId, projectId, templateIdentifier, versionLabel, refreshedYaml);
    templateService.updateTemplateEntity(templateEntity, ChangeType.MODIFY, false, "Refreshed template inputs");
  }

  private void updateTemplateYamlAndGitDetails(String accountId, TemplateResponseDTO templateResponse) {
    String orgId = templateResponse.getOrgIdentifier();
    String projectId = templateResponse.getProjectIdentifier();
    String templateIdentifier = templateResponse.getIdentifier();
    String versionLabel = templateResponse.getVersionLabel();
    String yaml = templateResponse.getYaml();
    String refreshedYaml = refreshLinkedTemplateInputs(accountId, orgId, projectId, yaml);
    long startTime = System.currentTimeMillis();
    try (AutoLogContext ignore1 = new NgAutoLogContextForMethod(
             projectId, orgId, accountId, "validateTemplateInputs#updateTemplateYamlAndGitDetails", OVERRIDE_NESTS);) {
      log.info("[TemplateService] Updating Template with identifier {} from project {}, org {}, account {}",
          templateIdentifier, projectId, orgId, accountId);
      TemplateEntity templateEntity = NGTemplateDtoMapper.toTemplateEntity(
          accountId, orgId, projectId, templateIdentifier, versionLabel, refreshedYaml);
      templateService.updateTemplateEntity(
          templateEntity, ChangeType.MODIFY, false, "Refreshed template inputs", templateResponse);
    } finally {
      log.info("[TemplateService] Updating Template with identifier {} from project {}, org {}, account {} took {}ms",
          templateIdentifier, projectId, orgId, accountId, System.currentTimeMillis() - startTime);
    }
  }

  private TemplateEntityGetResponse getTemplate(
      String accountId, String orgId, String projectId, String templateIdentifier, String versionLabel) {
    Optional<TemplateEntity> optionalTemplateEntity =
        templateService.get(accountId, orgId, projectId, templateIdentifier, versionLabel, false, false);

    if (!optionalTemplateEntity.isPresent()) {
      throw new InvalidRequestException(
          String.format("Template with the Identifier %s and versionLabel %s does not exist or has been deleted",
              templateIdentifier, versionLabel));
    }
    return new TemplateEntityGetResponse(
        optionalTemplateEntity.get(), NGTemplateDtoMapper.getEntityGitDetails(optionalTemplateEntity.get()));
  }

  @Override
  public String refreshLinkedTemplateInputs(String accountId, String orgId, String projectId, String yaml) {
    return templateInputsRefreshHelper.refreshTemplates(accountId, orgId, projectId, yaml);
  }

  @Override
  public ValidateTemplateInputsResponseDTO validateTemplateInputsInTemplate(
      String accountId, String orgId, String projectId, String templateIdentifier, String versionLabel) {
    TemplateEntityGetResponse templateEntityGetResponse =
        getTemplate(accountId, orgId, projectId, templateIdentifier, versionLabel);
    TemplateEntity template = templateEntityGetResponse.getTemplateEntity();
    TemplateUtils.setupGitParentEntityDetails(
        accountId, orgId, projectId, template.getRepo(), template.getConnectorRef());
    return getValidateTemplateInputsResponseDTO(accountId, orgId, projectId, templateEntityGetResponse);
  }

  private ValidateTemplateInputsResponseDTO getValidateTemplateInputsResponseDTO(
      String accountId, String orgId, String projectId, TemplateEntityGetResponse templateEntityGetResponse) {
    ValidateTemplateInputsResponseDTO validateTemplateInputsResponse =
        templateInputsValidator.validateNestedTemplateInputsForTemplates(
            accountId, orgId, projectId, templateEntityGetResponse);

    if (!validateTemplateInputsResponse.isValidYaml()) {
      return validateTemplateInputsResponse;
    }
    return ValidateTemplateInputsResponseDTO.builder().validYaml(true).build();
  }

  @Override
  public ValidateTemplateInputsResponseDTO validateTemplateInputsForYaml(
      String accountId, String orgId, String projectId, String yaml) {
    long start = System.currentTimeMillis();
    try (AutoLogContext ignore1 =
             new NgAutoLogContextForMethod(projectId, orgId, accountId, "validateTemplateInputs", OVERRIDE_NESTS);) {
      log.info(
          "[TemplateService] Starting validateTemplateInputsForYaml to pipeline yaml in project {}, org {}, account {}",
          projectId, orgId, accountId);
      return templateInputsValidator.validateNestedTemplateInputsForGivenYaml(accountId, orgId, projectId, yaml);
    } finally {
      log.info(
          "[TemplateService] validateTemplateInputsForYaml to pipeline yaml in project {}, org {}, account {} took {}ms ",
          projectId, orgId, accountId, System.currentTimeMillis() - start);
    }
  }

  @Override
  public YamlDiffResponseDTO getYamlDiffOnRefreshingTemplate(
      String accountId, String orgId, String projectId, String templateIdentifier, String versionLabel) {
    TemplateEntityGetResponse templateEntityGetResponse =
        getTemplate(accountId, orgId, projectId, templateIdentifier, versionLabel);
    TemplateEntity template = templateEntityGetResponse.getTemplateEntity();
    TemplateUtils.setupGitParentEntityDetails(
        accountId, orgId, projectId, template.getRepo(), template.getConnectorRef());
    String templateYaml = template.getYaml();
    String refreshedYaml = refreshLinkedTemplateInputs(accountId, orgId, projectId, templateYaml);

    return YamlDiffResponseDTO.builder().originalYaml(templateYaml).refreshedYaml(refreshedYaml).build();
  }

  @Override
  public void recursivelyRefreshTemplates(
      String accountId, String orgId, String projectId, String templateIdentifier, String versionLabel) {
    TemplateEntityGetResponse templateEntityGetResponse =
        getTemplate(accountId, orgId, projectId, templateIdentifier, versionLabel);
    TemplateEntity template = templateEntityGetResponse.getTemplateEntity();
    TemplateUtils.setupGitParentEntityDetails(
        accountId, orgId, projectId, template.getRepo(), template.getConnectorRef());
    ValidateTemplateInputsResponseDTO validateTemplateInputsResponse =
        getValidateTemplateInputsResponseDTO(accountId, orgId, projectId, templateEntityGetResponse);
    if (validateTemplateInputsResponse.isValidYaml()) {
      return;
    }

    refreshTemplateInputsForErrorNodes(
        accountId, orgId, projectId, validateTemplateInputsResponse.getErrorNodeSummary());
  }

  @Override
  public YamlFullRefreshResponseDTO recursivelyRefreshTemplatesForYaml(
      String accountId, String orgId, String projectId, String yaml) {
    ValidateTemplateInputsResponseDTO validateTemplateInputsResponse =
        validateTemplateInputsForYaml(accountId, orgId, projectId, yaml);

    if (validateTemplateInputsResponse.isValidYaml()) {
      return YamlFullRefreshResponseDTO.builder().shouldRefreshYaml(false).build();
    }
    GitEntityInfo gitEntityInfo = GitAwareContextHelper.getGitRequestParamsInfo();
    refreshTemplateInputsForErrorNodes(
        accountId, orgId, projectId, validateTemplateInputsResponse.getErrorNodeSummary());

    // Setting parent context again for fetching refreshLinkedTemplateInputs call
    GitAwareContextHelper.updateGitEntityContext(gitEntityInfo.toBuilder().build());
    String refreshedYaml = refreshLinkedTemplateInputs(accountId, orgId, projectId, yaml);
    return YamlFullRefreshResponseDTO.builder().shouldRefreshYaml(true).refreshedYaml(refreshedYaml).build();
  }

  private void refreshTemplateInputsForErrorNodes(
      String accountId, String orgId, String projectId, ErrorNodeSummary errorNodeSummary) {
    if (errorNodeSummary == null) {
      return;
    }

    Stack<ErrorNodeSummary> orderedStack = getProcessingOrderOfErrorNodes(errorNodeSummary);
    Set<TemplateResponseDTO> visitedTemplateSet = new HashSet<>();
    GitEntityInfo gitEntityInfo = GitAwareContextHelper.getGitRequestParamsInfo();
    while (!orderedStack.isEmpty()) {
      ErrorNodeSummary top = orderedStack.pop();
      if (top.getTemplateInfo() == null) {
        continue;
      }

      TemplateResponseDTO templateResponse = top.getTemplateResponse();
      if (!visitedTemplateSet.contains(templateResponse)) {
        accessControlClient.checkForAccessOrThrow(
            ResourceScope.of(accountId, templateResponse.getOrgIdentifier(), templateResponse.getProjectIdentifier()),
            Resource.of(TEMPLATE, templateResponse.getIdentifier()), PermissionTypes.TEMPLATE_EDIT_PERMISSION);
        GitAwareContextHelper.updateGitEntityContext(gitEntityInfo.toBuilder().build());
        updateTemplateYamlAndGitDetails(accountId, templateResponse);
        visitedTemplateSet.add(templateResponse);
      }
    }
  }

  private Stack<ErrorNodeSummary> getProcessingOrderOfErrorNodes(ErrorNodeSummary errorNodeSummary) {
    Stack<ErrorNodeSummary> processingStack = new Stack<>();
    Stack<ErrorNodeSummary> outputStack = new Stack<>();

    processingStack.push(errorNodeSummary);

    while (!processingStack.isEmpty()) {
      ErrorNodeSummary top = processingStack.pop();
      outputStack.push(top);
      if (EmptyPredicate.isEmpty(top.getChildrenErrorNodes())) {
        continue;
      }
      for (ErrorNodeSummary childrenErrorNodeSummary : top.getChildrenErrorNodes()) {
        processingStack.push(childrenErrorNodeSummary);
      }
    }
    return outputStack;
  }
}
