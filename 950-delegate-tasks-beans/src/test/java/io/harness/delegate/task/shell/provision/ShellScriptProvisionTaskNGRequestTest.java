/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.shell.provision;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.TMACARI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.task.shell.provisioner.ShellScriptProvisionTaskNGRequest;
import io.harness.expression.ExpressionEvaluator;
import io.harness.rule.Owner;

import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(CDP)
public class ShellScriptProvisionTaskNGRequestTest extends CategoryTest {
  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testFetchRequiredExecutionCapabilities() {
    ShellScriptProvisionTaskNGRequest request =
        ShellScriptProvisionTaskNGRequest.builder().accountId("accountId").build();

    List<ExecutionCapability> capabilities =
        request.fetchRequiredExecutionCapabilities(mock(ExpressionEvaluator.class));

    assertThat(capabilities).isEmpty();
  }
}
