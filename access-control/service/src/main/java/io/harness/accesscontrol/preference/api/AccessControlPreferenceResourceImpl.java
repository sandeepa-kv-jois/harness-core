/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.preference.api;

import io.harness.accesscontrol.preference.services.AccessControlPreferenceService;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.dto.ResponseDTO;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import javax.validation.executable.ValidateOnExecution;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@ValidateOnExecution
@Singleton
@Slf4j
@OwnedBy(HarnessTeam.PL)
public class AccessControlPreferenceResourceImpl implements AccessControlPreferenceResource {
  private final AccessControlPreferenceService accessControlPreferenceService;

  @Override
  public ResponseDTO<Boolean> upsertAccessControlPreference(String accountIdentifier, boolean enabled) {
    return ResponseDTO.newResponse(
        accessControlPreferenceService.upsertAccessControlEnabled(accountIdentifier, enabled));
  }
}
