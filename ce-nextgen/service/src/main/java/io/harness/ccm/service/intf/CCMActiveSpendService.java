/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.service.intf;

import io.harness.ccm.remote.beans.CostOverviewDTO;

public interface CCMActiveSpendService {
  CostOverviewDTO getActiveSpendStats(long startTime, long endTime, String accountIdentifier);
  CostOverviewDTO getForecastedSpendStats(long startTime, long endTime, String accountIdentifier);
  Long getActiveSpend(long startTime, long endTime, String accountIdentifier);
  Long getActiveSpendForPreviousPeriod(long startTime, long endTime, String accountIdentifier);
}