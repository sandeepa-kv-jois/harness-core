/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans;

import static io.harness.rule.OwnerRule.NAMAN;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.encryption.Scope;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

public class IdentifierRefTest extends CategoryTest {
  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testBuildScopedIdentifier() {
    String accountIdentifier = "accId";
    String orgIdentifier = "orgId";
    String projectIdentifier = "proId";
    String identifier = "myId";
    IdentifierRef projectIR = IdentifierRef.builder()
                                  .scope(Scope.PROJECT)
                                  .accountIdentifier(accountIdentifier)
                                  .orgIdentifier(orgIdentifier)
                                  .projectIdentifier(projectIdentifier)
                                  .identifier(identifier)
                                  .build();
    assertThat(projectIR.buildScopedIdentifier()).isEqualTo("myId");
    IdentifierRef orgIR = IdentifierRef.builder()
                              .scope(Scope.ORG)
                              .accountIdentifier(accountIdentifier)
                              .orgIdentifier(orgIdentifier)
                              .identifier(identifier)
                              .build();
    assertThat(orgIR.buildScopedIdentifier()).isEqualTo("org.myId");
    IdentifierRef accIR = IdentifierRef.builder()
                              .scope(Scope.ACCOUNT)
                              .accountIdentifier(accountIdentifier)
                              .identifier(identifier)
                              .build();
    assertThat(accIR.buildScopedIdentifier()).isEqualTo("account.myId");
    IdentifierRef unknownIR = IdentifierRef.builder()
                                  .scope(Scope.UNKNOWN)
                                  .accountIdentifier(accountIdentifier)
                                  .orgIdentifier(orgIdentifier)
                                  .projectIdentifier(projectIdentifier)
                                  .identifier(identifier)
                                  .build();
    assertThat(unknownIR.buildScopedIdentifier()).isEqualTo("");
  }
}
