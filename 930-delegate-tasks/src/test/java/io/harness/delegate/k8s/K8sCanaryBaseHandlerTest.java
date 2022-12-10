/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.k8s;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.delegate.k8s.K8sTestConstants.CONFIG_MAP_YAML;
import static io.harness.delegate.k8s.K8sTestConstants.DAEMON_SET_YAML;
import static io.harness.delegate.k8s.K8sTestConstants.DEPLOYMENT_YAML;
import static io.harness.k8s.releasehistory.IK8sRelease.Status.Failed;
import static io.harness.k8s.releasehistory.IK8sRelease.Status.InProgress;
import static io.harness.k8s.releasehistory.ReleaseHistory.defaultVersion;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.LogLevel.ERROR;
import static io.harness.rule.OwnerRule.ABOSII;
import static io.harness.rule.OwnerRule.ANSHUL;
import static io.harness.rule.OwnerRule.NAMAN_TALAYCHA;
import static io.harness.rule.OwnerRule.YOGESH;

import static java.lang.String.format;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.k8s.beans.K8sCanaryHandlerConfig;
import io.harness.delegate.task.k8s.K8sTaskHelperBase;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.ExplanationException;
import io.harness.exception.HintException;
import io.harness.exception.KubernetesTaskException;
import io.harness.k8s.exception.KubernetesExceptionExplanation;
import io.harness.k8s.exception.KubernetesExceptionHints;
import io.harness.k8s.exception.KubernetesExceptionMessages;
import io.harness.k8s.kubectl.Kubectl;
import io.harness.k8s.manifest.ManifestHelper;
import io.harness.k8s.model.HarnessLabelValues;
import io.harness.k8s.model.K8sDelegateTaskParams;
import io.harness.k8s.model.K8sPod;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.k8s.model.KubernetesResource;
import io.harness.k8s.model.KubernetesResourceId;
import io.harness.k8s.releasehistory.IK8sRelease;
import io.harness.k8s.releasehistory.K8sLegacyRelease;
import io.harness.k8s.releasehistory.ReleaseHistory;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
import io.harness.rule.Owner;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(CDP)
public class K8sCanaryBaseHandlerTest extends CategoryTest {
  @Mock private K8sTaskHelperBase k8sTaskHelperBase;
  @InjectMocks private K8sCanaryBaseHandler k8sCanaryBaseHandler;

  @Mock private LogCallback logCallback;
  @Mock private Kubectl client;

