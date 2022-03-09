/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.azure;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.azure.AzureEnvironmentType;
import io.harness.azure.client.AzureManagementClient;
import io.harness.connector.ConnectivityStatus;
import io.harness.connector.ConnectorValidationResult;
import io.harness.delegate.beans.connector.azureconnector.AzureConnectorDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureCredentialDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureManualDetailsDTO;
import io.harness.errorhandling.NGErrorHelper;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.SecretDecryptionService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Collections;
import java.util.List;

@OwnedBy(HarnessTeam.CDP)
@Singleton
public class AzureNgHelper {
  @Inject private SecretDecryptionService secretDecryptionService;
  @Inject private AzureManagementClient azureManagementClient;
  @Inject private NGErrorHelper ngErrorHelper;

  public ConnectorValidationResult getConnectorValidationResult(
      List<EncryptedDataDetail> encryptedDataDetails, AzureConnectorDTO connectorDTO) {
    ConnectorValidationResult connectorValidationResult;
    try {
      switch (connectorDTO.getCredential().getAzureCredentialType()) {
        case INHERIT_FROM_DELEGATE:
          throw new IllegalStateException(
              "Credential type not supported yet: " + connectorDTO.getCredential().getAzureCredentialType());
        case MANUAL_CREDENTIALS:
          handleValidateManualCredentialsTask(
              connectorDTO.getCredential(), encryptedDataDetails, connectorDTO.getAzureEnvironmentType());
          break;
        default:
          throw new IllegalStateException(
              "Unexpected credential type : " + connectorDTO.getCredential().getAzureCredentialType());
      }
      connectorValidationResult = ConnectorValidationResult.builder()
                                      .status(ConnectivityStatus.SUCCESS)
                                      .testedAt(System.currentTimeMillis())
                                      .build();
    } catch (Exception e) {
      String errorMessage = e.getMessage();
      connectorValidationResult = ConnectorValidationResult.builder()
                                      .status(ConnectivityStatus.FAILURE)
                                      .errors(Collections.singletonList(ngErrorHelper.createErrorDetail(errorMessage)))
                                      .errorSummary(ngErrorHelper.getErrorSummary(errorMessage))
                                      .testedAt(System.currentTimeMillis())
                                      .build();
    }
    return connectorValidationResult;
  }

  private void handleValidateManualCredentialsTask(AzureCredentialDTO credential,
      List<EncryptedDataDetail> encryptedDataDetails, AzureEnvironmentType azureEnvironmentType) {
    AzureManualDetailsDTO azureConfig = (AzureManualDetailsDTO) credential.getConfig();
    secretDecryptionService.decrypt(azureConfig, encryptedDataDetails);
    String secret = String.valueOf(azureConfig.getSecretRef().getDecryptedValue());
    switch (azureConfig.getSecretType()) {
      case SECRET_KEY:
        azureManagementClient.validateAzureConnection(
            azureConfig.getClientId(), azureConfig.getTenantId(), secret, azureEnvironmentType);
        break;
      case KEY_CERT:
        azureManagementClient.validateAzureConnectionWithCert(
            azureConfig.getClientId(), azureConfig.getTenantId(), secret.getBytes(), azureEnvironmentType);
        break;
      default:
        throw new IllegalStateException("Unexpected secret type : " + azureConfig.getSecretType());
    }
  }
}
