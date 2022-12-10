/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.beans;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class SLIValue {
  int goodCount;
  int badCount;
  int total;
  public double sliPercentage() {
    if (total <= 0) {
      return 100.0;
    } else {
      return (goodCount * 100.0) / total;
    }
  }
}
