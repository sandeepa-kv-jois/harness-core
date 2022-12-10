/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng;

import static io.harness.annotations.dev.HarnessTeam.CV;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.DecryptableEntity;
import io.harness.cvng.beans.DataCollectionRequest;
import io.harness.cvng.beans.cvnglog.ApiCallLogDTO;
import io.harness.cvng.beans.cvnglog.ApiCallLogDTO.ApiCallLogDTOField;
import io.harness.cvng.beans.cvnglog.TraceableType;
import io.harness.datacollection.DataCollectionDSLService;
import io.harness.datacollection.entity.RuntimeParameters;
import io.harness.perpetualtask.datacollection.DataCollectionLogContext;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.SecretDecryptionService;
import io.harness.serializer.JsonUtils;

import software.wings.delegatetasks.DelegateLogService;
import software.wings.delegatetasks.cv.DataCollectionException;
import software.wings.service.intfc.cvng.CVNGDataCollectionDelegateService;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutorService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(CV)
@TargetModule(HarnessModule._420_DELEGATE_AGENT)
public class CVNGDataCollectionDelegateServiceImpl implements CVNGDataCollectionDelegateService {
  @Inject private DataCollectionDSLService dataCollectionDSLService;
  @Inject private SecretDecryptionService secretDecryptionService;
  @Inject private Clock clock;
  @Inject private DelegateLogService delegateLogService;
  @Inject @Named("cvngSyncCallExecutor") protected ExecutorService cvngSyncCallExecutor;

  @Override
  public String getDataCollectionResult(String accountId, DataCollectionRequest dataCollectionRequest,
      List<List<EncryptedDataDetail>> encryptedDataDetails) {
    try (DataCollectionLogContext ignored = new DataCollectionLogContext(accountId, dataCollectionRequest)) {
      if (dataCollectionRequest.getConnectorConfigDTO() instanceof DecryptableEntity) {
        List<DecryptableEntity> decryptableEntities =
            dataCollectionRequest.getConnectorConfigDTO().getDecryptableEntities();

        if (isNotEmpty(decryptableEntities)) {
          if (decryptableEntities.size() != encryptedDataDetails.size()) {
            log.warn("Size of decryptableEntities is not same as size of encryptedDataDetails. "
                + "Probably it is because of version difference between delegate and manager and decyptable entities got added/removed.");
          }
          // using min of encryptedDataDetails, decryptableEntities size to avoid index out of bound exception because
          // of comparability issues. This allows us to add/remove decryptableEntities without breaking this. This can
          // still cause issues if not done carefully.
          for (int decryptableEntityIndex = 0;
               decryptableEntityIndex < Math.min(decryptableEntities.size(), encryptedDataDetails.size());
               decryptableEntityIndex++) {
            DecryptableEntity decryptableEntity = decryptableEntities.get(decryptableEntityIndex);
            List<EncryptedDataDetail> encryptedDataDetail = encryptedDataDetails.get(decryptableEntityIndex);
            secretDecryptionService.decrypt(decryptableEntity, encryptedDataDetail);
          }
        }
      }

      try {
        String dsl = dataCollectionRequest.getDSL();
        Instant now = clock.instant();
        final RuntimeParameters runtimeParameters = RuntimeParameters.builder()
                                                        .baseUrl(dataCollectionRequest.getBaseUrl())
                                                        .commonHeaders(dataCollectionRequest.collectionHeaders())
                                                        .commonOptions(dataCollectionRequest.collectionParams())
                                                        .otherEnvVariables(dataCollectionRequest.fetchDslEnvVariables())
                                                        .endTime(dataCollectionRequest.getEndTime(now))
                                                        .startTime(dataCollectionRequest.getStartTime(now))
                                                        .build();
        dataCollectionDSLService.registerDatacollectionExecutorService(cvngSyncCallExecutor);
        log.info("Starting execution of DSL ");
        String response = JsonUtils.asJson(dataCollectionDSLService.execute(dsl, runtimeParameters, callDetails -> {
          // TODO: write unit test case for this lambda expression.
          if (dataCollectionRequest.getTracingId() != null) {
            final ApiCallLogDTO cvngLogDTO = ApiCallLogDTO.builder()
                                                 .traceableId(dataCollectionRequest.getTracingId())
                                                 .traceableType(TraceableType.ONBOARDING)
                                                 .accountId(accountId)
                                                 .startTime(dataCollectionRequest.getStartTime(now).toEpochMilli())
                                                 .endTime(dataCollectionRequest.getEndTime(now).toEpochMilli())
                                                 .requestTime(callDetails.getRequestTime().toEpochMilli())
                                                 .responseTime(callDetails.getResponseTime().toEpochMilli())
                                                 .build();
            cvngLogDTO.addFieldToRequest(ApiCallLogDTOField.builder()
                                             .name("url")
                                             .type(ApiCallLogDTO.FieldType.URL)
                                             .value(callDetails.getRequest().request().url().toString())
                                             .build());

            cvngLogDTO.addFieldToResponse(callDetails.getResponse().code(),
                (callDetails.getResponse() != null && callDetails.getResponse().body() != null)
                    ? callDetails.getResponse().body()
                    : callDetails.getResponse(),
                ApiCallLogDTO.FieldType.JSON);
            delegateLogService.save(accountId, cvngLogDTO);
          }
        }));
        log.info("Returning DSL result of length : " + response.length());
        return response;
      } catch (Exception exception) {
        String errorMessage = parseDSLExceptionMessage(exception.getMessage());
        log.error(errorMessage);
        throw new DataCollectionException(errorMessage);
      }
    }
  }

  private String parseDSLExceptionMessage(String message) {
    String[] dslExceptionMsgs = new String[] {"io.harness.datacollection.exception.DataCollectionDSLException:",
        "io.harness.datacollection.exception.DataCollectionException:",
        "io.harness.datacollection.exception.DataCollectionRuntimeException:",
        "io.harness.datacollection.exception.RateLimitExceededException:"};
    for (String exceptionMsg : dslExceptionMsgs) {
      message.replace(exceptionMsg, "");
    }
    return message.stripTrailing();
  }
}
