/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.template.health;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.rule.OwnerRule.ARCHIT;

import static org.assertj.core.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.health.HealthException;
import io.harness.health.HealthService;
import io.harness.maintenance.MaintenanceController;
import io.harness.rule.Owner;

import com.codahale.metrics.health.HealthCheck;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@OwnedBy(CDC)
@RunWith(PowerMockRunner.class)
@PrepareForTest(MaintenanceController.class)
public class HealthResourceTest extends CategoryTest {
  private HealthResource healthResource;
  private HealthService healthService;

  @Before
  public void setup() {
    healthService = mock(HealthService.class);
    healthResource = new HealthResource(healthService);
    Mockito.mockStatic(MaintenanceController.class);
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testGet_success() throws Exception {
    when(MaintenanceController.getMaintenanceFlag()).thenReturn(false);
    when(healthService.check()).thenReturn(HealthCheck.Result.healthy());
    String healthResponse = healthResource.get().getData();
    Assertions.assertThat(healthResponse).isNotNull();
    Assertions.assertThat(healthResponse).isEqualTo("healthy");
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testGet_failure_due_to_monitor() throws Exception {
    when(healthService.check()).thenReturn(HealthCheck.Result.unhealthy("DB down"));
    try {
      String healthResponse = healthResource.get().getData();
      fail("Execution should not reach here");
    } catch (HealthException healthException) {
      // not required
    }
  }
}
