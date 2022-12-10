/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.dtos.deploymentinfo;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.k8s.ServiceSpecType;
import io.harness.util.InstanceSyncKey;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.codehaus.commons.nullanalysis.NotNull;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@Builder
@EqualsAndHashCode(callSuper = true)
@OwnedBy(CDP)
public class CustomDeploymentNGDeploymentInfoDTO extends DeploymentInfoDTO {
  @NotNull private String infratructureKey;
  private String instanceFetchScript;
  private String scriptOutput;
  private List<String> tags;
  private String artifactName;
  private String artifactSourceName;
  private String artifactStreamId;
  private String artifactBuildNum;
  @Override
  public String getType() {
    return ServiceSpecType.CUSTOM_DEPLOYMENT;
  }
  @Override
  public String prepareInstanceSyncHandlerKey() {
    return InstanceSyncKey.builder().part(infratructureKey).build().toString();
  }
}