  private final String namespace = "default";
  private final K8sDelegateTaskParams delegateTaskParams = K8sDelegateTaskParams.builder().build();
  private final KubernetesConfig kubernetesConfig = KubernetesConfig.builder().namespace(namespace).build();

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testDeploymentWorkloadsForCanaryNoWorkload() throws Exception {
    K8sCanaryHandlerConfig k8sCanaryHandlerConfig = prepareNoWorkloadNoResource();
    boolean result =
        k8sCanaryBaseHandler.prepareForCanary(k8sCanaryHandlerConfig, delegateTaskParams, false, logCallback, false);
    assertInvalidWorkloadsInManifest(result,
        "\nNo workload found in the Manifests. Can't do Canary Deployment. Only Deployment, DeploymentConfig (OpenShift) and StatefulSet workloads are supported in Canary workflow type.");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testDeploymentWorkloadsForCanaryNoWorkloadIsErrorFrameworkEnabled() {
    K8sCanaryHandlerConfig k8sCanaryHandlerConfig = prepareNoWorkloadNoResource();
    assertThatThrownBy(()
                           -> k8sCanaryBaseHandler.prepareForCanary(
                               k8sCanaryHandlerConfig, delegateTaskParams, false, logCallback, true))
        .matches(throwable -> {
          HintException hint = ExceptionUtils.cause(HintException.class, throwable);
          ExplanationException explanation = ExceptionUtils.cause(ExplanationException.class, throwable);
          KubernetesTaskException taskException = ExceptionUtils.cause(KubernetesTaskException.class, throwable);
          assertThat(hint).hasMessageContaining(KubernetesExceptionHints.CANARY_NO_WORKLOADS_FOUND);
          assertThat(explanation).hasMessageContaining(KubernetesExceptionExplanation.CANARY_NO_WORKLOADS_FOUND);
          assertThat(taskException).hasMessageContaining(KubernetesExceptionMessages.NO_WORKLOADS_FOUND);

          return true;
        });
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testDeploymentWorkloadsForCanaryMultipleWorkloads() throws Exception {
    K8sCanaryHandlerConfig k8sCanaryHandlerConfig = prepareMultipleWorkloads();
    boolean result =
        k8sCanaryBaseHandler.prepareForCanary(k8sCanaryHandlerConfig, delegateTaskParams, false, logCallback, false);
    assertInvalidWorkloadsInManifest(result,
        "\nMore than one workloads found in the Manifests. Canary deploy supports only one workload. Others should be marked with annotation harness.io/direct-apply: true");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testDeploymentWorkloadsForCanaryMultipleWorkloadsIsErrorFrameworkEnabled() {
    K8sCanaryHandlerConfig k8sCanaryHandlerConfig = prepareMultipleWorkloads();
    assertThatThrownBy(()
                           -> k8sCanaryBaseHandler.prepareForCanary(
                               k8sCanaryHandlerConfig, delegateTaskParams, false, logCallback, true))
        .matches(throwable -> {
          HintException hint = ExceptionUtils.cause(HintException.class, throwable);
          ExplanationException explanation = ExceptionUtils.cause(ExplanationException.class, throwable);
          KubernetesTaskException taskException = ExceptionUtils.cause(KubernetesTaskException.class, throwable);
          assertThat(hint).hasMessageContaining(KubernetesExceptionHints.CANARY_MULTIPLE_WORKLOADS);
          assertThat(explanation)
              .hasMessageContaining(format(KubernetesExceptionExplanation.CANARY_MULTIPLE_WORKLOADS, 2,
                  "Deployment/deployment, Deployment/deployment"));
          assertThat(taskException).hasMessageContaining(KubernetesExceptionMessages.MULTIPLE_WORKLOADS);

          return true;
        });
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testDeploymentWorkloadsForCanary() throws Exception {
    K8sCanaryHandlerConfig k8sCanaryHandlerConfig = prepareValidWorkloads();
    k8sCanaryHandlerConfig.setReleaseHistory(ReleaseHistory.createNew());

    doNothing().when(k8sTaskHelperBase).cleanup(any(), any(), any(), any());
    boolean result =
        k8sCanaryBaseHandler.prepareForCanary(k8sCanaryHandlerConfig, delegateTaskParams, false, logCallback, false);
    assertThat(result).isTrue();
    verify(k8sTaskHelperBase, times(1)).cleanup(any(), any(), any(), any());
    verify(k8sTaskHelperBase, times(1)).getResourcesInTableFormat(any());
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testDeploymentWorkloadsForCanaryCleanupCanaryTrue() throws Exception {
    K8sCanaryHandlerConfig k8sCanaryHandlerConfig = prepareValidWorkloads();
    List<K8sLegacyRelease> releaseList = new ArrayList<>();
    releaseList.add(K8sLegacyRelease.builder().number(1).status(IK8sRelease.Status.InProgress).build());
    k8sCanaryHandlerConfig.setReleaseHistory(
        ReleaseHistory.builder().version(defaultVersion).releases(releaseList).build());
    ArgumentCaptor<ReleaseHistory> captor = ArgumentCaptor.forClass(ReleaseHistory.class);
    doNothing().when(k8sTaskHelperBase).cleanup(any(), any(), captor.capture(), any());
    boolean result =
        k8sCanaryBaseHandler.prepareForCanary(k8sCanaryHandlerConfig, delegateTaskParams, false, logCallback, false);
    assertThat(result).isTrue();
    assertThat(captor.getValue().getRelease(1).getStatus()).isEqualTo(Failed);
    assertThat(captor.getValue().getRelease(2).getStatus()).isEqualTo(InProgress);
    verify(k8sTaskHelperBase, times(1)).cleanup(any(), any(), any(), any());
    verify(k8sTaskHelperBase, times(1)).getResourcesInTableFormat(any());
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testSupportedWorkloadsInBgWorkflow() throws Exception {
    K8sDelegateTaskParams delegateTaskParams = K8sDelegateTaskParams.builder().build();
    K8sCanaryHandlerConfig k8sCanaryHandlerConfig = new K8sCanaryHandlerConfig();

    List<KubernetesResource> kubernetesResources = new ArrayList<>();
    kubernetesResources.addAll(ManifestHelper.processYaml(DAEMON_SET_YAML));
    k8sCanaryHandlerConfig.setResources(kubernetesResources);

    boolean result =
        k8sCanaryBaseHandler.prepareForCanary(k8sCanaryHandlerConfig, delegateTaskParams, false, logCallback, false);
    assertInvalidWorkloadsInManifest(result,
        "\nNo workload found in the Manifests. Can't do Canary Deployment. Only Deployment, DeploymentConfig (OpenShift) and StatefulSet workloads are supported in Canary workflow type.");
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testGetAllPods() throws Exception {
    KubernetesResource kubernetesResource =
        KubernetesResource.builder().resourceId(KubernetesResourceId.builder().namespace("default").build()).build();
    K8sCanaryHandlerConfig canaryHandlerConfig = new K8sCanaryHandlerConfig();
    canaryHandlerConfig.setKubernetesConfig(kubernetesConfig);
    canaryHandlerConfig.setCanaryWorkload(kubernetesResource);
    testGetAllPodsWithNoPreviousPods(canaryHandlerConfig);
    testGetAllPodsWithPreviousPods(canaryHandlerConfig);
  }

  public void testGetAllPodsWithNoPreviousPods(K8sCanaryHandlerConfig canaryHandlerConfig) throws Exception {
    final String releaseName = "releaseName";
    final long timeoutInMillis = 50000;
    final K8sPod canaryPod = K8sPod.builder().name("pod-canary").build();
    when(k8sTaskHelperBase.getPodDetails(kubernetesConfig, namespace, releaseName, timeoutInMillis))
        .thenReturn(asList(canaryPod));
    when(k8sTaskHelperBase.getPodDetailsWithTrack(kubernetesConfig, namespace, releaseName, "canary", timeoutInMillis))
        .thenReturn(asList(canaryPod));

    final List<K8sPod> allPods = k8sCanaryBaseHandler.getAllPods(canaryHandlerConfig, releaseName, timeoutInMillis);
    assertThat(allPods).hasSize(1);
    assertThat(allPods.get(0).isNewPod()).isTrue();
    assertThat(allPods.get(0).getName()).isEqualTo(canaryPod.getName());
  }

  public void testGetAllPodsWithPreviousPods(K8sCanaryHandlerConfig canaryHandlerConfig) throws Exception {
    final String releaseName = "releaseName";
    final long timeoutInMillis = 50000;
    final K8sPod canaryPod = K8sPod.builder().name("pod-canary").build();
    final List<K8sPod> allPods =
        asList(K8sPod.builder().name("primary-1").build(), K8sPod.builder().name("primary-2").build(), canaryPod);
    when(k8sTaskHelperBase.getPodDetails(kubernetesConfig, namespace, releaseName, timeoutInMillis))
        .thenReturn(allPods);
    when(k8sTaskHelperBase.getPodDetailsWithTrack(kubernetesConfig, namespace, releaseName, "canary", timeoutInMillis))
        .thenReturn(asList(canaryPod));

    final List<K8sPod> pods = k8sCanaryBaseHandler.getAllPods(canaryHandlerConfig, releaseName, timeoutInMillis);
    assertThat(pods).hasSize(3);
    assertThat(pods.stream().filter(K8sPod::isNewPod).count()).isEqualTo(1);
    assertThat(pods.stream().map(K8sPod::getName).collect(Collectors.toList()))
        .containsExactlyInAnyOrder("pod-canary", "primary-1", "primary-2");
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testPrepareCanary() throws Exception {
    cannotDeployMoreThan1Workload();
    cannotDeployMoreThanEmptyWorkload();
    deploySingleWorkload();
  }

  public void cannotDeployMoreThan1Workload() throws Exception {
    K8sCanaryHandlerConfig canaryHandlerConfig = new K8sCanaryHandlerConfig();
    List<KubernetesResource> resources = asList(
        KubernetesResource.builder()
            .resourceId(KubernetesResourceId.builder().versioned(true).name("object-1").kind("configMap").build())
            .build(),
        KubernetesResource.builder()
            .resourceId(KubernetesResourceId.builder().versioned(false).name("object-2").kind("Deployment").build())
            .build(),
        KubernetesResource.builder()
            .resourceId(
                KubernetesResourceId.builder().versioned(false).name("object-3").kind("DeploymentConfig").build())
            .build());
    canaryHandlerConfig.setResources(resources);

    boolean success = k8sCanaryBaseHandler.prepareForCanary(
        canaryHandlerConfig, K8sDelegateTaskParams.builder().build(), false, logCallback, false);

    assertThat(success).isFalse();
  }

  public void cannotDeployMoreThanEmptyWorkload() throws Exception {
    K8sCanaryHandlerConfig canaryHandlerConfig = new K8sCanaryHandlerConfig();
    List<KubernetesResource> resources = asList(
        KubernetesResource.builder()
            .resourceId(KubernetesResourceId.builder().versioned(true).name("object-1").kind("configMap").build())
            .build());
    canaryHandlerConfig.setResources(resources);

    boolean success = k8sCanaryBaseHandler.prepareForCanary(
        canaryHandlerConfig, K8sDelegateTaskParams.builder().build(), false, logCallback, false);

    assertThat(success).isFalse();
  }

  public void deploySingleWorkload() throws Exception {
    Kubectl client = mock(Kubectl.class);
    KubernetesResource deployment = ManifestHelper.processYaml(DEPLOYMENT_YAML).get(0);
    K8sCanaryHandlerConfig canaryHandlerConfig = new K8sCanaryHandlerConfig();
    K8sDelegateTaskParams delegateTaskParams = K8sDelegateTaskParams.builder().build();
    ReleaseHistory releaseHistory = ReleaseHistory.createNew();
    List<KubernetesResource> resources = asList(
        KubernetesResource.builder()
            .resourceId(KubernetesResourceId.builder().versioned(true).name("object-1").kind("configMap").build())
            .build(),
        deployment);
    canaryHandlerConfig.setResources(resources);
    canaryHandlerConfig.setReleaseHistory(releaseHistory);
    canaryHandlerConfig.setReleaseName("release-01");
    canaryHandlerConfig.setClient(client);

    boolean success =
        k8sCanaryBaseHandler.prepareForCanary(canaryHandlerConfig, delegateTaskParams, false, logCallback, false);

    assertThat(success).isTrue();
    assertThat(canaryHandlerConfig.getCanaryWorkload()).isNotNull();
    verify(k8sTaskHelperBase, times(1)).cleanup(client, delegateTaskParams, releaseHistory, logCallback);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testUpdateTargetInstances() {
    K8sCanaryHandlerConfig canaryHandlerConfig = new K8sCanaryHandlerConfig();
    canaryHandlerConfig.setCanaryWorkload(ManifestHelper.processYaml(DEPLOYMENT_YAML).get(0));
    canaryHandlerConfig.setReleaseName("release-01");

    k8sCanaryBaseHandler.updateTargetInstances(canaryHandlerConfig, 4, logCallback);
    assertThat(canaryHandlerConfig.getTargetInstances()).isEqualTo(4);
    KubernetesResource canaryWorkload = canaryHandlerConfig.getCanaryWorkload();
    Map matchLabels = (Map) canaryWorkload.getField("spec.selector.matchLabels");
    Map podsSpecLabels = (Map) canaryWorkload.getField("spec.template.metadata.labels");
    assertThat(canaryWorkload.getResourceId().getName()).endsWith("canary");
    assertThat(matchLabels.get("harness.io/track")).isEqualTo("canary");
    assertThat(podsSpecLabels.get("harness.io/track")).isEqualTo("canary");
    assertThat(podsSpecLabels.get("harness.io/release-name")).isEqualTo("release-01");
    assertThat(canaryWorkload.getReplicaCount()).isEqualTo(4);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testUpdateDestinationRuleManifestFilesWithSubsets() throws IOException {
    List<KubernetesResource> kubernetesResources = Collections.emptyList();

    k8sCanaryBaseHandler.updateDestinationRuleManifestFilesWithSubsets(
        kubernetesResources, kubernetesConfig, logCallback);

    verify(k8sTaskHelperBase, times(1))
        .updateDestinationRuleManifestFilesWithSubsets(kubernetesResources,
            asList(HarnessLabelValues.trackCanary, HarnessLabelValues.trackStable), kubernetesConfig, logCallback);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testWrapUp() throws Exception {
    K8sDelegateTaskParams delegateTaskParams = K8sDelegateTaskParams.builder().build();
    k8sCanaryBaseHandler.wrapUp(client, delegateTaskParams, logCallback);
    verify(k8sTaskHelperBase, times(1)).describe(client, delegateTaskParams, logCallback);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testFailAndSaveKubernetesRelease() throws IOException {
    String releaseHistoryAsYaml = "dummy content";
    ReleaseHistory releaseHistory = mock(ReleaseHistory.class);
    K8sCanaryHandlerConfig canaryHandlerConfig = new K8sCanaryHandlerConfig();
    canaryHandlerConfig.setReleaseHistory(releaseHistory);
    canaryHandlerConfig.setKubernetesConfig(kubernetesConfig);
    doReturn(releaseHistoryAsYaml).when(releaseHistory).getAsYaml();

    k8sCanaryBaseHandler.failAndSaveKubernetesRelease(canaryHandlerConfig, "release");

    verify(releaseHistory, times(1)).setReleaseStatus(Failed);
    verify(k8sTaskHelperBase, times(1))
        .saveReleaseHistoryInConfigMap(kubernetesConfig, "release", releaseHistoryAsYaml);
  }

  private void assertInvalidWorkloadsInManifest(boolean result, String expectedMessage) throws Exception {
    assertThat(result).isFalse();
    verify(k8sTaskHelperBase, never()).cleanup(any(), any(), any(), any());
    verify(k8sTaskHelperBase, times(1)).getResourcesInTableFormat(any());

    ArgumentCaptor<String> msgCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<LogLevel> logLevelCaptor = ArgumentCaptor.forClass(LogLevel.class);
    ArgumentCaptor<CommandExecutionStatus> commandExecutionStatusCaptor =
        ArgumentCaptor.forClass(CommandExecutionStatus.class);
    verify(logCallback, times(1))
        .saveExecutionLog(msgCaptor.capture(), logLevelCaptor.capture(), commandExecutionStatusCaptor.capture());
    assertThat(logLevelCaptor.getValue()).isEqualTo(ERROR);
    assertThat(commandExecutionStatusCaptor.getValue()).isEqualTo(FAILURE);
    assertThat(msgCaptor.getValue()).isEqualTo(expectedMessage);
  }

  private K8sCanaryHandlerConfig prepareNoWorkloadNoResource() {
    List<KubernetesResource> kubernetesResources = new ArrayList<>();
    K8sCanaryHandlerConfig k8sCanaryHandlerConfig = new K8sCanaryHandlerConfig();
    kubernetesResources.addAll(ManifestHelper.processYaml(DAEMON_SET_YAML));
    k8sCanaryHandlerConfig.setResources(kubernetesResources);

    return k8sCanaryHandlerConfig;
  }

  private K8sCanaryHandlerConfig prepareMultipleWorkloads() {
    List<KubernetesResource> kubernetesResources = new ArrayList<>();
    K8sCanaryHandlerConfig k8sCanaryHandlerConfig = new K8sCanaryHandlerConfig();
    kubernetesResources.addAll(ManifestHelper.processYaml(DEPLOYMENT_YAML));
    kubernetesResources.addAll(ManifestHelper.processYaml(DEPLOYMENT_YAML));
    k8sCanaryHandlerConfig.setResources(kubernetesResources);

    return k8sCanaryHandlerConfig;
  }

  private K8sCanaryHandlerConfig prepareValidWorkloads() {
    List<KubernetesResource> kubernetesResources = new ArrayList<>();
    K8sCanaryHandlerConfig k8sCanaryHandlerConfig = new K8sCanaryHandlerConfig();
    kubernetesResources.addAll(ManifestHelper.processYaml(CONFIG_MAP_YAML));
    kubernetesResources.addAll(ManifestHelper.processYaml(DEPLOYMENT_YAML));
    k8sCanaryHandlerConfig.setResources(kubernetesResources);

    return k8sCanaryHandlerConfig;
  }
}
