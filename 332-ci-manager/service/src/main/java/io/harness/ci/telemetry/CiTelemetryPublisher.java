/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.telemetry;

import static io.harness.annotations.dev.HarnessTeam.CI;
import static io.harness.configuration.DeployVariant.DEPLOY_VERSION;
import static io.harness.telemetry.Destination.ALL;

import io.harness.ModuleType;
import io.harness.account.AccountClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.core.ci.services.CIOverviewDashboardService;
import io.harness.data.structure.EmptyPredicate;
import io.harness.licensing.entities.modules.ModuleLicense;
import io.harness.ng.core.dto.AccountDTO;
import io.harness.remote.client.CGRestUtils;
import io.harness.repositories.CITelemetryStatusRepository;
import io.harness.repositories.ModuleLicenseRepository;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(CI)
public class CiTelemetryPublisher {
  @Inject CIOverviewDashboardService ciOverviewDashboardService;
  @Inject TelemetryReporter telemetryReporter;
  @Inject AccountClient accountClient;
  @Inject CITelemetryStatusRepository ciTelemetryStatusRepository;
  @Inject ModuleLicenseRepository moduleLicenseRepository;
  String COUNT_ACTIVE_DEVELOPERS = "ci_license_developers_used";
  String ACCOUNT_DEPLOY_TYPE = "account_deploy_type";
  // Locking for a bit less than one day. It's ok to send a bit more than less considering downtime/etc
  static final long A_DAY_MINUS_TEN_MINS = 85800000;
  private static final String ACCOUNT = "Account";
  private static final String GLOBAL_ACCOUNT_ID = "__GLOBAL_ACCOUNT_ID__";
  private static final String GROUP_TYPE = "group_type";
  private static final String GROUP_ID = "group_id";

  public void recordTelemetry() {
    log.info("CiTelemetryPublisher recordTelemetry execute started.");
    try {
      List<AccountDTO> accountDTOList = getAllAccounts();
      for (AccountDTO accountDTO : accountDTOList) {
        String accountId = accountDTO.getIdentifier();
        if (EmptyPredicate.isNotEmpty(accountId) && !accountId.equals(GLOBAL_ACCOUNT_ID)) {
          if (ciTelemetryStatusRepository.updateTimestampIfOlderThan(
                  accountId, System.currentTimeMillis() - A_DAY_MINUS_TEN_MINS, System.currentTimeMillis())) {
            List<ModuleLicense> existing =
                moduleLicenseRepository.findByAccountIdentifierAndModuleType(accountId, ModuleType.CI);
            HashMap<String, Object> map = new HashMap<>();
            map.put(GROUP_TYPE, ACCOUNT);
            map.put(GROUP_ID, accountId);
            long developersCount = ciOverviewDashboardService.getActiveCommitterCount(accountId);
            if (existing.size() != 0 || developersCount != 0) {
              map.put(COUNT_ACTIVE_DEVELOPERS, developersCount);
              telemetryReporter.sendGroupEvent(accountId, null, map, Collections.singletonMap(ALL, true),
                  TelemetryOption.builder().sendForCommunity(true).build());
              log.info("Scheduled CiTelemetryPublisher event sent! for account {}", accountId);
            } else {
              map.put(COUNT_ACTIVE_DEVELOPERS, null);
              telemetryReporter.sendGroupEvent(accountId, null, map, Collections.singletonMap(ALL, true),
                  TelemetryOption.builder().sendForCommunity(true).build());
              log.info("Account {} does not have CI Module, sending null as count", accountId);
            }
            map.put(ACCOUNT_DEPLOY_TYPE, System.getenv().get(DEPLOY_VERSION));
          } else {
            log.info("Skipping already sent account {} in past 24 hours", accountId);
          }
        }
      }
    } catch (Exception e) {
      log.error("CITelemetryPublisher recordTelemetry execute failed.", e);
    } finally {
      log.info("CITelemetryPublisher recordTelemetry execute finished.");
    }
  }

  List<AccountDTO> getAllAccounts() {
    return CGRestUtils.getResponse(accountClient.getAllAccounts());
  }
}
