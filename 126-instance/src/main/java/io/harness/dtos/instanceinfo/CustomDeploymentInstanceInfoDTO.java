/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.dtos.instanceinfo;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.util.InstanceSyncKey;

import java.util.Map;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
@OwnedBy(HarnessTeam.CDP)
@Data
@Builder
@EqualsAndHashCode(callSuper = true)
public class CustomDeploymentInstanceInfoDTO extends InstanceInfoDTO {
  @NotNull private String instanceName;
  @NotNull private String infrastructureKey;
  private Map<String, Object> properties;

  @Override
  public String prepareInstanceKey() {
    return InstanceSyncKey.builder().clazz(CustomDeploymentInstanceInfoDTO.class).part(instanceName).build().toString();
  }

  @Override
  public String prepareInstanceSyncHandlerKey() {
    return InstanceSyncKey.builder().part(infrastructureKey).build().toString();
  }

  @Override
  public String getPodName() {
    return instanceName;
  }

  @Override
  public String getType() {
    return "CustomDeployment";
  }
}
