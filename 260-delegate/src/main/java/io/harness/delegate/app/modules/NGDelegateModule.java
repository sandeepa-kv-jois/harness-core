/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.app.modules;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.artifactory.service.ArtifactoryRegistryService;
import io.harness.artifactory.service.ArtifactoryRegistryServiceImpl;
import io.harness.artifacts.ami.service.AMIRegistryService;
import io.harness.artifacts.ami.service.AMIRegistryServiceImpl;
import io.harness.artifacts.azureartifacts.service.AzureArtifactsRegistryService;
import io.harness.artifacts.azureartifacts.service.AzureArtifactsRegistryServiceImpl;
import io.harness.artifacts.docker.service.DockerRegistryService;
import io.harness.artifacts.docker.service.DockerRegistryServiceImpl;
import io.harness.artifacts.gar.service.GARApiServiceImpl;
import io.harness.artifacts.gar.service.GarApiService;
import io.harness.artifacts.gcr.service.GcrApiService;
import io.harness.artifacts.gcr.service.GcrApiServiceImpl;
import io.harness.artifacts.githubpackages.client.GithubPackagesRestClientFactory;
import io.harness.artifacts.githubpackages.client.GithubPackagesRestClientFactoryImpl;
import io.harness.artifacts.githubpackages.service.GithubPackagesRegistryService;
import io.harness.artifacts.githubpackages.service.GithubPackagesRegistryServiceImpl;
import io.harness.delegate.task.artifacts.ami.AMIArtifactTaskHandler;
import io.harness.delegate.task.artifacts.artifactory.ArtifactoryArtifactTaskHandler;
import io.harness.delegate.task.artifacts.azureartifacts.AzureArtifactsTaskHandler;
import io.harness.delegate.task.artifacts.docker.DockerArtifactTaskHandler;
import io.harness.delegate.task.artifacts.githubpackages.GithubPackagesArtifactTaskHandler;
import io.harness.delegate.task.artifacts.nexus.NexusArtifactTaskHandler;
import io.harness.delegate.task.artifacts.s3.S3ArtifactTaskHandler;
import io.harness.http.HttpService;
import io.harness.http.HttpServiceImpl;
import io.harness.nexus.service.NexusRegistryService;
import io.harness.nexus.service.NexusRegistryServiceImpl;

import com.google.inject.AbstractModule;

@TargetModule(HarnessModule._420_DELEGATE_AGENT)
public class NGDelegateModule extends AbstractModule {
  @Override
  protected void configure() {
    bind(DockerRegistryService.class).to(DockerRegistryServiceImpl.class);
    bind(AzureArtifactsRegistryService.class).to(AzureArtifactsRegistryServiceImpl.class);
    bind(NexusRegistryService.class).to(NexusRegistryServiceImpl.class);
    bind(ArtifactoryRegistryService.class).to(ArtifactoryRegistryServiceImpl.class);
    bind(GcrApiService.class).to(GcrApiServiceImpl.class);
    bind(GithubPackagesRestClientFactory.class).to(GithubPackagesRestClientFactoryImpl.class);
    bind(GithubPackagesRegistryService.class).to(GithubPackagesRegistryServiceImpl.class);
    bind(AMIRegistryService.class).to(AMIRegistryServiceImpl.class);
    bind(GarApiService.class).to(GARApiServiceImpl.class);
    bind(HttpService.class).to(HttpServiceImpl.class);
    bind(DockerArtifactTaskHandler.class);
    bind(GithubPackagesArtifactTaskHandler.class);
    bind(S3ArtifactTaskHandler.class);
    bind(NexusArtifactTaskHandler.class);
    bind(ArtifactoryArtifactTaskHandler.class);
    bind(AzureArtifactsTaskHandler.class);
    bind(AMIArtifactTaskHandler.class);
  }
}
