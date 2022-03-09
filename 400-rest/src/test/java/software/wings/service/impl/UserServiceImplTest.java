/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.annotations.dev.HarnessModule._360_CG_MANAGER;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.ng.core.invites.dto.InviteOperationResponse.ACCOUNT_INVITE_ACCEPTED;
import static io.harness.ng.core.invites.dto.InviteOperationResponse.ACCOUNT_INVITE_ACCEPTED_NEED_PASSWORD;
import static io.harness.ng.core.invites.dto.InviteOperationResponse.FAIL;
import static io.harness.ng.core.invites.dto.InviteOperationResponse.INVITE_EXPIRED;
import static io.harness.ng.core.invites.dto.InviteOperationResponse.INVITE_INVALID;
import static io.harness.ng.core.invites.dto.InviteOperationResponse.USER_ALREADY_ADDED;
import static io.harness.rule.OwnerRule.DEEPAK;
import static io.harness.rule.OwnerRule.MOHIT;
import static io.harness.rule.OwnerRule.NANDAN;
import static io.harness.rule.OwnerRule.PRATEEK;
import static io.harness.rule.OwnerRule.RAJ;
import static io.harness.rule.OwnerRule.VIKAS_M;
import static io.harness.rule.OwnerRule.VOJIN;

import static software.wings.beans.Account.Builder.anAccount;
import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.beans.User.Builder.anUser;
import static software.wings.beans.UserInvite.UserInviteBuilder.anUserInvite;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.USER_EMAIL;
import static software.wings.utils.WingsTestConstants.USER_NAME;
import static software.wings.utils.WingsTestConstants.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.PageRequest;
import io.harness.category.element.UnitTests;
import io.harness.data.structure.UUIDGenerator;
import io.harness.event.handler.impl.EventPublishHelper;
import io.harness.exception.InvalidRequestException;
import io.harness.invites.remote.NgInviteClient;
import io.harness.ng.core.account.AuthenticationMechanism;
import io.harness.ng.core.common.beans.Generation;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.dto.UserInviteDTO;
import io.harness.ng.core.invites.dto.InviteOperationResponse;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.Account;
import software.wings.beans.User;
import software.wings.beans.UserInvite;
import software.wings.dl.WingsPersistence;
import software.wings.helpers.ext.url.SubdomainUrlHelperIntfc;
import software.wings.security.authentication.TOTPAuthHandler;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.SignupService;

import com.google.inject.Inject;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.UriInfo;
import org.apache.http.client.utils.URIBuilder;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import retrofit2.Response;

@OwnedBy(PL)
@TargetModule(_360_CG_MANAGER)
public class UserServiceImplTest extends WingsBaseTest {
  @Mock AccountService accountService;
  @Inject @InjectMocks UserServiceImpl userServiceImpl;
  @Inject private SubdomainUrlHelperIntfc subdomainUrlHelper;
  @Mock NgInviteClient ngInviteClient;
  @Mock SignupService signupService;
  @Mock EventPublishHelper eventPublishHelper;
  @Mock TOTPAuthHandler totpAuthHandler;
  @Mock UserServiceLimitChecker userServiceLimitChecker;
  @Inject WingsPersistence wingsPersistence;
  private static final String NG_AUTH_UI_PATH_PREFIX = "auth/";

  @Test
  @Owner(developers = DEEPAK)
  @Category(UnitTests.class)
  public void testTrialSignup() {
    String email = "email@email.com";
    UserInvite userInvite =
        anUserInvite().withEmail(email).withCompanyName("companyName").withAccountName("accountName").build();
    userInvite.setPassword("somePassword".toCharArray());
    when(signupService.getUserInviteByEmail(Matchers.eq(email))).thenReturn(null);
    userServiceImpl.trialSignup(userInvite);
    // Verifying that the mail is sent and event is published when a new user sign ups for trial
    Mockito.verify(accountService, times(1)).validateAccount(any(Account.class));
    Mockito.verify(signupService, times(1)).sendTrialSignupVerificationEmail(any(), any());
    Mockito.verify(eventPublishHelper, times(1)).publishTrialUserSignupEvent(any(), any(), any());
  }

