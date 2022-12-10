/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service;

import static software.wings.ngmigration.NGMigrationEntityType.WORKFLOW;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ngmigration.beans.BaseProvidedInput;
import io.harness.ngmigration.beans.InputDefaults;
import io.harness.ngmigration.beans.MigrationInputDTO;
import io.harness.ngmigration.dto.ApplicationFilter;
import io.harness.ngmigration.dto.ConnectorFilter;
import io.harness.ngmigration.dto.Filter;
import io.harness.ngmigration.dto.ImportDTO;
import io.harness.ngmigration.dto.SaveSummaryDTO;
import io.harness.ngmigration.dto.SecretFilter;
import io.harness.ngmigration.dto.SecretManagerFilter;
import io.harness.ngmigration.dto.ServiceFilter;
import io.harness.ngmigration.dto.SimilarWorkflowDetail;
import io.harness.ngmigration.dto.TemplateFilter;
import io.harness.ngmigration.dto.WorkflowFilter;
import io.harness.ngmigration.service.importer.AppImportService;
import io.harness.ngmigration.service.importer.ConnectorImportService;
import io.harness.ngmigration.service.importer.SecretManagerImportService;
import io.harness.ngmigration.service.importer.SecretsImportService;
import io.harness.ngmigration.service.importer.ServiceImportService;
import io.harness.ngmigration.service.importer.TemplateImportService;
import io.harness.ngmigration.service.importer.WorkflowImportService;
import io.harness.ngmigration.service.workflow.WorkflowHandlerFactory;
import io.harness.persistence.HPersistence;

import software.wings.beans.Workflow;
import software.wings.beans.Workflow.WorkflowKeys;
import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.DiscoveryResult;
import software.wings.ngmigration.NGMigrationEntityType;
import software.wings.service.intfc.WorkflowService;

import com.google.inject.Inject;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.ws.rs.core.StreamingOutput;

@OwnedBy(HarnessTeam.CDC)
public class MigrationResourceService {
  @Inject private ConnectorImportService connectorImportService;
  @Inject private SecretManagerImportService secretManagerImportService;
  @Inject private SecretsImportService secretsImportService;
  @Inject private AppImportService appImportService;
  @Inject private ServiceImportService serviceImportService;
  @Inject private DiscoveryService discoveryService;
  @Inject private TemplateImportService templateImportService;
  @Inject private WorkflowImportService workflowImportService;
  @Inject private WorkflowService workflowService;
  @Inject HPersistence hPersistence;
  @Inject WorkflowHandlerFactory workflowHandlerFactory;

  private DiscoveryResult discover(String authToken, ImportDTO importDTO) {
    // Migrate referenced entities as well.
    importDTO.setMigrateReferencedEntities(true);
    Filter filter = importDTO.getFilter();
    if (filter instanceof ConnectorFilter) {
      return connectorImportService.discover(authToken, importDTO);
    }
    if (filter instanceof SecretManagerFilter) {
      return secretManagerImportService.discover(authToken, importDTO);
    }
    if (filter instanceof SecretFilter) {
      return secretsImportService.discover(authToken, importDTO);
    }
    if (filter instanceof ApplicationFilter) {
      return appImportService.discover(authToken, importDTO);
    }
    if (filter instanceof ServiceFilter) {
      return serviceImportService.discover(authToken, importDTO);
    }
    if (filter instanceof TemplateFilter) {
      return templateImportService.discover(authToken, importDTO);
    }
    if (filter instanceof WorkflowFilter) {
      return workflowImportService.discover(authToken, importDTO);
    }
    return DiscoveryResult.builder().build();
  }

  public SaveSummaryDTO save(String authToken, ImportDTO importDTO) {
    DiscoveryResult discoveryResult = discover(authToken, importDTO);
    return discoveryService.migrateEntity(authToken, getMigrationInput(importDTO), discoveryResult);
  }

  public StreamingOutput exportYaml(String authToken, ImportDTO importDTO) {
    return discoveryService.exportYamlFilesAsZip(getMigrationInput(importDTO), discover(authToken, importDTO));
  }

