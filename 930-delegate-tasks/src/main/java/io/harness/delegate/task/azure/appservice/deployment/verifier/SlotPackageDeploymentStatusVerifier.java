/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.azure.appservice.deployment.verifier;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.task.azure.appservice.deployment.StreamPackageDeploymentLogsTask;
import io.harness.delegate.task.azure.appservice.deployment.context.SlotDeploymentVerifierContext;

@OwnedBy(CDP)
public class SlotPackageDeploymentStatusVerifier extends SlotStatusVerifier {
  private final StreamPackageDeploymentLogsTask logStreamer;

  public SlotPackageDeploymentStatusVerifier(SlotDeploymentVerifierContext context) {
    super(context.getLogCallback(), context.getSlotName(), context.getAzureWebClient(),
        context.getAzureWebClientContext(), context.getResponseMono());
    this.logStreamer = context.getLogStreamer();
  }

  @Override
  public boolean hasReachedSteadyState() {
    return logStreamer == null || logStreamer.operationCompleted();
  }
  @Override
  public String getSteadyState() {
    return null;
  }

  @Override
  public void stopPolling() {
    logStreamer.unsubscribe();
  }
}
