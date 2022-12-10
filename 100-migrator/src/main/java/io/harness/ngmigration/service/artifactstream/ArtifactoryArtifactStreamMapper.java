/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.artifactstream;

import static software.wings.ngmigration.NGMigrationEntityType.CONNECTOR;
import static software.wings.utils.RepositoryType.docker;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.artifact.bean.yaml.ArtifactoryRegistryArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.PrimaryArtifact;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.ngmigration.beans.MigrationInputDTO;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.beans.NgEntityDetail;
import io.harness.ngmigration.service.MigratorUtility;
import io.harness.pms.yaml.ParameterField;

import software.wings.beans.artifact.ArtifactStream;
import software.wings.beans.artifact.ArtifactoryArtifactStream;
import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.CgEntityNode;

import java.util.Map;
import java.util.Set;

@OwnedBy(HarnessTeam.CDC)
public class ArtifactoryArtifactStreamMapper implements ArtifactStreamMapper {
  @Override
  public PrimaryArtifact getArtifactDetails(MigrationInputDTO inputDTO, Map<CgEntityId, CgEntityNode> entities,
      Map<CgEntityId, Set<CgEntityId>> graph, ArtifactStream artifactStream,
      Map<CgEntityId, NGYamlFile> migratedEntities) {
    ArtifactoryArtifactStream artifactoryArtifactStream = (ArtifactoryArtifactStream) artifactStream;
    NgEntityDetail connector =
        migratedEntities.get(CgEntityId.builder().type(CONNECTOR).id(artifactoryArtifactStream.getSettingId()).build())
            .getNgEntityDetail();
    return PrimaryArtifact.builder()
        .sourceType(ArtifactSourceType.ARTIFACTORY_REGISTRY)
        .spec(docker.name().equals(artifactoryArtifactStream.getRepositoryType())
                ? generateDockerConfig(artifactoryArtifactStream, connector)
                : generateGeneticConfig(artifactoryArtifactStream, connector))
        .build();
  }

  private ArtifactoryRegistryArtifactConfig generateDockerConfig(
      ArtifactoryArtifactStream artifactStream, NgEntityDetail connector) {
    return ArtifactoryRegistryArtifactConfig.builder()
        .connectorRef(ParameterField.createValueField(MigratorUtility.getIdentifierWithScope(connector)))
        .primaryArtifact(true)
        .repository(ParameterField.createValueField(artifactStream.getJobname()))
        .repositoryUrl(ParameterField.createValueField(artifactStream.getDockerRepositoryServer()))
        .artifactPath(ParameterField.createValueField(artifactStream.getImageName()))
        .repositoryFormat(ParameterField.createValueField("docker"))
        .tag(ParameterField.createValueField("<+input>"))
        .build();
  }

  private ArtifactoryRegistryArtifactConfig generateGeneticConfig(
      ArtifactoryArtifactStream artifactStream, NgEntityDetail connector) {
    return ArtifactoryRegistryArtifactConfig.builder()
        .connectorRef(ParameterField.createValueField(MigratorUtility.getIdentifierWithScope(connector)))
        .primaryArtifact(true)
        .repository(ParameterField.createValueField(artifactStream.getJobname()))
        .repositoryFormat(ParameterField.createValueField("generic"))
        .artifactDirectory(ParameterField.createValueField(artifactStream.getArtifactPattern()))
        .artifactPath(ParameterField.createValueField("<+input>"))
        .build();
  }
}
