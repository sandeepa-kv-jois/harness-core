/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.manifest.mappers;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.cdng.manifest.ManifestType.AsgConfiguration;
import static io.harness.cdng.manifest.ManifestType.AsgLaunchTemplate;
import static io.harness.cdng.manifest.ManifestType.AsgScalingPolicy;
import static io.harness.cdng.manifest.ManifestType.AsgScheduledUpdateGroupAction;
import static io.harness.cdng.manifest.ManifestType.DeploymentRepo;
import static io.harness.cdng.manifest.ManifestType.EcsScalableTargetDefinition;
import static io.harness.cdng.manifest.ManifestType.EcsScalingPolicyDefinition;
import static io.harness.cdng.manifest.ManifestType.EcsServiceDefinition;
import static io.harness.cdng.manifest.ManifestType.EcsTaskDefinition;
import static io.harness.cdng.manifest.ManifestType.HelmChart;
import static io.harness.cdng.manifest.ManifestType.K8Manifest;
import static io.harness.cdng.manifest.ManifestType.Kustomize;
import static io.harness.cdng.manifest.ManifestType.KustomizePatches;
import static io.harness.cdng.manifest.ManifestType.OpenshiftParam;
import static io.harness.cdng.manifest.ManifestType.OpenshiftTemplate;
import static io.harness.cdng.manifest.ManifestType.ReleaseRepo;
import static io.harness.cdng.manifest.ManifestType.ServerlessAwsLambda;
import static io.harness.cdng.manifest.ManifestType.TAS_AUTOSCALER;
import static io.harness.cdng.manifest.ManifestType.TAS_MANIFEST;
import static io.harness.cdng.manifest.ManifestType.TAS_VARS;
import static io.harness.cdng.manifest.ManifestType.VALUES;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.manifest.yaml.AsgConfigurationManifestOutcome;
import io.harness.cdng.manifest.yaml.AsgLaunchTemplateManifestOutcome;
import io.harness.cdng.manifest.yaml.AsgScalingPolicyManifestOutcome;
import io.harness.cdng.manifest.yaml.AsgScheduledUpdateGroupActionManifestOutcome;
import io.harness.cdng.manifest.yaml.AutoScalerManifestOutcome;
import io.harness.cdng.manifest.yaml.DeploymentRepoManifestOutcome;
import io.harness.cdng.manifest.yaml.EcsScalableTargetDefinitionManifestOutcome;
import io.harness.cdng.manifest.yaml.EcsScalingPolicyDefinitionManifestOutcome;
import io.harness.cdng.manifest.yaml.EcsServiceDefinitionManifestOutcome;
import io.harness.cdng.manifest.yaml.EcsTaskDefinitionManifestOutcome;
import io.harness.cdng.manifest.yaml.HelmChartManifestOutcome;
import io.harness.cdng.manifest.yaml.K8sManifestOutcome;
import io.harness.cdng.manifest.yaml.KustomizeManifestOutcome;
import io.harness.cdng.manifest.yaml.KustomizePatchesManifestOutcome;
import io.harness.cdng.manifest.yaml.ManifestAttributes;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.cdng.manifest.yaml.OpenshiftManifestOutcome;
import io.harness.cdng.manifest.yaml.OpenshiftParamManifestOutcome;
import io.harness.cdng.manifest.yaml.ReleaseRepoManifestOutcome;
import io.harness.cdng.manifest.yaml.ServerlessAwsLambdaManifestOutcome;
import io.harness.cdng.manifest.yaml.TasManifestOutcome;
import io.harness.cdng.manifest.yaml.ValuesManifestOutcome;
import io.harness.cdng.manifest.yaml.VarsManifestOutcome;
import io.harness.cdng.manifest.yaml.kinds.AsgConfigurationManifest;
import io.harness.cdng.manifest.yaml.kinds.AsgLaunchTemplateManifest;
import io.harness.cdng.manifest.yaml.kinds.AsgScalingPolicyManifest;
import io.harness.cdng.manifest.yaml.kinds.AsgScheduledUpdateGroupActionManifest;
import io.harness.cdng.manifest.yaml.kinds.AutoScalerManifest;
import io.harness.cdng.manifest.yaml.kinds.EcsScalableTargetDefinitionManifest;
import io.harness.cdng.manifest.yaml.kinds.EcsScalingPolicyDefinitionManifest;
import io.harness.cdng.manifest.yaml.kinds.EcsServiceDefinitionManifest;
import io.harness.cdng.manifest.yaml.kinds.EcsTaskDefinitionManifest;
import io.harness.cdng.manifest.yaml.kinds.GitOpsDeploymentRepoManifest;
import io.harness.cdng.manifest.yaml.kinds.HelmChartManifest;
import io.harness.cdng.manifest.yaml.kinds.K8sManifest;
import io.harness.cdng.manifest.yaml.kinds.KustomizeManifest;
import io.harness.cdng.manifest.yaml.kinds.KustomizePatchesManifest;
import io.harness.cdng.manifest.yaml.kinds.OpenshiftManifest;
import io.harness.cdng.manifest.yaml.kinds.OpenshiftParamManifest;
import io.harness.cdng.manifest.yaml.kinds.ReleaseRepoManifest;
import io.harness.cdng.manifest.yaml.kinds.ServerlessAwsLambdaManifest;
import io.harness.cdng.manifest.yaml.kinds.TasManifest;
import io.harness.cdng.manifest.yaml.kinds.ValuesManifest;
import io.harness.cdng.manifest.yaml.kinds.VarsManifest;

