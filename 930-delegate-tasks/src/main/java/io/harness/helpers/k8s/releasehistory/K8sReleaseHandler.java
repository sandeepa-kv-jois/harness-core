/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.helpers.k8s.releasehistory;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.k8s.model.KubernetesConfig;
import io.harness.k8s.releasehistory.IK8sRelease;
import io.harness.k8s.releasehistory.IK8sReleaseHistory;
import io.harness.k8s.releasehistory.K8sReleaseCleanupDTO;
import io.harness.k8s.releasehistory.K8sReleasePersistDTO;

import javax.validation.constraints.NotNull;

@OwnedBy(CDP)
public interface K8sReleaseHandler {
  IK8sReleaseHistory getReleaseHistory(@NotNull KubernetesConfig kubernetesConfig, @NotNull String releaseName)
      throws Exception;
  IK8sRelease createRelease(@NotNull String name, @NotNull int number);
  void saveRelease(@NotNull K8sReleasePersistDTO releasePersistDTO) throws Exception;
  void cleanReleaseHistory(@NotNull K8sReleaseCleanupDTO releaseCleanupDTO) throws Exception;
}
