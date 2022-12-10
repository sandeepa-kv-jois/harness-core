/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.artifacts.gar.service;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.artifacts.beans.BuildDetailsInternal;
import io.harness.artifacts.gar.beans.GarInternalConfig;

import java.util.List;

@OwnedBy(HarnessTeam.CDC)
public interface GarApiService {
  List<BuildDetailsInternal> getBuilds(GarInternalConfig garinternalConfig, String versionRegex, int maxNumberOfBuilds);
  BuildDetailsInternal getLastSuccessfulBuildFromRegex(GarInternalConfig garinternalConfig, String versionRegex);

  BuildDetailsInternal verifyBuildNumber(GarInternalConfig garInternalConfig, String version);
}
