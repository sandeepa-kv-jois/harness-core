/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.artifact.utils;

import static io.harness.connector.ConnectorModule.DEFAULT_CONNECTOR_SERVICE;
import static io.harness.utils.DelegateOwner.getNGTaskSetupAbstractionsWithOwner;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.artifact.bean.ArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.AMIArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.AcrArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.AmazonS3ArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.ArtifactoryRegistryArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.AzureArtifactsConfig;
import io.harness.cdng.artifact.bean.yaml.CustomArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.DockerHubArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.EcrArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.GcrArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.GithubPackagesArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.GoogleArtifactRegistryConfig;
import io.harness.cdng.artifact.bean.yaml.JenkinsArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.NexusRegistryArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.nexusartifact.Nexus2RegistryArtifactConfig;
import io.harness.cdng.artifact.mappers.ArtifactConfigToDelegateReqMapper;
import io.harness.cdng.artifact.steps.ArtifactStepParameters;
import io.harness.cdng.common.beans.SetupAbstractionKeys;
import io.harness.cdng.expressions.CDExpressionResolver;
import io.harness.cdng.visitor.YamlTypes;
import io.harness.common.NGExpressionUtils;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.connector.utils.ConnectorUtils;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.TaskSelector;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.azureartifacts.AzureArtifactsAuthenticationType;
import io.harness.delegate.beans.connector.azureartifacts.AzureArtifactsConnectorDTO;
import io.harness.delegate.beans.connector.azureartifacts.AzureArtifactsCredentialsDTO;
import io.harness.delegate.beans.connector.azureartifacts.AzureArtifactsTokenDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureConnectorDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureCredentialType;
import io.harness.delegate.beans.connector.azureconnector.AzureInheritFromDelegateDetailsDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureMSIAuthDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureMSIAuthUADTO;
import io.harness.delegate.beans.connector.azureconnector.AzureManualDetailsDTO;
import io.harness.delegate.beans.connector.docker.DockerConnectorDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorDTO;
import io.harness.delegate.beans.connector.jenkins.JenkinsConnectorDTO;
import io.harness.delegate.beans.connector.nexusconnector.NexusConnectorDTO;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessDTO;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessType;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.delegate.beans.connector.scm.github.GithubTokenSpecDTO;
import io.harness.delegate.task.artifacts.ArtifactSourceDelegateRequest;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidConnectorTypeException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.ng.core.BaseNGAccess;
import io.harness.ng.core.NGAccess;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.utils.IdentifierRefHelper;

import software.wings.beans.TaskType;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
public class ArtifactStepHelper {
  @Named(DEFAULT_CONNECTOR_SERVICE) @Inject private ConnectorService connectorService;
  @Named("PRIVILEGED") @Inject private SecretManagerClientService secretManagerClientService;

  @Inject private CDExpressionResolver cdExpressionResolver;

