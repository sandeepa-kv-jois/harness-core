/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.template.resources;

import static io.harness.NGCommonEntityConstants.NEXT_REL;
import static io.harness.NGCommonEntityConstants.PAGE;
import static io.harness.NGCommonEntityConstants.PAGE_SIZE;
import static io.harness.NGCommonEntityConstants.PREVIOUS_REL;
import static io.harness.NGCommonEntityConstants.SELF_REL;
import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.ng.core.template.TemplateEntityConstants.ALL;
import static io.harness.ng.core.template.TemplateEntityConstants.LAST_UPDATES_TEMPLATE;
import static io.harness.ng.core.template.TemplateEntityConstants.STABLE_TEMPLATE;

import static java.lang.Long.parseLong;
import static java.lang.String.format;
import static javax.ws.rs.core.UriBuilder.fromPath;
import static org.apache.commons.lang3.StringUtils.isNumeric;

import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.OrgIdentifier;
import io.harness.accesscontrol.ProjectIdentifier;
import io.harness.accesscontrol.ResourceIdentifier;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.git.model.ChangeType;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.ng.core.template.TemplateEntityType;
import io.harness.ng.core.template.TemplateListType;
import io.harness.ng.core.template.TemplateMetadataSummaryResponseDTO;
import io.harness.ng.core.template.TemplateResponseDTO;
import io.harness.ng.core.template.TemplateWithInputsResponseDTO;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.spec.server.template.v1.model.GitCreateDetails;
import io.harness.spec.server.template.v1.model.GitFindDetails;
import io.harness.spec.server.template.v1.model.GitUpdateDetails;
import io.harness.spec.server.template.v1.model.TemplateMetadataSummaryResponse;
import io.harness.spec.server.template.v1.model.TemplateUpdateStableResponse;
import io.harness.template.TemplateFilterPropertiesDTO;
import io.harness.template.beans.FilterParamsDTO;
import io.harness.template.beans.PageParamsDTO;
import io.harness.template.beans.PermissionTypes;
import io.harness.template.beans.TemplateFilterProperties;
import io.harness.template.entity.TemplateEntity;
import io.harness.template.mappers.NGTemplateDtoMapper;
import io.harness.template.services.NGTemplateService;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.ws.rs.core.Link;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;

@OwnedBy(CDC)
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
@NextGenManagerAuth
public class TemplateResourceApiUtils {
  public static final String TEMPLATE = "TEMPLATE";
  public static final int FIRST_PAGE = 1;

  private final NGTemplateService templateService;
  private final AccessControlClient accessControlClient;
  private final TemplateResourceApiMapper templateResourceApiMapper;

  public Response getTemplate(@AccountIdentifier String account, @OrgIdentifier String org,
      @ProjectIdentifier String project, @ResourceIdentifier String templateIdentifier, String versionLabel,
      boolean deleted, String branch, String parentConnectorRef, String parentRepoName, String parentAccountId,
      String parentOrgId, String parentProjectId, Boolean getInputYaml) {
    // if label is not given, return stable template
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(account, org, project),
        Resource.of(TEMPLATE, templateIdentifier), PermissionTypes.TEMPLATE_VIEW_PERMISSION);
    GitAwareContextHelper.populateGitDetails(GitEntityInfo.builder()
                                                 .branch(branch)
                                                 .parentEntityConnectorRef(parentConnectorRef)
                                                 .parentEntityRepoName(parentRepoName)
                                                 .parentEntityAccountIdentifier(parentAccountId)
                                                 .parentEntityOrgIdentifier(parentOrgId)
                                                 .parentEntityProjectIdentifier(parentProjectId)
                                                 .build());
    if (String.valueOf(getInputYaml).equals("null")) {
      getInputYaml = false;
    }

