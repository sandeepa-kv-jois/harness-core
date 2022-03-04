/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.telemetry.helpers;

import static io.harness.ng.core.remote.OrganizationMapper.toOrganization;
import static io.harness.rule.OwnerRule.TEJAS;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ng.core.dto.OrganizationDTO;
import io.harness.ng.core.entities.Organization;
import io.harness.rule.Owner;
import io.harness.telemetry.TelemetryReporter;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PL)
public class OrganizationInstrumentationHelperTest {
  @InjectMocks OrganizationInstrumentationHelper instrumentationHelper;
  @Mock TelemetryReporter telemetryReporter;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  private OrganizationDTO createOrganizationDTO(String identifier) {
    return OrganizationDTO.builder().identifier(identifier).name(randomAlphabetic(10)).build();
  }

  @Test
  @Owner(developers = TEJAS)
  @Category(UnitTests.class)
  public void testCreateOrganizationFinishedTrackSend() {
    String accountIdentifier = randomAlphabetic(10);
    OrganizationDTO organizationDTO = createOrganizationDTO(randomAlphabetic(10));
    Organization organization = toOrganization(organizationDTO);
    instrumentationHelper.sendOrganizationCreateEvent(organization, accountIdentifier);
    try {
      verify(telemetryReporter, times(1)).sendTrackEvent(any(), any(), any(), any(), any());
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @Test
  @Owner(developers = TEJAS)
  @Category(UnitTests.class)
  public void testDeleteOrganiztionTrackSend() {
    String accountIdentifier = randomAlphabetic(10);
    OrganizationDTO organizationDTO = createOrganizationDTO(randomAlphabetic(10));
    Organization organization = toOrganization(organizationDTO);
    instrumentationHelper.sendOrganizationDeleteEvent(organization, accountIdentifier);
    try {
      verify(telemetryReporter, times(1)).sendTrackEvent(any(), any(), any(), any(), any());
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
