/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.template.helpers;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.template.beans.NGTemplateConstants.CUSTOM_DEPLOYMENT_TEMPLATE;
import static io.harness.template.beans.NGTemplateConstants.STABLE_VERSION;
import static io.harness.template.beans.NGTemplateConstants.TEMPLATE;
import static io.harness.template.beans.NGTemplateConstants.TEMPLATE_INPUTS;
import static io.harness.template.beans.NGTemplateConstants.TEMPLATE_REF;
import static io.harness.template.beans.NGTemplateConstants.TEMPLATE_VERSION_LABEL;

import io.harness.EntityType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.common.NGExpressionUtils;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum;
import io.harness.exception.InvalidIdentifierRefException;
import io.harness.ng.core.entitysetupusage.dto.EntitySetupUsageDTO;
import io.harness.pms.merger.YamlConfig;
import io.harness.pms.merger.fqn.FQN;
import io.harness.pms.merger.fqn.FQNNode;
import io.harness.preflight.PreFlightCheckMetadata;
import io.harness.template.TemplateReferenceProtoUtils;
import io.harness.template.entity.TemplateEntity;
import io.harness.template.helpers.crud.TemplateCrudHelper;
import io.harness.template.helpers.crud.TemplateCrudHelperFactory;
import io.harness.template.services.NGTemplateServiceHelper;
import io.harness.template.utils.TemplateUtils;
import io.harness.utils.IdentifierRefHelper;
import io.harness.utils.IdentifierRefProtoUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CDC)
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Singleton
@Slf4j
public class TemplateReferenceHelper {
  private static final String FQN_SEPARATOR = ".";
  TemplateYamlConversionHelper templateYamlConversionHelper;
  NGTemplateServiceHelper templateServiceHelper;
  TemplateSetupUsageHelper templateSetupUsageHelper;
  TemplateCrudHelperFactory templateCrudHelperFactory;

  public void deleteTemplateReferences(TemplateEntity templateEntity) {
    TemplateCrudHelper templateCrudHelper =
        templateCrudHelperFactory.getCrudHelperForTemplateType(templateEntity.getTemplateEntityType());
    if (!templateCrudHelper.supportsReferences()) {
      return;
    }

    templateSetupUsageHelper.deleteExistingSetupUsages(templateEntity);
  }

  public void populateTemplateReferences(TemplateEntity templateEntity) {
    TemplateCrudHelper templateCrudHelper =
        templateCrudHelperFactory.getCrudHelperForTemplateType(templateEntity.getTemplateEntityType());
    if (!templateCrudHelper.supportsReferences()) {
      return;
    }

    String entityYaml = templateYamlConversionHelper.convertTemplateYamlToEntityYaml(templateEntity);
    try {
      List<EntityDetailProtoDTO> referredEntities =
          new ArrayList<>(templateCrudHelper.getReferences(templateEntity, entityYaml));
      List<EntityDetailProtoDTO> referredEntitiesInLinkedTemplates =
          getNestedTemplateReferences(templateEntity.getAccountId(), templateEntity.getOrgIdentifier(),
              templateEntity.getProjectIdentifier(), entityYaml, true);
      referredEntities.addAll(referredEntitiesInLinkedTemplates);
      templateSetupUsageHelper.publishSetupUsageEvent(templateEntity, referredEntities);
    } catch (InvalidIdentifierRefException ex) {
      log.error("Error occurred while calculating template references {}", ex.getMessage());
      String scope = String.valueOf(templateEntity.getTemplateScope());
      throw new InvalidIdentifierRefException(String.format(
          "Unable to save to %s. Template can be saved to %s only when all the referenced entities are available in the scope.",
          scope, scope));
    }
  }