  public ArtifactSourceDelegateRequest toSourceDelegateRequest(ArtifactConfig artifactConfig, Ambiance ambiance) {
    List<EncryptedDataDetail> encryptedDataDetails = new ArrayList<>();
    ConnectorInfoDTO connectorDTO;
    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
    switch (artifactConfig.getSourceType()) {
      case DOCKER_REGISTRY:
        DockerHubArtifactConfig dockerConfig = (DockerHubArtifactConfig) artifactConfig;
        connectorDTO = getConnector(dockerConfig.getConnectorRef().getValue(), ambiance);
        if (!(connectorDTO.getConnectorConfig() instanceof DockerConnectorDTO)) {
          throw new InvalidConnectorTypeException("Provided Connector " + dockerConfig.getConnectorRef().getValue()
                  + " is not compatible with " + dockerConfig.getSourceType() + " Artifact",
              WingsException.USER);
        }
        DockerConnectorDTO connectorConfig = (DockerConnectorDTO) connectorDTO.getConnectorConfig();
        if (connectorConfig.getAuth() != null && connectorConfig.getAuth().getCredentials() != null) {
          encryptedDataDetails =
              secretManagerClientService.getEncryptionDetails(ngAccess, connectorConfig.getAuth().getCredentials());
        }
        return ArtifactConfigToDelegateReqMapper.getDockerDelegateRequest(
            dockerConfig, connectorConfig, encryptedDataDetails, dockerConfig.getConnectorRef().getValue());
      case GOOGLE_ARTIFACT_REGISTRY:
        GoogleArtifactRegistryConfig googleArtifactRegistryConfig = (GoogleArtifactRegistryConfig) artifactConfig;
        connectorDTO = getConnector(googleArtifactRegistryConfig.getConnectorRef().getValue(), ambiance);
        if (!(connectorDTO.getConnectorConfig() instanceof GcpConnectorDTO)) {
          throw new InvalidConnectorTypeException("Provided Connector "
                  + googleArtifactRegistryConfig.getConnectorRef().getValue() + " is not compatible with "
                  + googleArtifactRegistryConfig.getSourceType() + " Artifact",
              WingsException.USER);
        }
        GcpConnectorDTO gcpConnectorDTO = (GcpConnectorDTO) connectorDTO.getConnectorConfig();
        if (gcpConnectorDTO.getCredential() != null && gcpConnectorDTO.getCredential().getConfig() != null) {
          encryptedDataDetails =
              secretManagerClientService.getEncryptionDetails(ngAccess, gcpConnectorDTO.getCredential().getConfig());
        }
        return ArtifactConfigToDelegateReqMapper.getGarDelegateRequest(googleArtifactRegistryConfig, gcpConnectorDTO,
            encryptedDataDetails, googleArtifactRegistryConfig.getConnectorRef().getValue());

      case AMAZONS3:
        AmazonS3ArtifactConfig amazonS3ArtifactConfig = (AmazonS3ArtifactConfig) artifactConfig;
        connectorDTO = getConnector(amazonS3ArtifactConfig.getConnectorRef().getValue(), ambiance);
        if (!(connectorDTO.getConnectorConfig() instanceof AwsConnectorDTO)) {
          throw new InvalidConnectorTypeException("Provided Connector "
                  + amazonS3ArtifactConfig.getConnectorRef().getValue() + " is not compatible with "
                  + amazonS3ArtifactConfig.getSourceType() + " Artifact",
              WingsException.USER);
        }
        AwsConnectorDTO awsConnectorDTO = (AwsConnectorDTO) connectorDTO.getConnectorConfig();
        if (awsConnectorDTO.getCredential() != null && awsConnectorDTO.getCredential().getConfig() != null) {
          encryptedDataDetails =
              secretManagerClientService.getEncryptionDetails(ngAccess, awsConnectorDTO.getCredential().getConfig());
        }
        return ArtifactConfigToDelegateReqMapper.getAmazonS3DelegateRequest(amazonS3ArtifactConfig, awsConnectorDTO,
            encryptedDataDetails, amazonS3ArtifactConfig.getConnectorRef().getValue());
      case GITHUB_PACKAGES:
        GithubPackagesArtifactConfig githubPackagesArtifactConfig = (GithubPackagesArtifactConfig) artifactConfig;
        connectorDTO = getConnector(githubPackagesArtifactConfig.getConnectorRef().getValue(), ambiance);

        if (!(connectorDTO.getConnectorConfig() instanceof GithubConnectorDTO)) {
          throw new InvalidConnectorTypeException("Provided Connector "
                  + githubPackagesArtifactConfig.getConnectorRef().getValue() + " is not compatible with "
                  + githubPackagesArtifactConfig.getSourceType() + " Artifact",
              WingsException.USER);
        }

        GithubConnectorDTO githubConnectorDTO = (GithubConnectorDTO) connectorDTO.getConnectorConfig();
        encryptedDataDetails = getGithubEncryptedDetails(githubConnectorDTO, ngAccess);

        return ArtifactConfigToDelegateReqMapper.getGithubPackagesDelegateRequest(githubPackagesArtifactConfig,
            githubConnectorDTO, encryptedDataDetails, githubPackagesArtifactConfig.getConnectorRef().getValue());
      case AZURE_ARTIFACTS:
        AzureArtifactsConfig azureArtifactsConfig = (AzureArtifactsConfig) artifactConfig;

        connectorDTO = getConnector(azureArtifactsConfig.getConnectorRef().getValue(), ambiance);

        if (!(connectorDTO.getConnectorConfig() instanceof AzureArtifactsConnectorDTO)) {
          throw new InvalidConnectorTypeException("Provided Connector "
                  + azureArtifactsConfig.getConnectorRef().getValue() + " is not compatible with "
                  + azureArtifactsConfig.getSourceType() + " Artifact",
              WingsException.USER);
        }

        AzureArtifactsConnectorDTO azureArtifactsConnectorDTO =
            (AzureArtifactsConnectorDTO) connectorDTO.getConnectorConfig();

        encryptedDataDetails = getAzureArtifactsEncryptionDetails(azureArtifactsConnectorDTO, ngAccess);

        return ArtifactConfigToDelegateReqMapper.getAzureArtifactsDelegateRequest(azureArtifactsConfig,
            azureArtifactsConnectorDTO, encryptedDataDetails, azureArtifactsConfig.getConnectorRef().getValue());
      case AMI:
        AMIArtifactConfig amiArtifactConfig = (AMIArtifactConfig) artifactConfig;

        connectorDTO = getConnector(amiArtifactConfig.getConnectorRef().getValue(), ambiance);

        if (!(connectorDTO.getConnectorConfig() instanceof AwsConnectorDTO)) {
          throw new InvalidConnectorTypeException("Provided Connector " + amiArtifactConfig.getConnectorRef().getValue()
                  + " is not compatible with " + amiArtifactConfig.getSourceType() + " Artifact",
              WingsException.USER);
        }

        AwsConnectorDTO awsConnectorDTO1 = (AwsConnectorDTO) connectorDTO.getConnectorConfig();

        if (awsConnectorDTO1.getCredential() != null && awsConnectorDTO1.getCredential().getConfig() != null) {
          encryptedDataDetails =
              secretManagerClientService.getEncryptionDetails(ngAccess, awsConnectorDTO1.getCredential().getConfig());
        }

        return ArtifactConfigToDelegateReqMapper.getAMIDelegateRequest(
            amiArtifactConfig, awsConnectorDTO1, encryptedDataDetails, amiArtifactConfig.getConnectorRef().getValue());
      case GCR:
        GcrArtifactConfig gcrArtifactConfig = (GcrArtifactConfig) artifactConfig;
        connectorDTO = getConnector(gcrArtifactConfig.getConnectorRef().getValue(), ambiance);
        if (!(connectorDTO.getConnectorConfig() instanceof GcpConnectorDTO)) {
          throw new InvalidConnectorTypeException("Provided Connector " + gcrArtifactConfig.getConnectorRef().getValue()
                  + " is not compatible with " + gcrArtifactConfig.getSourceType() + " Artifact",
              WingsException.USER);
        }
        gcpConnectorDTO = (GcpConnectorDTO) connectorDTO.getConnectorConfig();
        if (gcpConnectorDTO.getCredential() != null && gcpConnectorDTO.getCredential().getConfig() != null) {
          encryptedDataDetails =
              secretManagerClientService.getEncryptionDetails(ngAccess, gcpConnectorDTO.getCredential().getConfig());
        }
        return ArtifactConfigToDelegateReqMapper.getGcrDelegateRequest(
            gcrArtifactConfig, gcpConnectorDTO, encryptedDataDetails, gcrArtifactConfig.getConnectorRef().getValue());
      case ECR:
        EcrArtifactConfig ecrArtifactConfig = (EcrArtifactConfig) artifactConfig;
        connectorDTO = getConnector(ecrArtifactConfig.getConnectorRef().getValue(), ambiance);
        if (!(connectorDTO.getConnectorConfig() instanceof AwsConnectorDTO)) {
          throw new InvalidConnectorTypeException("Provided Connector " + ecrArtifactConfig.getConnectorRef().getValue()
                  + " is not compatible with " + ecrArtifactConfig.getSourceType() + " Artifact",
              WingsException.USER);
        }
        AwsConnectorDTO connector = (AwsConnectorDTO) connectorDTO.getConnectorConfig();
        if (connector.getCredential() != null && connector.getCredential().getConfig() instanceof DecryptableEntity) {
          encryptedDataDetails = secretManagerClientService.getEncryptionDetails(
              ngAccess, (DecryptableEntity) connector.getCredential().getConfig());
        }
        return ArtifactConfigToDelegateReqMapper.getEcrDelegateRequest(
            ecrArtifactConfig, connector, encryptedDataDetails, ecrArtifactConfig.getConnectorRef().getValue());
      case NEXUS3_REGISTRY:
        NexusRegistryArtifactConfig nexusRegistryArtifactConfig = (NexusRegistryArtifactConfig) artifactConfig;
        connectorDTO = getConnector(nexusRegistryArtifactConfig.getConnectorRef().getValue(), ambiance);
        if (!(connectorDTO.getConnectorConfig() instanceof NexusConnectorDTO)) {
          throw new InvalidConnectorTypeException("Provided Connector "
                  + nexusRegistryArtifactConfig.getConnectorRef().getValue() + " is not compatible with "
                  + nexusRegistryArtifactConfig.getSourceType() + " Artifact",
              WingsException.USER);
        }
        NexusConnectorDTO nexusConnectorDTO = (NexusConnectorDTO) connectorDTO.getConnectorConfig();
        if (nexusConnectorDTO.getAuth() != null && nexusConnectorDTO.getAuth().getCredentials() != null) {
          encryptedDataDetails =
              secretManagerClientService.getEncryptionDetails(ngAccess, nexusConnectorDTO.getAuth().getCredentials());
        }
        return ArtifactConfigToDelegateReqMapper.getNexusArtifactDelegateRequest(nexusRegistryArtifactConfig,
            nexusConnectorDTO, encryptedDataDetails, nexusRegistryArtifactConfig.getConnectorRef().getValue());
      case NEXUS2_REGISTRY:
        Nexus2RegistryArtifactConfig nexus2RegistryArtifactConfig = (Nexus2RegistryArtifactConfig) artifactConfig;
        connectorDTO = getConnector(nexus2RegistryArtifactConfig.getConnectorRef().getValue(), ambiance);
        if (!(connectorDTO.getConnectorConfig() instanceof NexusConnectorDTO)) {
          throw new InvalidConnectorTypeException("Provided Connector "
                  + nexus2RegistryArtifactConfig.getConnectorRef().getValue() + " is not compatible with "
                  + nexus2RegistryArtifactConfig.getSourceType() + " Artifact",
              WingsException.USER);
        }
        NexusConnectorDTO nexus2ConnectorDTO = (NexusConnectorDTO) connectorDTO.getConnectorConfig();
        if (nexus2ConnectorDTO.getAuth() != null && nexus2ConnectorDTO.getAuth().getCredentials() != null) {
          encryptedDataDetails =
              secretManagerClientService.getEncryptionDetails(ngAccess, nexus2ConnectorDTO.getAuth().getCredentials());
        }
        return ArtifactConfigToDelegateReqMapper.getNexus2ArtifactDelegateRequest(nexus2RegistryArtifactConfig,
            nexus2ConnectorDTO, encryptedDataDetails, nexus2RegistryArtifactConfig.getConnectorRef().getValue());
      case ARTIFACTORY_REGISTRY:
        ArtifactoryRegistryArtifactConfig artifactoryRegistryArtifactConfig =
            (ArtifactoryRegistryArtifactConfig) artifactConfig;
        connectorDTO = getConnector(artifactoryRegistryArtifactConfig.getConnectorRef().getValue(), ambiance);
        if (!(connectorDTO.getConnectorConfig() instanceof ArtifactoryConnectorDTO)) {
          throw new InvalidConnectorTypeException("Provided Connector "
                  + artifactoryRegistryArtifactConfig.getConnectorRef().getValue() + " is not compatible with "
                  + artifactoryRegistryArtifactConfig.getSourceType() + " Artifact",
              WingsException.USER);
        }
        ArtifactoryConnectorDTO artifactoryConnectorDTO = (ArtifactoryConnectorDTO) connectorDTO.getConnectorConfig();
        if (artifactoryConnectorDTO.getAuth() != null && artifactoryConnectorDTO.getAuth().getCredentials() != null) {
          encryptedDataDetails = secretManagerClientService.getEncryptionDetails(
              ngAccess, artifactoryConnectorDTO.getAuth().getCredentials());
        }
        return ArtifactConfigToDelegateReqMapper.getArtifactoryArtifactDelegateRequest(
            artifactoryRegistryArtifactConfig, artifactoryConnectorDTO, encryptedDataDetails,
            artifactoryRegistryArtifactConfig.getConnectorRef().getValue());
      case ACR:
        AcrArtifactConfig acrArtifactConfig = (AcrArtifactConfig) artifactConfig;
        connectorDTO = getConnector(acrArtifactConfig.getConnectorRef().getValue(), ambiance);
        if (!(connectorDTO.getConnectorConfig() instanceof AzureConnectorDTO)) {
          throw new InvalidConnectorTypeException(
              String.format("Provided connector %s is not compatible with %s artifact",
                  acrArtifactConfig.getConnectorRef().getValue(), acrArtifactConfig.getSourceType()),
              WingsException.USER);
        }
        AzureConnectorDTO azureConnectorDTO = (AzureConnectorDTO) connectorDTO.getConnectorConfig();
        if (azureConnectorDTO.getCredential() != null && azureConnectorDTO.getCredential().getConfig() != null) {
          if (azureConnectorDTO.getCredential().getAzureCredentialType() == AzureCredentialType.MANUAL_CREDENTIALS) {
            encryptedDataDetails = secretManagerClientService.getEncryptionDetails(ngAccess,
                ((AzureManualDetailsDTO) azureConnectorDTO.getCredential().getConfig()).getAuthDTO().getCredentials());
          } else if (azureConnectorDTO.getCredential().getAzureCredentialType()
              == AzureCredentialType.INHERIT_FROM_DELEGATE) {
            AzureMSIAuthDTO azureMSIAuthDTO =
                ((AzureInheritFromDelegateDetailsDTO) azureConnectorDTO.getCredential().getConfig()).getAuthDTO();
            if (azureMSIAuthDTO instanceof AzureMSIAuthUADTO) {
              encryptedDataDetails = secretManagerClientService.getEncryptionDetails(
                  ngAccess, ((AzureMSIAuthUADTO) azureMSIAuthDTO).getCredentials());
            }
          }
        }
        return ArtifactConfigToDelegateReqMapper.getAcrDelegateRequest(
            acrArtifactConfig, azureConnectorDTO, encryptedDataDetails, acrArtifactConfig.getConnectorRef().getValue());
      case JENKINS:
        JenkinsArtifactConfig jenkinsArtifactConfig = (JenkinsArtifactConfig) artifactConfig;
        connectorDTO = getConnector(jenkinsArtifactConfig.getConnectorRef().getValue(), ambiance);
        if (!(connectorDTO.getConnectorConfig() instanceof JenkinsConnectorDTO)) {
          throw new InvalidConnectorTypeException("Provided Connector "
                  + jenkinsArtifactConfig.getConnectorRef().getValue() + " is not compatible with "
                  + jenkinsArtifactConfig.getSourceType() + " Artifact",
              WingsException.USER);
        }
        JenkinsConnectorDTO jenkinsConnectorDTO = (JenkinsConnectorDTO) connectorDTO.getConnectorConfig();
        if (jenkinsConnectorDTO.getAuth() != null && jenkinsConnectorDTO.getAuth().getCredentials() != null) {
          encryptedDataDetails =
              secretManagerClientService.getEncryptionDetails(ngAccess, jenkinsConnectorDTO.getAuth().getCredentials());
        }
        return ArtifactConfigToDelegateReqMapper.getJenkinsDelegateRequest(jenkinsArtifactConfig, jenkinsConnectorDTO,
            encryptedDataDetails, jenkinsArtifactConfig.getConnectorRef().getValue());
      case CUSTOM_ARTIFACT:
        CustomArtifactConfig customArtifactConfig = (CustomArtifactConfig) artifactConfig;
        return ArtifactConfigToDelegateReqMapper.getCustomDelegateRequest(customArtifactConfig, ambiance);
      default:
        throw new UnsupportedOperationException(
            String.format("Unknown Artifact Config type: [%s]", artifactConfig.getSourceType()));
    }
  }

