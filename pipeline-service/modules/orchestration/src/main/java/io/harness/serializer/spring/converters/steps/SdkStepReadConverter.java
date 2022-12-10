/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.serializer.spring.converters.steps;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.steps.SdkStep;
import io.harness.serializer.spring.ProtoReadConverter;

@OwnedBy(HarnessTeam.PIPELINE)
public class SdkStepReadConverter extends ProtoReadConverter<SdkStep> {
  public SdkStepReadConverter() {
    super(SdkStep.class);
  }
}
