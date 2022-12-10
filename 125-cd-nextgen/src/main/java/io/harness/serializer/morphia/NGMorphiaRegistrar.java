/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.serializer.morphia;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.artifact.bean.artifactsource.ArtifactSource;
import io.harness.cdng.artifact.bean.artifactsource.DockerArtifactSource;
import io.harness.cdng.artifact.steps.ArtifactStepParameters;
import io.harness.cdng.envGroup.beans.EnvironmentGroupEntity;
import io.harness.cdng.execution.StageExecutionInfo;
import io.harness.cdng.infra.beans.InfraUseFromStage;
import io.harness.cdng.infra.steps.InfraStepParameters;
import io.harness.cdng.instance.InstanceDeploymentInfo;
import io.harness.cdng.pipeline.executions.CDAccountExecutionMetadata;
import io.harness.cdng.provision.azure.beans.AzureARMConfig;
import io.harness.cdng.provision.cloudformation.beans.CloudformationConfig;
import io.harness.cdng.provision.terraform.TerraformConfig;
import io.harness.cdng.provision.terraform.executions.TerraformPlanExecutionDetails;
import io.harness.cdng.service.steps.ServiceStepParameters;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.morphia.MorphiaRegistrarHelperPut;
import io.harness.polling.bean.PollingDocument;
import io.harness.telemetry.beans.CdTelemetrySentStatus;

import java.util.Set;

@OwnedBy(HarnessTeam.CDP)
public class NGMorphiaRegistrar implements MorphiaRegistrar {
  @Override
  public void registerClasses(Set<Class> set) {
    set.add(DockerArtifactSource.class);
    set.add(ArtifactSource.class);
    set.add(TerraformConfig.class);
    set.add(CloudformationConfig.class);
    set.add(PollingDocument.class);
    set.add(CDAccountExecutionMetadata.class);
    set.add(EnvironmentGroupEntity.class);
    set.add(CdTelemetrySentStatus.class);
    set.add(StageExecutionInfo.class);
    set.add(TerraformPlanExecutionDetails.class);
    set.add(InstanceDeploymentInfo.class);
    set.add(AzureARMConfig.class);
  }

  @Override
  public void registerImplementationClasses(MorphiaRegistrarHelperPut h, MorphiaRegistrarHelperPut w) {
    h.put("cdng.artifact.steps.ArtifactStepParameters", ArtifactStepParameters.class);
    h.put("cdng.service.steps.ServiceStepParameters", ServiceStepParameters.class);
    h.put("cdng.infra.beans.InfraUseFromStage$Overrides", InfraUseFromStage.Overrides.class);
    h.put("cdng.infra.beans.InfraUseFromStage", InfraUseFromStage.class);
    h.put("cdng.infra.steps.InfraStepParameters", InfraStepParameters.class);
    h.put("io.harness.cdng.provision.terraform.TerraformConfig", TerraformConfig.class);
    h.put("io.harness.cdng.provision.cloudformation.beans.CloudformationConfig", CloudformationConfig.class);
    h.put("io.harness.polling.bean.PollingDocument", PollingDocument.class);
    h.put("io.harness.cdng.execution.StageExecutionInfo", StageExecutionInfo.class);
    h.put("io.harness.cdng.instance.InstanceDeploymentInfo", InstanceDeploymentInfo.class);
    h.put("io.harness.cdng.provision.terraform.executions.TerraformPlanExecutionDetails",
        TerraformPlanExecutionDetails.class);
  }
}
