/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cd.timescale;

import static io.harness.rule.OwnerRule.VIKYATH_HAREKAL;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.timescaledb.retention.RetentionManager;

import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class CDRetentionHandlerTest {
  private static final String SEVEN_MONTHS = "7 months";
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock RetentionManager retentionManager;
  private CDRetentionHandler retentionHandler;

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testConfigureRetentionPolicy() {
    retentionHandler = new CDRetentionHandler(SEVEN_MONTHS, retentionManager);
    retentionHandler.configureRetentionPolicy();
    verify(retentionManager).addPolicy(eq("instance_stats"), eq(SEVEN_MONTHS));
    verify(retentionManager).addPolicy(eq("instance_stats_day"), eq(SEVEN_MONTHS));
    verify(retentionManager).addPolicy(eq("instance_stats_hour"), eq(SEVEN_MONTHS));
  }
}
