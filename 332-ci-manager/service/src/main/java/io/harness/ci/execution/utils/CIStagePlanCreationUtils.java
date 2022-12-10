/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.utils;

import io.harness.beans.stages.IntegrationStageNode;
import io.harness.ci.execution.CIAccountExecutionMetadata;
import io.harness.ci.license.CILicenseService;
import io.harness.data.structure.CollectionUtils;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.licensing.Edition;
import io.harness.licensing.beans.summary.LicensesWithSummaryDTO;
import io.harness.plancreator.steps.common.StageElementParameters;
import io.harness.plancreator.steps.common.StageElementParameters.StageElementParametersBuilder;
import io.harness.pms.tags.TagUtils;
import io.harness.pms.yaml.ParameterField;
import io.harness.repositories.CIAccountExecutionMetadataRepository;
import io.harness.steps.SdkCoreStepUtils;
import io.harness.yaml.utils.NGVariablesUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CIStagePlanCreationUtils {
  public static StageElementParametersBuilder getStageParameters(IntegrationStageNode stageNode) {
    TagUtils.removeUuidFromTags(stageNode.getTags());

    StageElementParametersBuilder stageBuilder = StageElementParameters.builder();
    stageBuilder.name(stageNode.getName());
    stageBuilder.identifier(stageNode.getIdentifier());
    stageBuilder.description(SdkCoreStepUtils.getParameterFieldHandleValueNull(stageNode.getDescription()));
    stageBuilder.failureStrategies(stageNode.getFailureStrategies());
    stageBuilder.skipCondition(stageNode.getSkipCondition());
    stageBuilder.when(stageNode.getWhen());
    stageBuilder.type(stageNode.getType());
    stageBuilder.uuid(stageNode.getUuid());
    stageBuilder.variables(
        ParameterField.createValueField(NGVariablesUtils.getMapOfVariables(stageNode.getVariables())));
    stageBuilder.tags(CollectionUtils.emptyIfNull(stageNode.getTags()));

    return stageBuilder;
  }

  public static void validateFreeAccountStageExecutionLimit(
      CIAccountExecutionMetadataRepository accountExecutionMetadataRepository, CILicenseService ciLicenseService,
      String accountId) {
    LicensesWithSummaryDTO licensesWithSummaryDTO = ciLicenseService.getLicenseSummary(accountId);
    if (licensesWithSummaryDTO != null && licensesWithSummaryDTO.getEdition() == Edition.FREE) {
      Optional<CIAccountExecutionMetadata> accountExecutionMetadata =
          accountExecutionMetadataRepository.findByAccountId(accountId);

      if (accountExecutionMetadata.isPresent()) {
        LocalDate startDate = Instant.now().atZone(ZoneId.systemDefault()).toLocalDate();
        YearMonth yearMonth = YearMonth.of(startDate.getYear(), startDate.getMonth());
        String day = yearMonth + "-" + startDate.getDayOfMonth();
        Map<String, Long> countPerDay = accountExecutionMetadata.get().getAccountExecutionInfo().getCountPerDay();
        if (countPerDay != null) {
          if (countPerDay.getOrDefault(day, 0L) >= 100) {
            log.error("Daily stage execution rate limit for free plan has reached for accountId {}", accountId);
            throw new CIStageExecutionException(
                "Execution limit has reached for the day, Please reach out to Harness support");
          }
        }
      }
    }
  }
}
