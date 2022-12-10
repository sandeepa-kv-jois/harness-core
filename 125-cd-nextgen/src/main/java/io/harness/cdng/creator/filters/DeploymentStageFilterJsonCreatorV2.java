/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.creator.filters;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.creator.plan.stage.DeploymentStageConfig;
import io.harness.cdng.creator.plan.stage.DeploymentStageNode;
import io.harness.cdng.envgroup.yaml.EnvironmentGroupYaml;
import io.harness.cdng.environment.filters.Entity;
import io.harness.cdng.environment.filters.FilterYaml;
import io.harness.cdng.environment.yaml.EnvironmentYamlV2;
import io.harness.cdng.infra.yaml.InfraStructureDefinitionYaml;
import io.harness.cdng.pipeline.PipelineInfrastructure;
import io.harness.cdng.service.beans.ServiceConfig;
import io.harness.cdng.service.beans.ServiceDefinition;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.cdng.service.beans.ServiceYaml;
import io.harness.cdng.service.beans.ServiceYamlV2;
import io.harness.cdng.service.beans.ServicesYaml;
import io.harness.data.structure.EmptyPredicate;
import io.harness.data.structure.HarnessStringUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.filters.GenericStageFilterJsonCreatorV2;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.services.EnvironmentService;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity;
import io.harness.ng.core.infrastructure.services.InfrastructureEntityService;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.mappers.NGServiceEntityMapper;
import io.harness.ng.core.service.services.ServiceEntityService;
import io.harness.ng.core.service.yaml.NGServiceV2InfoConfig;
import io.harness.pms.cdng.sample.cd.creator.filters.CdFilter;
import io.harness.pms.cdng.sample.cd.creator.filters.CdFilter.CdFilterBuilder;
import io.harness.pms.exception.runtime.InvalidYamlRuntimeException;
import io.harness.pms.pipeline.filter.PipelineFilter;
import io.harness.pms.plan.creation.PlanCreatorUtils;
import io.harness.pms.sdk.core.filter.creation.beans.FilterCreationContext;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;

@OwnedBy(HarnessTeam.CDC)
public class DeploymentStageFilterJsonCreatorV2 extends GenericStageFilterJsonCreatorV2<DeploymentStageNode> {
  @Inject private ServiceEntityService serviceEntityService;
  @Inject private EnvironmentService environmentService;
  @Inject private InfrastructureEntityService infraService;

  @Override
  public Set<String> getSupportedStageTypes() {
    return Collections.singleton("Deployment");
  }

  @Override
  public Class<DeploymentStageNode> getFieldClass() {
    return DeploymentStageNode.class;
  }

  @Override
  public PipelineFilter getFilter(FilterCreationContext filterCreationContext, DeploymentStageNode yamlField) {
    CdFilterBuilder filterBuilder = CdFilter.builder();

    final DeploymentStageConfig deploymentStageConfig = yamlField.getDeploymentStageConfig();

    validate(filterCreationContext, deploymentStageConfig);
    addServiceFilters(filterCreationContext, filterBuilder, deploymentStageConfig);
    addInfraFilters(filterCreationContext, filterBuilder, deploymentStageConfig);

    return filterBuilder.build();
  }

  // This validation is added due to limitations of oneof wherein it introduces strict yaml checking breaking old
  // pipelines with extra fields
  private void validate(FilterCreationContext filterCreationContext, DeploymentStageConfig deploymentStageConfig) {
    if (deploymentStageConfig.getServiceConfig() != null) {
      validateV1(filterCreationContext, deploymentStageConfig);
    } else if (deploymentStageConfig.getService() != null) {
      validateV2(filterCreationContext, deploymentStageConfig);
    }
  }