import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(CDP)
public class ManifestOutcomeMapper {
  public List<ManifestOutcome> toManifestOutcome(List<ManifestAttributes> manifestAttributesList, int order) {
    return manifestAttributesList.stream()
        .map(manifest -> toManifestOutcome(manifest, order))
        .collect(Collectors.toCollection(LinkedList::new));
  }

  public ManifestOutcome toManifestOutcome(ManifestAttributes manifestAttributes, int order) {
    if (manifestAttributes.getStoreConfig() != null) {
      ManifestOutcomeValidator.validateStore(
          manifestAttributes.getStoreConfig(), manifestAttributes.getKind(), manifestAttributes.getIdentifier(), true);
    }

    switch (manifestAttributes.getKind()) {
      case K8Manifest:
        return getK8sOutcome(manifestAttributes);
      case VALUES:
        return getValuesOutcome(manifestAttributes, order);
      case TAS_MANIFEST:
        return getTasOutcome(manifestAttributes);
      case TAS_AUTOSCALER:
        return getAutoScalerOutcome(manifestAttributes);
      case TAS_VARS:
        return getVarsOutcome(manifestAttributes, order);
      case HelmChart:
        return getHelmChartOutcome(manifestAttributes);
      case Kustomize:
        return getKustomizeOutcome(manifestAttributes);
      case KustomizePatches:
        return getKustomizePatchesOutcome(manifestAttributes, order);
      case OpenshiftTemplate:
        return getOpenshiftOutcome(manifestAttributes);
      case OpenshiftParam:
        return getOpenshiftParamOutcome(manifestAttributes, order);
      case ServerlessAwsLambda:
        return getServerlessAwsOutcome(manifestAttributes, order);
      case ReleaseRepo:
        return getReleaseRepoOutcome(manifestAttributes);
      case DeploymentRepo:
        return getDeploymentRepoOutcome(manifestAttributes);
      case EcsTaskDefinition:
        return getEcsTaskDefinitionOutcome(manifestAttributes, order);
      case EcsServiceDefinition:
        return getEcsServiceDefinitionOutcome(manifestAttributes, order);
      case EcsScalableTargetDefinition:
        return getEcsScalableTargetDefinitionOutcome(manifestAttributes, order);
      case EcsScalingPolicyDefinition:
        return getEcsScalingPolicyDefinitionOutcome(manifestAttributes, order);
      case AsgLaunchTemplate:
        return getAsgLaunchTemplateOutcome(manifestAttributes);
      case AsgConfiguration:
        return getAsgConfigurationOutcome(manifestAttributes);
      case AsgScalingPolicy:
        return getAsgScalingPolicyOutcome(manifestAttributes);
      case AsgScheduledUpdateGroupAction:
        return getAsgScheduledUpdateGroupActionOutcome(manifestAttributes);
      default:
        throw new UnsupportedOperationException(
            format("Unknown Artifact Config type: [%s]", manifestAttributes.getKind()));
    }
  }

  private static ManifestOutcome getReleaseRepoOutcome(ManifestAttributes manifestAttributes) {
    ReleaseRepoManifest attributes = (ReleaseRepoManifest) manifestAttributes;
    return ReleaseRepoManifestOutcome.builder()
        .identifier(attributes.getIdentifier())
        .store(attributes.getStoreConfig())
        .build();
  }

  private static ManifestOutcome getDeploymentRepoOutcome(ManifestAttributes manifestAttributes) {
    GitOpsDeploymentRepoManifest attributes = (GitOpsDeploymentRepoManifest) manifestAttributes;
    return DeploymentRepoManifestOutcome.builder()
        .identifier(attributes.getIdentifier())
        .store(attributes.getStoreConfig())
        .build();
  }