    if (getInputYaml == true) {
      // returns template along with templateInputs yaml
      log.info(String.format(
          "Gets Template along with Template inputs for template with identifier %s in project %s, org %s, account %s",
          templateIdentifier, project, org, account));
      TemplateWithInputsResponseDTO templateWithInputs =
          templateService.getTemplateWithInputs(account, org, project, templateIdentifier, versionLabel);
      String version = "0";
      if (templateWithInputs != null && templateWithInputs.getTemplateResponseDTO() != null
          && templateWithInputs.getTemplateResponseDTO().getVersion() != null) {
        version = String.valueOf(templateWithInputs.getTemplateResponseDTO().getVersion());
      }
      return Response.ok()
          .entity(templateResourceApiMapper.toTemplateWithInputResponse(templateWithInputs))
          .tag(version)
          .build();
    } else {
      log.info(
          String.format("Retrieving Template with identifier %s and versionLabel %s in project %s, org %s, account %s",
              templateIdentifier, versionLabel, project, org, account));
      Optional<TemplateEntity> templateEntity =
          templateService.get(account, org, project, templateIdentifier, versionLabel, deleted, false);

      String version = "0";
      if (templateEntity.isPresent()) {
        version = templateEntity.get().getVersion().toString();
      }
      TemplateResponseDTO templateResponseDTO = NGTemplateDtoMapper.writeTemplateResponseDto(templateEntity.orElseThrow(
          ()
              -> new InvalidRequestException(String.format(
                  "Template with the given Identifier: %s and %s does not exist or has been deleted",
                  templateIdentifier,
                  EmptyPredicate.isEmpty(versionLabel) ? "stable versionLabel" : "versionLabel: " + versionLabel))));
      return Response.ok()
          .entity(templateResourceApiMapper.toTemplateResponseDefault(templateResponseDTO))
          .tag(version)
          .build();
    }
  }

  public Response createTemplate(@AccountIdentifier String account, @OrgIdentifier String org,
      @ProjectIdentifier String project, GitCreateDetails gitEntityCreateInfo, String templateYaml,
      boolean setDefaultTemplate, String comments) {
    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(account, org, project), Resource.of(TEMPLATE, null), PermissionTypes.TEMPLATE_EDIT_PERMISSION);
    GitAwareContextHelper.populateGitDetails(templateResourceApiMapper.populateGitCreateDetails(gitEntityCreateInfo));
    TemplateEntity templateEntity = NGTemplateDtoMapper.toTemplateEntity(account, org, project, templateYaml);
    log.info(String.format("Creating Template with identifier %s with label %s in project %s, org %s, account %s",
        templateEntity.getIdentifier(), templateEntity.getVersionLabel(), project, org, account));
    TemplateEntity createdTemplate = templateService.create(templateEntity, setDefaultTemplate, comments);
    TemplateResponseDTO templateResponseDTO = NGTemplateDtoMapper.writeTemplateResponseDto(createdTemplate);
    return Response.status(Response.Status.CREATED)
        .entity(templateResourceApiMapper.toTemplateResponse(templateResponseDTO))
        .tag(createdTemplate.getVersion().toString())
        .build();
  }

  public Response updateTemplate(@AccountIdentifier String account, @OrgIdentifier String org,
      @ProjectIdentifier String project, @ResourceIdentifier String templateIdentifier, String versionLabel,
      GitUpdateDetails gitEntityInfo, String templateYaml, boolean setDefaultTemplate, String comments) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(account, org, project),
        Resource.of(TEMPLATE, templateIdentifier), PermissionTypes.TEMPLATE_EDIT_PERMISSION);
    GitAwareContextHelper.populateGitDetails(templateResourceApiMapper.populateGitUpdateDetails(gitEntityInfo));
    TemplateEntity templateEntity =
        NGTemplateDtoMapper.toTemplateEntity(account, org, project, templateIdentifier, versionLabel, templateYaml);
    log.info(
        String.format("Updating Template with identifier %s with versionLabel %s in project %s, org %s, account %s",
            templateEntity.getIdentifier(), templateEntity.getVersionLabel(), project, org, account));
    templateEntity = templateEntity.withVersion(isNumeric("ifMatch") ? parseLong("ifMatch") : null);
    TemplateEntity updatedTemplate =
        templateService.updateTemplateEntity(templateEntity, ChangeType.MODIFY, setDefaultTemplate, comments);
    TemplateResponseDTO templateResponseDTO = NGTemplateDtoMapper.writeTemplateResponseDto(updatedTemplate);
    return Response.ok()
        .entity(templateResourceApiMapper.toTemplateResponse(templateResponseDTO))
        .tag(updatedTemplate.getVersion().toString())
        .build();
  }

  public Response updateStableTemplate(@AccountIdentifier String account, @OrgIdentifier String org,
      @ProjectIdentifier String project, @ResourceIdentifier String templateIdentifier, String versionLabel,
      GitFindDetails gitEntityBasicInfo, String comments) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(account, org, project),
        Resource.of(TEMPLATE, templateIdentifier), PermissionTypes.TEMPLATE_EDIT_PERMISSION);
    GitAwareContextHelper.populateGitDetails(templateResourceApiMapper.populateGitFindDetails(gitEntityBasicInfo));
    log.info(String.format(
        "Updating Stable Template with identifier %s with versionLabel %s in project %s, org %s, account %s",
        templateIdentifier, versionLabel, project, org, account));

    TemplateEntity templateEntity =
        templateService.updateStableTemplateVersion(account, org, project, templateIdentifier, versionLabel, comments);
    TemplateUpdateStableResponse templateUpdateStableResponse = new TemplateUpdateStableResponse();
    templateUpdateStableResponse.setStableVersion(templateEntity.getVersionLabel());
    return Response.ok().entity(templateUpdateStableResponse).tag(templateEntity.getVersion().toString()).build();
  }

  public Response deleteTemplate(@AccountIdentifier String account, @OrgIdentifier String org,
      @ProjectIdentifier String project, @ResourceIdentifier String templateIdentifier, String versionLabel,
      String comments) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(account, org, project),
        Resource.of(TEMPLATE, templateIdentifier), PermissionTypes.TEMPLATE_DELETE_PERMISSION);
    log.info(String.format("Deleting Template with identifier %s and versionLabel %s in project %s, org %s, account %s",
        templateIdentifier, versionLabel, project, org, account));

    templateService.delete(account, org, project, templateIdentifier, versionLabel,
        isNumeric("ifMatch") ? parseLong("ifMatch") : null, comments);
    return Response.status(Response.Status.NO_CONTENT).build();
  }

  public Response getTemplates(@AccountIdentifier String account, @OrgIdentifier String org,
      @ProjectIdentifier String project, int page, int limit, String sort, String order, String searchTerm,
      String listType, boolean recursive, List<String> names, List<String> identifiers, String description,
      List<String> entityTypes, List<String> childTypes) {
    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(account, org, project), Resource.of(TEMPLATE, null), PermissionTypes.TEMPLATE_VIEW_PERMISSION);
    log.info(String.format("Get List of templates in project: %s, org: %s, account: %s", project, org, account));
    TemplateFilterPropertiesDTO filterProperties = new TemplateFilterPropertiesDTO();
    List<TemplateEntityType> templateEntityTypes =
        entityTypes.stream().map(x -> TemplateEntityType.getTemplateType(x)).collect(Collectors.toList());
    filterProperties.setTemplateEntityTypes(templateEntityTypes);
    filterProperties.setTemplateNames(names);
    filterProperties.setDescription(description);
    filterProperties.setTemplateIdentifiers(identifiers);
    filterProperties.setChildTypes(childTypes);
    TemplateFilterProperties templateFilterProperties =
        NGTemplateDtoMapper.toTemplateFilterProperties(filterProperties);
    String type = toListType(listType);
    TemplateListType templateListType = TemplateListType.getTemplateType(type);
    FilterParamsDTO filterParamsDTO = NGTemplateDtoMapper.prepareFilterParamsDTO(
        searchTerm, "", templateListType, templateFilterProperties, recursive, false);

    String sortQuery = templateResourceApiMapper.mapSort(sort, order);
    PageParamsDTO pageParamsDTO =
        NGTemplateDtoMapper.preparePageParamsDTO(page, limit, Collections.singletonList(sortQuery));

    Page<TemplateMetadataSummaryResponseDTO> templateMetadataSummaryResponseDTOS =
        templateService.listTemplateMetadata(account, org, project, filterParamsDTO, pageParamsDTO)
            .map(NGTemplateDtoMapper::prepareTemplateMetaDataSummaryResponseDto);

    Page<TemplateMetadataSummaryResponse> templateMetadataSummaryResponses =
        templateMetadataSummaryResponseDTOS.map(templateResourceApiMapper::mapToTemplateMetadataResponse);
    List<TemplateMetadataSummaryResponse> templateList = templateMetadataSummaryResponses.getContent();
    ResponseBuilder responseBuilder = Response.ok();
    if (isEmpty(org)) {
      ResponseBuilder responseBuilderWithLinks =
          addLinksHeader(responseBuilder, "/v1/templates", templateList.size(), page, limit);
      return responseBuilderWithLinks.entity(templateList).build();
    } else if (isEmpty(project)) {
      ResponseBuilder responseBuilderWithLinks =
          addLinksHeader(responseBuilder, format("/v1/orgs/%s/templates", org), templateList.size(), page, limit);
      return responseBuilderWithLinks.entity(templateList).build();
    } else {
      ResponseBuilder responseBuilderWithLinks = addLinksHeader(
          responseBuilder, format("/v1/orgs/%s/projects/%s/templates", org, project), templateList.size(), page, limit);
      return responseBuilderWithLinks.entity(templateList).build();
    }
  }

  public ResponseBuilder addLinksHeader(
      ResponseBuilder responseBuilder, String path, int currentResultCount, int page, int limit) {
    ArrayList<Link> links = new ArrayList<>();

    links.add(
        Link.fromUri(fromPath(path).queryParam(PAGE, page).queryParam(PAGE_SIZE, limit).build()).rel(SELF_REL).build());

    if (page >= FIRST_PAGE) {
      links.add(Link.fromUri(fromPath(path).queryParam(PAGE, page - 1).queryParam(PAGE_SIZE, limit).build())
                    .rel(PREVIOUS_REL)
                    .build());
    }
    if (limit == currentResultCount) {
      links.add(Link.fromUri(fromPath(path).queryParam(PAGE, page + 1).queryParam(PAGE_SIZE, limit).build())
                    .rel(NEXT_REL)
                    .build());
    }
    return responseBuilder.links(links.toArray(new Link[links.size()]));
  }
  public String toListType(String listType) {
    String type;
    if (isEmpty(listType)) {
      listType = "ALL";
    }
    if (listType.equals("LAST_UPDATES_TEMPLATE")) {
      type = LAST_UPDATES_TEMPLATE;
    } else if (listType.equals("STABLE_TEMPLATE")) {
      type = STABLE_TEMPLATE;
    } else if (listType.equals("ALL")) {
      type = ALL;
    } else {
      throw new InvalidRequestException(String.format(
          "Expected query param 'type' to be of value LAST_UPDATES_TEMPLATE, STABLE_TEMPLATE, ALL. [%s] value Not allowed",
          listType));
    }
    return type;
  }
}