  private List<EncryptedDataDetail> getAzureArtifactsEncryptionDetails(
      AzureArtifactsConnectorDTO azureArtifactsConnectorDTO, NGAccess ngAccess) {
    List<EncryptedDataDetail> encryptedDataDetails = new ArrayList<>();

    if (azureArtifactsConnectorDTO.getAuth() != null && azureArtifactsConnectorDTO.getAuth().getCredentials() != null) {
      AzureArtifactsCredentialsDTO httpCredentialsDTO = azureArtifactsConnectorDTO.getAuth().getCredentials();

      if (httpCredentialsDTO.getType().equals(AzureArtifactsAuthenticationType.PERSONAL_ACCESS_TOKEN)) {
        AzureArtifactsTokenDTO httpCredentialsSpecDTO = httpCredentialsDTO.getCredentialsSpec();

        encryptedDataDetails = secretManagerClientService.getEncryptionDetails(ngAccess, httpCredentialsSpecDTO);

      } else {
        throw new InvalidRequestException("Please select the Authentication Type as Personal Access Token");
      }
    }

    return encryptedDataDetails;
  }

  private List<EncryptedDataDetail> getGithubEncryptedDetails(
      GithubConnectorDTO githubConnectorDTO, NGAccess ngAccess) {
    List<EncryptedDataDetail> encryptedDataDetails = new ArrayList<>();

    if (githubConnectorDTO.getApiAccess() != null) {
      encryptedDataDetails = getGithubEncryptionDetails(githubConnectorDTO, ngAccess);
    } else {
      throw new InvalidRequestException("Please enable API Access for the Github Connector");
    }

    return encryptedDataDetails;
  }

