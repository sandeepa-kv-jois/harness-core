/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.service.services;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.service.entity.ArtifactSourcesResponseDTO;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.entity.ServiceInputsMergedResponseDto;
import io.harness.pms.yaml.YamlNode;
import io.harness.repositories.UpsertOptions;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Optional;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(HarnessTeam.PIPELINE)
public interface ServiceEntityService {
  ServiceEntity create(ServiceEntity serviceEntity);

  Optional<ServiceEntity> get(
      String accountId, String orgIdentifier, String projectIdentifier, String serviceIdentifier, boolean deleted);

  // TODO(archit): make it transactional
  ServiceEntity update(ServiceEntity requestService);

  // TODO(archit): make it transactional
  ServiceEntity upsert(ServiceEntity requestService, UpsertOptions upsertOptions);

  Page<ServiceEntity> list(Criteria criteria, Pageable pageable);

  List<ServiceEntity> listRunTimePermission(Criteria criteria);

  boolean delete(
      String accountId, String orgIdentifier, String projectIdentifier, String serviceIdentifier, Long version);

  Page<ServiceEntity> bulkCreate(String accountId, List<ServiceEntity> serviceEntities);

  List<ServiceEntity> getAllServices(String accountIdentifier, String orgIdentifier, String projectIdentifier);

  List<ServiceEntity> getAllNonDeletedServices(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, List<String> sort);

  Integer findActiveServicesCountAtGivenTimestamp(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, long timestampInMs);

  String createServiceInputsYaml(String yaml, String serviceIdentifier);

  String createServiceInputsYamlGivenPrimaryArtifactRef(
      String serviceYaml, String serviceIdentifier, String primaryArtifactRef);

  ArtifactSourcesResponseDTO getArtifactSourceInputs(String yaml, String serviceIdentifier);

  ServiceInputsMergedResponseDto mergeServiceInputs(
      String accountId, String orgId, String projectId, String serviceId, String oldServiceInputsYaml);

  boolean forceDeleteAllInProject(String accountId, String orgIdentifier, String projectIdentifier);

  /**
   *
   * Locates the leaf node in a service entity for a given FQN of type
   * pipeline.stages.s1.spec.service.serviceInputs.serviceDefinition.spec.artifacts.primary.spec.tag
   * pipeline.stages.s1.spec.service.serviceInputs.serviceDefinition.spec.artifacts.sidecars[0].sidecar.spec.tag
   * service.serviceInputs.serviceDefinition.spec.artifacts.sidecars[0].sidecar.spec.tag
   *
   * @param fqn must contain serviceDefinition. FQN represents usage of a service within a pipeline
   * @return YamlNode
   */
  @NotNull
  YamlNode getYamlNodeForFqn(@NotEmpty String accountId, @NotEmpty String orgIdentifier,
      @NotEmpty String projectIdentifier, @NotEmpty String serviceIdentifier, @NotEmpty String fqn);

  List<ServiceEntity> getServices(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, List<String> serviceIdentifiers);

  boolean isServiceField(String fieldName, JsonNode value);

  Optional<ServiceEntity> getService(
      String accountId, String orgIdentifier, String projectIdentifier, String serviceIdentifier);
}
