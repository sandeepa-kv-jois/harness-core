/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.connector.mappers.azuremapper;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.entities.embedded.azureconnector.AzureConfig;
import io.harness.connector.entities.embedded.azureconnector.AzureManualCredential;
import io.harness.connector.mappers.ConnectorEntityToDTOMapper;
import io.harness.delegate.beans.connector.azureconnector.AzureConnectorDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureCredentialDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureCredentialType;
import io.harness.delegate.beans.connector.azureconnector.AzureManualDetailsDTO;
import io.harness.encryption.SecretRefData;
import io.harness.encryption.SecretRefHelper;
import io.harness.exception.InvalidRequestException;

import com.google.inject.Singleton;

@OwnedBy(HarnessTeam.CDP)
@Singleton
public class AzureEntityToDTO implements ConnectorEntityToDTOMapper<AzureConnectorDTO, AzureConfig> {
  @Override
  public AzureConnectorDTO createConnectorDTO(AzureConfig connector) {
    final AzureCredentialType credentialType = connector.getCredentialType();
    switch (credentialType) {
      case INHERIT_FROM_DELEGATE:
        return buildInheritFromDelegate(connector);
      case MANUAL_CREDENTIALS:
        return buildManualCredential(connector);
      default:
        throw new InvalidRequestException("Invalid Credential type.");
    }
  }

  private AzureConnectorDTO buildManualCredential(AzureConfig connector) {
    final AzureManualCredential auth = (AzureManualCredential) connector.getCredential();
    final SecretRefData secretRef = SecretRefHelper.createSecretRef(auth.getSecretKeyRef());
    final AzureManualDetailsDTO azureManualDetailsDTO = AzureManualDetailsDTO.builder()
                                                            .clientId(auth.getClientId())
                                                            .secretRef(secretRef)
                                                            .tenantId(auth.getTenantId())
                                                            .secretType(auth.getAzureSecretType())
                                                            .build();
    return AzureConnectorDTO.builder()
        .delegateSelectors(connector.getDelegateSelectors())
        .azureEnvironmentType(connector.getAzureEnvironmentType())
        .credential(AzureCredentialDTO.builder()
                        .azureCredentialType(AzureCredentialType.MANUAL_CREDENTIALS)
                        .config(azureManualDetailsDTO)
                        .build())
        .build();
  }

  private AzureConnectorDTO buildInheritFromDelegate(AzureConfig connector) {
    return AzureConnectorDTO.builder()
        .delegateSelectors(connector.getDelegateSelectors())
        .azureEnvironmentType(connector.getAzureEnvironmentType())
        .credential(AzureCredentialDTO.builder()
                        .azureCredentialType(AzureCredentialType.INHERIT_FROM_DELEGATE)
                        .config(null)
                        .build())
        .build();
  }
}
