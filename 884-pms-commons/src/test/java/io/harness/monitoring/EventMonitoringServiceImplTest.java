/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.monitoring;

import static io.harness.rule.OwnerRule.GARVIT;

import static org.mockito.Matchers.anyDouble;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.harness.PmsCommonsTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.metrics.service.api.MetricService;
import io.harness.rule.Owner;

import java.util.Collections;
import java.util.HashMap;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
@RunWith(MockitoJUnitRunner.class)
public class EventMonitoringServiceImplTest extends PmsCommonsTestBase {
  @Mock private MetricService metricService;
  @InjectMocks private EventMonitoringServiceImpl eventMonitoringService;

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testSendMetric() {
    String metricName = "m";
    eventMonitoringService.sendMetric(metricName, null, new HashMap<>());
    verify(metricService, never()).recordMetric(anyString(), anyDouble());
    eventMonitoringService.sendMetric(metricName, null, Collections.emptyMap());
  }
}
