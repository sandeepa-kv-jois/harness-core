/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.utils;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.BHAVYA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SortOrder.OrderType;
import io.harness.category.element.UnitTests;
import io.harness.ng.beans.PageRequest;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PL)
public class PaginationUtilsTest extends CategoryTest {
  private static final PageRequest pageRequest = PageRequest.builder().build();
  private static final String fieldName = "field";
  private static final OrderType orderType = OrderType.DESC;

  @Test
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  public void testPageSorting() {
    PaginationUtils.setSortOrder(pageRequest, fieldName, orderType);
    assertThat(pageRequest.getSortOrders().get(0).getFieldName()).isEqualTo(fieldName);
    assertThat(pageRequest.getSortOrders().get(0).getOrderType()).isEqualTo(orderType);
  }
}
