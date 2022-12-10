/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.customDeployment.eventlistener;

import static software.wings.beans.AccountType.log;

import static java.lang.String.format;
import static java.util.Objects.isNull;

import io.harness.EntityType;
import io.harness.beans.IdentifierRef;
import io.harness.beans.InfraDefReference;
import io.harness.beans.Scope;
import io.harness.cdng.customdeploymentng.CustomDeploymentInfrastructureHelper;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.entitysetupusage.dto.EntitySetupUsageDTO;
import io.harness.ng.core.entitysetupusage.service.EntitySetupUsageService;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity.InfrastructureEntityKeys;
import io.harness.ng.core.infrastructure.services.InfrastructureEntityService;

import com.google.inject.Inject;
import com.mongodb.client.result.UpdateResult;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.mongodb.core.query.Update;

public class CustomDeploymentEntityCRUDEventHandler {
  @Inject EntitySetupUsageService entitySetupUsageService;
  @Inject InfrastructureEntityService infrastructureEntityService;
  @Inject CustomDeploymentInfrastructureHelper customDeploymentInfrastructureHelper;
  public static final String STABLE_VERSION = "__STABLE__";

  public boolean updateInfraAsObsolete(
      String accountRef, String orgRef, String projectRef, String identifier, String versionLabel) {
    Scope scope =
        Scope.builder().accountIdentifier(accountRef).orgIdentifier(orgRef).projectIdentifier(projectRef).build();
    String entityFQN = getFullyQualifiedIdentifier(accountRef, orgRef, projectRef, identifier) + "/";
    if (versionLabel == null) {
      versionLabel = STABLE_VERSION;
    }
    entityFQN = entityFQN + versionLabel + "/";
    List<EntitySetupUsageDTO> entitySetupUsages = entitySetupUsageService.listAllEntityUsagePerReferredEntityScope(
        scope, entityFQN, EntityType.TEMPLATE, EntityType.INFRASTRUCTURE, null, null);
    if (entitySetupUsages.isEmpty()) {
      log.info("No infra Update for  Deployment Template with id :{}, for AccountId : {}", identifier, accountRef);
      return true;
    }
    Map<String, List<String>> envToOrgProjectIdMap = new HashMap<>();
    Map<String, List<String>> envToInfraMap = new HashMap<>();

    String templateYaml =
        customDeploymentInfrastructureHelper.getTemplateYaml(accountRef, orgRef, projectRef, identifier, versionLabel);
    for (EntitySetupUsageDTO entitySetupUsage : entitySetupUsages) {
      if (!isNull(entitySetupUsage) && !isNull(entitySetupUsage.getReferredByEntity())) {
        String infraId = entitySetupUsage.getReferredByEntity().getEntityRef().getIdentifier();
        String environment = getEnvironment(entitySetupUsage);
        String orgIdentifierEnv = entitySetupUsage.getReferredByEntity().getEntityRef().getOrgIdentifier();
        String projectIdentifierEnv = entitySetupUsage.getReferredByEntity().getEntityRef().getProjectIdentifier();
        String infraYaml = getInfraYaml(entitySetupUsage, accountRef);
        boolean updateRequired =
            customDeploymentInfrastructureHelper.checkIfInfraIsObsolete(infraYaml, templateYaml, accountRef);
        if (updateRequired) {
          envToOrgProjectIdMap.put(environment, Arrays.asList(orgIdentifierEnv, projectIdentifierEnv));
          envToInfraMap.computeIfAbsent(environment, k -> new ArrayList<>()).add(infraId);
        }
      }
    }
    updateInfrasAsObsolete(envToInfraMap, accountRef, envToOrgProjectIdMap);
    return true;
  }

  public String getInfraYaml(EntitySetupUsageDTO entitySetupUsage, String accRef) {
    String infraId = entitySetupUsage.getReferredByEntity().getEntityRef().getIdentifier();
    String orgId = entitySetupUsage.getReferredByEntity().getEntityRef().getOrgIdentifier();
    String projectId = entitySetupUsage.getReferredByEntity().getEntityRef().getProjectIdentifier();
    String environment = getEnvironment(entitySetupUsage);
    Optional<InfrastructureEntity> infrastructureOptional =
        infrastructureEntityService.get(entitySetupUsage.getReferredByEntity().getEntityRef().getAccountIdentifier(),
            orgId, projectId, environment, infraId);
    if (!infrastructureOptional.isPresent()) {
      log.error("No infra found to update for given deployment template with acc Id:{}", accRef);
      return null;
    }
    InfrastructureEntity infrastructure = infrastructureOptional.get();
    return infrastructure.getYaml();
  }

  private String getEnvironment(EntitySetupUsageDTO entitySetupUsage) {
    if (entitySetupUsage.getReferredByEntity().getEntityRef() instanceof InfraDefReference) {
      return ((InfraDefReference) entitySetupUsage.getReferredByEntity().getEntityRef()).getEnvIdentifier();
    } else {
      return ((IdentifierRef) entitySetupUsage.getReferredByEntity().getEntityRef()).getMetadata().get("envId");
    }
  }
  public void updateInfrasAsObsolete(Map<String, List<String>> envToInfraMap, String accountIdentifier,
      Map<String, List<String>> envToOrgProjectIdMap) {
    for (String environment : envToInfraMap.keySet()) {
      updateInfras(envToInfraMap.get(environment), environment, accountIdentifier, envToOrgProjectIdMap);
    }
  }

  public void updateInfras(
      List<String> infras, String environment, String accountId, Map<String, List<String>> envMap) {
    String orgId = envMap.get(environment).get(0);
    String projectId = envMap.get(environment).get(1);
    Update update = new Update();
    update.set(InfrastructureEntityKeys.obsolete, true);
    UpdateResult updateResult =
        infrastructureEntityService.batchUpdateInfrastructure(accountId, orgId, projectId, environment, infras, update);
    log.info("Infras updated successfully for accRef :{}, Environment :{} with updated result :{}", accountId,
        environment, updateResult);
  }

  public String getFullyQualifiedIdentifier(
      String accountId, String orgIdentifier, String projectIdentifier, String identifier) {
    validateIdentifier(identifier);
    if (EmptyPredicate.isNotEmpty(projectIdentifier)) {
      validateOrgIdentifier(orgIdentifier);
      validateAccountIdentifier(accountId);
      return format("%s/%s/%s/%s", accountId, orgIdentifier, projectIdentifier, identifier);
    } else if (EmptyPredicate.isNotEmpty(orgIdentifier)) {
      validateAccountIdentifier(accountId);
      return format("%s/%s/%s", accountId, orgIdentifier, identifier);
    } else if (EmptyPredicate.isNotEmpty(accountId)) {
      return format("%s/%s", accountId, identifier);
    }
    throw new InvalidRequestException("No account ID provided.");
  }

  private void validateIdentifier(String identifier) {
    if (EmptyPredicate.isEmpty(identifier)) {
      throw new InvalidRequestException("No identifier provided.");
    }
  }

  private void validateAccountIdentifier(String accountIdentifier) {
    if (EmptyPredicate.isEmpty(accountIdentifier)) {
      throw new InvalidRequestException("No account identifier provided.");
    }
  }

  private void validateOrgIdentifier(String orgIdentifier) {
    if (EmptyPredicate.isEmpty(orgIdentifier)) {
      throw new InvalidRequestException("No org identifier provided.");
    }
  }
}