  private List<EncryptedDataDetail> getGithubEncryptionDetails(
      GithubConnectorDTO githubConnectorDTO, NGAccess ngAccess) {
    List<EncryptedDataDetail> encryptedDataDetails = new ArrayList<>();

    GithubApiAccessDTO githubApiAccessDTO = githubConnectorDTO.getApiAccess();

    GithubApiAccessType type = githubApiAccessDTO.getType();

    if (type == GithubApiAccessType.TOKEN) {
      GithubTokenSpecDTO githubTokenSpecDTO = (GithubTokenSpecDTO) githubApiAccessDTO.getSpec();

      encryptedDataDetails = secretManagerClientService.getEncryptionDetails(ngAccess, githubTokenSpecDTO);

    } else {
      throw new InvalidRequestException("Please select the authentication type for API Access as Token");
    }

    return encryptedDataDetails;
  }

  private ConnectorInfoDTO getConnector(String connectorIdentifierRef, Ambiance ambiance) {
    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
    IdentifierRef connectorRef = IdentifierRefHelper.getIdentifierRef(connectorIdentifierRef,
        ngAccess.getAccountIdentifier(), ngAccess.getOrgIdentifier(), ngAccess.getProjectIdentifier());
    Optional<ConnectorResponseDTO> connectorDTO = connectorService.get(connectorRef.getAccountIdentifier(),
        connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier(), connectorRef.getIdentifier());
    if (connectorDTO.isEmpty()) {
      throw new InvalidRequestException(
          String.format("Connector not found for identifier : [%s]", connectorIdentifierRef), WingsException.USER);
    }
    ConnectorUtils.checkForConnectorValidityOrThrow(connectorDTO.get());
    return connectorDTO.get().getConnector();
  }

