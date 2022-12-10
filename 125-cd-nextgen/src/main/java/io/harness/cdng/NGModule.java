/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.WalkTreeModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.artifact.resources.acr.service.AcrResourceService;
import io.harness.cdng.artifact.resources.acr.service.AcrResourceServiceImpl;
import io.harness.cdng.artifact.resources.ami.AMIResourceService;
import io.harness.cdng.artifact.resources.ami.AMIResourceServiceImpl;
import io.harness.cdng.artifact.resources.artifactory.service.ArtifactoryResourceService;
import io.harness.cdng.artifact.resources.artifactory.service.ArtifactoryResourceServiceImpl;
import io.harness.cdng.artifact.resources.azureartifacts.AzureArtifactsResourceService;
import io.harness.cdng.artifact.resources.azureartifacts.AzureArtifactsResourceServiceImpl;
import io.harness.cdng.artifact.resources.custom.CustomResourceService;
import io.harness.cdng.artifact.resources.custom.CustomResourceServiceImpl;
import io.harness.cdng.artifact.resources.docker.service.DockerResourceService;
import io.harness.cdng.artifact.resources.docker.service.DockerResourceServiceImpl;
import io.harness.cdng.artifact.resources.ecr.service.EcrResourceService;
import io.harness.cdng.artifact.resources.ecr.service.EcrResourceServiceImpl;
import io.harness.cdng.artifact.resources.gcr.service.GcrResourceService;
import io.harness.cdng.artifact.resources.gcr.service.GcrResourceServiceImpl;
import io.harness.cdng.artifact.resources.githubpackages.service.GithubPackagesResourceService;
import io.harness.cdng.artifact.resources.githubpackages.service.GithubPackagesResourceServiceImpl;
import io.harness.cdng.artifact.resources.googleartifactregistry.service.GARResourceService;
import io.harness.cdng.artifact.resources.googleartifactregistry.service.GARResourceServiceImpl;
import io.harness.cdng.artifact.resources.jenkins.service.JenkinsResourceService;
import io.harness.cdng.artifact.resources.jenkins.service.JenkinsResourceServiceImpl;
import io.harness.cdng.artifact.resources.nexus.service.NexusResourceService;
import io.harness.cdng.artifact.resources.nexus.service.NexusResourceServiceImpl;
import io.harness.cdng.artifact.service.ArtifactSourceService;
import io.harness.cdng.artifact.service.impl.ArtifactSourceServiceImpl;
import io.harness.cdng.buckets.resources.s3.S3ResourceService;
import io.harness.cdng.buckets.resources.s3.S3ResourceServiceImpl;
import io.harness.cdng.buckets.resources.service.GcsResourceService;
import io.harness.cdng.buckets.resources.service.GcsResourceServiceImpl;
import io.harness.cdng.envGroup.mappers.EnvironmentGroupFilterPropertiesMapper;
import io.harness.cdng.envGroup.services.EnvironmentGroupService;
import io.harness.cdng.envGroup.services.EnvironmentGroupServiceImpl;
import io.harness.cdng.environment.EnvironmentFilterPropertiesMapper;
import io.harness.cdng.execution.service.StageExecutionInfoService;
import io.harness.cdng.execution.service.StageExecutionInfoServiceImpl;
import io.harness.cdng.gitops.ClusterServiceImpl;
import io.harness.cdng.gitops.service.ClusterService;
import io.harness.cdng.instance.info.InstanceInfoService;
import io.harness.cdng.instance.info.InstanceInfoServiceImpl;
import io.harness.cdng.instance.service.InstanceDeploymentInfoService;
import io.harness.cdng.instance.service.InstanceDeploymentInfoServiceImpl;
import io.harness.cdng.jira.resources.service.JiraResourceService;
import io.harness.cdng.jira.resources.service.JiraResourceServiceImpl;
import io.harness.cdng.k8s.resources.azure.service.AzureResourceService;
import io.harness.cdng.k8s.resources.azure.service.AzureResourceServiceImpl;
import io.harness.cdng.k8s.resources.gcp.service.GcpResourceService;
import io.harness.cdng.k8s.resources.gcp.service.impl.GcpResourceServiceImpl;
import io.harness.cdng.manifest.resources.HelmChartService;
import io.harness.cdng.manifest.resources.HelmChartServiceImpl;
import io.harness.cdng.provision.terraform.executions.TerraformPlanExectionDetailsService;
import io.harness.cdng.provision.terraform.executions.TerraformPlanExectionDetailsServiceImpl;
import io.harness.cdng.servicenow.resources.service.ServiceNowResourceService;
import io.harness.cdng.servicenow.resources.service.ServiceNowResourceServiceImpl;
import io.harness.cdng.tas.service.TasResourceService;
import io.harness.cdng.tas.service.TasResourceServiceImpl;
import io.harness.cdng.usage.impl.CDLicenseUsageImpl;
import io.harness.cdng.yaml.CdYamlSchemaService;
import io.harness.cdng.yaml.CdYamlSchemaServiceImpl;
import io.harness.filter.FilterType;
import io.harness.filter.impl.FilterServiceImpl;
import io.harness.filter.mapper.FilterPropertiesMapper;
import io.harness.filter.service.FilterService;
import io.harness.licensing.usage.interfaces.LicenseUsageInterface;
import io.harness.migration.NGMigrationSdkModule;
import io.harness.ng.core.NGCoreModule;
import io.harness.ng.core.environment.services.EnvironmentService;
import io.harness.ng.core.environment.services.impl.EnvironmentServiceImpl;
import io.harness.ng.core.infrastructure.services.InfrastructureEntityService;
import io.harness.ng.core.infrastructure.services.impl.InfrastructureEntityServiceImpl;
import io.harness.ng.core.service.services.ServiceEntityService;
import io.harness.ng.core.service.services.impl.ServiceEntityServiceImpl;
import io.harness.ng.core.serviceoverride.services.ServiceOverrideService;
import io.harness.ng.core.serviceoverride.services.impl.ServiceOverrideServiceImpl;
import io.harness.service.instance.InstanceService;
import io.harness.service.instance.InstanceServiceImpl;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.AbstractModule;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.name.Names;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDP)
@Slf4j
public class NGModule extends AbstractModule {
  private static final AtomicReference<NGModule> instanceRef = new AtomicReference<>();