  private void validateV1(FilterCreationContext filterCreationContext, DeploymentStageConfig deploymentStageConfig) {
    if (deploymentStageConfig.getInfrastructure() == null) {
      throw new InvalidYamlRuntimeException(
          format("infrastructure should be present in stage [%s]. Please add it and try again",
              YamlUtils.getFullyQualifiedName(filterCreationContext.getCurrentField().getNode())));
    }

    if (deploymentStageConfig.getEnvironment() != null) {
      throw new InvalidYamlRuntimeException(
          format("environment should not be present in stage [%s]. Please add infrastructure instead",
              YamlUtils.getFullyQualifiedName(filterCreationContext.getCurrentField().getNode())));
    }

    if (deploymentStageConfig.getEnvironmentGroup() != null) {
      throw new InvalidYamlRuntimeException(
          format("environmentGroup should not be present in stage [%s]. Please add infrastructure instead",
              YamlUtils.getFullyQualifiedName(filterCreationContext.getCurrentField().getNode())));
    }

    if (deploymentStageConfig.getDeploymentType() != null) {
      throw new InvalidYamlRuntimeException(
          format("deploymentType should not be present in stage [%s]. Please remove and try again",
              YamlUtils.getFullyQualifiedName(filterCreationContext.getCurrentField().getNode())));
    }

    if (deploymentStageConfig.getGitOpsEnabled()) {
      throw new InvalidYamlRuntimeException(
          format("gitOpsEnabled should not be set in stage [%s]. Please remove and try again",
              YamlUtils.getFullyQualifiedName(filterCreationContext.getCurrentField().getNode())));
    }
  }

  private void validateV2(FilterCreationContext filterCreationContext, DeploymentStageConfig deploymentStageConfig) {
    if (deploymentStageConfig.getInfrastructure() != null) {
      throw new InvalidYamlRuntimeException(format(
          "infrastructure should not be present in stage [%s]. Please add environment or environment group instead",
          YamlUtils.getFullyQualifiedName(filterCreationContext.getCurrentField().getNode())));
    }

    if (deploymentStageConfig.getDeploymentType() == null) {
      throw new InvalidYamlRuntimeException(
          format("deploymentType should be present in stage [%s]. Please add it and try again",
              YamlUtils.getFullyQualifiedName(filterCreationContext.getCurrentField().getNode())));
    }
  }

  private void addServiceFilters(FilterCreationContext filterCreationContext, CdFilterBuilder filterBuilder,
      DeploymentStageConfig deploymentStageConfig) {
    if (deploymentStageConfig.getServiceConfig() != null) {
      addFiltersFromServiceConfig(filterCreationContext, filterBuilder, deploymentStageConfig.getServiceConfig());
    } else if (deploymentStageConfig.getService() != null) {
      addFiltersFromServiceV2(filterCreationContext, filterBuilder, deploymentStageConfig.getService(),
          deploymentStageConfig.getDeploymentType());
    } else if (deploymentStageConfig.getServices() != null) {
      if (!deploymentStageConfig.getServices().getValues().isExpression()) {
        addFiltersForServices(filterCreationContext, filterBuilder, deploymentStageConfig.getServices(),
            deploymentStageConfig.getDeploymentType());
      }
    } else {
      throw new InvalidYamlRuntimeException(
          format("serviceConfig or service or services should be present in stage [%s]. Please add it and try again",
              YamlUtils.getFullyQualifiedName(filterCreationContext.getCurrentField().getNode())));
    }
  }

  private void addInfraFilters(FilterCreationContext filterCreationContext, CdFilterBuilder filterBuilder,
      DeploymentStageConfig deploymentStageConfig) {
    if (deploymentStageConfig.getInfrastructure() != null) {
      addFiltersFromPipelineInfra(filterCreationContext, filterBuilder, deploymentStageConfig.getInfrastructure());
    } else if (deploymentStageConfig.getEnvironment() != null) {
      addFiltersFromEnvironment(filterCreationContext, filterBuilder, deploymentStageConfig.getEnvironment(),
          deploymentStageConfig.getGitOpsEnabled());
    } else if (deploymentStageConfig.getEnvironmentGroup() != null) {
      addFiltersFromEnvironmentGroup(filterCreationContext, deploymentStageConfig.getEnvironmentGroup());
    } else if (deploymentStageConfig.getEnvironments() != null) {
      if (ParameterField.isNotNull(deploymentStageConfig.getEnvironments().getValues())
          && !deploymentStageConfig.getEnvironments().getValues().isExpression()) {
        for (EnvironmentYamlV2 environmentYamlV2 : deploymentStageConfig.getEnvironments().getValues().getValue()) {
          addFiltersFromEnvironment(
              filterCreationContext, filterBuilder, environmentYamlV2, deploymentStageConfig.getGitOpsEnabled());
        }
      }
    } else {
      throw new InvalidYamlRuntimeException(format(
          "infrastructure or environment or environments or environmentGroup should be present in stage [%s]. Please add it and try again",
          YamlUtils.getFullyQualifiedName(filterCreationContext.getCurrentField().getNode())));
    }
  }

