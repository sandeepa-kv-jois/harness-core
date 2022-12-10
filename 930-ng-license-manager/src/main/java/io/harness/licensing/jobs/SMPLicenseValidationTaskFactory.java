/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.licensing.jobs;

import io.harness.smp.license.models.SMPLicense;

import com.google.inject.assistedinject.Assisted;
import java.util.function.Function;

public interface SMPLicenseValidationTaskFactory {
  SMPLicenseValidationTask create(@Assisted("accountId") String accountIdentifier,
      @Assisted("licenseSign") String licenseSign,
      @Assisted("licenseProvider") Function<String, SMPLicense> licenseProvider);
}
