/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.expressions.functors;

import static io.harness.rule.OwnerRule.IVAN;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.instance.outcome.HostOutcome;
import io.harness.cdng.instance.outcome.InstanceOutcome;
import io.harness.cdng.instance.outcome.InstancesOutcome;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.ForMetadata;
import io.harness.pms.contracts.execution.StrategyMetadata;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.rule.Owner;

import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(HarnessTeam.CDP)
@RunWith(MockitoJUnitRunner.class)
public class InstanceFunctorTest extends CategoryTest {
  @Mock private ExecutionSweepingOutputService sweepingOutputService;

  @InjectMocks private InstanceFunctor instanceFunctor;

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGet() {
    Level level = Level.newBuilder()
                      .setIdentifier("CommandStep")
                      .setStrategyMetadata(StrategyMetadata.newBuilder()
                                               .setForMetadata(ForMetadata.newBuilder().setValue("hostName").build())
                                               .setCurrentIteration(1)
                                               .setTotalIterations(1)
                                               .build())
                      .setGroup(StepOutcomeGroup.STEP.name())
                      .build();
    Ambiance ambiance = Ambiance.newBuilder().addLevels(level).build();
    when(sweepingOutputService.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder().found(true).output(getInstancesOutcome()).build());

    assertThatThrownBy(() -> instanceFunctor.get(ambiance, "name"))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Not found step level repeat strategy item");
  }

  private InstancesOutcome getInstancesOutcome() {
    return InstancesOutcome.builder()
        .instances(List.of(InstanceOutcome.builder()
                               .name("instanceName")
                               .hostName("instanceHostname")
                               .host(HostOutcome.builder()
                                         .hostName("instanceHostname")
                                         .publicIp("publicIp")
                                         .privateIp("privateIp")
                                         .build())
                               .build()))
        .build();
  }
}
