/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states.rancher;

import static io.harness.annotations.dev.HarnessModule._870_CG_ORCHESTRATION;
import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.BreakDependencyOn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.context.ContextElementType;

import software.wings.api.RancherClusterElement;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.states.KubernetesSwapServiceSelectors;
import software.wings.sm.states.k8s.K8sStateHelper;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.inject.Inject;
import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
@TargetModule(_870_CG_ORCHESTRATION)
@OwnedBy(CDP)
@BreakDependencyOn("software.wings.service.intfc.DelegateService")
public class RancherKubernetesSwapServiceSelectors extends KubernetesSwapServiceSelectors {
  @Inject public RancherStateHelper rancherStateHelper;
  @Inject private K8sStateHelper k8sStateHelper;

  public RancherKubernetesSwapServiceSelectors(String name) {
    super(name);
  }

  @Override
  public ExecutionResponse execute(ExecutionContext context) {
    if (!k8sStateHelper.isRancherInfraMapping(context)) {
      return rancherStateHelper.getInvalidInfraDefFailedResponse();
    }

    if (Objects.isNull(context.getContextElement())
        || !(context.getContextElement() instanceof RancherClusterElement)) {
      return rancherStateHelper.getResolvedClusterElementNotFoundResponse();
    }

    return super.execute(context);
  }

  @Override
  public ContextElementType getRequiredContextElementType() {
    return ContextElementType.RANCHER_K8S_CLUSTER_CRITERIA;
  }
}