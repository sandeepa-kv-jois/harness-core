/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.utils;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import software.wings.app.MainConfiguration;
import software.wings.jre.JreConfig;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.DEL)
@Slf4j
@Singleton
public class DelegateJreVersionHelper {
  @Inject private MainConfiguration mainConfiguration;
  /**
   *  Returns delegate's JRE version
   *
   * @return
   */
  public String getTargetJreVersion() {
    final String jreVersion = mainConfiguration.getMigrateToJre();
    JreConfig jreConfig = mainConfiguration.getJreConfigs().get(jreVersion);
    return jreConfig.getVersion();
  }
}
