/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.instancesync.resources;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.gitops.beans.DeleteInstancesRequest;
import io.harness.cdng.gitops.beans.GitOpsInstance;
import io.harness.cdng.gitops.beans.GitOpsInstanceRequest;
import io.harness.dtos.InstanceDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.helper.GitOpsRequestDTOMapper;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.overview.dto.ServicePipelineInfo;
import io.harness.ng.overview.service.CDOverviewDashboardService;
import io.harness.service.instance.InstanceService;
import io.harness.service.instancesync.GitopsInstanceSyncService;

import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Hidden;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Api("gitOpsInstanceSync")
@Path("instancesync/gitops")
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@Produces({"application/json"})
@Consumes({"application/json"})
@Hidden
@OwnedBy(HarnessTeam.GITOPS)
@Slf4j
public class GitOpsInstanceSyncResource {
  private final InstanceService instanceService;
  private final GitopsInstanceSyncService gitopsInstanceSyncService;
  private final GitOpsRequestDTOMapper gitOpsRequestDTOMapper;
  private final CDOverviewDashboardService cdOverviewDashboardService;

  @POST
  @ApiOperation(value = "Create instances and save in DB", nickname = "createGitOpsInstances")
  public ResponseDTO<Boolean> createGitOpsInstances(
      @NotEmpty @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @NotEmpty @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @NotEmpty @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,

      @NotNull @Valid List<GitOpsInstanceRequest> gitOpsInstanceRequestList) {
    if (isEmpty(gitOpsInstanceRequestList)) {
      throw new InvalidRequestException("GitopsInstanceRequestList cannot be empty");
    }
    List<GitOpsInstance> processedInstances =
        prepareInstanceSync(accountId, orgIdentifier, projectIdentifier, gitOpsInstanceRequestList);
    final List<InstanceDTO> instanceDTOs =
        gitOpsRequestDTOMapper.toInstanceDTOList(accountId, orgIdentifier, projectIdentifier, processedInstances);
    gitopsInstanceSyncService.processInstanceSync(accountId, orgIdentifier, projectIdentifier, instanceDTOs);
    return ResponseDTO.newResponse(Boolean.TRUE);
  }

  @DELETE
  @ApiOperation(value = "Delete instances", nickname = "deleteGitOpsInstances")
  public ResponseDTO<DeleteInstancesRequest> deleteGitOpsInstances(
      @NotEmpty @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @NotNull @Valid List<GitOpsInstanceRequest> gitOpsInstanceRequestList) {
    final List<InstanceDTO> instanceDTOs =
        gitOpsRequestDTOMapper.toInstanceDTOListForDeletion(accountId, gitOpsInstanceRequestList);
    instanceService.deleteAll(instanceDTOs);
    return ResponseDTO.newResponse(
        DeleteInstancesRequest.builder().deletedCount(gitOpsInstanceRequestList.size()).status(true).build());
  }

  List<GitOpsInstance> prepareInstanceSync(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      List<GitOpsInstanceRequest> gitOpsInstanceRequestList) {
    List<GitOpsInstance> instanceDTOs = new ArrayList<>();

    final List<GitOpsInstance> gitOpsInstanceDTOs = gitOpsRequestDTOMapper.toGitOpsInstanceList(
        accountIdentifier, orgIdentifier, projectIdentifier, gitOpsInstanceRequestList);
    final Map<String, List<GitOpsInstance>> gitOpsInstancesGroupedByService =
        gitOpsInstanceDTOs.stream().collect(Collectors.groupingBy(GitOpsInstance::getServiceEnvIdentifier));

    final Set<String> envIdentifiers =
        gitOpsInstanceRequestList.stream().map(GitOpsInstanceRequest::getEnvIdentifier).collect(Collectors.toSet());
    final Set<String> serviceIdentifiers =
        gitOpsInstanceRequestList.stream().map(GitOpsInstanceRequest::getServiceIdentifier).collect(Collectors.toSet());
    // Get pipelines execution details
    Map<String, String> serviceEnvIdToPipelineIdMap = cdOverviewDashboardService.getLastPipeline(
        accountIdentifier, orgIdentifier, projectIdentifier, serviceIdentifiers, envIdentifiers);

    List<String> pipelineExecutionIdList = serviceEnvIdToPipelineIdMap.values().stream().collect(Collectors.toList());
    // Gets all the details for the pipeline execution id's in the list and stores it in a map.
    Map<String, ServicePipelineInfo> pipelineExecutionDetailsMap =
        cdOverviewDashboardService.getPipelineExecutionDetails(pipelineExecutionIdList);

    for (Map.Entry<String, List<GitOpsInstance>> instanceGroup : gitOpsInstancesGroupedByService.entrySet()) {
      // Get pipeline Info
      List<GitOpsInstance> instances = instanceGroup.getValue();
      String pipelineServiceEnvId = instanceGroup.getKey();
      if (!serviceEnvIdToPipelineIdMap.containsKey(pipelineServiceEnvId)) {
        log.warn("gitOps instance is not associated to a pipeline with serviceId and environmentId %s",
            pipelineServiceEnvId);
        continue;
      }
      String pipelineId = serviceEnvIdToPipelineIdMap.get(pipelineServiceEnvId);

      if (!pipelineExecutionDetailsMap.containsKey(pipelineId)) {
        log.warn("gitOps instance pipeline: %s, does not have any executions yet", pipelineId);
        continue;
      }
      ServicePipelineInfo pipelineInfo = pipelineExecutionDetailsMap.get(pipelineId);

      // set pipeline details
      if (pipelineInfo != null) {
        instances.forEach(gitInstance -> {
          gitInstance.setPipelineName(pipelineInfo.getName());
          gitInstance.setLastExecutedAt(pipelineInfo.getLastExecutedAt());
          gitInstance.setPipelineExecutionId(pipelineInfo.getPlanExecutionId());
          gitInstance.setLastDeployedById(pipelineInfo.getDeployedById());
          gitInstance.setLastDeployedByName(pipelineInfo.getDeployedByName());
          instanceDTOs.add(gitInstance);
        });
      } else {
        log.warn("gitOps instance pipeline does not have any execution details for pipeline %s", pipelineId);
      }
    }
    return instanceDTOs;
  }
}
