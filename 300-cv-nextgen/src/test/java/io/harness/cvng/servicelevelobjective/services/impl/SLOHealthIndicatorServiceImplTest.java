/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.services.impl;

import static io.harness.cvng.servicelevelobjective.entities.SLIRecord.SLIState.BAD;
import static io.harness.cvng.servicelevelobjective.entities.SLIRecord.SLIState.GOOD;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ARPITJ;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelIndicatorDTO;
import io.harness.cvng.servicelevelobjective.entities.SLIRecord;
import io.harness.cvng.servicelevelobjective.entities.SLOHealthIndicator;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.services.api.SLIRecordService;
import io.harness.cvng.servicelevelobjective.services.api.SLOHealthIndicatorService;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelIndicatorService;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class SLOHealthIndicatorServiceImplTest extends CvNextGenTestBase {
  @Inject SLOHealthIndicatorService sloHealthIndicatorService;
  @Inject SLIRecordService sliRecordService;
  @Inject ServiceLevelIndicatorService serviceLevelIndicatorService;

  private BuilderFactory builderFactory;

  @Before
  public void setup() {
    builderFactory = BuilderFactory.getDefault();
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testupsert_insertSuccess() {
    ProjectParams projectParams = builderFactory.getProjectParams();
    ServiceLevelObjective serviceLevelObjective = builderFactory.getServiceLevelObjectiveBuilder().build();
    ServiceLevelIndicatorDTO serviceLevelIndicatorDTO = builderFactory.getServiceLevelIndicatorDTOBuilder();
    createAndSaveSLI(projectParams, serviceLevelIndicatorDTO, serviceLevelObjective.getIdentifier());
    sloHealthIndicatorService.upsert(serviceLevelObjective);
    SLOHealthIndicator newSLOHealthIndicator =
        sloHealthIndicatorService.getBySLOIdentifier(projectParams, serviceLevelObjective.getIdentifier());

    assertThat(newSLOHealthIndicator.getServiceLevelObjectiveIdentifier())
        .isEqualTo(serviceLevelObjective.getIdentifier());
  }

  private void createAndSaveSLI(ProjectParams projectParams, ServiceLevelIndicatorDTO serviceLevelIndicatorDTO,
      String serviceLevelObjectiveIdentifier) {
    serviceLevelIndicatorService.create(projectParams, Collections.singletonList(serviceLevelIndicatorDTO),
        serviceLevelObjectiveIdentifier, generateUuid(), generateUuid());
  }

  private void insertDummySLIRecords(int numOfGoodRecords, int numOfBadReocrds, Instant startTime, Instant endTime,
      String sliId, String verificationTaskId, int sliVersion) {
    List<SLIRecord.SLIState> sliStateList = new ArrayList<>();

    Duration increment = Duration.between(startTime, endTime);

    increment.dividedBy(numOfBadReocrds + numOfGoodRecords + 1);

    for (int i = 0; i < numOfGoodRecords; i++) {
      sliStateList.add(GOOD);
    }

    for (int i = 0; i < numOfBadReocrds; i++) {
      sliStateList.add(BAD);
    }

    sliRecordService.create(
        getSLIRecordParams(startTime, sliStateList, increment), sliId, verificationTaskId, sliVersion);
  }

  private List<SLIRecord.SLIRecordParam> getSLIRecordParams(
      Instant startTime, List<SLIRecord.SLIState> sliStates, Duration increment) {
    List<SLIRecord.SLIRecordParam> sliRecordParams = new ArrayList<>();
    for (int i = 0; i < sliStates.size(); i++) {
      SLIRecord.SLIState sliState = sliStates.get(i);
      sliRecordParams.add(
          SLIRecord.SLIRecordParam.builder().sliState(sliState).timeStamp(startTime.plus(increment)).build());
    }
    return sliRecordParams;
  }
}