  private void addFiltersFromEnvironment(FilterCreationContext filterCreationContext, CdFilterBuilder filterBuilder,
      EnvironmentYamlV2 env, boolean gitOpsEnabled) {
    final ParameterField<String> environmentRef = env.getEnvironmentRef();
    if (ParameterField.isNull(environmentRef)) {
      throw new InvalidYamlRuntimeException(
          format("environmentRef should be present in stage [%s]. Please add it and try again",
              YamlUtils.getFullyQualifiedName(filterCreationContext.getCurrentField().getNode())));
    }

    if (ParameterField.isNotNull(env.getFilters()) && !env.getFilters().isExpression()
        && EmptyPredicate.isNotEmpty(env.getFilters().getValue())) {
      Set<Entity> unsupportedEntities = env.getFilters()
                                            .getValue()
                                            .stream()
                                            .map(FilterYaml::getEntities)
                                            .flatMap(Set::stream)
                                            .filter(e -> Entity.gitOpsClusters != e && Entity.infrastructures != e)
                                            .collect(Collectors.toSet());
      if (!unsupportedEntities.isEmpty()) {
        throw new InvalidYamlRuntimeException(
            format("Environment filters can only support [%s]. Please add the correct filters in stage [%s]",
                HarnessStringUtils.join(",", Entity.infrastructures.name(), Entity.gitOpsClusters.name()),
                YamlUtils.getFullyQualifiedName(filterCreationContext.getCurrentField().getNode())));
      }
    }

    if (!environmentRef.isExpression()) {
      Optional<Environment> environmentEntityOptional = environmentService.get(
          filterCreationContext.getSetupMetadata().getAccountId(), filterCreationContext.getSetupMetadata().getOrgId(),
          filterCreationContext.getSetupMetadata().getProjectId(), environmentRef.getValue(), false);
      environmentEntityOptional.ifPresent(environment -> {
        filterBuilder.environmentName(environment.getName());
        final List<InfraStructureDefinitionYaml> infraList = getInfraStructureDefinitionYamlsList(env);
        addFiltersForInfraYamlList(filterCreationContext, filterBuilder, environment, infraList);
      });
    }

    final ParameterField<Boolean> deployToAll = env.getDeployToAll();
    if (gitOpsEnabled && !deployToAll.isExpression()) {
      if (deployToAll.getValue() && ParameterField.isNotNull(env.getGitOpsClusters())) {
        throw new InvalidYamlRuntimeException(format(
            "When deploying to all, individual gitops clusters must not be provided in stage [%s]. Please remove the gitOpsClusters property and try again",
            YamlUtils.getFullyQualifiedName(filterCreationContext.getCurrentField().getNode())));
      }
      if (!deployToAll.getValue() && ParameterField.isNull(env.getGitOpsClusters())
          && ParameterField.isNull(env.getFilters())) {
        throw new InvalidYamlRuntimeException(format(
            "When deploy to all is false, list of gitops clusters or filters must be provided  in stage [%s].  Please specify the gitOpsClusters property and try again",
            YamlUtils.getFullyQualifiedName(filterCreationContext.getCurrentField().getNode())));
      }
    }
  }

