/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.service.services.impl;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.mappers.NGServiceEntityMapper;
import io.harness.ng.core.service.yaml.NGServiceConfig;
import io.harness.ng.core.setupusage.SetupUsageHelper;
import io.harness.ng.core.setupusage.SetupUsageOwnerEntity;
import io.harness.ng.core.template.TemplateReferenceRequestDTO;
import io.harness.remote.client.NGRestUtils;
import io.harness.template.remote.TemplateResourceClient;
import io.harness.walktree.visitor.SimpleVisitorFactory;
import io.harness.walktree.visitor.entityreference.EntityReferenceExtractorVisitor;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Set;

@Singleton
@OwnedBy(HarnessTeam.CDC)
public class ServiceEntitySetupUsageHelper {
  @Inject private SimpleVisitorFactory simpleVisitorFactory;
  @Inject private SetupUsageHelper setupUsageHelper;
  @Inject private TemplateResourceClient templateResourceClient;

  /**
   * Update setup usages for the current service entity
   */
  protected void updateSetupUsages(ServiceEntity entity) {
    final String ROOT_LEVEL_NAME = "service";
    final Set<EntityDetailProtoDTO> referredEntities = getAllReferredEntities(ROOT_LEVEL_NAME, entity);
    final SetupUsageOwnerEntity ownerEntity = getOwnerEntity(entity);
    if (isEmpty(referredEntities)) {
      setupUsageHelper.deleteServiceSetupUsages(ownerEntity);
    } else {
      setupUsageHelper.publishServiceEntitySetupUsage(ownerEntity, referredEntities);
    }
  }

  /**
   * Delete all setup usages where the 'referred by' is the current service entity
   */
  protected void deleteSetupUsages(ServiceEntity entity) {
    setupUsageHelper.deleteServiceSetupUsages(getOwnerEntity(entity));
  }

  private Set<EntityDetailProtoDTO> getAllReferredEntities(String rootName, ServiceEntity entity) {
    List<String> qualifiedNameList = List.of(rootName);
    EntityReferenceExtractorVisitor visitor = simpleVisitorFactory.obtainEntityReferenceExtractorVisitor(
        entity.getAccountId(), entity.getOrgIdentifier(), entity.getProjectIdentifier(), qualifiedNameList);
    NGServiceConfig ngServiceConfig = NGServiceEntityMapper.toNGServiceConfig(entity);
    visitor.walkElementTree(ngServiceConfig.getNgServiceV2InfoConfig());
    Set<EntityDetailProtoDTO> entityReferences = visitor.getEntityReferenceSet();
    if (entity.hasTemplateReferences()) {
      entityReferences.addAll(getTemplateReferencesForGivenYaml(
          entity.getAccountId(), entity.getOrgIdentifier(), entity.getProjectIdentifier(), entity.getYaml()));
    }
    return entityReferences;
  }

  private SetupUsageOwnerEntity getOwnerEntity(ServiceEntity entity) {
    return SetupUsageOwnerEntity.builder()
        .accountId(entity.getAccountId())
        .orgIdentifier(entity.getOrgIdentifier())
        .projectIdentifier(entity.getProjectIdentifier())
        .identifier(entity.getIdentifier())
        .name(entity.getName())
        .type(EntityTypeProtoEnum.SERVICE)
        .build();
  }

  public List<EntityDetailProtoDTO> getTemplateReferencesForGivenYaml(
      String accountId, String orgId, String projectId, String yaml) {
    // todo(@hinger): support fetching from git once service is supported
    return NGRestUtils.getResponse(templateResourceClient.getTemplateReferenceForGivenYaml(
        accountId, orgId, projectId, null, null, null, TemplateReferenceRequestDTO.builder().yaml(yaml).build()));
  }
}