  /**
   * This method gets template references and other references linked through template inputs.
   * @param accountId
   * @param orgId
   * @param projectId
   * @param yaml yaml for which we want to get references.
   * @param shouldModifyFqn We don't want to modify FQN in case we are getting references for pipeline. For pipeline
   *     this will be false and true for templates.
   * @return
   */
  public List<EntityDetailProtoDTO> getNestedTemplateReferences(
      String accountId, String orgId, String projectId, String yaml, boolean shouldModifyFqn) {
    List<EntityDetailProtoDTO> referredEntities = new ArrayList<>();
    YamlConfig yamlConfig = new YamlConfig(yaml);
    TemplateUtils.setupGitParentEntityDetails(accountId, orgId, projectId, null, null);
    Map<FQN, Object> fqnToValueMap = yamlConfig.getFqnToValueMap();
    Set<FQN> fqnSet = new LinkedHashSet<>(yamlConfig.getFqnToValueMap().keySet());
    Map<String, Object> fqnStringToValueMap = new HashMap<>();
    fqnToValueMap.forEach((fqn, value) -> fqnStringToValueMap.put(fqn.getExpressionFqn(), value));
    fqnSet.forEach(key -> {
      if (key.getFqnList().size() >= 2) {
        List<FQNNode> fqnList = new ArrayList<>(key.getFqnList());
        FQNNode lastNode = fqnList.get(fqnList.size() - 1);
        FQNNode secondLastNode = fqnList.get(fqnList.size() - 2);
        if (TEMPLATE_REF.equals(lastNode.getKey())
            && (TEMPLATE.equals(secondLastNode.getKey())
                || CUSTOM_DEPLOYMENT_TEMPLATE.equals(secondLastNode.getKey()))) {
          String identifier = ((JsonNode) fqnToValueMap.get(key)).asText();
          IdentifierRef templateIdentifierRef =
              IdentifierRefHelper.getIdentifierRef(identifier, accountId, orgId, projectId);

          // remove templateRef from FQN and add versionLabel to FQN to fetch corresponding template version.
          fqnList.remove(fqnList.size() - 1);
          fqnList.add(FQNNode.builder().nodeType(FQNNode.NodeType.KEY).key(TEMPLATE_VERSION_LABEL).build());
          JsonNode versionLabelNode = (JsonNode) fqnToValueMap.get(FQN.builder().fqnList(fqnList).build());
          String versionLabel = "";
          if (versionLabelNode == null) {
            Optional<TemplateEntity> templateEntity =
                templateServiceHelper.getMetadataOrThrowExceptionIfInvalid(templateIdentifierRef.getAccountIdentifier(),
                    templateIdentifierRef.getOrgIdentifier(), templateIdentifierRef.getProjectIdentifier(),
                    templateIdentifierRef.getIdentifier(), versionLabel, false);
            if (templateEntity.isPresent()) {
              versionLabel = templateEntity.get().getVersionLabel();
            }
          } else {
            versionLabel = versionLabelNode.asText();
          }

          // remove versionLabel from FQN.
          fqnList.remove(fqnList.size() - 1);
          String fqn = FQN.builder().fqnList(fqnList).build().getExpressionFqn();
          // add linked template as reference
          referredEntities.add(getTemplateReference(
              templateIdentifierRef, versionLabelNode == null ? STABLE_VERSION : versionLabel, fqn));
          // add runtime entities referred by linked template as references
          referredEntities.addAll(getEntitiesReferredByTemplate(accountId, orgId, projectId, templateIdentifierRef,
              versionLabel, fqnStringToValueMap, fqn, shouldModifyFqn));
        }
      }
    });
    return referredEntities;
  }

  private List<EntityDetailProtoDTO> getEntitiesReferredByTemplate(String accountId, String orgId, String projectId,
      IdentifierRef templateIdentifierRef, String versionLabel, Map<String, Object> fqnStringToValueMap,
      String linkedTemplateFqnExpression, boolean shouldModifyFqn) {
    List<EntityDetailProtoDTO> referredEntitiesInTemplate = new ArrayList<>();
    List<EntitySetupUsageDTO> referredUsagesInTemplate = templateSetupUsageHelper.getReferencesOfTemplate(
        templateIdentifierRef.getAccountIdentifier(), templateIdentifierRef.getOrgIdentifier(),
        templateIdentifierRef.getProjectIdentifier(), templateIdentifierRef.getIdentifier(), versionLabel);

    if (isEmpty(referredUsagesInTemplate)) {
      return referredEntitiesInTemplate;
    }

    for (EntitySetupUsageDTO referredEntity : referredUsagesInTemplate) {
      if (referredEntity.getReferredEntity() != null && referredEntity.getReferredEntity().getEntityRef() != null
          && referredEntity.getReferredEntity().getEntityRef() instanceof IdentifierRef) {
        IdentifierRef identifierRefOfReferredEntity = (IdentifierRef) referredEntity.getReferredEntity().getEntityRef();

        // we only want referred entity which were runtime input in linked template.
        if (isReferredEntityForRuntimeInput(identifierRefOfReferredEntity)) {
          String fqn = identifierRefOfReferredEntity.getMetadata().get(PreFlightCheckMetadata.FQN);
          String completeFqnForReferredEntity = linkedTemplateFqnExpression + FQN_SEPARATOR + fqn;
          JsonNode value = (JsonNode) fqnStringToValueMap.get(completeFqnForReferredEntity);

          if (value != null && isNotEmpty(value.asText())) {
            referredEntitiesInTemplate.add(
                convertToEntityDetailProtoDTO(accountId, orgId, projectId, completeFqnForReferredEntity, value.asText(),
                    referredEntity.getReferredEntity().getType(), shouldModifyFqn));
          }
        }
      }
    }
    return referredEntitiesInTemplate;
  }