  private K8sManifestOutcome getK8sOutcome(ManifestAttributes manifestAttributes) {
    K8sManifest k8sManifest = (K8sManifest) manifestAttributes;

    return K8sManifestOutcome.builder()
        .identifier(k8sManifest.getIdentifier())
        .store(k8sManifest.getStoreConfig())
        .valuesPaths(k8sManifest.getValuesPaths())
        .skipResourceVersioning(k8sManifest.getSkipResourceVersioning())
        .build();
  }

  private TasManifestOutcome getTasOutcome(ManifestAttributes manifestAttributes) {
    TasManifest tasManifest = (TasManifest) manifestAttributes;

    return TasManifestOutcome.builder()
        .identifier(tasManifest.getIdentifier())
        .store(tasManifest.getStoreConfig())
        .cfCliVersion(tasManifest.getCfCliVersion())
        .varsPaths(tasManifest.getVarsPaths())
        .autoScalerPath(tasManifest.getAutoScalerPath())
        .build();
  }

  private AutoScalerManifestOutcome getAutoScalerOutcome(ManifestAttributes manifestAttributes) {
    AutoScalerManifest autoScalerManifest = (AutoScalerManifest) manifestAttributes;

    return AutoScalerManifestOutcome.builder()
        .identifier(autoScalerManifest.getIdentifier())
        .store(autoScalerManifest.getStoreConfig())
        .build();
  }

  private ValuesManifestOutcome getValuesOutcome(ManifestAttributes manifestAttributes, int order) {
    ValuesManifest attributes = (ValuesManifest) manifestAttributes;
    return ValuesManifestOutcome.builder()
        .identifier(attributes.getIdentifier())
        .store(attributes.getStoreConfig())
        .order(order)
        .build();
  }

  private VarsManifestOutcome getVarsOutcome(ManifestAttributes manifestAttributes, int order) {
    VarsManifest attributes = (VarsManifest) manifestAttributes;
    return VarsManifestOutcome.builder()
        .identifier(attributes.getIdentifier())
        .store(attributes.getStoreConfig())
        .order(order)
        .build();
  }

  private HelmChartManifestOutcome getHelmChartOutcome(ManifestAttributes manifestAttributes) {
    HelmChartManifest helmChartManifest = (HelmChartManifest) manifestAttributes;

    return HelmChartManifestOutcome.builder()
        .identifier(helmChartManifest.getIdentifier())
        .store(helmChartManifest.getStoreConfig())
        .chartName(helmChartManifest.getChartName())
        .chartVersion(helmChartManifest.getChartVersion())
        .helmVersion(helmChartManifest.getHelmVersion())
        .valuesPaths(helmChartManifest.getValuesPaths())
        .skipResourceVersioning(helmChartManifest.getSkipResourceVersioning())
        .commandFlags(helmChartManifest.getCommandFlags())
        .build();
  }

  private KustomizeManifestOutcome getKustomizeOutcome(ManifestAttributes manifestAttributes) {
    KustomizeManifest kustomizeManifest = (KustomizeManifest) manifestAttributes;
    return KustomizeManifestOutcome.builder()
        .identifier(kustomizeManifest.getIdentifier())
        .store(kustomizeManifest.getStoreConfig())
        .skipResourceVersioning(kustomizeManifest.getSkipResourceVersioning())
        .pluginPath(kustomizeManifest.getPluginPath())
        .patchesPaths(kustomizeManifest.getPatchesPaths())
        .overlayConfiguration(kustomizeManifest.getOverlayConfiguration())
        .build();
  }

  private KustomizePatchesManifestOutcome getKustomizePatchesOutcome(ManifestAttributes manifestAttributes, int order) {
    KustomizePatchesManifest attributes = (KustomizePatchesManifest) manifestAttributes;
    return KustomizePatchesManifestOutcome.builder()
        .identifier(attributes.getIdentifier())
        .store(attributes.getStoreConfig())
        .order(order)
        .build();
  }

  private OpenshiftManifestOutcome getOpenshiftOutcome(ManifestAttributes manifestAttributes) {
    OpenshiftManifest openshiftManifest = (OpenshiftManifest) manifestAttributes;

    return OpenshiftManifestOutcome.builder()
        .identifier(openshiftManifest.getIdentifier())
        .store(openshiftManifest.getStoreConfig())
        .skipResourceVersioning(openshiftManifest.getSkipResourceVersioning())
        .paramsPaths(openshiftManifest.getParamsPaths())
        .build();
  }

