/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.governance.faktory;

/**
 * Faktory JobType
 * @author Sikandar Ali Awan
 */
public class JobType {
  String name;

  private JobType(String name) {
    this.name = name;
  }

  public static JobType of(String name) {
    return new JobType(name);
  }
}
