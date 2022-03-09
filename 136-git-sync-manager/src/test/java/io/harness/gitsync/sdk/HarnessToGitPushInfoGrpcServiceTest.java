/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.sdk;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.BHAVYA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.gitsync.FileInfo;
import io.harness.gitsync.GitSyncTestBase;
import io.harness.gitsync.common.impl.HarnessToGitHelperServiceImpl;
import io.harness.manage.GlobalContextManager;
import io.harness.rule.Owner;
import io.harness.security.Principal;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.ServicePrincipal;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.UserPrincipal;
import io.harness.security.dto.PrincipalType;

import com.google.inject.Inject;
import com.google.protobuf.StringValue;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(PL)
public class HarnessToGitPushInfoGrpcServiceTest extends GitSyncTestBase {
  private final String accountId = "accountId";
  private final String email = "email";
  private final String userName = "userName";
  private final String name = "name";
  private FileInfo fileInfo;
  private io.harness.security.dto.UserPrincipal userPrincipal;
  @Inject HarnessToGitPushInfoGrpcService harnessToGitPushInfoGrpcService;
  @Mock HarnessToGitHelperServiceImpl harnessToGitHelperService;

  @Before
  public void setup() throws Exception {
    MockitoAnnotations.initMocks(this);
    userPrincipal = new io.harness.security.dto.UserPrincipal(name, email, userName, accountId);
    when(harnessToGitHelperService.getFullSyncUser(any())).thenReturn(userPrincipal);
    FieldUtils.writeField(
        harnessToGitPushInfoGrpcService, "harnessToGitHelperService", harnessToGitHelperService, true);
  }

  @Test
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  public void testSetPrincipal() {
    UserPrincipal userPrincipal = UserPrincipal.newBuilder()
                                      .setUserId(StringValue.of(name))
                                      .setUserName(StringValue.of(userName))
                                      .setEmail(StringValue.of(email))
                                      .build();
    Principal principal = Principal.newBuilder().setUserPrincipal(userPrincipal).build();
    FileInfo fileInfo = buildFileInfo(principal, false);
    try (GlobalContextManager.GlobalContextGuard guard = GlobalContextManager.ensureGlobalContextGuard()) {
      harnessToGitPushInfoGrpcService.setPrincipal(fileInfo);
      assertThat(SecurityContextBuilder.getPrincipal().getType()).isEqualTo(PrincipalType.USER);
      assertThat(SourcePrincipalContextBuilder.getSourcePrincipal().getName()).isEqualTo(name);
    }
  }

  @Test
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  public void testSetPrincipal_forFullSync() {
    ServicePrincipal servicePrincipal = ServicePrincipal.newBuilder().setName("Full_Sync").build();
    Principal principal = Principal.newBuilder().setServicePrincipal(servicePrincipal).build();
    FileInfo fileInfo = buildFileInfo(principal, true);
    try (GlobalContextManager.GlobalContextGuard guard = GlobalContextManager.ensureGlobalContextGuard()) {
      harnessToGitPushInfoGrpcService.setPrincipal(fileInfo);
      assertThat(SecurityContextBuilder.getPrincipal().getType()).isEqualTo(PrincipalType.USER);
      assertThat(SourcePrincipalContextBuilder.getSourcePrincipal().getName()).isEqualTo(name);
    }
  }

  private FileInfo buildFileInfo(Principal principal, boolean isFullSyncFlow) {
    fileInfo =
        FileInfo.newBuilder().setAccountId(accountId).setPrincipal(principal).setIsFullSyncFlow(isFullSyncFlow).build();
    return fileInfo;
  }
}
