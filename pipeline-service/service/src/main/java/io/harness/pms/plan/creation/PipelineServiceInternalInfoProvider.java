/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.creation;

import static io.harness.cf.pipeline.FeatureFlagStageFilterJsonCreator.FEATURE_FLAG_SUPPORTED_TYPE;
import static io.harness.pms.yaml.YAMLFieldNameConstants.GROUP;
import static io.harness.pms.yaml.YAMLFieldNameConstants.PARALLEL;
import static io.harness.pms.yaml.YAMLFieldNameConstants.SPEC;
import static io.harness.pms.yaml.YAMLFieldNameConstants.STAGE;
import static io.harness.pms.yaml.YAMLFieldNameConstants.STAGES;
import static io.harness.pms.yaml.YAMLFieldNameConstants.STEP;
import static io.harness.pms.yaml.YAMLFieldNameConstants.STEPS;
import static io.harness.pms.yaml.YAMLFieldNameConstants.STRATEGY;
import static io.harness.steps.StepSpecTypeConstants.BARRIER;
import static io.harness.steps.StepSpecTypeConstants.FLAG_CONFIGURATION;
import static io.harness.steps.StepSpecTypeConstants.RESOURCE_CONSTRAINT;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cf.pipeline.CfExecutionPMSPlanCreator;
import io.harness.cf.pipeline.FeatureFlagStageFilterJsonCreator;
import io.harness.cf.pipeline.FeatureFlagStagePlanCreator;
import io.harness.filters.EmptyAnyFilterJsonCreator;
import io.harness.filters.EmptyFilterJsonCreator;
import io.harness.filters.ExecutionPMSFilterJsonCreator;
import io.harness.filters.GroupFilterJsonCreator;
import io.harness.filters.ParallelFilterJsonCreator;
import io.harness.filters.PipelineFilterJsonCreator;
import io.harness.filters.StepGroupPmsFilterJsonCreator;
import io.harness.plancreator.approval.ApprovalStageFilterJsonCreator;
import io.harness.plancreator.approval.ApprovalStagePlanCreatorV2;
import io.harness.plancreator.execution.ExecutionPmsPlanCreator;
import io.harness.plancreator.group.GroupPlanCreatorV1;
import io.harness.plancreator.pipeline.NGPipelinePlanCreator;
import io.harness.plancreator.pipeline.PipelinePlanCreatorV1;
import io.harness.plancreator.stages.StagesPlanCreator;
import io.harness.plancreator.stages.parallel.ParallelPlanCreator;
import io.harness.plancreator.stages.parallel.v1.ParallelPlanCreatorV1;
import io.harness.plancreator.stages.v1.StagesPlanCreatorV1;
import io.harness.plancreator.steps.NGStageStepsPlanCreator;
import io.harness.plancreator.steps.SpecNodePlanCreator;
import io.harness.plancreator.steps.StepGroupPMSPlanCreator;
import io.harness.plancreator.steps.barrier.BarrierStepPlanCreator;
import io.harness.plancreator.steps.email.EmailStepPlanCreator;
import io.harness.plancreator.steps.email.EmailStepVariableCreator;
import io.harness.plancreator.steps.http.HTTPStepVariableCreator;
import io.harness.plancreator.steps.http.HttpStepPlanCreator;
import io.harness.plancreator.steps.http.v1.HttpStepPlanCreatorV1;
import io.harness.plancreator.steps.internal.FlagConfigurationStepPlanCreator;
import io.harness.plancreator.steps.internal.PMSStepPlanCreator;
import io.harness.plancreator.steps.internal.PmsStepFilterJsonCreator;
import io.harness.plancreator.steps.internal.PmsStepFilterJsonCreatorV2;
import io.harness.plancreator.steps.internal.ShellScriptStepFilterJsonCreatorV2;
import io.harness.plancreator.steps.pluginstep.ContainerStepPlanCreator;
import io.harness.plancreator.steps.pluginstep.ContainerStepVariableCreator;
import io.harness.plancreator.steps.resourceconstraint.QueueStepPlanCreator;
import io.harness.plancreator.steps.resourceconstraint.ResourceConstraintStepPlanCreator;
import io.harness.plancreator.steps.v1.StepsPlanCreatorV1;
import io.harness.plancreator.strategy.StrategyConfigPlanCreator;
import io.harness.pms.contracts.steps.StepInfo;
import io.harness.pms.contracts.steps.StepMetaData;
import io.harness.pms.pipelinestage.PipelineStageFilterCreator;
import io.harness.pms.pipelinestage.plancreator.PipelineStagePlanCreator;
import io.harness.pms.sdk.core.pipeline.filters.FilterJsonCreator;
import io.harness.pms.sdk.core.pipeline.variables.ApprovalStageVariableCreator;
import io.harness.pms.sdk.core.pipeline.variables.PipelineVariableCreator;
import io.harness.pms.sdk.core.pipeline.variables.StepGroupVariableCreator;
import io.harness.pms.sdk.core.plan.creation.creators.PartialPlanCreator;
import io.harness.pms.sdk.core.plan.creation.creators.PipelineServiceInfoProvider;
import io.harness.pms.sdk.core.variables.EmptyAnyVariableCreator;
import io.harness.pms.sdk.core.variables.EmptyVariableCreator;
import io.harness.pms.sdk.core.variables.StrategyVariableCreator;
import io.harness.pms.sdk.core.variables.VariableCreator;
import io.harness.pms.utils.InjectorUtils;
import io.harness.steps.approval.ApprovalStepVariableCreator;
import io.harness.steps.approval.step.custom.CustomApprovalStepPlanCreator;
import io.harness.steps.approval.step.custom.CustomApprovalStepVariableCreator;
import io.harness.steps.approval.step.harness.HarnessApprovalStepPlanCreator;
import io.harness.steps.approval.step.jira.JiraApprovalStepPlanCreator;
import io.harness.steps.approval.step.jira.JiraApprovalStepVariableCreator;
import io.harness.steps.approval.step.servicenow.ServiceNowApprovalStepPlanCreator;
import io.harness.steps.approval.step.servicenow.ServiceNowApprovalStepVariableCreator;
import io.harness.steps.cf.FlagConfigurationStep;
import io.harness.steps.customstage.CustomStageFilterCreator;
import io.harness.steps.customstage.CustomStagePlanCreator;
import io.harness.steps.customstage.CustomStageVariableCreator;
import io.harness.steps.customstage.v1.CustomStagePlanCreatorV1;
import io.harness.steps.jira.JiraStepVariableCreator;
import io.harness.steps.jira.JiraUpdateStepVariableCreator;
import io.harness.steps.jira.create.JiraCreateStepPlanCreator;
import io.harness.steps.jira.update.JiraUpdateStepPlanCreator;
import io.harness.steps.pipelinestage.PipelineStageVariableCreator;
import io.harness.steps.policy.step.PolicyStepPlanCreator;
import io.harness.steps.policy.variables.PolicyStepVariableCreator;
import io.harness.steps.resourcerestraint.QueueStepVariableCreator;
import io.harness.steps.servicenow.ServiceNowCreateStepVariableCreator;
import io.harness.steps.servicenow.ServiceNowImportSetStepVariableCreator;
import io.harness.steps.servicenow.ServiceNowUpdateStepVariableCreator;
import io.harness.steps.servicenow.create.ServiceNowCreateStepPlanCreator;
import io.harness.steps.servicenow.importset.ServiceNowImportSetStepPlanCreator;
import io.harness.steps.servicenow.update.ServiceNowUpdateStepPlanCreator;
import io.harness.steps.shellscript.ShellScriptStepPlanCreator;
import io.harness.steps.shellscript.ShellScriptStepVariableCreator;
import io.harness.steps.shellscript.v1.ShellScriptStepPlanCreatorV1;
import io.harness.steps.wait.WaitStepPlanCreator;
import io.harness.steps.wait.WaitStepVariableCreator;
import io.harness.variables.ExecutionVariableCreator;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;