  public static NGModule getInstance() {
    if (instanceRef.get() == null) {
      instanceRef.compareAndSet(null, new NGModule());
    }
    return instanceRef.get();
  }

  @Override
  protected void configure() {
    install(NGCoreModule.getInstance());
    install(WalkTreeModule.getInstance());
    install(NGMigrationSdkModule.getInstance());

    bind(ArtifactSourceService.class).to(ArtifactSourceServiceImpl.class);
    bind(DockerResourceService.class).to(DockerResourceServiceImpl.class);
    bind(NexusResourceService.class).to(NexusResourceServiceImpl.class);
    bind(ArtifactoryResourceService.class).to(ArtifactoryResourceServiceImpl.class);
    bind(GcrResourceService.class).to(GcrResourceServiceImpl.class);
    bind(EcrResourceService.class).to(EcrResourceServiceImpl.class);
    bind(JiraResourceService.class).to(JiraResourceServiceImpl.class);
    bind(CdYamlSchemaService.class).to(CdYamlSchemaServiceImpl.class);
    bind(GcpResourceService.class).to(GcpResourceServiceImpl.class);
    bind(S3ResourceService.class).to(S3ResourceServiceImpl.class);
    bind(GcsResourceService.class).to(GcsResourceServiceImpl.class);
    bind(InstanceInfoService.class).to(InstanceInfoServiceImpl.class);
    bind(LicenseUsageInterface.class).to(CDLicenseUsageImpl.class);
    bind(InstanceService.class).to(InstanceServiceImpl.class);
    bind(ServiceEntityService.class).to(ServiceEntityServiceImpl.class);
    bind(EnvironmentService.class).to(EnvironmentServiceImpl.class);
    bind(ServiceNowResourceService.class).to(ServiceNowResourceServiceImpl.class);
    bind(ArtifactoryResourceService.class).to(ArtifactoryResourceServiceImpl.class);
    bind(EnvironmentGroupService.class).to(EnvironmentGroupServiceImpl.class);
    bind(AcrResourceService.class).to(AcrResourceServiceImpl.class);
    bind(AzureResourceService.class).to(AzureResourceServiceImpl.class);
    bind(TasResourceService.class).to(TasResourceServiceImpl.class);
    bind(JenkinsResourceService.class).to(JenkinsResourceServiceImpl.class);
    bind(GithubPackagesResourceService.class).to(GithubPackagesResourceServiceImpl.class);
    bind(AzureArtifactsResourceService.class).to(AzureArtifactsResourceServiceImpl.class);
    bind(AMIResourceService.class).to(AMIResourceServiceImpl.class);
    bind(FilterService.class).to(FilterServiceImpl.class);
    bind(ClusterService.class).to(ClusterServiceImpl.class);
    bind(InfrastructureEntityService.class).to(InfrastructureEntityServiceImpl.class);
    bind(ServiceOverrideService.class).to(ServiceOverrideServiceImpl.class);
    bind(CustomResourceService.class).to(CustomResourceServiceImpl.class);
    bind(ScheduledExecutorService.class)
        .annotatedWith(Names.named("CdTelemetryPublisherExecutor"))
        .toInstance(new ScheduledThreadPoolExecutor(1,
            new ThreadFactoryBuilder()
                .setNameFormat("Cd-ng-telemetry-publisher-Thread-%d")
                .setPriority(Thread.NORM_PRIORITY)
                .build()));
    bind(StageExecutionInfoService.class).to(StageExecutionInfoServiceImpl.class);
    bind(TerraformPlanExectionDetailsService.class).to(TerraformPlanExectionDetailsServiceImpl.class);
    bind(InstanceDeploymentInfoService.class).to(InstanceDeploymentInfoServiceImpl.class);
    bind(GARResourceService.class).to(GARResourceServiceImpl.class);
    bind(HelmChartService.class).to(HelmChartServiceImpl.class);
    MapBinder<String, FilterPropertiesMapper> filterPropertiesMapper =
        MapBinder.newMapBinder(binder(), String.class, FilterPropertiesMapper.class);
    filterPropertiesMapper.addBinding(FilterType.ENVIRONMENTGROUP.toString())
        .to(EnvironmentGroupFilterPropertiesMapper.class);
    filterPropertiesMapper.addBinding(FilterType.ENVIRONMENT.toString()).to(EnvironmentFilterPropertiesMapper.class);
  }
}
