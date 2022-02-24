/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.security;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.rule.OwnerRule.BOOPESH;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTCreationException;
import com.auth0.jwt.interfaces.Claim;
import com.google.common.collect.ImmutableMap;
import java.io.UnsupportedEncodingException;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PL)
public class JWTTokenServiceUtilsTest extends CategoryTest {
  private static final String ISSUER = "Harness Inc";
  private static final String EMAIL = randomAlphabetic(9) + ".com";
  private static final String JWT_PASSWORD_SECRET = randomAlphabetic(10);
  private static final String EMAIL_ID = "email";
  private static final String EXP = "exp";
  private static final String TYPE = "type";
  private static final String NAME = "name";

  private String generateToken(ImmutableMap<String, String> claims) {
    try {
      Algorithm algorithm = Algorithm.HMAC256(JWT_PASSWORD_SECRET);
      JWTCreator.Builder jwtBuilder = JWT.create().withIssuer(ISSUER).withIssuedAt(new Date());

      if (!isEmpty(claims)) {
        claims.forEach(jwtBuilder::withClaim);
      }
      return jwtBuilder.sign(algorithm);
    } catch (UnsupportedEncodingException | JWTCreationException exception) {
      throw new JWTCreationException("JWTToken could not be generated", exception);
    }
  }

  @Test
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void isvalidServiceAuthTest() {
    ImmutableMap<String, String> claims = ImmutableMap.of(EMAIL_ID, EMAIL);
    String jwtToken = JWTTokenServiceUtils.generateJWTToken(
        claims, TimeUnit.MILLISECONDS.convert(60, TimeUnit.DAYS), JWT_PASSWORD_SECRET);
    Pair<Boolean, Map<String, Claim> > authMap =
        JWTTokenServiceUtils.isServiceAuthorizationValid(jwtToken, JWT_PASSWORD_SECRET);
    System.out.println(authMap);
    assertThat(authMap.getKey().booleanValue()).isTrue();
    assertThat(authMap.getValue().containsKey(EXP)).isTrue();
  }

  @Test
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void isvalidServiceAuthTestWithPrincipal() {
    ImmutableMap<String, String> claims = ImmutableMap.of(TYPE, "USER", NAME, EMAIL);
    String jwtToken = JWTTokenServiceUtils.generateJWTToken(
        claims, TimeUnit.MILLISECONDS.convert(60, TimeUnit.DAYS), JWT_PASSWORD_SECRET);
    Pair<Boolean, Map<String, Claim> > authMap =
        JWTTokenServiceUtils.isServiceAuthorizationValid(jwtToken, JWT_PASSWORD_SECRET);
    assertThat(authMap.getKey().booleanValue()).isTrue();
    assertThat(authMap.getValue().containsKey(EXP)).isTrue();
    assertThat(authMap.getValue().containsKey(TYPE)).isTrue();
  }

  @Test
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void isInvalidServiceAuthTestWithPrincipalWithoutExp() {
    ImmutableMap<String, String> claims = ImmutableMap.of(TYPE, "USER", NAME, "xyz@gmail.com");
    String jwtToken = generateToken(claims);
    Pair<Boolean, Map<String, Claim> > authMap =
        JWTTokenServiceUtils.isServiceAuthorizationValid(jwtToken, JWT_PASSWORD_SECRET);
    assertThat(authMap.getKey().booleanValue()).isTrue();
    assertThat(authMap.getValue().containsKey(EXP)).isFalse();
    assertThat(authMap.getValue().containsKey(TYPE)).isTrue();
  }

  @Test
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void isInvalidServiceAuthTestWithoutPrincipalWithoutExp() {
    ImmutableMap<String, String> claims = ImmutableMap.of(EMAIL_ID, EMAIL);
    String jwtToken = generateToken(claims);
    Pair<Boolean, Map<String, Claim> > authMap =
        JWTTokenServiceUtils.isServiceAuthorizationValid(jwtToken, JWT_PASSWORD_SECRET);
    assertThat(authMap.getKey().booleanValue()).isTrue();
    assertThat(authMap.getValue().containsKey(EXP)).isFalse();
    assertThat(authMap.getValue().containsKey(TYPE)).isFalse();
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = BOOPESH)
  @Category(UnitTests.class)
  public void isInvalidServiceAuthTestExpiredToken() throws InterruptedException {
    ImmutableMap<String, String> claims = ImmutableMap.of(EMAIL_ID, EMAIL);
    String jwtToken = JWTTokenServiceUtils.generateJWTToken(claims, 1L, JWT_PASSWORD_SECRET);
    Thread.sleep(1100);
    JWTTokenServiceUtils.isServiceAuthorizationValid(jwtToken, JWT_PASSWORD_SECRET);
  }
}