  private List<InfraStructureDefinitionYaml> getInfraStructureDefinitionYamlsList(EnvironmentYamlV2 env) {
    List<InfraStructureDefinitionYaml> infraList = new ArrayList<>();
    if (ParameterField.isNotNull(env.getInfrastructureDefinitions())) {
      if (!env.getInfrastructureDefinitions().isExpression()) {
        infraList.addAll(env.getInfrastructureDefinitions().getValue());
      }
    } else if (ParameterField.isNotNull(env.getInfrastructureDefinition())) {
      if (!env.getInfrastructureDefinition().isExpression()) {
        infraList.add(env.getInfrastructureDefinition().getValue());
      }
    }
    return infraList;
  }

  private void addFiltersForInfraYamlList(FilterCreationContext filterCreationContext, CdFilterBuilder filterBuilder,
      Environment entity, List<InfraStructureDefinitionYaml> infraList) {
    if (isEmpty(infraList)) {
      return;
    }
    List<InfrastructureEntity> infrastructureEntities = infraService.getAllInfrastructureFromIdentifierList(
        filterCreationContext.getSetupMetadata().getAccountId(), filterCreationContext.getSetupMetadata().getOrgId(),
        filterCreationContext.getSetupMetadata().getProjectId(), entity.getIdentifier(),
        infraList.stream()
            .map(InfraStructureDefinitionYaml::getIdentifier)
            .filter(field -> !field.isExpression())
            .map(ParameterField::getValue)
            .collect(Collectors.toList()));
    for (InfrastructureEntity infrastructureEntity : infrastructureEntities) {
      if (infrastructureEntity.getType() == null) {
        throw new InvalidRequestException(format(
            "Infrastructure Definition [%s] in environment [%s] does not have an associated type. Please select a type for the infrastructure and try again",
            infrastructureEntity.getIdentifier(), infrastructureEntity.getEnvIdentifier()));
      }
      filterBuilder.infrastructureType(infrastructureEntity.getType().getDisplayName());
    }
  }

  private void addFiltersFromEnvironmentGroup(
      FilterCreationContext filterCreationContext, EnvironmentGroupYaml envGroupYaml) {
    final ParameterField<String> envGroupRef = envGroupYaml.getEnvGroupRef();
    if (envGroupRef == null || envGroupRef.fetchFinalValue() == null) {
      throw new InvalidYamlRuntimeException(
          format("envGroupRef should be present in stage [%s]. Please add it and try again",
              YamlUtils.getFullyQualifiedName(filterCreationContext.getCurrentField().getNode())));
    }
  }

  private void addFiltersForServices(FilterCreationContext filterCreationContext, CdFilterBuilder filterBuilder,
      ServicesYaml services, ServiceDefinitionType deploymentType) {
    if (services.getValues().isExpression()) {
      return;
    }

    final List<String> serviceRefs = new ArrayList<>();
    for (ServiceYamlV2 serviceYamlV2 : services.getValues().getValue()) {
      final ParameterField<String> serviceEntityRef = serviceYamlV2.getServiceRef();
      if (ParameterField.isNull(serviceEntityRef)) {
        throw new InvalidYamlRuntimeException(format(
            "serviceRef should be present in stage [%s] when referring to a service entity. Please add it and try again",
            YamlUtils.getFullyQualifiedName(filterCreationContext.getCurrentField().getNode())));
      }
      if (!serviceEntityRef.isExpression()) {
        serviceRefs.add(serviceEntityRef.getValue());
      }
    }

    if (!serviceRefs.isEmpty()) {
      List<ServiceEntity> serviceEntities = serviceEntityService.getServices(
          filterCreationContext.getSetupMetadata().getAccountId(), filterCreationContext.getSetupMetadata().getOrgId(),
          filterCreationContext.getSetupMetadata().getProjectId(), serviceRefs);
      for (ServiceEntity se : serviceEntities) {
        NGServiceV2InfoConfig config = NGServiceEntityMapper.toNGServiceConfig(se).getNgServiceV2InfoConfig();
        filterBuilder.serviceName(se.getName());
        if (config.getServiceDefinition() == null) {
          throw new InvalidYamlRuntimeException(
              format("ServiceDefinition should be present in service [%s]. Please add it and try again", se.getName()));
        }
        if (config.getServiceDefinition().getType() != deploymentType) {
          throw new InvalidYamlRuntimeException(format(
              "deploymentType should be the same as in service [%s]. Please correct it and try again", se.getName()));
        }
        filterBuilder.deploymentType(config.getServiceDefinition().getType().getYamlName());
      }
    }
  }

