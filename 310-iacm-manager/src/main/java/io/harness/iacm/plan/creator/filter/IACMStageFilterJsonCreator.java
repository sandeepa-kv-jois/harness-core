/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.iacm.plan.creator.filter;

import static io.harness.filters.FilterCreatorHelper.convertToEntityDetailProtoDTO;
import static io.harness.git.GitClientHelper.getGitRepo;
import static io.harness.pms.yaml.YAMLFieldNameConstants.CI;
import static io.harness.pms.yaml.YAMLFieldNameConstants.CI_CODE_BASE;
import static io.harness.pms.yaml.YAMLFieldNameConstants.PROPERTIES;
import static io.harness.walktree.visitor.utilities.VisitorParentPathUtils.PATH_CONNECTOR;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.yaml.extended.cache.Caching;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.beans.yaml.extended.runtime.Runtime;
import io.harness.ci.buildstate.ConnectorUtils;
import io.harness.ci.integrationstage.IntegrationStageUtils;
import io.harness.cimanager.stages.IntegrationStageConfig;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.filters.GenericStageFilterJsonCreatorV2;
import io.harness.iacm.beans.steps.StepSpecTypeConstants;
import io.harness.iacm.stages.IACMStageNode;
import io.harness.ng.core.BaseNGAccess;
import io.harness.plancreator.execution.ExecutionElementConfig;
import io.harness.pms.pipeline.filter.PipelineFilter;
import io.harness.pms.sdk.core.filter.creation.beans.FilterCreationContext;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.yaml.extended.ci.codebase.CodeBase;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import java.util.HashSet;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.IACM)
public class IACMStageFilterJsonCreator extends GenericStageFilterJsonCreatorV2<IACMStageNode> {
  @Inject ConnectorUtils connectorUtils;

  @Override
  public Set<String> getSupportedStageTypes() {
    return ImmutableSet.of(StepSpecTypeConstants.IACM_STAGE);
  }

  @Override
  public Class<IACMStageNode> getFieldClass() {
    return IACMStageNode.class;
  }

  /**
   * getFilter returns a Pipeline Filter with the information of the CodeBase block.
   * It can also be used to reject a pipeline given missing/not missing fields in the yaml
   * We are going to use it here to validate the pipeline and reject it if there is a problem with the yaml
   * but then return null because we don't want to use the filter function.
   * */
  @Override
  public PipelineFilter getFilter(FilterCreationContext filterCreationContext, IACMStageNode stageNode) {
    log.info("Received filter creation request for integration stage {}", stageNode.getIdentifier());
    String accountId = filterCreationContext.getSetupMetadata().getAccountId();
    String orgIdentifier = filterCreationContext.getSetupMetadata().getOrgId();
    String projectIdentifier = filterCreationContext.getSetupMetadata().getProjectId();

    BaseNGAccess baseNGAccess = BaseNGAccess.builder()
                                    .accountIdentifier(accountId)
                                    .orgIdentifier(orgIdentifier)
                                    .projectIdentifier(projectIdentifier)
                                    .build();

    CodeBase ciCodeBase = null;
    try {
      YamlNode properties =
          YamlUtils.getGivenYamlNodeFromParentPath(filterCreationContext.getCurrentField().getNode(), PROPERTIES);
      YamlNode ciCodeBaseNode = properties.getField(CI).getNode().getField(CI_CODE_BASE).getNode();
      ciCodeBase = IntegrationStageUtils.getCiCodeBase(ciCodeBaseNode);
    } catch (Exception ex) {
      // Ignore exception because code base is not mandatory in case git clone is false
      log.warn("Failed to retrieve ciCodeBase from pipeline");
    }

    if (ciCodeBase != null && ciCodeBase.getConnectorRef().getValue() != null) {
      try {
        ConnectorDetails connectorDetails =
            connectorUtils.getConnectorDetails(baseNGAccess, ciCodeBase.getConnectorRef().getValue());
        getGitRepo(connectorUtils.retrieveURL(connectorDetails));
      } catch (Exception exception) {
        log.warn("Failed to retrieve repo");
      }
    }

    validateStage(stageNode);

    log.info("Successfully created filter for integration stage {}", stageNode.getIdentifier());
    return null;
  }

  private void validateRuntime(IntegrationStageConfig integrationStageConfig) {
    Runtime runtime = integrationStageConfig.getRuntime();
    if (runtime != null && (runtime.getType() != Runtime.Type.CLOUD)) {
      throw new CIStageExecutionException("Runtime only supports field with type Cloud");
    }
  }

  private void validateInfrastructure(IntegrationStageConfig integrationStageConfig) {
    Infrastructure infrastructure = integrationStageConfig.getInfrastructure();
    if (infrastructure != null) {
      throw new CIStageExecutionException("Infrastructure is not supported in this stage");
    }
  }

  private void validateExecution(IntegrationStageConfig integrationStageConfig) {
    ExecutionElementConfig executionElementConfig = integrationStageConfig.getExecution();
    if (executionElementConfig == null) {
      throw new CIStageExecutionException("Execution field is required in this stage");
    }
  }

  private void validateCache(IntegrationStageConfig integrationStageConfig) {
    Caching caching = integrationStageConfig.getCaching();
    if (caching != null) {
      throw new CIStageExecutionException("Caching field is not required in this stage");
    }
  }

  private void validateStage(IACMStageNode stageNode) {
    IntegrationStageConfig integrationStageConfig = (IntegrationStageConfig) stageNode.getStageInfoConfig();

    validateRuntime(integrationStageConfig);
    validateInfrastructure(integrationStageConfig);
    validateExecution(integrationStageConfig);
    validateCache(integrationStageConfig);
  }

  /**
   * This function seems to be preparing some proto information that I guess is sent to the pms sdk
   * */
  public Set<EntityDetailProtoDTO> getReferredEntities(
      FilterCreationContext filterCreationContext, IACMStageNode stageNode) {
    CodeBase ciCodeBase = null;
    String accountIdentifier = filterCreationContext.getSetupMetadata().getAccountId();
    String orgIdentifier = filterCreationContext.getSetupMetadata().getOrgId();
    String projectIdentifier = filterCreationContext.getSetupMetadata().getProjectId();

    try {
      YamlNode properties =
          YamlUtils.getGivenYamlNodeFromParentPath(filterCreationContext.getCurrentField().getNode(), PROPERTIES);
      YamlNode ciCodeBaseNode = properties.getField(CI).getNode().getField(CI_CODE_BASE).getNode();
      ciCodeBase = IntegrationStageUtils.getCiCodeBase(ciCodeBaseNode);
    } catch (Exception ex) {
      // Ignore exception because code base is not mandatory in case git clone is false
      log.warn("Failed to retrieve ciCodeBase from pipeline");
    }

    Set<EntityDetailProtoDTO> result = new HashSet<>();
    if (ciCodeBase != null) {
      String fullQualifiedDomainName =
          YamlUtils.getFullyQualifiedName(filterCreationContext.getCurrentField().getNode()) + PATH_CONNECTOR
          + YAMLFieldNameConstants.SPEC + PATH_CONNECTOR + ciCodeBase.getConnectorRef();

      result.add(convertToEntityDetailProtoDTO(accountIdentifier, orgIdentifier, projectIdentifier,
          fullQualifiedDomainName, ciCodeBase.getConnectorRef(), EntityTypeProtoEnum.CONNECTORS));
    }

    return result;
  }
}