@OwnedBy(HarnessTeam.PIPELINE)
@Singleton
public class PipelineServiceInternalInfoProvider implements PipelineServiceInfoProvider {
  @Inject InjectorUtils injectorUtils;

  @Override
  public List<PartialPlanCreator<?>> getPlanCreators() {
    List<PartialPlanCreator<?>> planCreators = new ArrayList<>();
    planCreators.add(new NGPipelinePlanCreator());
    planCreators.add(new PipelinePlanCreatorV1());
    planCreators.add(new StagesPlanCreator());
    planCreators.add(new StagesPlanCreatorV1());
    planCreators.add(new ParallelPlanCreator());
    planCreators.add(new ParallelPlanCreatorV1());
    planCreators.add(new PMSStepPlanCreator());
    planCreators.add(new HttpStepPlanCreator());
    planCreators.add(new HttpStepPlanCreatorV1());
    planCreators.add(new EmailStepPlanCreator());
    planCreators.add(new JiraCreateStepPlanCreator());
    planCreators.add(new JiraUpdateStepPlanCreator());
    planCreators.add(new ShellScriptStepPlanCreator());
    planCreators.add(new ShellScriptStepPlanCreatorV1());
    planCreators.add(new NGStageStepsPlanCreator());
    planCreators.add(new StepsPlanCreatorV1());
    planCreators.add(new ApprovalStagePlanCreatorV2());
    planCreators.add(new ExecutionPmsPlanCreator());
    planCreators.add(new StepGroupPMSPlanCreator());
    planCreators.add(new ResourceConstraintStepPlanCreator());
    planCreators.add(new QueueStepPlanCreator());
    planCreators.add(new FeatureFlagStagePlanCreator());
    planCreators.add(new CfExecutionPMSPlanCreator());
    planCreators.add(new ServiceNowApprovalStepPlanCreator());
    planCreators.add(new JiraApprovalStepPlanCreator());
    planCreators.add(new HarnessApprovalStepPlanCreator());
    planCreators.add(new BarrierStepPlanCreator());
    planCreators.add(new FlagConfigurationStepPlanCreator());
    planCreators.add(new PolicyStepPlanCreator());
    planCreators.add(new ServiceNowCreateStepPlanCreator());
    planCreators.add(new ServiceNowUpdateStepPlanCreator());
    planCreators.add(new ServiceNowImportSetStepPlanCreator());
    planCreators.add(new StrategyConfigPlanCreator());
    planCreators.add(new CustomStagePlanCreator());
    planCreators.add(new CustomStagePlanCreatorV1());
    planCreators.add(new CustomApprovalStepPlanCreator());
    planCreators.add(new SpecNodePlanCreator());
    planCreators.add(new WaitStepPlanCreator());
    planCreators.add(new PipelineStagePlanCreator());
    planCreators.add(new ContainerStepPlanCreator());
    planCreators.add(new GroupPlanCreatorV1());
    injectorUtils.injectMembers(planCreators);
    return planCreators;
  }