  public TaskType getArtifactStepTaskType(ArtifactConfig artifactConfig) {
    switch (artifactConfig.getSourceType()) {
      case DOCKER_REGISTRY:
        return TaskType.DOCKER_ARTIFACT_TASK_NG;
      case GCR:
        return TaskType.GCR_ARTIFACT_TASK_NG;
      case GOOGLE_ARTIFACT_REGISTRY:
        return TaskType.GOOGLE_ARTIFACT_REGISTRY_TASK_NG;
      case ECR:
        return TaskType.ECR_ARTIFACT_TASK_NG;
      case ACR:
        return TaskType.ACR_ARTIFACT_TASK_NG;
      case NEXUS2_REGISTRY:
      case NEXUS3_REGISTRY:
        return TaskType.NEXUS_ARTIFACT_TASK_NG;
      case ARTIFACTORY_REGISTRY:
        return TaskType.ARTIFACTORY_ARTIFACT_TASK_NG;
      case AMAZONS3:
        return TaskType.AMAZON_S3_ARTIFACT_TASK_NG;
      case JENKINS:
        return TaskType.JENKINS_ARTIFACT_TASK_NG;
      case GITHUB_PACKAGES:
        return TaskType.GITHUB_PACKAGES_TASK_NG;
      case CUSTOM_ARTIFACT:
        return TaskType.CUSTOM_ARTIFACT_NG;
      case AZURE_ARTIFACTS:
        return TaskType.AZURE_ARTIFACT_TASK_NG;
      case AMI:
        return TaskType.AMI_ARTIFACT_TASK_NG;
      default:
        throw new UnsupportedOperationException(
            String.format("Unknown Artifact Config type: [%s]", artifactConfig.getSourceType()));
    }
  }

