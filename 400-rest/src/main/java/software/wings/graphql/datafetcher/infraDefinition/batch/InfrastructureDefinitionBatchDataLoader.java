/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.infraDefinition.batch;

import static io.harness.persistence.HQuery.excludeAuthority;

import io.harness.data.structure.EmptyPredicate;

import software.wings.beans.Environment;
import software.wings.dl.WingsPersistence;
import software.wings.graphql.schema.type.QLInfrastructureDefinition;
import software.wings.graphql.schema.type.QLInfrastructureDefinition.QLInfrastructureDefinitionBuilder;
import software.wings.infra.InfrastructureDefinition;
import software.wings.infra.InfrastructureDefinition.InfrastructureDefinitionKeys;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import org.apache.commons.collections4.CollectionUtils;
import org.dataloader.MappedBatchLoader;

public class InfrastructureDefinitionBatchDataLoader implements MappedBatchLoader<String, QLInfrastructureDefinition> {
  @Inject private WingsPersistence wingsPersistence;

  @Override
  public CompletionStage<Map<String, QLInfrastructureDefinition>> load(Set<String> infraDefIds) {
    return CompletableFuture.supplyAsync(() -> {
      Map<String, QLInfrastructureDefinition> infrastructureDefinitionMap = null;
      final Set<String> infraDefIdsFiltered =
          infraDefIds.stream().filter(EmptyPredicate::isNotEmpty).collect(Collectors.toSet());
      if (!CollectionUtils.isEmpty(infraDefIdsFiltered)) {
        infrastructureDefinitionMap = getInfrastructureDefinitionMap(infraDefIdsFiltered);
      } else {
        infrastructureDefinitionMap = Collections.EMPTY_MAP;
      }
      return infrastructureDefinitionMap;
    });
  }

  public Map<String, QLInfrastructureDefinition> getInfrastructureDefinitionMap(@NotNull Set<String> infraDefIds) {
    final List<InfrastructureDefinition> infrastructureDefinitions =
        wingsPersistence.createQuery(InfrastructureDefinition.class, excludeAuthority)
            .field(InfrastructureDefinitionKeys.uuid)
            .in(infraDefIds)
            .asList();
    Map<String, QLInfrastructureDefinition> infrastructureDefinitionMap = new HashMap<>();
    infrastructureDefinitions.forEach(infrastructureDefinition -> {
      final QLInfrastructureDefinitionBuilder builder =
          QLInfrastructureDefinition.builder()
              .id(infrastructureDefinition.getUuid())
              .name(infrastructureDefinition.getName())
              .deploymentType(infrastructureDefinition.getDeploymentType().getDisplayName())
              .scopedToServices(infrastructureDefinition.getScopedToServices())
              .createdAt(infrastructureDefinition.getCreatedAt())
              .environment(wingsPersistence.get(Environment.class, infrastructureDefinition.getEnvId()));
      infrastructureDefinitionMap.put(infrastructureDefinition.getUuid(), builder.build());
    });
    return infrastructureDefinitionMap;
  }
}