  @Test
  @Owner(developers = DEEPAK)
  @Category(UnitTests.class)
  public void testValidateAccountName() {
    Mockito.doNothing().when(accountService).validateAccount(any(Account.class));
    userServiceImpl.validateAccountName("correctName", "correctAccountName");
    Mockito.verify(accountService, times(1)).validateAccount(any(Account.class));
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = DEEPAK)
  @Category(UnitTests.class)
  public void testValidateAccountNameWhenInvalidName() {
    doThrow(new InvalidRequestException("")).when(accountService).validateAccount(any(Account.class));
    userServiceImpl.validateAccountName("someInvalidName", "someInvalidName");
    Mockito.verify(accountService, times(1)).validateAccount(any(Account.class));
  }

  @Test
  @Owner(developers = MOHIT)
  @Category(UnitTests.class)
  public void testGetUserCountForPendingAccounts() {
    Account account = anAccount().withUuid("ACCOUNT_ID").build();
    wingsPersistence.save(account);
    User user = anUser().pendingAccounts(Arrays.asList(account)).build();
    wingsPersistence.save(user);
    assertThat(userServiceImpl.getTotalUserCount("ACCOUNT_ID", true)).isEqualTo(1);
  }

  private void setup() {
    Account account = anAccount().withUuid("ACCOUNT_ID").build();
    wingsPersistence.save(account);
    User user1 = User.Builder.anUser()
                     .uuid(UUIDGenerator.generateUuid())
                     .accounts(Collections.singletonList(account))
                     .email("aBc@harness.io")
                     .name("pqr")
                     .build();
    User user2 = User.Builder.anUser()
                     .uuid(UUIDGenerator.generateUuid())
                     .accounts(Collections.singletonList(account))
                     .email("xYz@harness.io")
                     .name("eFg")
                     .build();
    User user3 = User.Builder.anUser()
                     .uuid(UUIDGenerator.generateUuid())
                     .accounts(Collections.singletonList(account))
                     .email("pqR@harness.io")
                     .name("lMN")
                     .build();
    wingsPersistence.save(user1);
    wingsPersistence.save(user2);
    wingsPersistence.save(user3);
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void test_checkInviteStatus_userAddedRequiredPassword() {
    Account withNonSso = anAccount()
                             .withAccountName("harness")
                             .withCompanyName("harness")
                             .withAppId(GLOBAL_APP_ID)
                             .withUuid(UUID)
                             .withAuthenticationMechanism(AuthenticationMechanism.USER_PASSWORD)
                             .build();
    wingsPersistence.save(withNonSso);
    UserInvite userInvite = anUserInvite()
                                .withUuid(UUIDGenerator.generateUuid())
                                .withAccountId(ACCOUNT_ID)
                                .withEmail(USER_EMAIL)
                                .withName(USER_NAME)
                                .withCompleted(Boolean.FALSE)
                                .build();
    wingsPersistence.save(userInvite);
    User user = anUser()
                    .uuid(UUIDGenerator.generateUuid())
                    .name(userInvite.getName().trim())
                    .email(userInvite.getEmail().trim().toLowerCase())
                    .build();
    user.getAccounts().add(withNonSso);
    wingsPersistence.save(user);
    when(accountService.get(ACCOUNT_ID)).thenReturn(withNonSso);
    UserInvite userInviteRetrieved = userServiceImpl.getUserInviteByEmailAndAccount(USER_EMAIL, ACCOUNT_ID);
    InviteOperationResponse inviteOperationResponse =
        userServiceImpl.checkInviteStatus(userInviteRetrieved, Generation.CG);
    assertThat(inviteOperationResponse).isEqualTo(ACCOUNT_INVITE_ACCEPTED_NEED_PASSWORD);
    verify(eventPublishHelper, times(1)).publishUserInviteVerifiedFromAccountEvent(withNonSso.getUuid(), USER_EMAIL);
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void test_checkInviteStatus_userInviteAccepted() {
    Account withNonSso = anAccount()
                             .withAccountName("harness")
                             .withCompanyName("harness")
                             .withAppId(GLOBAL_APP_ID)
                             .withUuid(UUID)
                             .withAuthenticationMechanism(AuthenticationMechanism.USER_PASSWORD)
                             .build();
    wingsPersistence.save(withNonSso);
    UserInvite userInvite = anUserInvite()
                                .withUuid(UUIDGenerator.generateUuid())
                                .withAccountId(ACCOUNT_ID)
                                .withEmail(USER_EMAIL)
                                .withName(USER_NAME)
                                .withCompleted(Boolean.TRUE)
                                .build();
    wingsPersistence.save(userInvite);
    User user = anUser()
                    .uuid(UUIDGenerator.generateUuid())
                    .name(userInvite.getName().trim())
                    .email(userInvite.getEmail().trim().toLowerCase())
                    .build();
    user.getAccounts().add(withNonSso);
    wingsPersistence.save(user);
    when(accountService.get(ACCOUNT_ID)).thenReturn(withNonSso);
    UserInvite userInviteRetrieved = userServiceImpl.getUserInviteByEmailAndAccount(USER_EMAIL, ACCOUNT_ID);
    InviteOperationResponse inviteOperationResponse =
        userServiceImpl.checkInviteStatus(userInviteRetrieved, Generation.CG);
    assertThat(inviteOperationResponse).isEqualTo(USER_ALREADY_ADDED);
    verify(eventPublishHelper, times(0)).publishUserInviteVerifiedFromAccountEvent(withNonSso.getUuid(), USER_EMAIL);
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void test_checkInviteStatus_userInvalid() {
    Account withNonSso = anAccount()
                             .withAccountName("harness")
                             .withCompanyName("harness")
                             .withAppId(GLOBAL_APP_ID)
                             .withUuid(UUID)
                             .withAuthenticationMechanism(AuthenticationMechanism.USER_PASSWORD)
                             .build();
    wingsPersistence.save(withNonSso);
    UserInvite userInvite = anUserInvite()
                                .withUuid(UUIDGenerator.generateUuid())
                                .withAccountId(ACCOUNT_ID)
                                .withEmail("spam@harness.io")
                                .withName(USER_NAME)
                                .withCompleted(Boolean.TRUE)
                                .build();
    wingsPersistence.save(userInvite);
    User user = anUser().uuid(UUIDGenerator.generateUuid()).name(userInvite.getName().trim()).email(USER_EMAIL).build();
    user.getAccounts().add(withNonSso);
    wingsPersistence.save(user);
    when(accountService.get(ACCOUNT_ID)).thenReturn(withNonSso);
    UserInvite userInviteRetrieved = userServiceImpl.getUserInviteByEmailAndAccount("spam@harness.io", ACCOUNT_ID);
    InviteOperationResponse inviteOperationResponse =
        userServiceImpl.checkInviteStatus(userInviteRetrieved, Generation.CG);
    assertThat(inviteOperationResponse).isEqualTo(INVITE_INVALID);
    verify(eventPublishHelper, times(0)).publishUserInviteVerifiedFromAccountEvent(withNonSso.getUuid(), USER_EMAIL);
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void test_checkInviteStatus_accountInvalid() {
    Account withNonSso = anAccount()
                             .withAccountName("harness")
                             .withCompanyName("harness")
                             .withAppId(GLOBAL_APP_ID)
                             .withUuid(UUID)
                             .withAuthenticationMechanism(AuthenticationMechanism.USER_PASSWORD)
                             .build();
    wingsPersistence.save(withNonSso);
    UserInvite userInvite = anUserInvite()
                                .withUuid(UUIDGenerator.generateUuid())
                                .withAccountId(ACCOUNT_ID)
                                .withEmail(USER_EMAIL)
                                .withName(USER_NAME)
                                .withCompleted(Boolean.TRUE)
                                .build();
    wingsPersistence.save(userInvite);
    User user = anUser().uuid(UUIDGenerator.generateUuid()).name(userInvite.getName().trim()).email(USER_EMAIL).build();
    user.getAccounts().add(withNonSso);
    wingsPersistence.save(user);
    UserInvite userInviteRetrieved = userServiceImpl.getUserInviteByEmailAndAccount(USER_EMAIL, ACCOUNT_ID);
    InviteOperationResponse inviteOperationResponse =
        userServiceImpl.checkInviteStatus(userInviteRetrieved, Generation.CG);
    assertThat(inviteOperationResponse).isEqualTo(INVITE_INVALID);
    verify(eventPublishHelper, times(0)).publishUserInviteVerifiedFromAccountEvent(withNonSso.getUuid(), USER_EMAIL);
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void test_checkInviteStatus_userInviteInvalid() {
    Account withNonSso = anAccount()
                             .withAccountName("harness")
                             .withCompanyName("harness")
                             .withAppId(GLOBAL_APP_ID)
                             .withUuid(UUID)
                             .withAuthenticationMechanism(AuthenticationMechanism.USER_PASSWORD)
                             .build();
    wingsPersistence.save(withNonSso);
    UserInvite userInvite = anUserInvite()
                                .withUuid(UUIDGenerator.generateUuid())
                                .withAccountId(ACCOUNT_ID)
                                .withEmail(USER_EMAIL)
                                .withName(USER_NAME)
                                .withCompleted(Boolean.TRUE)
                                .build();
    User user = anUser().uuid(UUIDGenerator.generateUuid()).name(userInvite.getName().trim()).email(USER_EMAIL).build();
    user.getAccounts().add(withNonSso);
    wingsPersistence.save(user);
    when(accountService.get(ACCOUNT_ID)).thenReturn(withNonSso);
    InviteOperationResponse inviteOperationResponse = userServiceImpl.checkInviteStatus(userInvite, Generation.CG);
    assertThat(inviteOperationResponse).isEqualTo(INVITE_INVALID);
    verify(eventPublishHelper, times(0)).publishUserInviteVerifiedFromAccountEvent(withNonSso.getUuid(), USER_EMAIL);
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void test_checkInviteStatus_SsoUserAdded() {
    Account withSso = anAccount()
                          .withAccountName("harness")
                          .withCompanyName("harness")
                          .withAppId(GLOBAL_APP_ID)
                          .withUuid(UUID)
                          .withAuthenticationMechanism(AuthenticationMechanism.SAML)
                          .build();
    wingsPersistence.save(withSso);
    UserInvite userInvite = anUserInvite()
                                .withUuid(UUIDGenerator.generateUuid())
                                .withAccountId(ACCOUNT_ID)
                                .withEmail(USER_EMAIL)
                                .withName(USER_NAME)
                                .withCompleted(Boolean.FALSE)
                                .build();
    wingsPersistence.save(userInvite);
    User user = anUser().uuid(UUIDGenerator.generateUuid()).name(userInvite.getName().trim()).email(USER_EMAIL).build();
    user.getAccounts().add(withSso);
    wingsPersistence.save(user);
    when(accountService.get(ACCOUNT_ID)).thenReturn(withSso);
    UserInvite userInviteRetrieved = userServiceImpl.getUserInviteByEmailAndAccount(USER_EMAIL, ACCOUNT_ID);
    InviteOperationResponse inviteOperationResponse =
        userServiceImpl.checkInviteStatus(userInviteRetrieved, Generation.CG);
    assertThat(inviteOperationResponse).isEqualTo(ACCOUNT_INVITE_ACCEPTED);
    verify(eventPublishHelper, times(1)).publishUserInviteVerifiedFromAccountEvent(withSso.getUuid(), USER_EMAIL);
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void test_CompleteNgInvite() throws Exception {
    Account account = anAccount()
                          .withAccountName("harness")
                          .withCompanyName("harness")
                          .withAppId(GLOBAL_APP_ID)
                          .withUuid(UUID)
                          .withAuthenticationMechanism(AuthenticationMechanism.USER_PASSWORD)
                          .build();
    wingsPersistence.save(account);
    UserInviteDTO inviteDTO =
        UserInviteDTO.builder().accountId(ACCOUNT_ID).email(USER_EMAIL).name(USER_NAME).token(UUID).build();
    when(accountService.get(ACCOUNT_ID)).thenReturn(account);
    retrofit2.Call<ResponseDTO<Boolean>> req = mock(retrofit2.Call.class);
    when(ngInviteClient.completeInvite(anyString())).thenReturn(req);
    when(req.execute()).thenReturn(Response.success(ResponseDTO.newResponse()));
    userServiceImpl.completeNGInvite(inviteDTO, false);
    verify(eventPublishHelper, times(1)).publishUserRegistrationCompletionEvent(anyString(), any());
  }

  @Test
  @Owner(developers = RAJ)
  @Category(UnitTests.class)
  public void test_CompleteNgInvite_2FA() throws Exception {
    Account account = anAccount()
                          .withAccountName("harness")
                          .withCompanyName("harness")
                          .withAppId(GLOBAL_APP_ID)
                          .withUuid(UUID)
                          .withAuthenticationMechanism(AuthenticationMechanism.USER_PASSWORD)
                          .build();
    wingsPersistence.save(account);

    String userEmail = "testemail@hello.com";
    UserInviteDTO inviteDTO =
        UserInviteDTO.builder().accountId(ACCOUNT_ID).email(userEmail).name(USER_NAME).token(UUID).build();
    when(accountService.get(ACCOUNT_ID)).thenReturn(account);
    User user = User.Builder.anUser().twoFactorAuthenticationEnabled(true).email(userEmail).build();
    wingsPersistence.save(user);
    retrofit2.Call<ResponseDTO<Boolean>> req = mock(retrofit2.Call.class);
    when(ngInviteClient.completeInvite(anyString())).thenReturn(req);
    when(req.execute()).thenReturn(Response.success(ResponseDTO.newResponse()));
    userServiceImpl.completeNGInvite(inviteDTO, false);
    verify(totpAuthHandler, times(1)).sendTwoFactorAuthenticationResetEmail(any());
    verify(eventPublishHelper, times(1)).publishUserRegistrationCompletionEvent(anyString(), any());
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void test_getInviteAcceptRedirectURL_AccountInviteAcceptedNeedPassword() throws URISyntaxException {
    InviteOperationResponse inviteOperationResponse = ACCOUNT_INVITE_ACCEPTED_NEED_PASSWORD;
    UserInvite userInvite = anUserInvite()
                                .withUuid(UUIDGenerator.generateUuid())
                                .withAccountId(ACCOUNT_ID)
                                .withEmail(USER_EMAIL)
                                .withName(USER_NAME)
                                .withCompleted(Boolean.FALSE)
                                .build();
    String jwtToken = "jwtToken";
    String encodedEmail = "user%40wings.software";
    String accountCreationFragment =
        String.format("accountIdentifier=%s&email=%s&token=%s&generation=CG", ACCOUNT_ID, encodedEmail, jwtToken);
    String baseUrl = subdomainUrlHelper.getPortalBaseUrl(ACCOUNT_ID);
    URIBuilder uriBuilder = new URIBuilder(baseUrl);
    uriBuilder.setPath(NG_AUTH_UI_PATH_PREFIX);
    uriBuilder.setFragment("/accept-invite?" + accountCreationFragment);
    assertThat(userServiceImpl.getInviteAcceptRedirectURL(inviteOperationResponse, userInvite, jwtToken))
        .isEqualTo(uriBuilder.build());
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void test_getInviteAcceptRedirectURL_FailedInvite() throws URISyntaxException {
    InviteOperationResponse inviteOperationResponse = FAIL;
    UserInvite userInvite = anUserInvite()
                                .withUuid(UUIDGenerator.generateUuid())
                                .withAccountId(ACCOUNT_ID)
                                .withEmail(USER_EMAIL)
                                .withName(USER_NAME)
                                .withCompleted(Boolean.FALSE)
                                .build();
    String jwtToken = "jwtToken";
    String baseUrl = subdomainUrlHelper.getPortalBaseUrl(ACCOUNT_ID);
    URIBuilder uriBuilder = new URIBuilder(baseUrl);
    uriBuilder.setPath(NG_AUTH_UI_PATH_PREFIX);
    assertThat(userServiceImpl.getInviteAcceptRedirectURL(inviteOperationResponse, userInvite, jwtToken))
        .isEqualTo(uriBuilder.build());
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void test_getInviteAcceptRedirectURL_ExpiredInvite() throws URISyntaxException {
    InviteOperationResponse inviteOperationResponse = INVITE_EXPIRED;
    UserInvite userInvite = anUserInvite()
                                .withUuid(UUIDGenerator.generateUuid())
                                .withAccountId(ACCOUNT_ID)
                                .withEmail(USER_EMAIL)
                                .withName(USER_NAME)
                                .withCompleted(Boolean.FALSE)
                                .build();
    String jwtToken = "jwtToken";
    String baseUrl = subdomainUrlHelper.getPortalBaseUrl(ACCOUNT_ID);
    URIBuilder uriBuilder = new URIBuilder(baseUrl);
    uriBuilder.setPath(NG_AUTH_UI_PATH_PREFIX);
    uriBuilder.setFragment("/signin?errorCode=INVITE_EXPIRED");
    assertThat(userServiceImpl.getInviteAcceptRedirectURL(inviteOperationResponse, userInvite, jwtToken))
        .isEqualTo(uriBuilder.build());
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void test_getInviteAcceptRedirectURL_InvalidInvite() throws URISyntaxException {
    InviteOperationResponse inviteOperationResponse = INVITE_INVALID;
    UserInvite userInvite = anUserInvite()
                                .withUuid(UUIDGenerator.generateUuid())
                                .withAccountId(ACCOUNT_ID)
                                .withEmail(USER_EMAIL)
                                .withName(USER_NAME)
                                .withCompleted(Boolean.FALSE)
                                .build();
    String jwtToken = "jwtToken";
    String baseUrl = subdomainUrlHelper.getPortalBaseUrl(ACCOUNT_ID);
    URIBuilder uriBuilder = new URIBuilder(baseUrl);
    uriBuilder.setPath(NG_AUTH_UI_PATH_PREFIX);
    uriBuilder.setFragment("/signin?errorCode=INVITE_INVALID");
    assertThat(userServiceImpl.getInviteAcceptRedirectURL(inviteOperationResponse, userInvite, jwtToken))
        .isEqualTo(uriBuilder.build());
  }

  @Test
  @Owner(developers = MOHIT)
  @Category(UnitTests.class)
  public void shouldSortUsers() {
    setup();

    PageRequest pageRequest = mock(PageRequest.class);
    MultivaluedMap<String, String> map = new MultivaluedHashMap<>();
    UriInfo uriInfo = mock(UriInfo.class);
    when(pageRequest.getUriInfo()).thenReturn(uriInfo);

    map.put("sort[0][direction]", Arrays.asList("ASC"));
    map.put("sort[0][field]", Arrays.asList("name"));
    when(uriInfo.getQueryParameters(true)).thenReturn(map);
    List<User> userList = userServiceImpl.listUsers(pageRequest, "ACCOUNT_ID", "", 0, 30, false, true);
    assertThat(userList.get(2).getName()).isEqualTo("pqr");

    map.put("sort[0][field]", Arrays.asList("email"));
    when(uriInfo.getQueryParameters(true)).thenReturn(map);
    userList = userServiceImpl.listUsers(pageRequest, "ACCOUNT_ID", "", 0, 30, false, true);
    assertThat(userList.get(2).getName()).isEqualTo("eFg");
  }

  @Test
  @Owner(developers = MOHIT)
  @Category(UnitTests.class)
  public void shouldSearchUsers() {
    setup();

    PageRequest pageRequest = mock(PageRequest.class);
    MultivaluedMap<String, String> map = new MultivaluedHashMap<>();
    UriInfo uriInfo = mock(UriInfo.class);
    when(pageRequest.getUriInfo()).thenReturn(uriInfo);
    when(uriInfo.getQueryParameters(true)).thenReturn(map);

    List<User> userList = userServiceImpl.listUsers(pageRequest, "ACCOUNT_ID", "ab", 0, 30, false, true);
    assertThat(userList.size()).isEqualTo(1);
    assertThat(userList.get(0).getName()).isEqualTo("pqr");

    userList = userServiceImpl.listUsers(pageRequest, "ACCOUNT_ID", "PqR", 0, 30, false, true);
    assertThat(userList.size()).isEqualTo(2);
  }

  @Test
  @Owner(developers = MOHIT)
  @Category(UnitTests.class)
  public void shouldSearchAndSortUsers() {
    setup();

    PageRequest pageRequest = mock(PageRequest.class);
    MultivaluedMap<String, String> map = new MultivaluedHashMap<>();
    UriInfo uriInfo = mock(UriInfo.class);
    when(pageRequest.getUriInfo()).thenReturn(uriInfo);
    when(uriInfo.getQueryParameters(true)).thenReturn(map);

    map.put("sort[0][direction]", Arrays.asList("DESC"));
    map.put("sort[0][field]", Arrays.asList("email"));
    List<User> userList = userServiceImpl.listUsers(pageRequest, "ACCOUNT_ID", "PqR", 0, 30, false, true);
    assertThat(userList.get(1).getName()).isEqualTo("pqr");

    userList = userServiceImpl.listUsers(pageRequest, "ACCOUNT_ID", "fgh", 0, 30, false, true);
    assertThat(userList.size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = PRATEEK)
  @Category(UnitTests.class)
  public void shouldSearchUsersNotDisabled() {
    Account account = anAccount().withUuid("ACCOUNT_ID").build();
    wingsPersistence.save(account);
    User user1 = User.Builder.anUser()
                     .uuid(UUIDGenerator.generateUuid())
                     .accounts(Collections.singletonList(account))
                     .email("aBc@harness.io")
                     .name("pqr")
                     .build();
    user1.setDisabled(false);
    wingsPersistence.save(user1);

    PageRequest pageRequest = mock(PageRequest.class);
    MultivaluedMap<String, String> map = new MultivaluedHashMap<>();
    UriInfo uriInfo = mock(UriInfo.class);
    when(pageRequest.getUriInfo()).thenReturn(uriInfo);
    when(uriInfo.getQueryParameters(true)).thenReturn(map);

    List<User> userList = userServiceImpl.listUsers(pageRequest, "ACCOUNT_ID", "ab", 0, 30, false, true);
    assertThat(userList.size()).isEqualTo(1);
    assertThat(userList.get(0).getName()).isEqualTo("pqr");
  }

  @Test
  @Owner(developers = PRATEEK)
  @Category(UnitTests.class)
  public void shouldNotSearchUsersDisabled() {
    Account account = anAccount().withUuid("ACCOUNT_ID").build();
    wingsPersistence.save(account);
    User user1 = User.Builder.anUser()
                     .uuid(UUIDGenerator.generateUuid())
                     .accounts(Collections.singletonList(account))
                     .email("aBc@harness.io")
                     .name("pqr")
                     .build();
    user1.setDisabled(true);
    wingsPersistence.save(user1);

    PageRequest pageRequest = mock(PageRequest.class);
    MultivaluedMap<String, String> map = new MultivaluedHashMap<>();
    UriInfo uriInfo = mock(UriInfo.class);
    when(pageRequest.getUriInfo()).thenReturn(uriInfo);
    when(uriInfo.getQueryParameters(true)).thenReturn(map);

    List<User> userList = userServiceImpl.listUsers(pageRequest, "ACCOUNT_ID", "ab", 0, 30, false, true);
    assertThat(userList.size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = VOJIN)
  @Category(UnitTests.class)
  public void sanitizeUserNameTest_without_malicious_content() {
    String name1 = "Vojin Đukić";
    String name2 = "Peter O'Toole";
    String name3 = "You <p>user login</p> is <strong>owasp-user01</strong>";
    String expectedName3 = "You user login is owasp-user01";
    assertThat(userServiceImpl.sanitizeUserName(name1)).isEqualTo(name1);
    assertThat(userServiceImpl.sanitizeUserName(name2)).isEqualTo(name2);
    assertThat(userServiceImpl.sanitizeUserName(name3)).isEqualTo(expectedName3);
  }

  @Test
  @Owner(developers = NANDAN)
  @Category(UnitTests.class)
  public void sanitizeUserNameTest_with_malicious_content() {
    String name1 = "<script>alert(22);</script>" + USER_NAME + "<img src='#' onload='javascript:alert(23);'>";
    String name2 = "'''><img src=x onerror=alert(1)> '''><img srx onerror=alert(1)>";
    String name3 = "</h2>special offer <a href=www.attacker.site>" + USER_NAME + "</a><h2>";

    String expectedName2 = "'''> '''>";
    String expectedName3 = "special offer " + USER_NAME;
    assertThat(userServiceImpl.sanitizeUserName(name1)).isEqualTo(USER_NAME);
    assertThat(userServiceImpl.sanitizeUserName(name2)).isEqualTo(expectedName2);
    assertThat(userServiceImpl.sanitizeUserName(name3)).isEqualTo(expectedName3);
  }
}
