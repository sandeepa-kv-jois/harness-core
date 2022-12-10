/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.jobs;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.VUK;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.VerificationApplication;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.services.api.CVConfigService;
import io.harness.eventsframework.entity_crud.organization.OrganizationEntityChangeDTO;
import io.harness.persistence.PersistentEntity;
import io.harness.reflection.HarnessReflections;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class OrganisationChangeEventMessageProcessorTest extends CvNextGenTestBase {
  @Inject private OrganizationChangeEventMessageProcessor organizationChangeEventMessageProcessor;
  @Inject private CVConfigService cvConfigService;
  private String projectIdentifier;
  private BuilderFactory builderFactory;

  @Before
  public void setup() {
    projectIdentifier = generateUuid();
    builderFactory = BuilderFactory.getDefault();
  }

  @Test
  @Owner(developers = VUK)
  @Category(UnitTests.class)
  public void testProcessDeleteAction() {
    String accountId = generateUuid();
    CVConfig cvConfig1 = createCVConfig(accountId, "organisation1");
    CVConfig cvConfig2 = createCVConfig(accountId, "organisation2");
    cvConfigService.save(cvConfig1);
    cvConfigService.save(cvConfig2);
    organizationChangeEventMessageProcessor.processDeleteAction(OrganizationEntityChangeDTO.newBuilder()
                                                                    .setAccountIdentifier(accountId)
                                                                    .setIdentifier("organisation1")
                                                                    .build());
    assertThat(cvConfigService.get(cvConfig1.getUuid())).isNull();
    assertThat(cvConfigService.get(cvConfig2.getUuid())).isNotNull();

    // For every message processing, idemptotency is assumed - Redelivery of a message produces the same result and
    // there are no side effects
    CVConfig retrievedCVConfig1 = cvConfigService.get(cvConfig1.getUuid());
    CVConfig retrievedCVConfig2 = cvConfigService.get(cvConfig2.getUuid());

    organizationChangeEventMessageProcessor.processDeleteAction(OrganizationEntityChangeDTO.newBuilder()
                                                                    .setAccountIdentifier(accountId)
                                                                    .setIdentifier("organisation1")
                                                                    .build());
    assertThat(retrievedCVConfig1).isNull();
    assertThat(retrievedCVConfig2).isNotNull();
  }

  @Test
  @Owner(developers = VUK)
  @Category(UnitTests.class)
  public void testProcessDeleteActionWithoutOrgIdentifier() {
    String accountId = generateUuid();
    CVConfig cvConfig1 = createCVConfig(accountId, "organisation1");
    CVConfig cvConfig2 = createCVConfig(accountId, "organisation2");
    cvConfigService.save(cvConfig1);
    cvConfigService.save(cvConfig2);
    organizationChangeEventMessageProcessor.processDeleteAction(
        OrganizationEntityChangeDTO.newBuilder().setAccountIdentifier(accountId).build());
    assertThat(cvConfigService.get(cvConfig1.getUuid())).isNotNull();
    assertThat(cvConfigService.get(cvConfig2.getUuid())).isNotNull();
  }

  @Test
  @Owner(developers = VUK)
  @Category(UnitTests.class)
  public void testProcessDeleteAction_entitiesList() {
    Set<Class<? extends PersistentEntity>> entitiesWithVerificationTaskId = new HashSet<>();
    entitiesWithVerificationTaskId.addAll(OrganizationChangeEventMessageProcessor.ENTITIES_MAP.keySet());
    Set<Class<? extends PersistentEntity>> reflections =
        HarnessReflections.get()
            .getSubTypesOf(PersistentEntity.class)
            .stream()
            .filter(klazz
                -> StringUtils.startsWithAny(
                    klazz.getPackage().getName(), VerificationApplication.class.getPackage().getName()))
            .collect(Collectors.toSet());
    Set<Class<? extends PersistentEntity>> withOrganisationIdentifier = new HashSet<>();
    reflections.forEach(entity -> {
      if (doesClassContainField(entity, "accountId") && doesClassContainField(entity, "orgIdentifier")
          && !OrganizationChangeEventMessageProcessor.EXCEPTIONS.contains(entity)) {
        withOrganisationIdentifier.add(entity);
      }
    });
    assertThat(entitiesWithVerificationTaskId)
        .isEqualTo(withOrganisationIdentifier)
        .withFailMessage("Entities with organisationIdentifier found which is not added to ENTITIES_MAP");
  }

  private boolean doesClassContainField(Class<?> clazz, String fieldName) {
    return Arrays.stream(clazz.getDeclaredFields()).anyMatch(f -> f.getName().equals(fieldName));
  }

  private CVConfig createCVConfig(String accountId, String orgIdentifier) {
    return builderFactory.splunkCVConfigBuilder().accountId(accountId).orgIdentifier(orgIdentifier).build();
  }
}
