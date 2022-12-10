/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.freeze.beans.response;

import io.harness.encryption.Scope;
import io.harness.freeze.beans.CurrentOrUpcomingWindow;
import io.harness.freeze.beans.FreezeWindow;

import java.util.List;
import javax.validation.constraints.NotEmpty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FreezeBannerDetails {
  CurrentOrUpcomingWindow window;
  List<FreezeWindow> windows;
  String identifier;
  String name;
  String orgIdentifier;
  String projectIdentifier;
  Scope freezeScope;
  @NotEmpty String accountId;
}
