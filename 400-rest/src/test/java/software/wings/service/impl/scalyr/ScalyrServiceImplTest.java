/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.scalyr;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.RAGHU;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.ScalyrConfig;
import software.wings.delegatetasks.cv.beans.CustomLogResponseMapper;
import software.wings.service.intfc.scalyr.ScalyrService;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ScalyrServiceImplTest extends WingsBaseTest {
  @Inject private ScalyrService scalyrService;
  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void test_createLogCollectionMapping() {
    String hostnameField = generateUuid();
    String messageField = generateUuid();
    String timestampField = generateUuid();

    final Map<String, Map<String, CustomLogResponseMapper>> logCollectionMapping =
        scalyrService.createLogCollectionMapping(hostnameField, messageField, timestampField);

    assertThat(logCollectionMapping.size()).isEqualTo(1);
    final Map<String, CustomLogResponseMapper> responseMap = logCollectionMapping.get(ScalyrConfig.QUERY_URL);
    assertThat(responseMap.get("host"))
        .isEqualTo(CustomLogResponseMapper.builder()
                       .fieldName("host")
                       .jsonPath(Collections.singletonList(hostnameField))
                       .build());
    assertThat(responseMap.get("timestamp"))
        .isEqualTo(CustomLogResponseMapper.builder()
                       .fieldName("timestamp")
                       .jsonPath(Collections.singletonList(timestampField))
                       .build());
    assertThat(responseMap.get("logMessage"))
        .isEqualTo(CustomLogResponseMapper.builder()
                       .fieldName("logMessage")
                       .jsonPath(Collections.singletonList(messageField))
                       .build());
  }
}
