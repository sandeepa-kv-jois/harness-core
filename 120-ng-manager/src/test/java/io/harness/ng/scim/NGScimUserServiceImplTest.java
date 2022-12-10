/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.scim;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.BOOPESH;
import static io.harness.rule.OwnerRule.UJJAWAL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.NgManagerTestBase;
import io.harness.account.AccountClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.api.UserGroupService;
import io.harness.ng.core.dto.GatewayAccountRequestDTO;
import io.harness.ng.core.invites.api.InviteService;
import io.harness.ng.core.user.UserInfo;
import io.harness.ng.core.user.UserMembershipUpdateSource;
import io.harness.ng.core.user.remote.dto.UserMetadataDTO;
import io.harness.ng.core.user.service.NgUserService;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.scim.ScimListResponse;
import io.harness.scim.ScimUser;

import software.wings.beans.Account;
import software.wings.beans.UserInvite;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.ws.rs.core.Response;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import retrofit2.Call;

@OwnedBy(PL)
public class NGScimUserServiceImplTest extends NgManagerTestBase {
  private NgUserService ngUserService;

  private UserGroupService userGroupService;
  private InviteService inviteService;
  private NGScimUserServiceImpl scimUserService;
  @Mock private AccountClient accountClient;

  @Before
  public void setup() throws IOException {
    inviteService = mock(InviteService.class);
    ngUserService = mock(NgUserService.class);
    userGroupService = mock(UserGroupService.class);

    Call<RestResponse<Boolean>> ffCall = mock(Call.class);
    when(accountClient.isFeatureFlagEnabled(any(), anyString())).thenReturn(ffCall);
    when(ffCall.execute()).thenReturn(retrofit2.Response.success(new RestResponse<>(true)));

    scimUserService = new NGScimUserServiceImpl(ngUserService, inviteService, userGroupService, accountClient);
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testScim_IfUserIsPartOfAccountAlready_ItShouldUpdate() {
    ScimUser scimUser = new ScimUser();
    Account account = new Account();
    account.setUuid(generateUuid());
    account.setAccountName("account_name");

    scimUser.setUserName("username@harness.io");
    scimUser.setDisplayName("display_name");

    UserInfo userInfo = UserInfo.builder().admin(true).email("username@harness.io").name("display_name").build();

    UserMetadataDTO userMetadataDTO = Optional.of(userInfo)
                                          .map(user
                                              -> UserMetadataDTO.builder()
                                                     .uuid(user.getUuid())
                                                     .name(user.getName())
                                                     .email(user.getEmail())
                                                     .locked(user.isLocked())
                                                     .disabled(user.isDisabled())
                                                     .externallyManaged(user.isExternallyManaged())
                                                     .build())
                                          .orElse(null);

    UserInvite userInvite = new UserInvite();
    userInvite.setEmail("username@harness.io");

    when(ngUserService.getUserInfoByEmailFromCG(any())).thenReturn(Optional.ofNullable(userInfo));
    when(ngUserService.getUserByEmail(userInfo.getEmail(), true)).thenReturn(Optional.ofNullable(userMetadataDTO));
    when(ngUserService.getUserById(any())).thenReturn(Optional.ofNullable(userInfo));
    Response response = scimUserService.createUser(scimUser, account.getUuid());

    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(201);
    assertThat(response.getEntity()).isNotNull();
    assertThat(((ScimUser) response.getEntity()).getUserName()).isEqualTo(userInfo.getEmail());
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testScim_IfUserIsNotPartOfAccountAlready_ItShouldAddToAccount_AlreadyPresent() {
    ScimUser scimUser = new ScimUser();
    Account account = new Account();
    account.setUuid(generateUuid());
    account.setAccountName("account_name");

    scimUser.setUserName("username@harness.io");
    scimUser.setDisplayName("display_name");

    UserInfo userInfo = UserInfo.builder().admin(true).email("username@harness.io").name("display_name").build();

    UserInvite userInvite = new UserInvite();
    userInvite.setEmail("username@harness.io");

    when(ngUserService.getUserInfoByEmailFromCG(anyString())).thenReturn(Optional.ofNullable(userInfo));
    when(ngUserService.getUserByEmail(userInfo.getEmail(), true)).thenReturn(Optional.ofNullable(null));
    when(ngUserService.getUserById(anyString())).thenReturn(Optional.ofNullable(userInfo));
    Response response = scimUserService.createUser(scimUser, account.getUuid());

    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(404);
    assertThat(response.getEntity()).isNull();
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testScim_IfUserIsNotPartOfAccountAlready_ItShouldAddToAccount() {
    ScimUser scimUser = new ScimUser();
    Account account = new Account();
    final String accountId = generateUuid();
    account.setUuid(accountId);
    account.setAccountName("account_name");

    scimUser.setUserName("username@harness.io");
    scimUser.setDisplayName("display_name");

    List<GatewayAccountRequestDTO> accounts = new ArrayList<>();
    accounts.add(GatewayAccountRequestDTO.builder().uuid(account.getUuid()).build());
    UserInfo userInfo =
        UserInfo.builder().admin(true).email("username@harness.io").accounts(accounts).name("display_name").build();

    UserInvite userInvite = new UserInvite();
    userInvite.setEmail("username@harness.io");

    UserMetadataDTO userMetadataDTO = Optional.of(userInfo)
                                          .map(user
                                              -> UserMetadataDTO.builder()
                                                     .uuid(user.getUuid())
                                                     .name(user.getName())
                                                     .email(user.getEmail())
                                                     .locked(user.isLocked())
                                                     .disabled(user.isDisabled())
                                                     .externallyManaged(user.isExternallyManaged())
                                                     .build())
                                          .orElse(null);

    when(ngUserService.getUserInfoByEmailFromCG(any())).thenReturn(Optional.ofNullable(userInfo));
    when(ngUserService.getUserByEmail(userInfo.getEmail(), true)).thenReturn(Optional.ofNullable(userMetadataDTO));
    when(ngUserService.getUserById(any())).thenReturn(Optional.ofNullable(userInfo));
    Response response = scimUserService.createUser(scimUser, account.getUuid());

    assertThat(response).isNotNull();
    assertThat(response.getStatus()).isEqualTo(201);
    assertThat(response.getEntity()).isNotNull();
    assertThat(((ScimUser) response.getEntity()).getUserName()).isEqualTo(userInfo.getEmail());
    verify(ngUserService, times(1))
        .addUserToScope(
            userMetadataDTO.getUuid(), Scope.of(accountId, null, null), null, null, UserMembershipUpdateSource.SYSTEM);
  }

  @Test
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void testNGScimSearchForCGUser() {
    ScimUser scimUser = new ScimUser();
    scimUser.setUserName("randomEmail.com");
    scimUser.setDisplayName("randomDisplayname");
    ScimListResponse<ScimUser> scimUserScimListResponse = new ScimListResponse<>();
    List<ScimUser> resources = new ArrayList<>();
    resources.add(scimUser);
    scimUserScimListResponse.setResources(resources);
    scimUserScimListResponse.setTotalResults(1);
    when(ngUserService.searchScimUsersByEmailQuery(anyString(), anyString(), any(), any()))
        .thenReturn(scimUserScimListResponse);
    when(ngUserService.getUserByEmail(anyString(), anyBoolean())).thenReturn(Optional.empty());
    ScimListResponse<ScimUser> result = scimUserService.searchUser("accountId", "filter", 1, 0);
    assertThat(result.getTotalResults()).isEqualTo(0);
    assertThat(result.getResources().size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void testNGScimSearchForNGUser() {
    ScimUser scimUser = new ScimUser();
    scimUser.setUserName("randomEmail.com");
    scimUser.setDisplayName("randomDisplayname");
    ScimListResponse<ScimUser> scimUserScimListResponse = new ScimListResponse<>();
    List<ScimUser> resources = new ArrayList<>();
    resources.add(scimUser);
    scimUserScimListResponse.setResources(resources);
    scimUserScimListResponse.setTotalResults(1);
    UserMetadataDTO userMetadataDTO = new UserMetadataDTO();
    userMetadataDTO.setEmail("randomEmail.com");
    userMetadataDTO.setUuid("random");
    when(ngUserService.searchScimUsersByEmailQuery(anyString(), anyString(), any(), any()))
        .thenReturn(scimUserScimListResponse);
    when(ngUserService.getUserByEmail(anyString(), anyBoolean())).thenReturn(Optional.ofNullable(userMetadataDTO));
    when(ngUserService.isUserAtScope(anyString(), any())).thenReturn(true);
    ScimListResponse<ScimUser> result = scimUserService.searchUser("accountId", "filter", 1, 0);
    assertThat(result.getTotalResults()).isEqualTo(1);
    assertThat(result.getResources().size()).isEqualTo(1);
    assertThat(result.getResources().get(0).getDisplayName()).isEqualTo("randomDisplayname");
    assertThat(result.getResources().get(0).getUserName()).isEqualTo("randomEmail.com");
  }

  @Test
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void testNGScimSearchForNGUserButInDifferentAccount() {
    ScimUser scimUser = new ScimUser();
    scimUser.setUserName("randomEmail.com");
    scimUser.setDisplayName("randomDisplayname");
    ScimListResponse<ScimUser> scimUserScimListResponse = new ScimListResponse<>();
    List<ScimUser> resources = new ArrayList<>();
    resources.add(scimUser);
    scimUserScimListResponse.setResources(resources);
    scimUserScimListResponse.setTotalResults(1);
    UserMetadataDTO userMetadataDTO = new UserMetadataDTO();
    userMetadataDTO.setEmail("randomEmail.com");
    userMetadataDTO.setUuid("random");
    when(ngUserService.searchScimUsersByEmailQuery(anyString(), anyString(), any(), any()))
        .thenReturn(scimUserScimListResponse);
    when(ngUserService.getUserByEmail(anyString(), anyBoolean())).thenReturn(Optional.ofNullable(userMetadataDTO));
    when(ngUserService.isUserAtScope(anyString(), any())).thenReturn(false);
    ScimListResponse<ScimUser> result = scimUserService.searchUser("accountId", "filter", 1, 0);
    assertThat(result.getTotalResults()).isEqualTo(0);
    assertThat(result.getResources().size()).isEqualTo(0);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void testGetUserInCGNotInNG() {
    UserInfo userInfo =
        UserInfo.builder().admin(true).email("username@harness.io").name("display_name").uuid("someRandom").build();
    when(ngUserService.getUserById(userInfo.getUuid())).thenReturn(Optional.of(userInfo));
    when(ngUserService.isUserAtScope(anyString(), any())).thenReturn(true);
    scimUserService.getUser(userInfo.getUuid(), "someRandom");
  }

  @Test
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void testGetUserInBothCGAndNG() {
    UserInfo userInfo =
        UserInfo.builder().admin(true).email("username@harness.io").name("display_name").uuid("someRandom").build();
    UserMetadataDTO userMetadataDTO = new UserMetadataDTO();
    userMetadataDTO.setEmail("username@harness.io");
    userMetadataDTO.setUuid("someRandom");
    when(ngUserService.getUserById(userInfo.getUuid())).thenReturn(Optional.of(userInfo));
    when(ngUserService.getUserByEmail(userInfo.getEmail(), false)).thenReturn(Optional.of(userMetadataDTO));
    when(ngUserService.isUserAtScope(anyString(), any())).thenReturn(true);
    ScimUser scimUser = scimUserService.getUser(userInfo.getUuid(), "someRandom");
    assertThat(scimUser).isNotNull();
    assertThat(scimUser.getUserName().equals("someRandom"));
    assertThat(scimUser.getDisplayName().equals("display_name"));
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void testGetUserInBothCGAndNGButDifferentAccountInNg() {
    UserInfo userInfo =
        UserInfo.builder().admin(true).email("username@harness.io").name("display_name").uuid("someRandom").build();
    UserMetadataDTO userMetadataDTO = new UserMetadataDTO();
    userMetadataDTO.setEmail("username@harness.io");
    userMetadataDTO.setUuid("someRandom");
    when(ngUserService.getUserById(userInfo.getUuid())).thenReturn(Optional.of(userInfo));
    when(ngUserService.getUserByEmail(userInfo.getEmail(), false)).thenReturn(Optional.of(userMetadataDTO));
    when(ngUserService.isUserAtScope(anyString(), any())).thenReturn(false);
    ScimUser scimUser = scimUserService.getUser(userInfo.getUuid(), "someRandom");
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testGetUserInBothCGAndNGButDifferentAccountInNg2() {
    UserInfo userInfo =
        UserInfo.builder().admin(true).email("username@harness.io").name("display_name").uuid("userId").build();
    UserMetadataDTO userMetadataDTO = new UserMetadataDTO();
    userMetadataDTO.setEmail("username@harness.io");
    userMetadataDTO.setUuid("userId");
    when(ngUserService.getUserById(userInfo.getUuid())).thenReturn(Optional.of(userInfo));
    when(ngUserService.getUserByEmail(userInfo.getEmail(), false)).thenReturn(Optional.of(userMetadataDTO));
    when(ngUserService.isUserAtScope(anyString(), any())).thenReturn(true);
    ScimUser scimUser = scimUserService.getUser(userInfo.getUuid(), "accountId");
    assertThat(scimUser).isNotNull();
    assertThat(scimUser.getName()).isNotNull();
    assertThat(scimUser.getDisplayName()).isNotNull();
    assertThat(scimUser.getDisplayName()).isEqualTo(userInfo.getName());
    assertThat(scimUser.getUserName()).isNotNull();
    assertThat(scimUser.getUserName()).isEqualTo(userInfo.getEmail());
    assertThat(scimUser.getId()).isNotNull();
    assertThat(scimUser.getId()).isEqualTo(userInfo.getUuid());
  }
}
