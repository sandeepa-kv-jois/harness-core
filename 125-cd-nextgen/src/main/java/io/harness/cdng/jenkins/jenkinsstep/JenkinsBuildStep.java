/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.jenkins.jenkinsstep;

import io.harness.EntityType;
import io.harness.beans.IdentifierRef;
import io.harness.delegate.task.artifacts.jenkins.JenkinsArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.jenkins.JenkinsArtifactDelegateRequest.JenkinsArtifactDelegateRequestBuilder;
import io.harness.delegate.task.artifacts.response.ArtifactTaskResponse;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.logstreaming.ILogStreamingStepClient;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.ng.core.EntityDetail;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.plancreator.steps.common.rollback.TaskExecutableWithRollbackAndRbac;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.rbac.PipelineRbacHelper;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.steps.StepUtils;
import io.harness.supplier.ThrowingSupplier;
import io.harness.utils.IdentifierRefHelper;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;

public class JenkinsBuildStep extends TaskExecutableWithRollbackAndRbac<ArtifactTaskResponse> {
  public static final StepType STEP_TYPE =
      StepType.newBuilder().setType(StepSpecTypeConstants.JENKINS_BUILD).setStepCategory(StepCategory.STEP).build();
  public String COMMAND_UNIT = "Execute";

  @Inject private JenkinsBuildStepHelperService jenkinsBuildStepHelperService;
  @Inject private PipelineRbacHelper pipelineRbacHelper;
  @Inject private LogStreamingStepClientFactory logStreamingStepClientFactory;

  @Override
  public void validateResources(Ambiance ambiance, StepElementParameters stepParameters) {
    String accountIdentifier = AmbianceUtils.getAccountId(ambiance);
    String orgIdentifier = AmbianceUtils.getOrgIdentifier(ambiance);
    String projectIdentifier = AmbianceUtils.getProjectIdentifier(ambiance);
    JenkinsBuildSpecParameters specParameters = (JenkinsBuildSpecParameters) stepParameters.getSpec();
    String connectorRef = specParameters.getConnectorRef().getValue();
    IdentifierRef identifierRef =
        IdentifierRefHelper.getIdentifierRef(connectorRef, accountIdentifier, orgIdentifier, projectIdentifier);
    EntityDetail entityDetail = EntityDetail.builder().type(EntityType.CONNECTORS).entityRef(identifierRef).build();
    List<EntityDetail> entityDetailList = new ArrayList<>();
    entityDetailList.add(entityDetail);
    pipelineRbacHelper.checkRuntimePermissions(ambiance, entityDetailList, true);
  }

  @Override
  public StepResponse handleTaskResultWithSecurityContext(Ambiance ambiance, StepElementParameters stepParameters,
      ThrowingSupplier<ArtifactTaskResponse> responseDataSupplier) throws Exception {
    try {
      return jenkinsBuildStepHelperService.prepareStepResponse(responseDataSupplier);
    } finally {
      // Closing the log stream.
      closeLogStream(ambiance);
    }
  }

  @Override
  public TaskRequest obtainTaskAfterRbac(
      Ambiance ambiance, StepElementParameters stepParameters, StepInputPackage inputPackage) {
    // Creating the log stream once and will close at the end of the task.
    ILogStreamingStepClient logStreamingStepClient = logStreamingStepClientFactory.getLogStreamingStepClient(ambiance);
    logStreamingStepClient.openStream(COMMAND_UNIT);
    JenkinsBuildSpecParameters specParameters = (JenkinsBuildSpecParameters) stepParameters.getSpec();
    JenkinsArtifactDelegateRequestBuilder paramBuilder =
        JenkinsArtifactDelegateRequest.builder()
            .connectorRef(specParameters.getConnectorRef().getValue())
            .jobName(specParameters.getJobName().getValue())
            .unstableStatusAsSuccess(specParameters.isUnstableStatusAsSuccess())
            .useConnectorUrlForJobExecution(specParameters.isUseConnectorUrlForJobExecution())
            .delegateSelectors(
                StepUtils.getDelegateSelectorListFromTaskSelectorYaml(specParameters.getDelegateSelectors()))
            .jobParameter(JenkinsBuildStepUtils.processJenkinsFieldsInParameters(specParameters.getFields()));

    return jenkinsBuildStepHelperService.prepareTaskRequest(paramBuilder, ambiance,
        specParameters.getConnectorRef().getValue(), stepParameters.getTimeout().getValue(),
        "Jenkins Task: Create Jenkins Build Task");
  }

  @Override
  public Class<StepElementParameters> getStepParametersClass() {
    return StepElementParameters.class;
  }

  private void closeLogStream(Ambiance ambiance) {
    ILogStreamingStepClient logStreamingStepClient = logStreamingStepClientFactory.getLogStreamingStepClient(ambiance);
    logStreamingStepClient.closeStream(COMMAND_UNIT);
  }
}
