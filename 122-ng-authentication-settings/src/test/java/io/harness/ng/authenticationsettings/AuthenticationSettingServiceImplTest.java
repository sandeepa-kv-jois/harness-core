/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.authenticationsettings;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.PRATEEK;
import static io.harness.rule.OwnerRule.VIKAS_M;

import static junit.framework.TestCase.assertNotNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.authenticationsettings.dtos.mechanisms.LDAPSettings;
import io.harness.ng.authenticationsettings.impl.AuthenticationSettingsServiceImpl;
import io.harness.ng.authenticationsettings.remote.AuthSettingsManagerClient;
import io.harness.ng.core.api.UserGroupService;
import io.harness.ng.core.user.entities.UserGroup;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;

import software.wings.beans.sso.LdapConnectionSettings;
import software.wings.beans.sso.SamlSettings;
import software.wings.security.authentication.SSOConfig;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(PL)
public class AuthenticationSettingServiceImplTest extends CategoryTest {
  @Mock private AuthSettingsManagerClient managerClient;
  @Mock private UserGroupService userGroupService;
  @Inject @InjectMocks AuthenticationSettingsServiceImpl authenticationSettingsServiceImpl;

  private SamlSettings samlSettings;
  private static final String ACCOUNT_ID = "ACCOUNT_ID";

