package io.harness.resourcegroup.framework.v2.service;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.resourcegroup.v2.remote.dto.ResourceGroupRequest;

@OwnedBy(PL)
public interface ResourceGroupValidator {
  void validateResourceGroup(ResourceGroupRequest resourceGroupRequest);
}
