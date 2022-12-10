/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.service.impl;

import static io.harness.k8s.KubernetesConvention.getAccountIdentifier;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.DelegateGroup;
import io.harness.delegate.beans.DelegateGroup.DelegateGroupKeys;
import io.harness.delegate.beans.UpgradeCheckResult;
import io.harness.delegate.service.DelegateVersionService;
import io.harness.delegate.service.intfc.DelegateUpgraderService;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import javax.validation.executable.ValidateOnExecution;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Singleton
@ValidateOnExecution
@Slf4j
@OwnedBy(HarnessTeam.DEL)
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class DelegateUpgraderServiceImpl implements DelegateUpgraderService {
  private final DelegateVersionService delegateVersionService;
  private final HPersistence persistence;

  @Override
  public UpgradeCheckResult getDelegateImageTag(
      String accountId, String currentDelegateImageTag, String delegateGroupName) {
    String newDelegateImageTag = delegateVersionService.getImmutableDelegateImageTag(accountId);
    updateDelegateUpgrader(accountId, fetchDelegateGroupName(delegateGroupName, accountId));
    final boolean shouldUpgrade = !currentDelegateImageTag.equals(newDelegateImageTag);
    return new UpgradeCheckResult(shouldUpgrade ? newDelegateImageTag : currentDelegateImageTag, shouldUpgrade);
  }

  private String fetchDelegateGroupName(String delegateGroupName, String accountId) {
    String accountShort = getAccountIdentifier(accountId);
    String[] split = delegateGroupName.split("-");
    // In CG deployment name is appended with accountIdShort.
    if (split.length > 0 && split[split.length - 1].equals(accountShort)) {
      return delegateGroupName.substring(0, delegateGroupName.lastIndexOf('-'));
    }
    return delegateGroupName;
  }

  private void updateDelegateUpgrader(String accountId, String delegateGroupName) {
    persistence.update(persistence.createQuery(DelegateGroup.class)
                           .filter(DelegateGroupKeys.accountId, accountId)
                           .filter(DelegateGroupKeys.name, delegateGroupName),
        persistence.createUpdateOperations(DelegateGroup.class)
            .set(DelegateGroupKeys.upgraderLastUpdated, System.currentTimeMillis()));
  }

  @Override
  public UpgradeCheckResult getDelegateImageTag(String accountId, String currentDelegateImageTag) {
    String newDelegateImageTag = delegateVersionService.getImmutableDelegateImageTag(accountId);
    final boolean shouldUpgrade = !currentDelegateImageTag.equals(newDelegateImageTag);
    return new UpgradeCheckResult(shouldUpgrade ? newDelegateImageTag : currentDelegateImageTag, shouldUpgrade);
  }

  @Override
  public UpgradeCheckResult getUpgraderImageTag(String accountId, String currentUpgraderImageTag) {
    String newUpgraderImageTag = delegateVersionService.getUpgraderImageTag(accountId, true);
    final boolean shouldUpgrade = !currentUpgraderImageTag.equals(newUpgraderImageTag);
    return new UpgradeCheckResult(shouldUpgrade ? newUpgraderImageTag : currentUpgraderImageTag, shouldUpgrade);
  }
}