  private void addFiltersFromServiceV2(FilterCreationContext filterCreationContext, CdFilterBuilder filterBuilder,
      ServiceYamlV2 service, ServiceDefinitionType deploymentType) {
    if (service.getUseFromStage() != null) {
      if (isEmpty(service.getUseFromStage().getStage())) {
        throw new InvalidYamlRuntimeException(format(
            "stage identifier should be present in stage [%s] when propagating service from a different stage. Please add it and try again",
            YamlUtils.getFullyQualifiedName(filterCreationContext.getCurrentField().getNode())));
      }
      return;
    }

    final ParameterField<String> serviceEntityRef = service.getServiceRef();
    if (serviceEntityRef == null || serviceEntityRef.fetchFinalValue() == null) {
      throw new InvalidYamlRuntimeException(format(
          "serviceRef should be present in stage [%s] when referring to a service entity. Please add it and try again",
          YamlUtils.getFullyQualifiedName(filterCreationContext.getCurrentField().getNode())));
    }

    if (!serviceEntityRef.isExpression()) {
      Optional<ServiceEntity> serviceEntityOptional = serviceEntityService.get(
          filterCreationContext.getSetupMetadata().getAccountId(), filterCreationContext.getSetupMetadata().getOrgId(),
          filterCreationContext.getSetupMetadata().getProjectId(), serviceEntityRef.getValue(), false);
      serviceEntityOptional.ifPresent(se -> {
        NGServiceV2InfoConfig config = NGServiceEntityMapper.toNGServiceConfig(se).getNgServiceV2InfoConfig();
        filterBuilder.serviceName(se.getName());
        if (config.getServiceDefinition() == null) {
          throw new InvalidYamlRuntimeException(
              format("ServiceDefinition should be present in service [%s]. Please add it and try again", se.getName()));
        }
        if (config.getServiceDefinition().getType() != deploymentType) {
          throw new InvalidYamlRuntimeException(format(
              "deploymentType should be the same as in service [%s]. Please correct it and try again", se.getName()));
        }
        filterBuilder.deploymentType(config.getServiceDefinition().getType().getYamlName());
      });
    }
  }

  private void addFiltersFromServiceConfig(
      FilterCreationContext filterCreationContext, CdFilterBuilder cdFilter, ServiceConfig serviceConfig) {
    ServiceYaml service = serviceConfig.getService();
    if (service == null
        && (serviceConfig.getServiceRef() == null || serviceConfig.getServiceRef().fetchFinalValue() == null)
        && serviceConfig.getUseFromStage() == null) {
      throw new InvalidYamlRuntimeException(format(
          "One of service, serviceRef and useFromStage should be present in stage [%s]. Please add it and try again",
          YamlUtils.getFullyQualifiedName(filterCreationContext.getCurrentField().getNode())));
    }
    if (service != null && isNotEmpty(service.getName())) {
      cdFilter.serviceName(service.getName());
    }

    ParameterField<String> serviceRef = serviceConfig.getServiceRef();
    if (serviceRef != null && !serviceRef.isExpression()) {
      Optional<ServiceEntity> serviceEntityOptional = serviceEntityService.get(
          filterCreationContext.getSetupMetadata().getAccountId(), filterCreationContext.getSetupMetadata().getOrgId(),
          filterCreationContext.getSetupMetadata().getProjectId(), serviceRef.getValue(), false);
      serviceEntityOptional.ifPresent(serviceEntity -> cdFilter.serviceName(serviceEntity.getName()));
    }

    ServiceDefinition serviceDefinition = serviceConfig.getServiceDefinition();
    if (serviceDefinition != null && serviceDefinition.getType() != null) {
      cdFilter.deploymentType(serviceDefinition.getType().getYamlName());
    }
  }