  public List<TaskSelector> getDelegateSelectors(ArtifactConfig artifactConfig, Ambiance ambiance) {
    ConnectorInfoDTO connectorDTO;
    switch (artifactConfig.getSourceType()) {
      case DOCKER_REGISTRY:
        DockerHubArtifactConfig dockerConfig = (DockerHubArtifactConfig) artifactConfig;
        connectorDTO = getConnector(dockerConfig.getConnectorRef().getValue(), ambiance);
        return TaskSelectorYaml.toTaskSelector(((DockerConnectorDTO) connectorDTO.getConnectorConfig())
                                                   .getDelegateSelectors()
                                                   .stream()
                                                   .map(TaskSelectorYaml::new)
                                                   .collect(Collectors.toList()));
      case GOOGLE_ARTIFACT_REGISTRY:
        GoogleArtifactRegistryConfig garconfig = (GoogleArtifactRegistryConfig) artifactConfig;
        connectorDTO = getConnector(garconfig.getConnectorRef().getValue(), ambiance);
        return TaskSelectorYaml.toTaskSelector(((GcpConnectorDTO) connectorDTO.getConnectorConfig())
                                                   .getDelegateSelectors()
                                                   .stream()
                                                   .map(TaskSelectorYaml::new)
                                                   .collect(Collectors.toList()));

      case GCR:
        GcrArtifactConfig gcrArtifactConfig = (GcrArtifactConfig) artifactConfig;
        connectorDTO = getConnector(gcrArtifactConfig.getConnectorRef().getValue(), ambiance);
        return TaskSelectorYaml.toTaskSelector(((GcpConnectorDTO) connectorDTO.getConnectorConfig())
                                                   .getDelegateSelectors()
                                                   .stream()
                                                   .map(TaskSelectorYaml::new)
                                                   .collect(Collectors.toList()));
      case ECR:
        EcrArtifactConfig ecrArtifactConfig = (EcrArtifactConfig) artifactConfig;
        connectorDTO = getConnector(ecrArtifactConfig.getConnectorRef().getValue(), ambiance);
        return TaskSelectorYaml.toTaskSelector(((AwsConnectorDTO) connectorDTO.getConnectorConfig())
                                                   .getDelegateSelectors()
                                                   .stream()
                                                   .map(TaskSelectorYaml::new)
                                                   .collect(Collectors.toList()));
      case NEXUS3_REGISTRY:
        NexusRegistryArtifactConfig nexusRegistryArtifactConfig = (NexusRegistryArtifactConfig) artifactConfig;
        connectorDTO = getConnector(nexusRegistryArtifactConfig.getConnectorRef().getValue(), ambiance);
        return TaskSelectorYaml.toTaskSelector(((NexusConnectorDTO) connectorDTO.getConnectorConfig())
                                                   .getDelegateSelectors()
                                                   .stream()
                                                   .map(TaskSelectorYaml::new)
                                                   .collect(Collectors.toList()));
      case NEXUS2_REGISTRY:
        Nexus2RegistryArtifactConfig nexus2RegistryArtifactConfig = (Nexus2RegistryArtifactConfig) artifactConfig;
        connectorDTO = getConnector(nexus2RegistryArtifactConfig.getConnectorRef().getValue(), ambiance);
        return TaskSelectorYaml.toTaskSelector(((NexusConnectorDTO) connectorDTO.getConnectorConfig())
                                                   .getDelegateSelectors()
                                                   .stream()
                                                   .map(TaskSelectorYaml::new)
                                                   .collect(Collectors.toList()));

      case ARTIFACTORY_REGISTRY:
        ArtifactoryRegistryArtifactConfig artifactoryRegistryArtifactConfig =
            (ArtifactoryRegistryArtifactConfig) artifactConfig;
        connectorDTO = getConnector(artifactoryRegistryArtifactConfig.getConnectorRef().getValue(), ambiance);
        return TaskSelectorYaml.toTaskSelector(((ArtifactoryConnectorDTO) connectorDTO.getConnectorConfig())
                                                   .getDelegateSelectors()
                                                   .stream()
                                                   .map(TaskSelectorYaml::new)
                                                   .collect(Collectors.toList()));
      case ACR:
        AcrArtifactConfig acrArtifactConfig = (AcrArtifactConfig) artifactConfig;
        connectorDTO = getConnector(acrArtifactConfig.getConnectorRef().getValue(), ambiance);
        return TaskSelectorYaml.toTaskSelector(((AzureConnectorDTO) connectorDTO.getConnectorConfig())
                                                   .getDelegateSelectors()
                                                   .stream()
                                                   .map(TaskSelectorYaml::new)
                                                   .collect(Collectors.toList()));
      case AMAZONS3:
        AmazonS3ArtifactConfig amazonS3ArtifactConfig = (AmazonS3ArtifactConfig) artifactConfig;
        connectorDTO = getConnector(amazonS3ArtifactConfig.getConnectorRef().getValue(), ambiance);
        return TaskSelectorYaml.toTaskSelector(((AwsConnectorDTO) connectorDTO.getConnectorConfig())
                                                   .getDelegateSelectors()
                                                   .stream()
                                                   .map(TaskSelectorYaml::new)
                                                   .collect(Collectors.toList()));
      case JENKINS:
        JenkinsArtifactConfig jenkinsArtifactConfig = (JenkinsArtifactConfig) artifactConfig;
        connectorDTO = getConnector(jenkinsArtifactConfig.getConnectorRef().getValue(), ambiance);
        return TaskSelectorYaml.toTaskSelector(((JenkinsConnectorDTO) connectorDTO.getConnectorConfig())
                                                   .getDelegateSelectors()
                                                   .stream()
                                                   .map(TaskSelectorYaml::new)
                                                   .collect(Collectors.toList()));
      case GITHUB_PACKAGES:
        GithubPackagesArtifactConfig githubPackagesArtifactConfig = (GithubPackagesArtifactConfig) artifactConfig;
        connectorDTO = getConnector(githubPackagesArtifactConfig.getConnectorRef().getValue(), ambiance);
        return TaskSelectorYaml.toTaskSelector(((GithubConnectorDTO) connectorDTO.getConnectorConfig())
                                                   .getDelegateSelectors()
                                                   .stream()
                                                   .map(TaskSelectorYaml::new)
                                                   .collect(Collectors.toList()));
      case CUSTOM_ARTIFACT:
        return TaskSelectorYaml.toTaskSelector(((CustomArtifactConfig) artifactConfig).getDelegateSelectors());
      case AZURE_ARTIFACTS:
        AzureArtifactsConfig azureArtifactsConfig = (AzureArtifactsConfig) artifactConfig;

        connectorDTO = getConnector(azureArtifactsConfig.getConnectorRef().getValue(), ambiance);

        return TaskSelectorYaml.toTaskSelector(((AzureArtifactsConnectorDTO) connectorDTO.getConnectorConfig())
                                                   .getDelegateSelectors()
                                                   .stream()
                                                   .map(TaskSelectorYaml::new)
                                                   .collect(Collectors.toList()));
      case AMI:
        AMIArtifactConfig amiArtifactConfig = (AMIArtifactConfig) artifactConfig;

        connectorDTO = getConnector(amiArtifactConfig.getConnectorRef().getValue(), ambiance);

        return TaskSelectorYaml.toTaskSelector(((AwsConnectorDTO) connectorDTO.getConnectorConfig())
                                                   .getDelegateSelectors()
                                                   .stream()
                                                   .map(TaskSelectorYaml::new)
                                                   .collect(Collectors.toList()));
      default:
        throw new UnsupportedOperationException(
            String.format("Unknown Artifact Config type: [%s]", artifactConfig.getSourceType()));
    }
  }

