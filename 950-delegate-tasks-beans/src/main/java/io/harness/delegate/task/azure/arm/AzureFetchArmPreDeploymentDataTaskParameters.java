/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.azure.arm;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;
import io.harness.delegate.beans.connector.azureconnector.AzureConnectorDTO;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.security.encryption.EncryptedDataDetail;

import java.util.ArrayList;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.tuple.Pair;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@OwnedBy(CDP)
public class AzureFetchArmPreDeploymentDataTaskParameters extends AzureResourceCreationTaskNGParameters {
  String subscriptionId;
  String resourceGroupName;

  @Builder
  public AzureFetchArmPreDeploymentDataTaskParameters(String accountId, AzureARMTaskType taskType,
      AzureConnectorDTO connectorDTO, String subscriptionId, String resourceGroupName,
      @NotNull List<EncryptedDataDetail> encryptedDataDetails, long timeoutInMs,
      CommandUnitsProgress commandUnitsProgress) {
    super(accountId, taskType, connectorDTO, encryptedDataDetails, timeoutInMs, commandUnitsProgress);
    this.subscriptionId = subscriptionId;
    this.resourceGroupName = resourceGroupName;
  }

  @Override
  public List<Pair<DecryptableEntity, List<EncryptedDataDetail>>> fetchDecryptionDetails() {
    List<Pair<DecryptableEntity, List<EncryptedDataDetail>>> decryptionDetails = new ArrayList<>();
    List<DecryptableEntity> decryptableEntities = this.azureConnectorDTO.getDecryptableEntities();

    decryptableEntities.forEach(
        decryptableEntity -> decryptionDetails.add(Pair.of(decryptableEntity, encryptedDataDetails)));
    return decryptionDetails;
  }
}
