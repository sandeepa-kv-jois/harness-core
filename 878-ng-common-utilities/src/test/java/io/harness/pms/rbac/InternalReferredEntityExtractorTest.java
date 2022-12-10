/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.rbac;

import static io.harness.rule.OwnerRule.SAHIL;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import io.harness.CategoryTest;
import io.harness.EntityType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.beans.InfraDefReference;
import io.harness.category.element.UnitTests;
import io.harness.entitysetupusageclient.remote.EntitySetupUsageClient;
import io.harness.ng.core.EntityDetail;
import io.harness.ng.core.entitysetupusage.dto.EntityReferencesDTO;
import io.harness.remote.client.NGRestUtils;
import io.harness.rule.Owner;
import io.harness.utils.NGFeatureFlagHelperService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.powermock.core.classloader.annotations.PrepareForTest;

@PrepareForTest({NGRestUtils.class})
@OwnedBy(HarnessTeam.PIPELINE)
public class InternalReferredEntityExtractorTest extends CategoryTest {
  private static final String ACCOUNT_ID = "accountId";
  @Mock EntitySetupUsageClient entitySetupUsageClient;
  @Mock NGFeatureFlagHelperService ngFeatureFlagHelperService;
  @InjectMocks InternalReferredEntityExtractor internalReferredEntityExtractor;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @After
  public void verifyInteractions() {
    Mockito.verifyNoMoreInteractions(entitySetupUsageClient);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testExtractInternalEntities() {
    String dummy = "dummy";
    List<EntityDetail> entityDetailList = new ArrayList<>();

    Mockito.mockStatic(NGRestUtils.class);
    Mockito.when(NGRestUtils.getResponse(any(), any()))
        .thenReturn(EntityReferencesDTO.builder().entitySetupUsageBatchList(new ArrayList<>()).build());

    for (int i = 0; i < 5; i++) {
      String name = dummy + i;
      entityDetailList.add(getEntityDetail("conn-" + name, EntityType.CONNECTORS));
      entityDetailList.add(getEntityDetail("svc-" + name, EntityType.SERVICE));
      entityDetailList.add(getInfraDefRefEntityDetail("infra-" + name));

      // should be ignored
      entityDetailList.add(getEntityDetail("is-" + name, EntityType.INPUT_SETS));
    }

    Mockito.doReturn(false).when(ngFeatureFlagHelperService).isEnabled(eq(ACCOUNT_ID), any());
    internalReferredEntityExtractor.extractInternalEntities(ACCOUNT_ID, entityDetailList);

    Mockito.verify(entitySetupUsageClient)
        .listAllReferredUsagesBatch(ACCOUNT_ID,
            Arrays.asList("accountId/conn-dummy0", "accountId/conn-dummy1", "accountId/conn-dummy2",
                "accountId/conn-dummy3", "accountId/conn-dummy4"),
            EntityType.CONNECTORS, EntityType.SECRETS);

    Mockito.doReturn(true).when(ngFeatureFlagHelperService).isEnabled(eq(ACCOUNT_ID), any());
    internalReferredEntityExtractor.extractInternalEntities(ACCOUNT_ID, entityDetailList);

    Mockito.verify(entitySetupUsageClient, Mockito.times(1))
        .listAllReferredUsagesBatch(ACCOUNT_ID,
            Arrays.asList("accountId/conn-dummy0", "accountId/conn-dummy1", "accountId/conn-dummy2",
                "accountId/conn-dummy3", "accountId/conn-dummy4"),
            EntityType.CONNECTORS, EntityType.SECRETS);
  }

  private EntityDetail getInfraDefRefEntityDetail(String name) {
    return EntityDetail.builder()
        .entityRef(InfraDefReference.builder()
                       .identifier(name)
                       .accountIdentifier(ACCOUNT_ID)
                       .orgIdentifier("ORG")
                       .projectIdentifier("PROJ")
                       .envIdentifier("ENV")
                       .build())
        .type(EntityType.INFRASTRUCTURE)
        .build();
  }

  private EntityDetail getEntityDetail(String name, EntityType entityType) {
    return EntityDetail.builder()
        .entityRef(IdentifierRef.builder().identifier(name).accountIdentifier(ACCOUNT_ID).build())
        .type(entityType)
        .build();
  }
}
