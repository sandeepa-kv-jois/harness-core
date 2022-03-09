/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.steps.common.pipeline;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.common.ParameterFieldHelper;
import io.harness.data.structure.CollectionUtils;
import io.harness.plancreator.flowcontrol.FlowControlConfig;
import io.harness.plancreator.pipeline.PipelineInfoConfig;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.plan.PlanCreationContextValue;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.tags.TagUtils;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.SkipAutoEvaluation;
import io.harness.yaml.core.properties.NGProperties;
import io.harness.yaml.core.variables.NGVariable;
import io.harness.yaml.utils.NGVariablesUtils;

import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

@Data
@NoArgsConstructor
@TypeAlias("pipelineSetupStepParameters")
@OwnedBy(PIPELINE)
@RecasterAlias("io.harness.steps.common.pipeline.PipelineSetupStepParameters")
public class PipelineSetupStepParameters implements StepParameters {
  String childNodeID;

  String name;
  String identifier;
  FlowControlConfig flowControl;
  ParameterField<String> description;
  Map<String, String> tags;
  ParameterField<Map<String, Object>> properties;
  @SkipAutoEvaluation ParameterField<Map<String, Object>> variables;

  String executionId;
  int sequenceId;

  @Builder(builderMethodName = "newBuilder")
  public PipelineSetupStepParameters(String childNodeID, String name, String identifier, FlowControlConfig flowControl,
      ParameterField<String> description, Map<String, String> tags, NGProperties properties,
      List<NGVariable> originalVariables, String executionId, int sequenceId) {
    this.childNodeID = childNodeID;
    this.name = name;
    this.identifier = identifier;
    this.flowControl = flowControl;
    this.description = description;
    this.tags = CollectionUtils.emptyIfNull(tags);
    this.properties = ParameterField.createValueField(NGVariablesUtils.getMapOfNGProperties(properties));
    this.variables = ParameterField.createValueField(NGVariablesUtils.getMapOfVariables(originalVariables));
    this.executionId = executionId;
    this.sequenceId = sequenceId;
  }

  public static PipelineSetupStepParameters getStepParameters(
      PlanCreationContext ctx, PipelineInfoConfig infoConfig, String childNodeID) {
    PlanCreationContextValue planCreationContextValue = ctx.getGlobalContext().get("metadata");
    ExecutionMetadata executionMetadata = planCreationContextValue.getMetadata();
    if (infoConfig == null) {
      return PipelineSetupStepParameters.newBuilder()
          .childNodeID(childNodeID)
          .executionId(executionMetadata.getExecutionUuid())
          .sequenceId(executionMetadata.getRunSequence())
          .build();
    }

    TagUtils.removeUuidFromTags(infoConfig.getTags());

    return new PipelineSetupStepParameters(childNodeID, infoConfig.getName(), infoConfig.getIdentifier(),
        infoConfig.getFlowControl(), ParameterFieldHelper.getParameterFieldHandleValueNull(infoConfig.getDescription()),
        infoConfig.getTags(), infoConfig.getProperties(), infoConfig.getVariables(),
        executionMetadata.getExecutionUuid(), executionMetadata.getRunSequence());
  }
}
