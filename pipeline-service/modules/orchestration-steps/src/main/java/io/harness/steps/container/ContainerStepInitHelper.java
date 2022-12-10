/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.container;

import static io.harness.beans.sweepingoutputs.ContainerPortDetails.PORT_DETAILS;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.HARNESS_SERVICE_LOG_KEY_VARIABLE;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.PORT_STARTING_RANGE;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.STEP_PREFIX;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.STEP_REQUEST_MEMORY_MIB;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.STEP_REQUEST_MILLI_CPU;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.beans.connector.ConnectorType.DOCKER;
import static io.harness.steps.container.constants.ContainerStepExecutionConstants.CLEANUP_DETAILS;
import static io.harness.steps.container.execution.ContainerDetailsSweepingOutput.INIT_POD;
import static io.harness.steps.plugin.infrastructure.ContainerStepInfra.Type.KUBERNETES_DIRECT;

import static java.lang.String.format;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.beans.environment.ConnectorConversionInfo;
import io.harness.beans.environment.pod.container.ContainerDefinitionInfo;
import io.harness.beans.environment.pod.container.ContainerImageDetails;
import io.harness.beans.sweepingoutputs.ContainerPortDetails;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.ci.buildstate.StepContainerUtils;
import io.harness.ci.utils.ContainerSecretEvaluator;
import io.harness.ci.utils.PortFinder;
import io.harness.delegate.beans.ci.k8s.CIK8InitializeTaskParams;
import io.harness.delegate.beans.ci.pod.CIContainerType;
import io.harness.delegate.beans.ci.pod.CIK8ContainerParams;
import io.harness.delegate.beans.ci.pod.CIK8PodParams;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.ci.pod.ContainerResourceParams;
import io.harness.delegate.beans.ci.pod.ContainerSecrets;
import io.harness.delegate.beans.ci.pod.ContainerSecurityContext;
import io.harness.delegate.beans.ci.pod.ImageDetailsWithConnector;
import io.harness.delegate.beans.ci.pod.PodVolume;
import io.harness.delegate.beans.ci.pod.SecretVariableDetails;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.docker.DockerConnectorDTO;
import io.harness.delegate.task.citasks.cik8handler.params.CIConstants;
import io.harness.k8s.model.ImageDetails;
import io.harness.ng.core.NGAccess;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.expression.ExpressionResolverUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.steps.container.exception.ContainerStepExecutionException;
import io.harness.steps.container.execution.ContainerDetailsSweepingOutput;
import io.harness.steps.container.utils.ConnectorUtils;
import io.harness.steps.container.utils.ContainerParamsProvider;
import io.harness.steps.container.utils.ContainerStepImageUtils;
import io.harness.steps.container.utils.ContainerStepResolverUtils;
import io.harness.steps.container.utils.K8sPodInitUtils;
import io.harness.steps.container.utils.SecretUtils;
import io.harness.steps.plugin.ContainerStepInfo;
import io.harness.steps.plugin.infrastructure.ContainerCleanupDetails;
import io.harness.steps.plugin.infrastructure.ContainerK8sInfra;
import io.harness.utils.IdentifierRefHelper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class ContainerStepInitHelper {
  @Inject private ConnectorUtils connectorUtils;
  @Inject private ExecutionSweepingOutputService executionSweepingOutputResolver;
  @Inject private ContainerStepImageUtils harnessImageUtils;
  @Inject private ContainerParamsProvider internalContainerParamsProvider;

  @Inject K8sPodInitUtils k8sPodInitUtils;
  @Inject SecretUtils secretUtils;

  public CIK8InitializeTaskParams getK8InitializeTaskParams(
      ContainerStepInfo containerStepInfo, Ambiance ambiance, String logPrefix) {
    ContainerK8sInfra infrastructure = (ContainerK8sInfra) containerStepInfo.getInfrastructure();

    if (infrastructure == null) {
      throw new ContainerStepExecutionException("Input infrastructure can not be empty");
    }

    if (infrastructure.getType() != KUBERNETES_DIRECT) {
      throw new ContainerStepExecutionException(format("Invalid infrastructure type: %s", infrastructure.getType()));
    }

    ContainerDetailsSweepingOutput k8PodDetails = ContainerDetailsSweepingOutput.builder()
                                                      .stepIdentifier(containerStepInfo.getIdentifier())
                                                      .accountId(AmbianceUtils.getAccountId(ambiance))
                                                      .build();
    k8sPodInitUtils.consumeSweepingOutput(ambiance, k8PodDetails, INIT_POD);

    return buildK8DirectTaskParams(containerStepInfo, k8PodDetails, infrastructure, ambiance, logPrefix);
  }

  private CIK8InitializeTaskParams buildK8DirectTaskParams(ContainerStepInfo containerStepInfo,
      ContainerDetailsSweepingOutput k8PodDetails, ContainerK8sInfra infrastructure, Ambiance ambiance,
      String logPrefix) {
    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
    String connectorRef = infrastructure.getSpec().getConnectorRef().getValue();

    ConnectorDetails k8sConnector = connectorUtils.getConnectorDetails(ngAccess, connectorRef);
    return CIK8InitializeTaskParams.builder()
        .k8sConnector(k8sConnector)
        .cik8PodParams(getK8DirectPodParams(containerStepInfo, k8PodDetails, infrastructure, ambiance, logPrefix))
        .podMaxWaitUntilReadySecs(k8sPodInitUtils.getPodWaitUntilReadTimeout(infrastructure))
        .build();
  }

  private CIK8PodParams<CIK8ContainerParams> getK8DirectPodParams(ContainerStepInfo containerStepInfo,
      ContainerDetailsSweepingOutput k8PodDetails, ContainerK8sInfra k8sDirectInfraYaml, Ambiance ambiance,
      String logPrefix) {
    String podName = getPodName(ambiance, containerStepInfo.getIdentifier().toLowerCase());
    Map<String, String> buildLabels = k8sPodInitUtils.getLabels(ambiance, containerStepInfo.getIdentifier());
    Map<String, String> annotations = ExpressionResolverUtils.resolveMapParameter(
        "annotations", "ContainerStep", "stepSetup", k8sDirectInfraYaml.getSpec().getAnnotations(), false);
    Map<String, String> labels = ExpressionResolverUtils.resolveMapParameter(
        "labels", "ContainerStep", "stepSetup", k8sDirectInfraYaml.getSpec().getLabels(), false);
    Map<String, String> nodeSelector = ExpressionResolverUtils.resolveMapParameter(
        "nodeSelector", "ContainerStep", "stepSetup", k8sDirectInfraYaml.getSpec().getNodeSelector(), false);
    Integer stepAsUser =
        ExpressionResolverUtils.resolveIntegerParameter(k8sDirectInfraYaml.getSpec().getRunAsUser(), null);
    String serviceAccountName = ExpressionResolverUtils.resolveStringParameter("serviceAccountName", "ContainerStep",
        "stageSetup", k8sDirectInfraYaml.getSpec().getServiceAccountName(), false);

    if (isNotEmpty(labels)) {
      buildLabels.putAll(labels);
    }

    List<PodVolume> volumes = k8sPodInitUtils.convertDirectK8Volumes(k8sDirectInfraYaml);
    Pair<CIK8ContainerParams, List<CIK8ContainerParams>> podContainers =
        getStepContainers(containerStepInfo, k8PodDetails, k8sDirectInfraYaml, ambiance, volumes, logPrefix);
    saveSweepingOutput(podName, k8sDirectInfraYaml, podContainers, ambiance);

    return CIK8PodParams.<CIK8ContainerParams>builder()
        .name(podName)
        .namespace(k8sDirectInfraYaml.getSpec().getNamespace().getValue())
        .labels(buildLabels)
        .serviceAccountName(serviceAccountName)
        .annotations(annotations)
        .nodeSelector(nodeSelector)
        .runAsUser(stepAsUser)
        .automountServiceAccountToken(k8sDirectInfraYaml.getSpec().getAutomountServiceAccountToken().getValue())
        .priorityClassName(k8sDirectInfraYaml.getSpec().getPriorityClassName().getValue())
        //                .tolerations(k8InitializeTaskUtils.getPodTolerations(k8sDirectInfraYaml.getSpec().getTolerations()))
        .containerParamsList(podContainers.getRight())
        .initContainerParamsList(singletonList(podContainers.getLeft()))
        .activeDeadLineSeconds(CIConstants.POD_MAX_TTL_SECS)
        .volumes(volumes)
        .build();
  }

  private Pair<CIK8ContainerParams, List<CIK8ContainerParams>> getStepContainers(ContainerStepInfo containerStepInfo,
      ContainerDetailsSweepingOutput k8PodDetails, ContainerK8sInfra infrastructure, Ambiance ambiance,
      List<PodVolume> volumes, String logPrefix) {
    Map<String, String> volumeToMountPath = k8sPodInitUtils.getVolumeToMountPath(volumes);
    OSType os = k8sPodInitUtils.getOS(infrastructure);
    String accountId = AmbianceUtils.getAccountId(ambiance);
    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);

    Map<String, String> logEnvVars = k8sPodInitUtils.getLogServiceEnvVariables(k8PodDetails, accountId);
    Map<String, String> commonEnvVars =
        k8sPodInitUtils.getCommonStepEnvVariables(k8PodDetails, k8sPodInitUtils.getWorkDir(), logPrefix, ambiance);

    ConnectorDetails harnessInternalImageConnector =
        harnessImageUtils.getHarnessImageConnectorDetailsForK8(ngAccess, infrastructure);

    ContainerSecretEvaluator liteEngineSecretEvaluator =
        ContainerSecretEvaluator.builder().secretUtils(secretUtils).build();
    List<SecretVariableDetails> secretVariableDetails =
        liteEngineSecretEvaluator.resolve(containerStepInfo, ngAccess, ambiance.getExpressionFunctorToken());
    k8sPodInitUtils.checkSecretAccess(ambiance, secretVariableDetails, accountId,
        AmbianceUtils.getProjectIdentifier(ambiance), AmbianceUtils.getOrgIdentifier(ambiance));

    CIK8ContainerParams setupAddOnContainerParams = internalContainerParamsProvider.getSetupAddonContainerParams(
        harnessInternalImageConnector, volumeToMountPath, k8sPodInitUtils.getWorkDir(),
        k8sPodInitUtils.getCtrSecurityContext(infrastructure), ngAccess.getAccountIdentifier(), os);

    Pair<Integer, Integer> wrapperRequests = k8sPodInitUtils.getStepRequest(containerStepInfo, accountId);
    Integer stageCpuRequest = wrapperRequests.getLeft();
    Integer stageMemoryRequest = wrapperRequests.getRight();

    CIK8ContainerParams liteEngineContainerParams = internalContainerParamsProvider.getLiteEngineContainerParams(
        harnessInternalImageConnector, k8PodDetails, stageCpuRequest, stageMemoryRequest, logEnvVars, volumeToMountPath,
        k8sPodInitUtils.getWorkDir(), k8sPodInitUtils.getCtrSecurityContext(infrastructure), logPrefix, ambiance);

    List<CIK8ContainerParams> containerParams = new ArrayList<>();
    containerParams.add(liteEngineContainerParams);

    List<ContainerDefinitionInfo> stepCtrDefinitions =
        getStepContainerDefinitions(containerStepInfo, infrastructure, ambiance);
    consumePortDetails(ambiance, stepCtrDefinitions);
    // todo(abhinav) skipping it for run step.
    //        Map<String, List<K8BuildJobEnvInfo.ConnectorConversionInfo>> stepConnectors =
    //                k8sPodInitUtils.getStepConnectorRefs(containerStepInfo.getStageElementConfig(), ambiance);

    for (ContainerDefinitionInfo containerDefinitionInfo : stepCtrDefinitions) {
      CIK8ContainerParams cik8ContainerParams =
          createCIK8ContainerParams(ngAccess, containerDefinitionInfo, harnessInternalImageConnector, commonEnvVars,
              Collections.emptyMap(), volumeToMountPath, k8sPodInitUtils.getWorkDir(),
              k8sPodInitUtils.getCtrSecurityContext(infrastructure), logPrefix, secretVariableDetails, os);
      containerParams.add(cik8ContainerParams);
    }
    return Pair.of(setupAddOnContainerParams, containerParams);
  }

  private CIK8ContainerParams createCIK8ContainerParams(NGAccess ngAccess,
      ContainerDefinitionInfo containerDefinitionInfo, ConnectorDetails harnessInternalImageConnector,
      Map<String, String> commonEnvVars, Map<String, List<ConnectorConversionInfo>> connectorRefs,
      Map<String, String> volumeToMountPath, String workDirPath, ContainerSecurityContext ctrSecurityContext,
      String logPrefix, List<SecretVariableDetails> secretVariableDetails, OSType os) {
    Map<String, String> envVars = new HashMap<>();
    if (isNotEmpty(containerDefinitionInfo.getEnvVars())) {
      envVars.putAll(containerDefinitionInfo.getEnvVars()); // Put customer input env variables
    }
    Map<String, ConnectorDetails> stepConnectorDetails = new HashMap<>();
    if (isNotEmpty(containerDefinitionInfo.getStepIdentifier()) && isNotEmpty(connectorRefs)) {
      List<ConnectorConversionInfo> connectorConversionInfos =
          connectorRefs.get(containerDefinitionInfo.getStepIdentifier());
      if (connectorConversionInfos != null && connectorConversionInfos.size() > 0) {
        for (ConnectorConversionInfo connectorConversionInfo : connectorConversionInfos) {
          ConnectorDetails connectorDetails =
              connectorUtils.getConnectorDetailsWithConversionInfo(ngAccess, connectorConversionInfo);
          IdentifierRef identifierRef = IdentifierRefHelper.getIdentifierRef(connectorConversionInfo.getConnectorRef(),
              ngAccess.getAccountIdentifier(), ngAccess.getOrgIdentifier(), ngAccess.getProjectIdentifier());
          stepConnectorDetails.put(identifierRef.getFullyQualifiedName(), connectorDetails);
        }
      }
    }

    ImageDetails imageDetails = containerDefinitionInfo.getContainerImageDetails().getImageDetails();
    ConnectorDetails connectorDetails = null;
    if (containerDefinitionInfo.getContainerImageDetails().getConnectorIdentifier() != null) {
      connectorDetails = connectorUtils.getConnectorDetails(
          ngAccess, containerDefinitionInfo.getContainerImageDetails().getConnectorIdentifier());
    }

    ConnectorDetails imgConnector = connectorDetails;
    if (containerDefinitionInfo.isHarnessManagedImage()) {
      imgConnector = harnessInternalImageConnector;
    }
    String fullyQualifiedImageName = getFullyQualifiedImageName(imageDetails.getName(), imgConnector);
    imageDetails.setName(fullyQualifiedImageName);
    ImageDetailsWithConnector imageDetailsWithConnector =
        ImageDetailsWithConnector.builder().imageConnectorDetails(imgConnector).imageDetails(imageDetails).build();

    List<SecretVariableDetails> containerSecretVariableDetails =
        k8sPodInitUtils.getSecretVariableDetails(ngAccess, containerDefinitionInfo, secretVariableDetails);

    Map<String, String> envVarsWithSecretRef = k8sPodInitUtils.removeEnvVarsWithSecretRef(envVars);
    envVars.putAll(commonEnvVars); //  commonEnvVars needs to be put in end because they overrides webhook parameters
    if (containerDefinitionInfo.getContainerType() == CIContainerType.SERVICE) {
      envVars.put(HARNESS_SERVICE_LOG_KEY_VARIABLE,
          format("%s/serviceId:%s", logPrefix, containerDefinitionInfo.getStepIdentifier()));
    }

    if (containerDefinitionInfo.getPrivileged() != null) {
      ctrSecurityContext.setPrivileged(containerDefinitionInfo.getPrivileged());
    }
    if (containerDefinitionInfo.getRunAsUser() != null) {
      ctrSecurityContext.setRunAsUser(containerDefinitionInfo.getRunAsUser());
    }
    boolean privileged = containerDefinitionInfo.getPrivileged() != null && containerDefinitionInfo.getPrivileged();

    CIK8ContainerParams cik8ContainerParams =
        CIK8ContainerParams.builder()
            .name(containerDefinitionInfo.getName())
            .containerResourceParams(containerDefinitionInfo.getContainerResourceParams())
            .containerType(containerDefinitionInfo.getContainerType())
            .envVars(envVars)
            .envVarsWithSecretRef(envVarsWithSecretRef)
            .containerSecrets(
                ContainerSecrets.builder()
                    .secretVariableDetails(containerSecretVariableDetails)
                    .connectorDetailsMap(stepConnectorDetails)
                    .plainTextSecretsByName(internalContainerParamsProvider.getLiteEngineSecretVars(emptyMap()))
                    .build())
            .commands(containerDefinitionInfo.getCommands())
            .ports(containerDefinitionInfo.getPorts())
            .args(containerDefinitionInfo.getArgs())
            .imageDetailsWithConnector(imageDetailsWithConnector)
            .volumeToMountPath(volumeToMountPath)
            .imagePullPolicy(containerDefinitionInfo.getImagePullPolicy())
            .securityContext(ctrSecurityContext)
            .build();

    if (os != OSType.Windows) {
      cik8ContainerParams.setPrivileged(privileged);
      cik8ContainerParams.setRunAsUser(containerDefinitionInfo.getRunAsUser());
    }

    if (containerDefinitionInfo.getContainerType() != CIContainerType.SERVICE) {
      cik8ContainerParams.setWorkingDir(workDirPath);
    }
    return cik8ContainerParams;
  }

  private List<ContainerDefinitionInfo> getStepContainerDefinitions(
      ContainerStepInfo initializeStepInfo, ContainerK8sInfra infrastructure, Ambiance ambiance) {
    OSType os = k8sPodInitUtils.getOS(infrastructure);
    Set<Integer> usedPorts = new HashSet<>();
    PortFinder portFinder = PortFinder.builder().startingPort(PORT_STARTING_RANGE).usedPorts(usedPorts).build();

    ContainerDefinitionInfo stepCtrDefinitionInfos =
        createStepContainerDefinitions(initializeStepInfo, portFinder, AmbianceUtils.getAccountId(ambiance), os);

    List<ContainerDefinitionInfo> containerDefinitionInfos = new ArrayList<>();
    containerDefinitionInfos.add(stepCtrDefinitionInfos);
    return containerDefinitionInfos;
  }

  private void saveSweepingOutput(String podName, ContainerK8sInfra infrastructure,
      Pair<CIK8ContainerParams, List<CIK8ContainerParams>> podContainers, Ambiance ambiance) {
    List<String> containerNames = podContainers.getRight().stream().map(CIK8ContainerParams::getName).collect(toList());
    containerNames.add(podContainers.getLeft().getName());

    k8sPodInitUtils.consumeSweepingOutput(ambiance,
        ContainerCleanupDetails.builder()
            .infrastructure(infrastructure)
            .podName(podName)
            .cleanUpContainerNames(containerNames)
            .build(),
        CLEANUP_DETAILS);
  }

  private String getPodName(Ambiance ambiance, String stageId) {
    return k8sPodInitUtils.generatePodName(stageId);
  }

  private void consumePortDetails(Ambiance ambiance, List<ContainerDefinitionInfo> containerDefinitionInfos) {
    Map<String, List<Integer>> portDetails = containerDefinitionInfos.stream().collect(
        Collectors.toMap(ContainerDefinitionInfo::getStepIdentifier, ContainerDefinitionInfo::getPorts));
    k8sPodInitUtils.consumeSweepingOutput(
        ambiance, ContainerPortDetails.builder().portDetails(portDetails).build(), PORT_DETAILS);
  }

  private ContainerDefinitionInfo createStepContainerDefinitions(
      ContainerStepInfo runStepInfo, PortFinder portFinder, String accountId, OSType os) {
    if (runStepInfo.getImage() == null) {
      throw new ContainerStepExecutionException("image can't be empty in k8s infrastructure");
    }

    if (runStepInfo.getConnectorRef() == null) {
      throw new ContainerStepExecutionException("connector ref can't be empty in k8s infrastructure");
    }
    String identifier = runStepInfo.getIdentifier();
    Integer port = portFinder.getNextPort();
    String containerName = format("%s%s", STEP_PREFIX, runStepInfo.getIdentifier().toLowerCase());

    Map<String, String> stepEnvVars = new HashMap<>();
    Map<String, String> envvars = ExpressionResolverUtils.resolveMapParameter(
        "envVariables", "Run", identifier, runStepInfo.getEnvVariables(), false);
    if (!isEmpty(envvars)) {
      stepEnvVars.putAll(envvars);
    }
    Integer runAsUser = ExpressionResolverUtils.resolveIntegerParameter(runStepInfo.getRunAsUser(), null);

    return ContainerDefinitionInfo.builder()
        .name(containerName)
        .commands(StepContainerUtils.getCommand(os))
        .args(StepContainerUtils.getArguments(port))
        .envVars(stepEnvVars)
        .stepIdentifier(identifier)
        .containerImageDetails(
            ContainerImageDetails.builder()
                .imageDetails(k8sPodInitUtils.getImageInfo(ExpressionResolverUtils.resolveStringParameter(
                    "Image", "Run", identifier, runStepInfo.getImage(), true)))
                .connectorIdentifier(ExpressionResolverUtils.resolveStringParameter(
                    "connectorRef", "Run", identifier, runStepInfo.getConnectorRef(), true))
                .build())
        .containerResourceParams(getStepContainerResource(runStepInfo, "Run", identifier, accountId))
        .ports(Arrays.asList(port))
        .containerType(CIContainerType.RUN)
        .stepName(runStepInfo.getName())
        .privileged(runStepInfo.getPrivileged().getValue())
        .runAsUser(runAsUser)
        .imagePullPolicy(ContainerStepResolverUtils.resolveImagePullPolicy(runStepInfo.getImagePullPolicy()))
        .build();
  }

  private ContainerResourceParams getStepContainerResource(
      ContainerStepInfo resource, String stepType, String stepId, String accountId) {
    Integer cpuLimit;
    Integer memoryLimit;
    Pair<Integer, Integer> stepRequest = k8sPodInitUtils.getStepRequest(resource, accountId);
    cpuLimit = stepRequest.getLeft();
    memoryLimit = stepRequest.getRight();

    return ContainerResourceParams.builder()
        .resourceRequestMilliCpu(STEP_REQUEST_MILLI_CPU)
        .resourceRequestMemoryMiB(STEP_REQUEST_MEMORY_MIB)
        .resourceLimitMilliCpu(cpuLimit)
        .resourceLimitMemoryMiB(memoryLimit)
        .build();
  }

  public String getFullyQualifiedImageName(String imageName, ConnectorDetails connectorDetails) {
    if (connectorDetails == null) {
      return imageName;
    }

    ConnectorType connectorType = connectorDetails.getConnectorType();
    if (connectorType != DOCKER) {
      return imageName;
    }

    DockerConnectorDTO dockerConnectorDTO = (DockerConnectorDTO) connectorDetails.getConnectorConfig();
    String dockerRegistryUrl = dockerConnectorDTO.getDockerRegistryUrl();
    return harnessImageUtils.getImageWithRegistryPath(imageName, dockerRegistryUrl, connectorDetails.getIdentifier());
  }
}