  private boolean isReferredEntityForRuntimeInput(IdentifierRef identifierRefOfReferredEntity) {
    return identifierRefOfReferredEntity.getMetadata() != null
        && isNotEmpty(identifierRefOfReferredEntity.getMetadata().get(PreFlightCheckMetadata.FQN))
        && isNotEmpty(identifierRefOfReferredEntity.getMetadata().get(PreFlightCheckMetadata.EXPRESSION))
        && NGExpressionUtils.matchesInputSetPattern(identifierRefOfReferredEntity.getIdentifier());
  }

  private EntityDetailProtoDTO convertToEntityDetailProtoDTO(String accountId, String orgId, String projectId,
      String fullQualifiedDomainName, String entityRefValue, EntityType entityType, boolean shouldModifyFqn) {
    Map<String, String> metadata = new HashMap<>();
    if (shouldModifyFqn) {
      fullQualifiedDomainName = replaceBaseIdentifierInFQNWithTemplateInputs(fullQualifiedDomainName);
    }
    metadata.put(PreFlightCheckMetadata.FQN, fullQualifiedDomainName);

    if (NGExpressionUtils.isRuntimeOrExpressionField(entityRefValue)) {
      metadata.put(PreFlightCheckMetadata.EXPRESSION, entityRefValue);
      IdentifierRef identifierRef = IdentifierRefHelper.createIdentifierRefWithUnknownScope(
          accountId, orgId, projectId, entityRefValue, metadata);
      return EntityDetailProtoDTO.newBuilder()
          .setIdentifierRef(IdentifierRefProtoUtils.createIdentifierRefProtoFromIdentifierRef(identifierRef))
          .setType(getEntityTypeProtoEnumFromEntityType(entityType))
          .build();
    } else {
      IdentifierRef identifierRef =
          IdentifierRefHelper.getIdentifierRef(entityRefValue, accountId, orgId, projectId, metadata);
      return EntityDetailProtoDTO.newBuilder()
          .setIdentifierRef(IdentifierRefProtoUtils.createIdentifierRefProtoFromIdentifierRef(identifierRef))
          .setType(getEntityTypeProtoEnumFromEntityType(entityType))
          .build();
    }
  }

  private EntityDetailProtoDTO getTemplateReference(IdentifierRef identifierRef, String versionLabel, String fqn) {
    Map<String, String> metadata = new HashMap<>();
    metadata.put(PreFlightCheckMetadata.FQN, fqn);
    return EntityDetailProtoDTO.newBuilder()
        .setType(EntityTypeProtoEnum.TEMPLATE)
        .setTemplateRef(TemplateReferenceProtoUtils.createTemplateReferenceProtoFromIdentifierRef(
            identifierRef, versionLabel, metadata))
        .build();
  }

  private String replaceBaseIdentifierInFQNWithTemplateInputs(String fqn) {
    int indexOfFirstDot = fqn.indexOf(FQN_SEPARATOR);
    if (indexOfFirstDot != -1) {
      return TEMPLATE_INPUTS + fqn.substring(indexOfFirstDot);
    }
    return fqn;
  }

  private EntityTypeProtoEnum getEntityTypeProtoEnumFromEntityType(EntityType entityType) {
    return EntityTypeProtoEnum.valueOf(entityType.name());
  }
}
