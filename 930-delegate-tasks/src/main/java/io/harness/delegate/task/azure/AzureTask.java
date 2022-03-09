/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.azure;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.connector.azureconnector.AzureTaskParams;
import io.harness.delegate.beans.connector.azureconnector.AzureTaskType;
import io.harness.delegate.beans.connector.azureconnector.AzureValidateTaskResponse;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.AbstractDelegateRunnableTask;
import io.harness.delegate.task.TaskParameters;
import io.harness.exception.InvalidRequestException;

import com.google.inject.Inject;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

@OwnedBy(HarnessTeam.CDP)
public class AzureTask extends AbstractDelegateRunnableTask {
  @Inject private AzureNgHelper azureNgHelper;

  public AzureTask(DelegateTaskPackage delegateTaskPackage, ILogStreamingTaskClient logStreamingTaskClient,
      Consumer<DelegateTaskResponse> consumer, BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  public DelegateResponseData run(Object[] parameters) {
    throw new UnsupportedOperationException("Object Array parameters not supported");
  }

  @Override
  public DelegateResponseData run(TaskParameters parameters) {
    if (!(parameters instanceof AzureTaskParams)) {
      throw new InvalidRequestException("Task Params are not of expected type: AzureTaskParameters");
    }
    AzureTaskParams azureTaskParams = (AzureTaskParams) parameters;
    if (azureTaskParams.getAzureTaskType() == AzureTaskType.VALIDATE) {
      return AzureValidateTaskResponse.builder()
          .connectorValidationResult(azureNgHelper.getConnectorValidationResult(
              azureTaskParams.getEncryptionDetails(), azureTaskParams.getAzureConnector()))
          .build();
    } else {
      throw new InvalidRequestException("Task type not identified");
    }
  }
}
