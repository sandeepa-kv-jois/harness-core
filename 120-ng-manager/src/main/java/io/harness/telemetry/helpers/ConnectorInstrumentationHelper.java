/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.telemetry.helpers;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.data.structure.EmptyPredicate;
import io.harness.telemetry.Category;
import io.harness.telemetry.Destination;
import io.harness.telemetry.TelemetryOption;
import io.harness.telemetry.TelemetryReporter;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.HashMap;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@OwnedBy(HarnessTeam.PL)
public class ConnectorInstrumentationHelper {
  @Inject TelemetryReporter telemetryReporter;
  public static final String GLOBAL_ACCOUNT_ID = "__GLOBAL_ACCOUNT_ID__";
  String ACCOUNT_ID = "account_id";
  String CONNECTOR_ID = "connector_id";
  String CONNECTOR_PROJECT = "connector_project";
  String CONNECTOR_ORG = "connector_org";
  String CONNECTOR_NAME = "connector_name";
  String CONNECTOR_TYPE = "connector_type";

  public void sendConnectorCreateEvent(ConnectorInfoDTO connector, String accountId) {
    try {
      if (EmptyPredicate.isNotEmpty(accountId) || !accountId.equals(GLOBAL_ACCOUNT_ID)) {
        HashMap<String, Object> map = new HashMap<>();
        String projectIdentifier = connector.getProjectIdentifier();
        String orgIdentifier = connector.getOrgIdentifier();
        if (projectIdentifier != null) {
          map.put(CONNECTOR_PROJECT, projectIdentifier);
        }
        if (orgIdentifier != null) {
          map.put(CONNECTOR_ORG, orgIdentifier);
        }
        map.put(ACCOUNT_ID, accountId);
        map.put(CONNECTOR_ID, connector.getIdentifier());
        map.put(CONNECTOR_TYPE, connector.getConnectorType());
        map.put(CONNECTOR_NAME, connector.getName());
        telemetryReporter.sendTrackEvent("connector_creation_finished", map,
            ImmutableMap.<Destination, Boolean>builder()
                .put(Destination.AMPLITUDE, true)
                .put(Destination.ALL, false)
                .build(),
            Category.PLATFORM, TelemetryOption.builder().sendForCommunity(true).build());
      } else {
        log.info("There is no account found for account ID = " + accountId
            + "!. Cannot send Connector Creation Finished event.");
      }
    } catch (Exception e) {
      log.error("Connector creation event failed for accountID= " + accountId, e);
    }
  }

  public void sendConnectorDeleteEvent(
      String orgIdentifier, String projectIdentifier, String connectorIdentifier, String accountId) {
    try {
      if (EmptyPredicate.isNotEmpty(accountId) || !accountId.equals(GLOBAL_ACCOUNT_ID)) {
        HashMap<String, Object> map = new HashMap<>();
        if (projectIdentifier != null) {
          map.put(CONNECTOR_PROJECT, projectIdentifier);
        }
        if (orgIdentifier != null) {
          map.put(CONNECTOR_ORG, orgIdentifier);
        }
        map.put(ACCOUNT_ID, accountId);
        map.put(CONNECTOR_ID, connectorIdentifier);
        telemetryReporter.sendTrackEvent("connector_deletion", map,
            ImmutableMap.<Destination, Boolean>builder()
                .put(Destination.AMPLITUDE, true)
                .put(Destination.ALL, false)
                .build(),
            Category.PLATFORM, TelemetryOption.builder().sendForCommunity(true).build());
      } else {
        log.info(
            "There is no account found for account ID = " + accountId + "!. Cannot send Connector Deletion event.");
      }
    } catch (Exception e) {
      log.error("Connector deletion event failed for accountID= " + accountId, e);
    }
  }
}
