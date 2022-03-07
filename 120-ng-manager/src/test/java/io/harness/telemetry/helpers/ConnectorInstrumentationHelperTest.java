/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.telemetry.helpers;

import static io.harness.delegate.beans.connector.ConnectorType.KUBERNETES_CLUSTER;
import static io.harness.delegate.beans.connector.k8Connector.KubernetesCredentialType.MANUAL_CREDENTIALS;
import static io.harness.rule.OwnerRule.TEJAS;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesAuthDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesAuthType;
import io.harness.delegate.beans.connector.k8Connector.KubernetesClusterConfigDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesClusterDetailsDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesCredentialDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesUserNamePasswordDTO;
import io.harness.encryption.SecretRefData;
import io.harness.rule.Owner;
import io.harness.telemetry.TelemetryReporter;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PL)
public class ConnectorInstrumentationHelperTest {
  @InjectMocks ConnectorInstrumentationHelper instrumentationHelper;
  @Mock TelemetryReporter telemetryReporter;

  String userName = "userName";
  String masterUrl = "https://abc.com";
  String identifier = "identifier";
  String name = "name";
  String accountIdentifier = "accountIdentifier";
  String projectIdentifier = "projectIdentifier";
  String orgIdentifier = "orgIdentifier";
  String connectorIdentifier = "connectorIdentifier";
  SecretRefData passwordSecretRef;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  private ConnectorDTO createKubernetesConnectorRequestDTO(String connectorIdentifier, String name) {
    KubernetesAuthDTO kubernetesAuthDTO =
        KubernetesAuthDTO.builder()
            .authType(KubernetesAuthType.USER_PASSWORD)
            .credentials(
                KubernetesUserNamePasswordDTO.builder().username(userName).passwordRef(passwordSecretRef).build())
            .build();
    KubernetesCredentialDTO connectorDTOWithDelegateCreds =
        KubernetesCredentialDTO.builder()
            .kubernetesCredentialType(MANUAL_CREDENTIALS)
            .config(KubernetesClusterDetailsDTO.builder().masterUrl(masterUrl).auth(kubernetesAuthDTO).build())
            .build();
    KubernetesClusterConfigDTO k8sClusterConfig =
        KubernetesClusterConfigDTO.builder().credential(connectorDTOWithDelegateCreds).build();
    ConnectorInfoDTO connectorInfo = ConnectorInfoDTO.builder()
                                         .name(name)
                                         .identifier(connectorIdentifier)
                                         .connectorType(KUBERNETES_CLUSTER)
                                         .connectorConfig(k8sClusterConfig)
                                         .build();
    return ConnectorDTO.builder().connectorInfo(connectorInfo).build();
  }

  private ConnectorResponseDTO createConnector(String connectorIdentifier, String name) {
    ConnectorDTO connectorRequestDTO = createKubernetesConnectorRequestDTO(connectorIdentifier, name);

    return ConnectorResponseDTO.builder().connector(connectorRequestDTO.getConnectorInfo()).build();
  }

  @Test
  @Owner(developers = TEJAS)
  @Category(UnitTests.class)
  public void testCreateConnectorTrackSend() {
    ConnectorResponseDTO connectorDTOOutput = createConnector(identifier, name);
    instrumentationHelper.sendConnectorCreateEvent(connectorDTOOutput.getConnector(), accountIdentifier);
    try {
      verify(telemetryReporter, times(1)).sendTrackEvent(any(), any(), any(), any(), any());
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @Test
  @Owner(developers = TEJAS)
  @Category(UnitTests.class)
  public void testDeleteConnectorTrackSend() {
    instrumentationHelper.sendConnectorDeleteEvent(
        orgIdentifier, projectIdentifier, connectorIdentifier, accountIdentifier);
    try {
      verify(telemetryReporter, times(1)).sendTrackEvent(any(), any(), any(), any(), any());
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
