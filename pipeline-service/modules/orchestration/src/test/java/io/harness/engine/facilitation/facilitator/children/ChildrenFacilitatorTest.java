/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.facilitation.facilitator.children;

import static io.harness.pms.contracts.execution.ExecutionMode.CHILDREN;
import static io.harness.rule.OwnerRule.PRASHANT;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.OrchestrationTestBase;
import io.harness.category.element.UnitTests;
import io.harness.engine.facilitation.facilitator.chilidren.ChildrenFacilitator;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.facilitators.FacilitatorResponseProto;
import io.harness.pms.execution.facilitator.DefaultFacilitatorParams;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;

import com.google.inject.Inject;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ChildrenFacilitatorTest extends OrchestrationTestBase {
  @Inject private ChildrenFacilitator childrenFacilitator;
  @Inject private KryoSerializer kryoSerializer;

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestFacilitate() {
    Ambiance ambiance = Ambiance.newBuilder().build();
    byte[] parameters = kryoSerializer.asBytes(DefaultFacilitatorParams.builder().build());
    FacilitatorResponseProto response = childrenFacilitator.facilitate(ambiance, parameters);
    assertThat(response).isNotNull();
    assertThat(response.getExecutionMode()).isEqualTo(CHILDREN);
    assertThat(response.getInitialWait().getSeconds()).isEqualTo(0);
  }
}
