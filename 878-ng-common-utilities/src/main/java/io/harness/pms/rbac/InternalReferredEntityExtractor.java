/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.rbac;

import io.harness.EntityType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.entitysetupusageclient.remote.EntitySetupUsageClient;
import io.harness.ng.core.EntityDetail;
import io.harness.ng.core.entitysetupusage.dto.EntityReferencesDTO;
import io.harness.ng.core.entitysetupusage.dto.EntitySetupUsageBatchDTO;
import io.harness.ng.core.entitysetupusage.dto.EntitySetupUsageDTO;
import io.harness.remote.client.NGRestUtils;
import io.harness.utils.NGFeatureFlagHelperService;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Singleton
@OwnedBy(HarnessTeam.PIPELINE)
public class InternalReferredEntityExtractor {
  private static final int MAX_PAGE_SIZE = 50;
  @Inject NGFeatureFlagHelperService ngFeatureFlagHelperService;

  @Inject private EntitySetupUsageClient entitySetupUsageClient;

  public Map<EntityType, EntityType> getReferredByEntityTypeToReferredEntityTypeMap(String accountId) {
    Map<EntityType, EntityType> referredByEntityTypeToReferredEntityTypeMap = new HashMap<>();

    if (!ngFeatureFlagHelperService.isEnabled(accountId, FeatureName.PL_CONNECTOR_ENCRYPTION_PRIVILEGED_CALL)) {
      referredByEntityTypeToReferredEntityTypeMap.put(EntityType.CONNECTORS, EntityType.SECRETS);
    }
    return referredByEntityTypeToReferredEntityTypeMap;
  }

  public List<EntityDetail> extractInternalEntities(String accountIdentifier, List<EntityDetail> entityDetails) {
    List<EntityDetail> referredEntitiesContainingInternalEntities =
        entityDetails.stream()
            .filter(entityDetail -> hasInternalReferredEntities(accountIdentifier, entityDetail.getType()))
            .collect(Collectors.toList());
    List<EntityDetail> internalReferredEntities = new ArrayList<>();
    Map<EntityType, List<String>> entityTypeEntityDetailMap = new HashMap<>();
    for (EntityDetail entityDetail : referredEntitiesContainingInternalEntities) {
      List<String> entities = entityTypeEntityDetailMap.getOrDefault(entityDetail.getType(), new ArrayList<>());
      entities.add(entityDetail.getEntityRef().getFullyQualifiedName());
      entityTypeEntityDetailMap.put(entityDetail.getType(), entities);
    }
    for (Map.Entry<EntityType, List<String>> entry : entityTypeEntityDetailMap.entrySet()) {
      List<List<String>> partitionedList = Lists.partition(entry.getValue(), MAX_PAGE_SIZE);
      for (List<String> entityDetail : partitionedList) {
        EntityReferencesDTO entityReferencesDTO = NGRestUtils.getResponse(
            entitySetupUsageClient.listAllReferredUsagesBatch(accountIdentifier, entityDetail, entry.getKey(),
                getReferredByEntityTypeToReferredEntityTypeMap(accountIdentifier).get(entry.getKey())),
            "Internal refereed entities could not be extracted after {} attempts.");
        for (EntitySetupUsageBatchDTO entitySetupUsageBatchDTO : entityReferencesDTO.getEntitySetupUsageBatchList()) {
          internalReferredEntities.addAll(entitySetupUsageBatchDTO.getReferredEntities()
                                              .stream()
                                              .map(EntitySetupUsageDTO::getReferredEntity)
                                              .collect(Collectors.toList()));
        }
      }
    }
    return internalReferredEntities;
  }

  private boolean hasInternalReferredEntities(String accountId, EntityType entityType) {
    return getReferredByEntityTypeToReferredEntityTypeMap(accountId).containsKey(entityType);
  }
}
