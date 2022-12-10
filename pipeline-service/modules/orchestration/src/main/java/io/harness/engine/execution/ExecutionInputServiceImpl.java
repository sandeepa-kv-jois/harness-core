/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.execution;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.pms.yaml.validation.RuntimeInputValuesValidator.validateStaticValues;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.engine.OrchestrationEngine;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.engine.pms.data.PmsEngineExpressionService;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.ExecutionInputInstance;
import io.harness.execution.NodeExecution;
import io.harness.expression.common.ExpressionMode;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.execution.utils.NodeProjectionUtils;
import io.harness.pms.merger.YamlConfig;
import io.harness.pms.merger.fqn.FQN;
import io.harness.pms.merger.helpers.MergeHelper;
import io.harness.pms.merger.helpers.YamlSubMapExtractor;
import io.harness.repositories.ExecutionInputRepository;
import io.harness.waiter.WaitNotifyEngine;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@OwnedBy(PIPELINE)
public class ExecutionInputServiceImpl implements ExecutionInputService {
  @Inject WaitNotifyEngine waitNotifyEngine;
  @Inject ExecutionInputRepository executionInputRepository;
  @Inject ExecutionInputServiceHelper executionInputServiceHelper;
  @Inject NodeExecutionService nodeExecutionService;
  @Inject OrchestrationEngine engine;
  @Inject PmsEngineExpressionService pmsEngineExpressionService;
  @Override
  // TODO(BRIJESH): Use lock so that only one input can be processed and only one doneWith should be called.
  public boolean continueExecution(String nodeExecutionId, String executionInputYaml) {
    ExecutionInputInstance executionInputInstance;
    try {
      executionInputInstance = mergeUserInputInTemplate(nodeExecutionId, executionInputYaml);
    } catch (NoSuchElementException ex) {
      log.error("User input could not be processed for nodeExecutionId {}", nodeExecutionId, ex);
      return false;
    }
    waitNotifyEngine.doneWith(executionInputInstance.getInputInstanceId(),
        ExecutionInputData.builder().inputInstanceId(executionInputInstance.getInputInstanceId()).build());
    return true;
  }

  @Override
  public boolean continueWithDefault(String nodeExecutionId) {
    try {
      mergeUserInputInTemplate(nodeExecutionId, "");
    } catch (NoSuchElementException ex) {
      log.error("User input could not be processed for nodeExecutionId {}", nodeExecutionId, ex);
      return false;
    }

    NodeExecution nodeExecution = nodeExecutionService.updateStatusWithOps(
        nodeExecutionId, Status.QUEUED, null, EnumSet.of(Status.INPUT_WAITING));
    engine.startNodeExecution(nodeExecution.getAmbiance());
    return true;
  }

  @Override
  public ExecutionInputInstance getExecutionInputInstance(String nodeExecutionId) {
    Optional<ExecutionInputInstance> optional = executionInputRepository.findByNodeExecutionId(nodeExecutionId);
    if (optional.isPresent()) {
      return optional.get();
    }
    log.info("Execution Input template does not exist for input execution id: {}", nodeExecutionId);
    return null;
  }

  @Override
  public boolean isPresent(String nodeExecutionId) {
    Optional<ExecutionInputInstance> optional = executionInputRepository.findByNodeExecutionId(nodeExecutionId);
    return optional.isPresent();
  }

  @Override
  public ExecutionInputInstance save(ExecutionInputInstance executionInputInstance) {
    return executionInputRepository.save(executionInputInstance);
  }

  @Override
  public List<ExecutionInputInstance> getExecutionInputInstances(Set<String> nodeExecutionIds) {
    return executionInputRepository.findByNodeExecutionIds(nodeExecutionIds);
  }

  private ExecutionInputInstance mergeUserInputInTemplate(String nodeExecutionId, String executionInputYaml) {
    Optional<ExecutionInputInstance> optional = executionInputRepository.findByNodeExecutionId(nodeExecutionId);
    if (optional.isPresent()) {
      ExecutionInputInstance executionInputInstance = optional.get();
      if (EmptyPredicate.isEmpty(executionInputYaml)) {
        executionInputYaml = executionInputInstance.getTemplate();
      }
      Ambiance ambiance =
          nodeExecutionService.getWithFieldsIncluded(nodeExecutionId, NodeProjectionUtils.withAmbianceAndStatus)
              .getAmbiance();
      executionInputYaml = (String) pmsEngineExpressionService.resolve(
          ambiance, executionInputYaml, ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED);
      executionInputInstance.setUserInput(executionInputYaml);

      Map<FQN, String> invalidFQNsInInputSet =
          getInvalidFQNsInInputSet(executionInputInstance.getTemplate(), executionInputYaml);
      if (!EmptyPredicate.isEmpty(invalidFQNsInInputSet)) {
        throw new InvalidRequestException("Some fields are not valid: "
            + invalidFQNsInInputSet.keySet()
                  .stream()
                  .map(FQN::getExpressionFqn)
                  .collect(Collectors.toList())
                  .toString());
      }
      JsonNode mergedJsonNode = MergeHelper.mergeExecutionInputIntoOriginalYamlJsonNode(
          executionInputInstance.getTemplate(), executionInputInstance.getUserInput(), false);
      executionInputInstance.setMergedInputTemplate(
          executionInputServiceHelper.getExecutionInputMap(executionInputInstance.getTemplate(), mergedJsonNode));
      return executionInputRepository.save(executionInputInstance);
    } else {
      throw new InvalidRequestException(
          String.format("Execution Input template does not exist for input execution id : %s", nodeExecutionId));
    }
  }

  // Using duplicate method of InputSetErrorHelper in pipeline-service. Will refactor the method and use the bring
  // down to 870.
  public Map<FQN, String> getInvalidFQNsInInputSet(String templateYaml, String inputSetPipelineCompYaml) {
    Map<FQN, String> errorMap = new LinkedHashMap<>();
    YamlConfig inputSetConfig = new YamlConfig(inputSetPipelineCompYaml);
    Set<FQN> inputSetFQNs = new LinkedHashSet<>(inputSetConfig.getFqnToValueMap().keySet());
    if (EmptyPredicate.isEmpty(templateYaml)) {
      inputSetFQNs.forEach(fqn -> errorMap.put(fqn, "Pipeline no longer contains any runtime input"));
      return errorMap;
    }
    YamlConfig templateConfig = new YamlConfig(templateYaml);

    templateConfig.getFqnToValueMap().keySet().forEach(key -> {
      if (inputSetFQNs.contains(key)) {
        Object templateValue = templateConfig.getFqnToValueMap().get(key);
        Object value = inputSetConfig.getFqnToValueMap().get(key);
        if (key.isType() || key.isIdentifierOrVariableName()) {
          if (!value.toString().equals(templateValue.toString())) {
            errorMap.put(key,
                "The value for " + key.getExpressionFqn() + " is " + templateValue.toString()
                    + "in the pipeline yaml, but the input set has it as " + value.toString());
          }
        } else {
          String error = validateStaticValues(templateValue, value);
          if (EmptyPredicate.isNotEmpty(error)) {
            errorMap.put(key, error);
          }
        }

        inputSetFQNs.remove(key);
      } else {
        Map<FQN, Object> subMap = YamlSubMapExtractor.getFQNToObjectSubMap(inputSetConfig.getFqnToValueMap(), key);
        subMap.keySet().forEach(inputSetFQNs::remove);
      }
    });
    inputSetFQNs.forEach(fqn -> errorMap.put(fqn, "Field either not present in pipeline or not a runtime input"));
    return errorMap;
  }
}
