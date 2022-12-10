/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.ssh;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.azureconnector.AzureConnectorDTO;
import io.harness.ng.core.dto.secrets.WinRmCredentialsSpecDTO;
import io.harness.security.encryption.EncryptedDataDetail;

import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Builder;
import lombok.Getter;

@Getter
@OwnedBy(HarnessTeam.CDP)
public class AzureWinrmInfraDelegateConfig extends AzureInfraDelegateConfig implements WinRmInfraDelegateConfig {
  Set<String> hosts;
  List<EncryptedDataDetail> encryptionDataDetails;
  WinRmCredentialsSpecDTO winRmCredentials;

  @Builder(builderMethodName = "winrmAzureBuilder")
  public AzureWinrmInfraDelegateConfig(AzureConnectorDTO azureConnectorDTO,
      List<EncryptedDataDetail> connectorEncryptionDataDetails, String subscriptionId, String resourceGroup,
      Map<String, String> tags, List<EncryptedDataDetail> encryptionDataDetails,
      WinRmCredentialsSpecDTO winRmCredentials, String hostConnectionType) {
    super(azureConnectorDTO, connectorEncryptionDataDetails, subscriptionId, resourceGroup, tags, "WINDOWS",
        hostConnectionType);
    this.encryptionDataDetails = encryptionDataDetails;
    this.winRmCredentials = winRmCredentials;
  }
}
