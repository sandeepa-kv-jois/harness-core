/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.beans;

import static io.harness.cvng.servicelevelobjective.beans.SLIMissingDataType.BAD;
import static io.harness.cvng.servicelevelobjective.beans.SLIMissingDataType.GOOD;
import static io.harness.cvng.servicelevelobjective.beans.SLIMissingDataType.IGNORE;
import static io.harness.rule.OwnerRule.KAMAL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

public class SLIMissingDataTypeTest extends CategoryTest {
  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testCalculateSLIValue_allEnumsAreHandled() {
    for (SLIMissingDataType sliMissingDataType : SLIMissingDataType.values()) {
      assertThat(sliMissingDataType.calculateSLIValue(1, 2, 3, 0)).isNotNull(); // TO check if exception is not thrown.
    }
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testCalculateSLIValue_testActualCalculationForDifferentEnums() {
    assertThat(GOOD.calculateSLIValue(1, 2, 5, 0).sliPercentage()).isCloseTo(300.0 / 5.0, offset(.001));
    assertThat(BAD.calculateSLIValue(1, 2, 5, 0).sliPercentage()).isCloseTo(100.0 / 5.0, offset(.001));
    assertThat(IGNORE.calculateSLIValue(1, 2, 5, 0).sliPercentage()).isCloseTo(100.0 / 3.0, offset(.001));
    assertThat(IGNORE.calculateSLIValue(0, 0, 5, 0).sliPercentage()).isCloseTo(100.0, offset(.001));
  }
}
