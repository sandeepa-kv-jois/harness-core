/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.azureartifacts;

import io.harness.delegate.beans.connector.ConnectorTaskParams;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.delegate.task.TaskParameters;
import io.harness.expression.ExpressionEvaluator;
import io.harness.security.encryption.EncryptedDataDetail;

import java.util.List;
import lombok.Data;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
public class AzureArtifactsTestConnectionTaskParams
    extends ConnectorTaskParams implements TaskParameters, ExecutionCapabilityDemander {
  /**
   * Azure Artifacts Connector DTO
   */
  AzureArtifactsConnectorDTO azureArtifactsConnector;

  /**
   * Registry Url
   */
  String registryUrl;

  /**
   * Encryption Details
   */
  private List<EncryptedDataDetail> encryptionDetails;

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    return AzureArtifactsCapabilityHelper.fetchRequiredExecutionCapabilities(azureArtifactsConnector, maskingEvaluator);
  }
}