  private void addFiltersFromPipelineInfra(
      FilterCreationContext filterCreationContext, CdFilterBuilder cdFilter, PipelineInfrastructure infrastructure) {
    if (infrastructure == null) {
      throw new InvalidYamlRuntimeException(
          format("Infrastructure cannot be null in stage [%s]. Please add it and try again",
              YamlUtils.getFullyQualifiedName(filterCreationContext.getCurrentField().getNode())));
    }
    if (infrastructure.getEnvironment() == null
        && (infrastructure.getEnvironmentRef() == null || infrastructure.getEnvironmentRef().fetchFinalValue() == null)
        && infrastructure.getUseFromStage() == null) {
      throw new InvalidYamlRuntimeException(format(
          "One of environment, environment and useFromStage should be present in stage [%s]. Please add it and try again",
          YamlUtils.getFullyQualifiedName(filterCreationContext.getCurrentField().getNode())));
    }

    if (infrastructure.getEnvironment() != null && isNotEmpty(infrastructure.getEnvironment().getName())) {
      cdFilter.environmentName(infrastructure.getEnvironment().getName());
    }

    ParameterField<String> environmentRef = infrastructure.getEnvironmentRef();
    if (environmentRef != null && !environmentRef.isExpression()) {
      Optional<Environment> environmentEntityOptional = environmentService.get(
          filterCreationContext.getSetupMetadata().getAccountId(), filterCreationContext.getSetupMetadata().getOrgId(),
          filterCreationContext.getSetupMetadata().getProjectId(), environmentRef.getValue(), false);
      environmentEntityOptional.ifPresent(environment -> cdFilter.environmentName(environment.getName()));
    }

    if (infrastructure.getInfrastructureDefinition() != null
        && isNotEmpty(infrastructure.getInfrastructureDefinition().getType().getDisplayName())) {
      cdFilter.infrastructureType(infrastructure.getInfrastructureDefinition().getType().getDisplayName());
    }
  }

  @Override
  @NotNull
  protected Map<String, YamlField> getDependencies(YamlField stageField) {
    // Add dependency for rollback steps
    Map<String, YamlField> dependencies = new HashMap<>(super.getDependencies(stageField));
    YamlField pipelineInfraField = stageField.getNode()
                                       .getField(YAMLFieldNameConstants.SPEC)
                                       .getNode()
                                       .getField(YAMLFieldNameConstants.PIPELINE_INFRASTRUCTURE);
    if (pipelineInfraField != null) {
      YamlField provisionerField = pipelineInfraField.getNode()
                                       .getField("infrastructureDefinition")
                                       .getNode()
                                       .getField(YAMLFieldNameConstants.PROVISIONER);

      if (provisionerField != null) {
        YamlField stepsField = provisionerField.getNode().getField("steps");
        if (stepsField != null && stepsField.getNode().asArray().size() != 0) {
          addRollbackDependencies(dependencies, stepsField);
        }
      }
    }
    YamlField executionField =
        stageField.getNode().getField(YAMLFieldNameConstants.SPEC).getNode().getField(YAMLFieldNameConstants.EXECUTION);
    YamlField rollbackStepsField = executionField.getNode().getField(YAMLFieldNameConstants.ROLLBACK_STEPS);
    if (rollbackStepsField != null && rollbackStepsField.getNode().asArray().size() != 0) {
      addRollbackDependencies(dependencies, rollbackStepsField);
    }
    return dependencies;
  }

  private void addRollbackDependencies(Map<String, YamlField> dependencies, YamlField rollbackStepsField) {
    List<YamlField> stepYamlFields = PlanCreatorUtils.getStepYamlFields(rollbackStepsField.getNode().asArray());
    for (YamlField stepYamlField : stepYamlFields) {
      dependencies.put(stepYamlField.getNode().getUuid(), stepYamlField);
    }
  }
}