  @Before
  public void setup() {
    initMocks(this);
    samlSettings = SamlSettings.builder().accountId(ACCOUNT_ID).build();
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void testDeleteSamlMetadata() throws IOException {
    Call<RestResponse<SamlSettings>> request = mock(Call.class);
    doReturn(request).when(managerClient).getSAMLMetadata(ACCOUNT_ID);
    RestResponse<SamlSettings> mockResponse = new RestResponse<>(samlSettings);
    doReturn(Response.success(mockResponse)).when(request).execute();
    List<String> userGroups = new ArrayList<>();
    doReturn(userGroups).when(userGroupService).getUserGroupsBySsoId(ACCOUNT_ID, samlSettings.getUuid());
    SSOConfig ssoConfig = SSOConfig.builder().accountId(ACCOUNT_ID).build();
    Call<RestResponse<SSOConfig>> config = mock(Call.class);
    doReturn(config).when(managerClient).deleteSAMLMetadata(ACCOUNT_ID);
    RestResponse<SSOConfig> mockConfig = new RestResponse<>(ssoConfig);
    doReturn(Response.success(mockConfig)).when(config).execute();
    SSOConfig expectedConfig = authenticationSettingsServiceImpl.deleteSAMLMetadata(ACCOUNT_ID);
    assertThat(expectedConfig.getAccountId()).isEqualTo(ACCOUNT_ID);
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void testDeleteSamlMetadata_InvalidSSO_throwsException() throws IOException {
    Call<RestResponse<SamlSettings>> request = mock(Call.class);
    doReturn(request).when(managerClient).getSAMLMetadata(ACCOUNT_ID);
    RestResponse<SamlSettings> mockResponse = new RestResponse<>(null);
    doReturn(Response.success(mockResponse)).when(request).execute();
    try {
      authenticationSettingsServiceImpl.deleteSAMLMetadata(ACCOUNT_ID);
      fail("Deleting SAML metadata should fail.");
    } catch (InvalidRequestException e) {
      assertThat(e.getMessage()).isEqualTo("No Saml Metadata found for this account");
    }
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void testDeleteSamlMetadata_WithExistingUserGroupsLinked_throwsException() throws IOException {
    Call<RestResponse<SamlSettings>> request = mock(Call.class);
    doReturn(request).when(managerClient).getSAMLMetadata(ACCOUNT_ID);
    RestResponse<SamlSettings> mockResponse = new RestResponse<>(samlSettings);
    doReturn(Response.success(mockResponse)).when(request).execute();
    List<String> userGroups = Collections.singletonList("userGroup1");
    doReturn(userGroups).when(userGroupService).getUserGroupsBySsoId(ACCOUNT_ID, samlSettings.getUuid());
    try {
      authenticationSettingsServiceImpl.deleteSAMLMetadata(ACCOUNT_ID);
      fail("Deleting SAML metadata should fail.");
    } catch (InvalidRequestException e) {
      assertThat(e.getMessage())
          .isEqualTo("Deleting Saml provider with linked user groups is not allowed. Unlink the user groups first");
    }
  }

  @Test
  @Owner(developers = PRATEEK)
  @Category(UnitTests.class)
  public void testGetLdapSettings() throws IOException {
    final String displayName = "NG_LDAP";
    Call<RestResponse<software.wings.beans.sso.LdapSettings>> request = mock(Call.class);
    doReturn(request).when(managerClient).getLdapSettings(ACCOUNT_ID);
    RestResponse<software.wings.beans.sso.LdapSettings> mockResponse =
        new RestResponse<>(software.wings.beans.sso.LdapSettings.builder()
                               .displayName(displayName)
                               .connectionSettings(new LdapConnectionSettings())
                               .build());
    doReturn(Response.success(mockResponse)).when(request).execute();
    LDAPSettings ngLdapSettings = authenticationSettingsServiceImpl.getLdapSettings(ACCOUNT_ID);
    assertNotNull(ngLdapSettings);
    assertThat(ngLdapSettings.getDisplayName()).isEqualTo(displayName);
  }

  @Test
  @Owner(developers = PRATEEK)
  @Category(UnitTests.class)
  public void testCreateLdapSettings() throws IOException {
    final String displayName = "NG_LDAP";
    final String cronExpr = "0 0/30 * 1/1 * ? *";
    LDAPSettings settings = LDAPSettings.builder()
                                .displayName(displayName)
                                .connectionSettings(new LdapConnectionSettings())
                                .userSettingsList(new ArrayList<>())
                                .groupSettingsList(new ArrayList<>())
                                .cronExpression(cronExpr)
                                .build();
    Call<RestResponse<software.wings.beans.sso.LdapSettings>> request = mock(Call.class);
    doReturn(request).when(managerClient).createLdapSettings(anyString(), any());
    software.wings.beans.sso.LdapSettings builtSettings = software.wings.beans.sso.LdapSettings.builder()
                                                              .displayName(displayName)
                                                              .accountId(ACCOUNT_ID)
                                                              .connectionSettings(new LdapConnectionSettings())
                                                              .build();
    builtSettings.setCronExpression(cronExpr);
    RestResponse<software.wings.beans.sso.LdapSettings> mockResponse = new RestResponse<>(builtSettings);
    doReturn(Response.success(mockResponse)).when(request).execute();
    LDAPSettings createdLDAPSettings = authenticationSettingsServiceImpl.createLdapSettings(ACCOUNT_ID, settings);
    assertNotNull(createdLDAPSettings);
    assertThat(createdLDAPSettings.getDisplayName()).isEqualTo(displayName);
    assertThat(createdLDAPSettings.getCronExpression()).isEqualTo(cronExpr);
  }

  @Test
  @Owner(developers = PRATEEK)
  @Category(UnitTests.class)
  public void testUpdateLdapSettings() throws IOException {
    final String displayName = "NG_LDAP_NEW";
    final String cronExpr = "0 0/30 * 1/1 * ? *";
    LDAPSettings settings = LDAPSettings.builder()
                                .displayName(displayName)
                                .connectionSettings(new LdapConnectionSettings())
                                .userSettingsList(new ArrayList<>())
                                .groupSettingsList(new ArrayList<>())
                                .cronExpression(cronExpr)
                                .build();
    Call<RestResponse<software.wings.beans.sso.LdapSettings>> request = mock(Call.class);
    doReturn(request).when(managerClient).updateLdapSettings(anyString(), any());
    software.wings.beans.sso.LdapSettings builtSettings = software.wings.beans.sso.LdapSettings.builder()
                                                              .displayName(displayName)
                                                              .accountId(ACCOUNT_ID)
                                                              .connectionSettings(new LdapConnectionSettings())
                                                              .build();
    builtSettings.setCronExpression(cronExpr);
    RestResponse<software.wings.beans.sso.LdapSettings> mockResponse = new RestResponse<>(builtSettings);
    doReturn(Response.success(mockResponse)).when(request).execute();
    LDAPSettings createdLDAPSettings = authenticationSettingsServiceImpl.updateLdapSettings(ACCOUNT_ID, settings);
    assertNotNull(createdLDAPSettings);
    assertThat(createdLDAPSettings.getDisplayName()).isEqualTo(displayName);
    assertThat(createdLDAPSettings.getCronExpression()).isEqualTo(cronExpr);
  }

  @Test
  @Owner(developers = PRATEEK)
  @Category(UnitTests.class)
  public void testDeleteLdapSettings() throws IOException {
    Call<RestResponse<software.wings.beans.sso.LdapSettings>> request = mock(Call.class);
    doReturn(request).when(managerClient).getLdapSettings(ACCOUNT_ID);
    RestResponse<software.wings.beans.sso.LdapSettings> mockResponse =
        new RestResponse<>(software.wings.beans.sso.LdapSettings.builder()
                               .displayName("displayName")
                               .connectionSettings(new LdapConnectionSettings())
                               .build());
    doReturn(Response.success(mockResponse)).when(request).execute();
    UserGroup ug1 = UserGroup.builder()
                        .identifier("UG1")
                        .accountIdentifier(ACCOUNT_ID)
                        .orgIdentifier("ORG_ID")
                        .projectIdentifier("PROJECT_ID")
                        .ssoGroupId("groupDn")
                        .users(Collections.singletonList("testUserEmail"))
                        .build();
    when(userGroupService.getUserGroupsBySsoId(anyString(), anyString())).thenReturn(Collections.singletonList(ug1));

    doReturn(request).when(managerClient).deleteLdapSettings(ACCOUNT_ID);
    doReturn(Response.success(mockResponse)).when(request).execute();
    authenticationSettingsServiceImpl.deleteLdapSettings(ACCOUNT_ID);
    verify(managerClient, times(1)).deleteLdapSettings(ACCOUNT_ID);
  }
}
