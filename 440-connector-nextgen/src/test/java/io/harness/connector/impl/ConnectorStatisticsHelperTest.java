/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.impl;

import static io.harness.annotations.dev.HarnessTeam.DX;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectivityStatus;
import io.harness.connector.ConnectorConnectivityDetails;
import io.harness.connector.ConnectorsTestBase;
import io.harness.connector.entities.Connector;
import io.harness.connector.stats.ConnectorStatistics;
import io.harness.connector.stats.ConnectorStatusStats;
import io.harness.connector.stats.ConnectorTypeStats;
import io.harness.connector.utils.AWSConnectorTestHelper;
import io.harness.connector.utils.DockerConnectorTestHelper;
import io.harness.connector.utils.KubernetesConnectorTestHelper;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.encryption.Scope;
import io.harness.git.model.ChangeType;
import io.harness.ng.core.accountsetting.dto.AccountSettingType;
import io.harness.ng.core.accountsetting.dto.ConnectorSettings;
import io.harness.ng.core.accountsetting.entities.AccountSettings;
import io.harness.outbox.api.OutboxService;
import io.harness.repositories.ConnectorRepository;
import io.harness.repositories.accountsetting.AccountSettingRepository;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import com.google.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(DX)
public class ConnectorStatisticsHelperTest extends ConnectorsTestBase {
  @Inject ConnectorRepository connectorRepository;
  @Inject ConnectorStatisticsHelper connectorStatisticsHelper;
  @Inject OutboxService outboxService;
  @Inject AccountSettingRepository accountSettingRepository;
  private static final String accountIdentifier = "accountIdentifier";
  private static final String orgIdentifier = "orgIdentifier";
  private static final String projectIdentifier = "projectIdentifier";
  private static final String identifier = "identifier";

  @Test
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)
  public void getStats() {
    accountSettingRepository.save(AccountSettings.builder()
                                      .accountIdentifier(accountIdentifier)
                                      .type(AccountSettingType.CONNECTOR)
                                      .config(ConnectorSettings.builder().builtInSMDisabled(false).build())
                                      .build());
    for (int i = 0; i < 5; i++) {
      Connector connector = KubernetesConnectorTestHelper.createK8sConnector(
          accountIdentifier, orgIdentifier, projectIdentifier, identifier, Scope.PROJECT);
      setStatus(connector, ConnectivityStatus.SUCCESS);
      connectorRepository.save(connector, ChangeType.ADD);
    }

    for (int i = 0; i < 3; i++) {
      Connector connector = AWSConnectorTestHelper.createAWSConnector(
          accountIdentifier, orgIdentifier, projectIdentifier, identifier, Scope.PROJECT);
      setStatus(connector, ConnectivityStatus.FAILURE);
      connectorRepository.save(connector, ChangeType.ADD);
    }

    for (int i = 0; i < 4; i++) {
      Connector connector = DockerConnectorTestHelper.createDockerConnector(
          accountIdentifier, orgIdentifier, projectIdentifier, identifier, Scope.PROJECT);
      setStatus(connector, ConnectivityStatus.FAILURE);
      connectorRepository.save(connector, ChangeType.ADD);
    }

    for (int i = 0; i < 6; i++) {
      Connector connector = DockerConnectorTestHelper.createDockerConnector(
          accountIdentifier, orgIdentifier, projectIdentifier, identifier, Scope.PROJECT);
      setStatus(connector, ConnectivityStatus.SUCCESS);
      connectorRepository.save(connector, ChangeType.ADD);
    }

    ConnectorStatistics connectorStatistics =
        connectorStatisticsHelper.getStats(accountIdentifier, orgIdentifier, projectIdentifier);
    assertThat(connectorStatistics).isNotNull();
    List<ConnectorTypeStats> connectorTypeStatsList = connectorStatistics.getTypeStats();
    assertThat(connectorTypeStatsList.size()).isEqualTo(3);
    Map<ConnectorType, Integer> typeCount = connectorTypeStatsList.stream().collect(
        Collectors.toMap(ConnectorTypeStats::getType, ConnectorTypeStats::getCount));
    assertThat(typeCount.get(ConnectorType.AWS)).isEqualTo(3);
    assertThat(typeCount.get(ConnectorType.DOCKER)).isEqualTo(10);
    assertThat(typeCount.get(ConnectorType.KUBERNETES_CLUSTER)).isEqualTo(5);

    List<ConnectorStatusStats> connectorStatusStatsList = connectorStatistics.getStatusStats();
    assertThat(connectorStatusStatsList.size()).isEqualTo(2);
    Map<ConnectivityStatus, Integer> statusCount = connectorStatusStatsList.stream().collect(
        Collectors.toMap(ConnectorStatusStats::getStatus, ConnectorStatusStats::getCount));
    assertThat(statusCount.get(ConnectivityStatus.SUCCESS)).isEqualTo(11);
    assertThat(statusCount.get(ConnectivityStatus.FAILURE)).isEqualTo(7);
  }

  private void setStatus(Connector connector, ConnectivityStatus status) {
    connector.setConnectivityDetails(ConnectorConnectivityDetails.builder().status(status).build());
  }
}
