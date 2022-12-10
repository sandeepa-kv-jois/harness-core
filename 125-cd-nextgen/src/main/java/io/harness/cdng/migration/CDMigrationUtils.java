/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.migration;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.entities.InfrastructureMapping;
import io.harness.entities.InfrastructureMapping.InfrastructureMappingNGKeys;
import io.harness.entities.Instance;
import io.harness.entities.Instance.InstanceKeys;
import io.harness.entities.instancesyncperpetualtaskinfo.InstanceSyncPerpetualTaskInfo;
import io.harness.entities.instancesyncperpetualtaskinfo.InstanceSyncPerpetualTaskInfo.InstanceSyncPerpetualTaskInfoKeys;
import io.harness.service.instancesyncperpetualtask.InstanceSyncPerpetualTaskService;

import com.google.inject.Inject;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(CDP)
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class CDMigrationUtils {
  private MongoTemplate mongoTemplate;
  private InstanceSyncPerpetualTaskService instanceSyncPerpetualTaskService;

  public void deleteOrphanInstance(InfrastructureMapping infrastructureMapping) {
    try {
      Query query = new Query(new Criteria()
                                  .and(InstanceKeys.infrastructureMappingId)
                                  .is(infrastructureMapping.getId())
                                  .and(InstanceKeys.accountIdentifier)
                                  .is(infrastructureMapping.getAccountIdentifier())
                                  .and(InstanceKeys.orgIdentifier)
                                  .is(infrastructureMapping.getOrgIdentifier()));
      Update update =
          new Update().set(InstanceKeys.isDeleted, true).set(InstanceKeys.deletedAt, System.currentTimeMillis());
      UpdateResult result = mongoTemplate.updateMulti(query, update, Instance.class);
      log.info("{} Instances deleted successfully.", result.getModifiedCount());
    } catch (Exception ex) {
      log.error("Unexpected error occurred while deleting orphan instances", ex);
    }
  }

  /**
   * Deletes perpetualTask, instanceSyncPerpetualTaskInfo, and infrastructureMapping for the infrastructureMapping
   */
  public void deleteRelatedOrphanEntities(InfrastructureMapping infrastructureMapping) {
    try {
      Query instanceSyncPerpetualTaskInfoQuery =
          new Query(new Criteria()
                        .and(InstanceSyncPerpetualTaskInfoKeys.accountIdentifier)
                        .is(infrastructureMapping.getAccountIdentifier())
                        .and(InstanceSyncPerpetualTaskInfoKeys.infrastructureMappingId)
                        .is(infrastructureMapping.getId()));
      InstanceSyncPerpetualTaskInfo instanceSyncPerpetualTaskInfo =
          mongoTemplate.findOne(instanceSyncPerpetualTaskInfoQuery, InstanceSyncPerpetualTaskInfo.class);

      if (instanceSyncPerpetualTaskInfo != null) {
        instanceSyncPerpetualTaskService.deletePerpetualTask(
            instanceSyncPerpetualTaskInfo.getAccountIdentifier(), instanceSyncPerpetualTaskInfo.getPerpetualTaskId());
        log.info("Deleted perpetualTask {}", instanceSyncPerpetualTaskInfo.getPerpetualTaskId());
        deleteInstanceSyncPerpetualTaskInfo(instanceSyncPerpetualTaskInfo.getId());
      }
      deleteInfrastructureMapping(infrastructureMapping.getId());
    } catch (Exception ex) {
      log.error("Unexpected error occurred while deleting related orphan entities", ex);
    }
  }

  private void deleteInstanceSyncPerpetualTaskInfo(String id) {
    DeleteResult result =
        mongoTemplate.remove(new Query(new Criteria().and(InstanceSyncPerpetualTaskInfoKeys.id).is(id)),
            InstanceSyncPerpetualTaskInfo.class);
    if (result.getDeletedCount() == 1) {
      log.info("Deleted instanceSyncPerpetualTaskInfo {}", id);
    }
  }

  private void deleteInfrastructureMapping(String id) {
    DeleteResult result = mongoTemplate.remove(
        new Query(new Criteria().and(InfrastructureMappingNGKeys.id).is(id)), InfrastructureMapping.class);
    if (result.getDeletedCount() == 1) {
      log.info("Deleted infrastructureMapping {}", id);
    }
  }
}
