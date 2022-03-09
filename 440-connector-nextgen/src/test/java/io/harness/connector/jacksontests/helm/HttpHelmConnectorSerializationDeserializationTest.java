/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.jacksontests.helm;

import static io.harness.connector.jacksontests.ConnectorJacksonTestHelper.createConnectorRequestDTO;
import static io.harness.connector.jacksontests.ConnectorJacksonTestHelper.readFileAsString;
import static io.harness.delegate.beans.connector.ConnectorType.HTTP_HELM_REPO;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.connector.helm.HttpHelmAuthType;
import io.harness.delegate.beans.connector.helm.HttpHelmAuthenticationDTO;
import io.harness.delegate.beans.connector.helm.HttpHelmConnectorDTO;
import io.harness.delegate.beans.connector.helm.HttpHelmConnectorDTO.HttpHelmConnectorDTOBuilder;
import io.harness.delegate.beans.connector.helm.HttpHelmUsernamePasswordDTO;
import io.harness.encryption.Scope;
import io.harness.encryption.SecretRefData;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.serializer.HObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@Slf4j
public class HttpHelmConnectorSerializationDeserializationTest extends CategoryTest {
  private ObjectMapper objectMapper;
  private static final String BASE_PATH = "440-connector-nextgen/src/test/resources/helm/";

  @Before
  public void setup() {
    objectMapper = new ObjectMapper();
    HObjectMapper.configureObjectMapperForNG(objectMapper);
  }

  @Test
  @Owner(developers = OwnerRule.ACASIAN)
  @Category(UnitTests.class)
  public void testShouldSerializeHttpHelmConnectorWithAnonymousAuth() {
    HttpHelmConnectorDTO httpHelmConnectorDTO = buildConnector(HttpHelmAuthType.ANONYMOUS);
    ConnectorInfoDTO connectorRequestDTO = createConnectorRequestDTO(httpHelmConnectorDTO, HTTP_HELM_REPO);
    ConnectorDTO connectorDTO = ConnectorDTO.builder().connectorInfo(connectorRequestDTO).build();
    String connectorString = "";
    try {
      connectorString = objectMapper.writeValueAsString(connectorDTO);
    } catch (Exception ex) {
      Assert.fail("Encountered exception while serializing http helm repo connector " + ex.getMessage());
    }
    String expectedResult = readFileAsString(BASE_PATH + "httpHelmConnectorWithAnonymousAuth.json");
    try {
      JsonNode tree1 = objectMapper.readTree(expectedResult);
      JsonNode tree2 = objectMapper.readTree(connectorString);
      log.info("Expected Connector String: {}", tree1.toString());
      log.info("Actual Connector String: {}", tree2.toString());
      assertThat(tree1.equals(tree2)).isTrue();
    } catch (Exception ex) {
      Assert.fail("Encountered exception while checking the two http helm json value connector" + ex.getMessage());
    }
  }

  @Test
  @Owner(developers = OwnerRule.ACASIAN)
  @Category(UnitTests.class)
  public void testShouldDeserializeHttpHelmConnectorWithAnonymousAuth() {
    String connectorInput = readFileAsString(BASE_PATH + "httpHelmConnectorWithAnonymousAuth.json");
    ConnectorDTO inputConnector = null;
    try {
      inputConnector = objectMapper.readValue(connectorInput, ConnectorDTO.class);
    } catch (Exception ex) {
      Assert.fail("Encountered exception while deserialize http helm connector " + ex.getMessage());
    }
    HttpHelmConnectorDTO httpHelmConnectorDTO = buildConnector(HttpHelmAuthType.ANONYMOUS);
    ConnectorInfoDTO connectorRequestDTO = createConnectorRequestDTO(httpHelmConnectorDTO, HTTP_HELM_REPO);
    ConnectorDTO connectorDTO = ConnectorDTO.builder().connectorInfo(connectorRequestDTO).build();
    assertThat(inputConnector).isEqualTo(connectorDTO);
  }

  @Test
  @Owner(developers = OwnerRule.ACASIAN)
  @Category(UnitTests.class)
  public void testShouldSerializeHttpHelmConnectorWithUsernamePasswordAuth() {
    HttpHelmConnectorDTO httpHelmConnectorDTO = buildConnector(HttpHelmAuthType.USER_PASSWORD);
    ConnectorInfoDTO connectorRequestDTO = createConnectorRequestDTO(httpHelmConnectorDTO, HTTP_HELM_REPO);
    ConnectorDTO connectorDTO = ConnectorDTO.builder().connectorInfo(connectorRequestDTO).build();
    String connectorString = "";
    try {
      connectorString = objectMapper.writeValueAsString(connectorDTO);
    } catch (Exception ex) {
      Assert.fail("Encountered exception while serialize http helm connector " + ex.getMessage());
    }
    String expectedResult = readFileAsString(BASE_PATH + "httpHelmConnectorWithUsernamePasswordAuth.json");
    try {
      JsonNode tree1 = objectMapper.readTree(expectedResult);
      JsonNode tree2 = objectMapper.readTree(connectorString);
      log.info("Expected Connector String: {}", tree1.toString());
      log.info("Actual Connector String: {}", tree2.toString());
      assertThat(tree1.equals(tree2)).isTrue();
    } catch (Exception ex) {
      Assert.fail("Encountered exception while checking the two http helm json value connector" + ex.getMessage());
    }
  }

  @Test
  @Owner(developers = OwnerRule.ACASIAN)
  @Category(UnitTests.class)
  public void testShouldDeserializeHttpHelmConnectorWithUsernamePasswordAuth() {
    String connectorInput = readFileAsString(BASE_PATH + "httpHelmConnectorWithUsernamePasswordAuth.json");
    ConnectorDTO inputConnector = null;
    try {
      inputConnector = objectMapper.readValue(connectorInput, ConnectorDTO.class);
    } catch (Exception ex) {
      Assert.fail("Encountered exception while deserialize http helm connector " + ex.getMessage());
    }
    HttpHelmConnectorDTO httpHelmConnectorDTO = buildConnector(HttpHelmAuthType.USER_PASSWORD);
    ConnectorInfoDTO connectorRequestDTO = createConnectorRequestDTO(httpHelmConnectorDTO, HTTP_HELM_REPO);
    ConnectorDTO connectorDTO = ConnectorDTO.builder().connectorInfo(connectorRequestDTO).build();
    assertThat(inputConnector).isEqualTo(connectorDTO);
  }

  private HttpHelmConnectorDTO buildConnector(HttpHelmAuthType authType) {
    HttpHelmConnectorDTOBuilder builder =
        HttpHelmConnectorDTO.builder().helmRepoUrl("http://storage.googleapis.com/kubernetes-charts");

    if (authType == HttpHelmAuthType.USER_PASSWORD) {
      builder.auth(
          HttpHelmAuthenticationDTO.builder()
              .authType(authType)
              .credentials(
                  HttpHelmUsernamePasswordDTO.builder()
                      .username("test")
                      .passwordRef(SecretRefData.builder().identifier("httpHelmPassword").scope(Scope.ACCOUNT).build())
                      .build())
              .build());
    } else {
      builder.auth(HttpHelmAuthenticationDTO.builder().authType(authType).build());
    }

    return builder.build();
  }
}