  private static MigrationInputDTO getMigrationInput(ImportDTO importDTO) {
    Map<NGMigrationEntityType, InputDefaults> defaults = new HashMap<>();
    Map<CgEntityId, BaseProvidedInput> overrides = new HashMap<>();
    Map<String, String> expressions = new HashMap<>();
    if (importDTO.getInputs() != null) {
      overrides = importDTO.getInputs().getOverrides();
      defaults = importDTO.getInputs().getDefaults();
      expressions = importDTO.getInputs().getExpressions();
    }

    // We do not want to auto migrate WFs. We want customers to migrate WFs by choice.
    if (!WORKFLOW.equals(importDTO.getEntityType())) {
      InputDefaults inputDefaults = defaults.getOrDefault(WORKFLOW, new InputDefaults());
      inputDefaults.setSkipMigration(true);
      defaults.put(WORKFLOW, inputDefaults);
    }

    return MigrationInputDTO.builder()
        .accountIdentifier(importDTO.getAccountIdentifier())
        .orgIdentifier(importDTO.getDestinationDetails().getOrgIdentifier())
        .projectIdentifier(importDTO.getDestinationDetails().getProjectIdentifier())
        .migrateReferencedEntities(importDTO.isMigrateReferencedEntities())
        .overrides(overrides)
        .defaults(defaults)
        .customExpressions(expressions)
        .build();
  }

  public List<Set<SimilarWorkflowDetail>> listSimilarWorkflow(String accountId) {
    Map<String, Workflow> workflowMap = new HashMap<>();
    List<Workflow> workflowsWithAppId = hPersistence.createQuery(Workflow.class)
                                            .filter(WorkflowKeys.accountId, accountId)
                                            .project(WorkflowKeys.uuid, true)
                                            .project(WorkflowKeys.appId, true)
                                            .asList();
    int[] list = new int[workflowsWithAppId.size()];
    for (int i = 0; i < list.length; ++i) {
      list[i] = i;
    }

    for (int i = 0; i < workflowsWithAppId.size(); ++i) {
      for (int j = i + 1; j < workflowsWithAppId.size(); ++j) {
        if (!areConnected(list, i, j)) {
          Workflow workflow1 =
              getWorkflow(workflowMap, workflowsWithAppId.get(i).getUuid(), workflowsWithAppId.get(i).getAppId());
          Workflow workflow2 =
              getWorkflow(workflowMap, workflowsWithAppId.get(j).getUuid(), workflowsWithAppId.get(j).getAppId());
          if (workflowHandlerFactory.areSimilar(workflow1, workflow2)) {
            connect(list, i, j);
          }
        }
      }
    }
    Map<Integer, Set<SimilarWorkflowDetail>> similarWorkflows = new HashMap<>();
    for (int i = 0; i < list.length; ++i) {
      Set<SimilarWorkflowDetail> ids = similarWorkflows.getOrDefault(list[i], new HashSet<>());
      ids.add(SimilarWorkflowDetail.builder()
                  .appId(workflowsWithAppId.get(i).getAppId())
                  .workflowId(workflowsWithAppId.get(i).getUuid())
                  .build());
      similarWorkflows.put(list[i], ids);
    }

    return similarWorkflows.values().stream().filter(set -> set.size() > 1).collect(Collectors.toList());
  }

  private Workflow getWorkflow(Map<String, Workflow> workflowMap, String workflowId, String appId) {
    if (workflowMap.containsKey(workflowId)) {
      return workflowMap.get(workflowId);
    }
    Workflow workflow = workflowService.readWorkflow(appId, workflowId);
    workflowMap.put(workflowId, workflow);
    return workflow;
  }

  private void connect(int[] list, int i, int j) {
    if (list[i] != list[j]) {
      int temp = list[i];
      list[i] = list[j];
      while (list[temp] != list[j]) {
        temp = list[temp];
        list[temp] = list[j];
      }
    }
  }

  private boolean areConnected(int[] list, int i, int j) {
    return list[i] == list[j];
  }
}
