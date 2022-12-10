/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.gitops.beans;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import java.util.List;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.Setter;
import lombok.Value;
import lombok.experimental.NonFinal;

@OwnedBy(HarnessTeam.CDP)
@Value
@Builder
public class GitOpsInstanceRequest {
  @Data
  @Builder
  public static class K8sBasicInfo {
    @NotNull private String namespace;
    @NotNull private String podName;
    @NotNull private String podId;
    @NotEmpty private String agentIdentifier;
    @NotEmpty private String clusterIdentifier;
    private List<K8sContainer> containerList;
  }

  @Data
  @Builder
  public static class K8sContainer {
    private String containerId;
    private String name;
    private String image;
  }

  @NotEmpty private String accountIdentifier;
  @NotEmpty private String orgIdentifier;
  @NotEmpty private String projectIdentifier;
  @NotEmpty private String applicationIdentifier;
  @NotEmpty private String agentIdentifier;
  @NotEmpty private String envIdentifier;
  @NotEmpty private String serviceIdentifier;
  @NotEmpty private String buildId;
  @NotEmpty private String clusterIdentifier;
  @NotNull private long creationTimestamp;
  @NotNull private long lastDeployedAt;
  @NotNull private K8sBasicInfo instanceInfo; // PodInfo

  @NonFinal @Setter private String pipelineName;
  @NonFinal @Setter private Long lastExecutedAt;
  @NonFinal @Setter private String pipelineExecutionId;

  @NonFinal @Setter private String lastDeployedByName;
  @NonFinal @Setter private String lastDeployedById;
}