  private OpenshiftParamManifestOutcome getOpenshiftParamOutcome(ManifestAttributes manifestAttributes, int order) {
    OpenshiftParamManifest attributes = (OpenshiftParamManifest) manifestAttributes;

    return OpenshiftParamManifestOutcome.builder()
        .identifier(attributes.getIdentifier())
        .store(attributes.getStoreConfig())
        .order(order)
        .build();
  }

  private ServerlessAwsLambdaManifestOutcome getServerlessAwsOutcome(ManifestAttributes manifestAttributes, int order) {
    ServerlessAwsLambdaManifest attributes = (ServerlessAwsLambdaManifest) manifestAttributes;
    return ServerlessAwsLambdaManifestOutcome.builder()
        .identifier(attributes.getIdentifier())
        .store(attributes.getStoreConfig())
        .configOverridePath(attributes.getConfigOverridePath())
        .order(order)
        .build();
  }

  private EcsTaskDefinitionManifestOutcome getEcsTaskDefinitionOutcome(
      ManifestAttributes manifestAttributes, int order) {
    EcsTaskDefinitionManifest attributes = (EcsTaskDefinitionManifest) manifestAttributes;
    return EcsTaskDefinitionManifestOutcome.builder()
        .identifier(attributes.getIdentifier())
        .store(attributes.getStoreConfig())
        .order(order)
        .build();
  }

  private EcsServiceDefinitionManifestOutcome getEcsServiceDefinitionOutcome(
      ManifestAttributes manifestAttributes, int order) {
    EcsServiceDefinitionManifest attributes = (EcsServiceDefinitionManifest) manifestAttributes;
    return EcsServiceDefinitionManifestOutcome.builder()
        .identifier(attributes.getIdentifier())
        .store(attributes.getStoreConfig())
        .order(order)
        .build();
  }

  private EcsScalableTargetDefinitionManifestOutcome getEcsScalableTargetDefinitionOutcome(
      ManifestAttributes manifestAttributes, int order) {
    EcsScalableTargetDefinitionManifest attributes = (EcsScalableTargetDefinitionManifest) manifestAttributes;
    return EcsScalableTargetDefinitionManifestOutcome.builder()
        .identifier(attributes.getIdentifier())
        .store(attributes.getStoreConfig())
        .order(order)
        .build();
  }

  private EcsScalingPolicyDefinitionManifestOutcome getEcsScalingPolicyDefinitionOutcome(
      ManifestAttributes manifestAttributes, int order) {
    EcsScalingPolicyDefinitionManifest attributes = (EcsScalingPolicyDefinitionManifest) manifestAttributes;
    return EcsScalingPolicyDefinitionManifestOutcome.builder()
        .identifier(attributes.getIdentifier())
        .store(attributes.getStoreConfig())
        .order(order)
        .build();
  }

  private AsgLaunchTemplateManifestOutcome getAsgLaunchTemplateOutcome(ManifestAttributes manifestAttributes) {
    AsgLaunchTemplateManifest manifest = (AsgLaunchTemplateManifest) manifestAttributes;
    return AsgLaunchTemplateManifestOutcome.builder()
        .identifier(manifest.getIdentifier())
        .store(manifest.getStoreConfig())
        .build();
  }

  private AsgConfigurationManifestOutcome getAsgConfigurationOutcome(ManifestAttributes manifestAttributes) {
    AsgConfigurationManifest manifest = (AsgConfigurationManifest) manifestAttributes;
    return AsgConfigurationManifestOutcome.builder()
        .identifier(manifest.getIdentifier())
        .store(manifest.getStoreConfig())
        .build();
  }

  private AsgScalingPolicyManifestOutcome getAsgScalingPolicyOutcome(ManifestAttributes manifestAttributes) {
    AsgScalingPolicyManifest manifest = (AsgScalingPolicyManifest) manifestAttributes;
    return AsgScalingPolicyManifestOutcome.builder()
        .identifier(manifest.getIdentifier())
        .store(manifest.getStoreConfig())
        .build();
  }

  private AsgScheduledUpdateGroupActionManifestOutcome getAsgScheduledUpdateGroupActionOutcome(
      ManifestAttributes manifestAttributes) {
    AsgScheduledUpdateGroupActionManifest manifest = (AsgScheduledUpdateGroupActionManifest) manifestAttributes;
    return AsgScheduledUpdateGroupActionManifestOutcome.builder()
        .identifier(manifest.getIdentifier())
        .store(manifest.getStoreConfig())
        .build();
  }
}
