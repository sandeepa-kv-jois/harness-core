/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.alert;

import io.harness.alert.AlertData;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;

@TargetModule(HarnessModule._955_ALERT_BEANS)
@OwnedBy(HarnessTeam.DEL)
public class DeprecatedDelegateAlert implements AlertData {
  @Override
  public boolean matches(AlertData alertData) {
    return false;
  }

  @Override
  public String buildTitle() {
    return "dummy";
  }
}
