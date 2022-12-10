/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.approval;

import io.harness.steps.approval.step.beans.ApprovalInstanceResponseDTO;
import io.harness.steps.approval.step.beans.ApprovalType;
import io.harness.steps.approval.step.harness.beans.HarnessApprovalActivityRequestDTO;
import io.harness.steps.approval.step.harness.beans.HarnessApprovalInstanceAuthorizationDTO;

import java.io.IOException;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;

public interface ApprovalResourceService {
  ApprovalInstanceResponseDTO get(@NotNull String approvalInstanceId);
  ApprovalInstanceResponseDTO addHarnessApprovalActivity(
      @NotNull String approvalInstanceId, @NotNull @Valid HarnessApprovalActivityRequestDTO request);
  HarnessApprovalInstanceAuthorizationDTO getHarnessApprovalInstanceAuthorization(@NotNull String approvalInstanceId);
  String getYamlSnippet(ApprovalType approvalType, String accountId) throws IOException;
}
