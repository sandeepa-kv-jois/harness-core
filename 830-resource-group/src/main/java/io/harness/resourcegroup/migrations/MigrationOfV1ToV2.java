package io.harness.resourcegroup.migrations;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.migration.NGMigration;
import io.harness.resourcegroup.framework.v1.repositories.spring.ResourceGroupRepository;
import io.harness.resourcegroup.framework.v2.repositories.spring.ResourceGroupV2Repository;
import io.harness.resourcegroup.framework.v2.service.ResourceGroupService;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.PL)
public class MigrationOfV1ToV2 implements NGMigration {
  private final ResourceGroupRepository resourceGroupRepository;
  private final ResourceGroupV2Repository resourceGroupV2Repository;
  private final ResourceGroupService resourceGroupService;

  @Inject
  public MigrationOfV1ToV2(ResourceGroupRepository resourceGroupRepository,
      ResourceGroupV2Repository resourceGroupV2Repository, ResourceGroupService resourceGroupService) {
    this.resourceGroupRepository = resourceGroupRepository;
    this.resourceGroupV2Repository = resourceGroupV2Repository;
    this.resourceGroupService = resourceGroupService;
  }

  @Override
  public void migrate() {
    log.info("[MigrationOfV1ToV2] starting migration....");
    try {
      resourceGroupRepository.findAll().forEach(resourceGroup -> { resourceGroupService.upsert(resourceGroup); });
    } catch (Exception exception) {
      log.error("Unexpected error occurred during Migration of Resource Groups ", exception);
    }
  }
}