  @Override
  public List<FilterJsonCreator> getFilterJsonCreators() {
    List<FilterJsonCreator> filterJsonCreators = new ArrayList<>();
    filterJsonCreators.add(new PipelineFilterJsonCreator());
    filterJsonCreators.add(new ParallelFilterJsonCreator());
    filterJsonCreators.add(new ApprovalStageFilterJsonCreator());
    filterJsonCreators.add(new PmsStepFilterJsonCreator());
    filterJsonCreators.add(new PmsStepFilterJsonCreatorV2());
    filterJsonCreators.add(new ShellScriptStepFilterJsonCreatorV2());
    filterJsonCreators.add(new ExecutionPMSFilterJsonCreator());
    filterJsonCreators.add(new StepGroupPmsFilterJsonCreator());
    filterJsonCreators.add(new FeatureFlagStageFilterJsonCreator());
    filterJsonCreators.add(new CustomStageFilterCreator());
    filterJsonCreators.add(new PipelineStageFilterCreator());
    filterJsonCreators.add(new GroupFilterJsonCreator());
    filterJsonCreators.add(new EmptyAnyFilterJsonCreator(ImmutableSet.of(STAGES, STRATEGY, STEPS, SPEC)));
    filterJsonCreators.add(new EmptyFilterJsonCreator(STEP, ImmutableSet.of(FLAG_CONFIGURATION)));
    injectorUtils.injectMembers(filterJsonCreators);
    return filterJsonCreators;
  }

  @Override
  public List<VariableCreator> getVariableCreators() {
    List<VariableCreator> variableCreators = new ArrayList<>();
    variableCreators.add(new PipelineVariableCreator());
    variableCreators.add(new HTTPStepVariableCreator());
    variableCreators.add(new EmailStepVariableCreator());
    variableCreators.add(new StepGroupVariableCreator());
    variableCreators.add(new ShellScriptStepVariableCreator());
    variableCreators.add(new JiraStepVariableCreator());
    variableCreators.add(new ApprovalStepVariableCreator());
    variableCreators.add(new ExecutionVariableCreator());
    variableCreators.add(new ApprovalStageVariableCreator());
    variableCreators.add(new PolicyStepVariableCreator());
    variableCreators.add(new ServiceNowApprovalStepVariableCreator());
    variableCreators.add(new JiraUpdateStepVariableCreator());
    variableCreators.add(new JiraApprovalStepVariableCreator());
    variableCreators.add(new ServiceNowCreateStepVariableCreator());
    variableCreators.add(new ServiceNowUpdateStepVariableCreator());
    variableCreators.add(new ServiceNowImportSetStepVariableCreator());
    variableCreators.add(new CustomStageVariableCreator());
    variableCreators.add(new QueueStepVariableCreator());
    variableCreators.add(new CustomApprovalStepVariableCreator());
    variableCreators.add(new StrategyVariableCreator());
    variableCreators.add(new PipelineStageVariableCreator());
    variableCreators.add(new WaitStepVariableCreator());
    variableCreators.add(new EmptyAnyVariableCreator(ImmutableSet.of(GROUP, PARALLEL, STEPS, SPEC, STAGES)));
    variableCreators.add(new EmptyVariableCreator(STAGE, ImmutableSet.of(FEATURE_FLAG_SUPPORTED_TYPE)));
    variableCreators.add(
        new EmptyVariableCreator(STEP, ImmutableSet.of(FLAG_CONFIGURATION, BARRIER, RESOURCE_CONSTRAINT)));
    variableCreators.add(new ContainerStepVariableCreator());
    injectorUtils.injectMembers(variableCreators);
    return variableCreators;
  }

  @Override
  public List<StepInfo> getStepInfo() {
    StepInfo k8sRolling = StepInfo.newBuilder()
                              .setName(FlagConfigurationStep.STEP_NAME)
                              .setType(FLAG_CONFIGURATION)
                              .setStepMetaData(StepMetaData.newBuilder()
                                                   .addCategory(FlagConfigurationStep.STEP_CATEGORY)
                                                   .addFolderPaths("Feature Flags")
                                                   .build())
                              .build();

    List<StepInfo> stepInfos = new ArrayList<>();

    stepInfos.add(k8sRolling);
    return stepInfos;
  }
}
