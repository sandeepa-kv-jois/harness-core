/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.connector.helper;

import io.harness.beans.DecryptableEntity;
import io.harness.beans.FeatureName;
import io.harness.ng.core.BaseNGAccess;
import io.harness.ng.core.NGAccess;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.utils.NGFeatureFlagHelperService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.List;

@Singleton
public class EncryptionHelper {
  @Inject private SecretManagerClientService ngSecretService;
  @Inject @Named("PRIVILEGED") private SecretManagerClientService ngSecretServicePrivileged;
  @Inject private NGFeatureFlagHelperService ngFeatureFlagHelperService;

  public List<EncryptedDataDetail> getEncryptionDetail(
      DecryptableEntity decryptableEntity, String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    if (decryptableEntity == null) {
      return null;
    }
    NGAccess basicNGAccessObject = BaseNGAccess.builder()
                                       .accountIdentifier(accountIdentifier)
                                       .orgIdentifier(orgIdentifier)
                                       .projectIdentifier(projectIdentifier)
                                       .build();
    if (ngFeatureFlagHelperService.isEnabled(accountIdentifier, FeatureName.PL_CONNECTOR_ENCRYPTION_PRIVILEGED_CALL)) {
      return ngSecretServicePrivileged.getEncryptionDetails(basicNGAccessObject, decryptableEntity);
    }
    return ngSecretService.getEncryptionDetails(basicNGAccessObject, decryptableEntity);
  }
}
