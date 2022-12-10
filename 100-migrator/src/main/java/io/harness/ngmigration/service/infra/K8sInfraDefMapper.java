/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.infra;

import static software.wings.ngmigration.NGMigrationEntityType.CONNECTOR;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.infra.yaml.Infrastructure;
import io.harness.cdng.infra.yaml.K8SDirectInfrastructure;
import io.harness.cdng.infra.yaml.K8sGcpInfrastructure;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.infrastructure.InfrastructureType;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.beans.NgEntityDetail;
import io.harness.ngmigration.service.MigratorUtility;
import io.harness.pms.yaml.ParameterField;

import software.wings.infra.AzureKubernetesService;
import software.wings.infra.DirectKubernetesInfrastructure;
import software.wings.infra.GoogleKubernetesEngine;
import software.wings.infra.InfrastructureDefinition;
import software.wings.ngmigration.CgEntityId;

import java.util.Map;

@OwnedBy(HarnessTeam.CDC)
public class K8sInfraDefMapper implements InfraDefMapper {
  @Override
  public ServiceDefinitionType getServiceDefinition(InfrastructureDefinition infrastructureDefinition) {
    return ServiceDefinitionType.KUBERNETES;
  }

  @Override
  public InfrastructureType getInfrastructureType(InfrastructureDefinition infrastructureDefinition) {
    switch (infrastructureDefinition.getCloudProviderType()) {
      case KUBERNETES_CLUSTER:
        return InfrastructureType.KUBERNETES_DIRECT;
      case GCP:
        return InfrastructureType.KUBERNETES_GCP;
      case AZURE:
        return InfrastructureType.KUBERNETES_AZURE;
      default:
        throw new InvalidRequestException("Unsupported Infra for K8s deployment");
    }
  }

  @Override
  public Infrastructure getSpec(
      InfrastructureDefinition infrastructureDefinition, Map<CgEntityId, NGYamlFile> migratedEntities) {
    NgEntityDetail connectorDetail;
    switch (infrastructureDefinition.getCloudProviderType()) {
      case KUBERNETES_CLUSTER:
        DirectKubernetesInfrastructure k8s =
            (DirectKubernetesInfrastructure) infrastructureDefinition.getInfrastructure();
        connectorDetail =
            migratedEntities.get(CgEntityId.builder().type(CONNECTOR).id(k8s.getCloudProviderId()).build())
                .getNgEntityDetail();
        return K8SDirectInfrastructure.builder()
            .namespace(ParameterField.createValueField(k8s.getNamespace()))
            .releaseName(ParameterField.createValueField(k8s.getReleaseName()))
            .connectorRef(ParameterField.createValueField(MigratorUtility.getIdentifierWithScope(connectorDetail)))
            .build();
      case GCP:
        GoogleKubernetesEngine gcpK8s = (GoogleKubernetesEngine) infrastructureDefinition.getInfrastructure();
        connectorDetail =
            migratedEntities.get(CgEntityId.builder().type(CONNECTOR).id(gcpK8s.getCloudProviderId()).build())
                .getNgEntityDetail();
        return K8sGcpInfrastructure.builder()
            .connectorRef(ParameterField.createValueField(MigratorUtility.getIdentifierWithScope(connectorDetail)))
            .namespace(ParameterField.createValueField(gcpK8s.getNamespace()))
            .releaseName(ParameterField.createValueField(gcpK8s.getReleaseName()))
            .build();
      case AZURE:
        AzureKubernetesService aks = (AzureKubernetesService) infrastructureDefinition.getInfrastructure();
        connectorDetail =
            migratedEntities.get(CgEntityId.builder().type(CONNECTOR).id(aks.getCloudProviderId()).build())
                .getNgEntityDetail();
        return InfraDefMapperUtils.buildK8sAzureInfrastructure(aks, connectorDetail);
      default:
        throw new InvalidRequestException("Unsupported Infra for K8s deployment");
    }
  }
}