  public ArtifactConfig applyArtifactsOverlay(ArtifactStepParameters stepParameters) {
    List<ArtifactConfig> artifactList = new LinkedList<>();
    // 1. Original artifacts
    if (stepParameters.getSpec() != null) {
      artifactList.add(stepParameters.getSpec());
    }
    // 2. Stage Overrides
    if (stepParameters.getStageOverride() != null) {
      artifactList.add(stepParameters.getStageOverride());
    }
    if (EmptyPredicate.isEmpty(artifactList)) {
      throw new InvalidArgumentsException("No artifacts defined");
    }
    ArtifactConfig resultantArtifact = artifactList.get(0);
    for (ArtifactConfig artifact : artifactList.subList(1, artifactList.size())) {
      resultantArtifact = resultantArtifact.applyOverrides(artifact);
    }
    return resultantArtifact;
  }

  public String getArtifactProcessedServiceYaml(Ambiance ambiance, String serviceYaml) {
    try {
      YamlField yamlField = processArtifactsInYaml(ambiance, serviceYaml);
      return YamlUtils.writeYamlString(yamlField);
    } catch (IOException ex) {
      throw new InvalidRequestException("Error processing artifact sources in service Yaml", ex);
    }
  }

  public YamlField processArtifactsInYaml(Ambiance ambiance, String serviceEntityYaml) throws IOException {
    YamlField yamlField = YamlUtils.readTree(serviceEntityYaml);
    YamlField serviceDefField =
        yamlField.getNode().getField(YamlTypes.SERVICE_ENTITY).getNode().getField(YamlTypes.SERVICE_DEFINITION);
    if (serviceDefField == null) {
      throw new InvalidRequestException(
          "Invalid Service being referred as serviceDefinition section is not there in Service");
    }

    YamlField serviceSpecField = serviceDefField.getNode().getField(YamlTypes.SERVICE_SPEC);
    if (serviceSpecField == null) {
      throw new InvalidRequestException(String.format(
          "Invalid Service being referred as spec inside serviceDefinition section is not there in Service"));
    }

    YamlField artifactsField = serviceSpecField.getNode().getField(YamlTypes.ARTIFACT_LIST_CONFIG);
    if (artifactsField == null) {
      return yamlField;
    }

    YamlField primaryArtifactField = artifactsField.getNode().getField(YamlTypes.PRIMARY_ARTIFACT);
    if (primaryArtifactField == null) {
      return yamlField;
    }

    YamlField primaryArtifactRef = primaryArtifactField.getNode().getField(YamlTypes.PRIMARY_ARTIFACT_REF);
    if (primaryArtifactRef == null) {
      return yamlField;
    }

    YamlField artifactSourcesField = primaryArtifactField.getNode().getField(YamlTypes.ARTIFACT_SOURCES);
    String primaryArtifactRefValue = primaryArtifactRef.getNode().asText();

    if (artifactSourcesField != null && artifactSourcesField.getNode().isArray() && primaryArtifactRefValue != null) {
      if (EmptyPredicate.isEmpty(primaryArtifactRefValue)) {
        throw new InvalidRequestException("Primary artifact ref cannot be empty");
      }

      ObjectNode artifactsNode = (ObjectNode) artifactsField.getNode().getCurrJsonNode();
      List<YamlNode> artifactSources = artifactSourcesField.getNode().asArray();

      ObjectNode primaryNode = null;
      // If there is only 1 artifact source, default to that
      if (artifactSources.size() == 1) {
        if (artifactSources.get(0).isObject()) {
          primaryNode = (ObjectNode) artifactSources.get(0).getCurrJsonNode();
          primaryNode.remove(YamlTypes.IDENTIFIER);
        }
      } else {
        primaryArtifactRefValue = cdExpressionResolver.renderExpression(ambiance, primaryArtifactRefValue);
        if (NGExpressionUtils.isRuntimeOrExpressionField(primaryArtifactRefValue)) {
          throw new InvalidRequestException("Primary artifact ref cannot be runtime or expression inside service");
        }
        for (YamlNode artifactSource : artifactSources) {
          String artifactSourceIdentifier = artifactSource.getIdentifier();
          if (primaryArtifactRefValue.equals(artifactSourceIdentifier) && artifactSource.isObject()) {
            primaryNode = (ObjectNode) artifactSource.getCurrJsonNode();
            primaryNode.remove(YamlTypes.IDENTIFIER);
            break;
          }
        }
      }

      if (primaryNode != null) {
        artifactsNode.set(YamlTypes.PRIMARY_ARTIFACT, primaryNode);
      } else {
        throw new InvalidRequestException(
            String.format("No artifact source exists with the identifier %s inside service", primaryArtifactRefValue));
      }
    }
    return yamlField;
  }

  public static Map<String, String> getTaskSetupAbstractions(BaseNGAccess ngAccess) {
    Map<String, String> owner = getNGTaskSetupAbstractionsWithOwner(
        ngAccess.getAccountIdentifier(), ngAccess.getOrgIdentifier(), ngAccess.getProjectIdentifier());
    Map<String, String> abstractions = new HashMap<>(owner);
    abstractions.put(SetupAbstractionKeys.ng, "true");
    if (ngAccess.getOrgIdentifier() != null) {
      abstractions.put(SetupAbstractionKeys.orgIdentifier, ngAccess.getOrgIdentifier());
    }
    if (ngAccess.getProjectIdentifier() != null) {
      abstractions.put(SetupAbstractionKeys.projectIdentifier, ngAccess.getProjectIdentifier());
    }
    return abstractions;
  }
}
