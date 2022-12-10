/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.triggers.webhook.helpers;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.constants.Constants.X_HUB_SIGNATURE_256;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.ngtriggers.Constants.MANDATE_GITHUB_AUTHENTICATION_TRUE_VALUE;
import static io.harness.ngtriggers.Constants.TRIGGERS_MANDATE_GITHUB_AUTHENTICATION;
import static io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus.INVALID_RUNTIME_INPUT_YAML;
import static io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus.TARGET_EXECUTION_REQUESTED;
import static io.harness.ngtriggers.beans.response.TriggerEventResponse.FinalStatus.TRIGGER_AUTHENTICATION_FAILED;
import static io.harness.ngtriggers.beans.source.WebhookTriggerType.AWS_CODECOMMIT;
import static io.harness.ngtriggers.beans.source.WebhookTriggerType.AZURE;
import static io.harness.ngtriggers.beans.source.WebhookTriggerType.BITBUCKET;
import static io.harness.ngtriggers.beans.source.WebhookTriggerType.CUSTOM;
import static io.harness.ngtriggers.beans.source.WebhookTriggerType.GITHUB;
import static io.harness.ngtriggers.beans.source.WebhookTriggerType.GITLAB;
import static io.harness.pms.contracts.triggers.Type.WEBHOOK;

import io.harness.annotations.dev.OwnedBy;
import io.harness.authorization.AuthorizationServiceHeader;
import io.harness.beans.DelegateTaskRequest;
import io.harness.beans.FeatureName;
import io.harness.beans.HeaderConfig;
import io.harness.beans.WebhookEncryptedSecretDTO;
import io.harness.delegate.beans.gitapi.GitRepoType;
import io.harness.delegate.beans.trigger.TriggerAuthenticationTaskParams;
import io.harness.delegate.beans.trigger.TriggerAuthenticationTaskResponse;
import io.harness.encryption.SecretRefData;
import io.harness.encryption.SecretRefHelper;
import io.harness.execution.PlanExecution;
import io.harness.ng.core.BaseNGAccess;
import io.harness.ng.core.NGAccess;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.ngtriggers.WebhookSecretData;
import io.harness.ngtriggers.beans.config.NGTriggerConfigV2;
import io.harness.ngtriggers.beans.dto.TriggerDetails;
import io.harness.ngtriggers.beans.dto.TriggerMappingRequestData;
import io.harness.ngtriggers.beans.dto.eventmapping.WebhookEventMappingResponse;
import io.harness.ngtriggers.beans.dto.eventmapping.WebhookEventProcessingResult;
import io.harness.ngtriggers.beans.dto.eventmapping.WebhookEventProcessingResult.WebhookEventProcessingResultBuilder;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity.NGTriggerEntityKeys;
import io.harness.ngtriggers.beans.entity.TriggerWebhookEvent;
import io.harness.ngtriggers.beans.entity.metadata.WebhookRegistrationStatus;
import io.harness.ngtriggers.beans.entity.metadata.status.WebhookAutoRegistrationStatus;
import io.harness.ngtriggers.beans.response.TargetExecutionSummary;
import io.harness.ngtriggers.beans.response.TriggerEventResponse;
import io.harness.ngtriggers.beans.source.NGTriggerType;
import io.harness.ngtriggers.helpers.TriggerEventResponseHelper;
import io.harness.ngtriggers.helpers.TriggerHelper;
import io.harness.ngtriggers.helpers.WebhookEventMapperHelper;
import io.harness.ngtriggers.utils.TaskExecutionUtils;
import io.harness.pms.contracts.triggers.ArtifactData;
import io.harness.pms.contracts.triggers.ManifestData;
import io.harness.pms.contracts.triggers.ParsedPayload;
import io.harness.pms.contracts.triggers.SourceType;
import io.harness.pms.contracts.triggers.TriggerPayload;
import io.harness.pms.contracts.triggers.TriggerPayload.Builder;
import io.harness.pms.contracts.triggers.Type;
import io.harness.pms.triggers.TriggerExecutionHelper;
import io.harness.polling.contracts.PollingResponse;
import io.harness.product.ci.scm.proto.ParseWebhookResponse;
import io.harness.remote.client.NGRestUtils;
import io.harness.repositories.spring.NGTriggerRepository;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.dto.ServicePrincipal;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.serializer.KryoSerializer;
import io.harness.tasks.BinaryResponseData;
import io.harness.tasks.ErrorResponseData;
import io.harness.tasks.ResponseData;
import io.harness.utils.PmsFeatureFlagService;

import software.wings.beans.TaskType;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.query.Criteria;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(PIPELINE)
public class TriggerEventExecutionHelper {
  private KryoSerializer kryoSerializer;
  private SecretManagerClientService ngSecretService;
  private TaskExecutionUtils taskExecutionUtils;
  private final NGSettingsClient settingsClient;
  private final NGTriggerRepository ngTriggerRepository;
  private final PmsFeatureFlagService pmsFeatureFlagService;
  private final TriggerExecutionHelper triggerExecutionHelper;
  private final WebhookEventMapperHelper webhookEventMapperHelper;

  public WebhookEventProcessingResult handleTriggerWebhookEvent(TriggerMappingRequestData mappingRequestData) {
    WebhookEventMappingResponse webhookEventMappingResponse =
        webhookEventMapperHelper.mapWebhookEventToTriggers(mappingRequestData);

    TriggerWebhookEvent triggerWebhookEvent = mappingRequestData.getTriggerWebhookEvent();
    WebhookEventProcessingResultBuilder resultBuilder = WebhookEventProcessingResult.builder();
    List<TriggerEventResponse> eventResponses = new ArrayList<>();
    if (!webhookEventMappingResponse.isFailedToFindTrigger()) {
      if (pmsFeatureFlagService.isEnabled(
              triggerWebhookEvent.getAccountId(), FeatureName.SPG_NG_GITHUB_WEBHOOK_AUTHENTICATION)) {
        authenticateTriggers(triggerWebhookEvent, webhookEventMappingResponse);
      }
      log.info("Preparing for pipeline execution request");
      resultBuilder.mappedToTriggers(true);
      if (isNotEmpty(webhookEventMappingResponse.getTriggers())) {
        for (TriggerDetails triggerDetails : webhookEventMappingResponse.getTriggers()) {
          if (triggerDetails.getNgTriggerEntity() == null) {
            log.error("Trigger Entity is empty, This should not happen, please check");
            continue;
          }
          if (triggerDetails.getAuthenticated() != null && !triggerDetails.getAuthenticated()) {
            eventResponses.add(generateEventHistoryForAuthenticationError(
                triggerWebhookEvent, triggerDetails, triggerDetails.getNgTriggerEntity()));
            continue;
          }
          long yamlVersion = triggerDetails.getNgTriggerEntity().getYmlVersion() == null
              ? 3
              : triggerDetails.getNgTriggerEntity().getYmlVersion();
          NGTriggerEntity triggerEntity = triggerDetails.getNgTriggerEntity();
          Criteria criteria = Criteria.where(NGTriggerEntityKeys.accountId)
                                  .is(triggerEntity.getAccountId())
                                  .and(NGTriggerEntityKeys.orgIdentifier)
                                  .is(triggerEntity.getOrgIdentifier())
                                  .and(NGTriggerEntityKeys.projectIdentifier)
                                  .is(triggerEntity.getProjectIdentifier())
                                  .and(NGTriggerEntityKeys.targetIdentifier)
                                  .is(triggerEntity.getTargetIdentifier())
                                  .and(NGTriggerEntityKeys.identifier)
                                  .is(triggerEntity.getIdentifier())
                                  .and(NGTriggerEntityKeys.deleted)
                                  .is(false);
          if (triggerEntity.getVersion() != null) {
            criteria.and(NGTriggerEntityKeys.version).is(triggerEntity.getVersion());
          }
          try {
            TriggerHelper.stampWebhookRegistrationInfo(triggerEntity,
                WebhookAutoRegistrationStatus.builder().registrationResult(WebhookRegistrationStatus.SUCCESS).build());
          } catch (Exception ex) {
            log.error("Webhook registration status update failed", ex);
          }
          ngTriggerRepository.updateValidationStatus(criteria, triggerEntity);
          eventResponses.add(triggerPipelineExecution(triggerWebhookEvent, triggerDetails,
              getTriggerPayloadForWebhookTrigger(webhookEventMappingResponse, triggerWebhookEvent, yamlVersion),
              triggerWebhookEvent.getPayload()));
        }
      }
    } else {
      resultBuilder.mappedToTriggers(false);
      eventResponses.add(webhookEventMappingResponse.getWebhookEventResponse());
    }

    return resultBuilder.responses(eventResponses).build();
  }

  @VisibleForTesting
  TriggerPayload getTriggerPayloadForWebhookTrigger(
      WebhookEventMappingResponse webhookEventMappingResponse, TriggerWebhookEvent triggerWebhookEvent, long version) {
    Builder builder = TriggerPayload.newBuilder().setType(Type.WEBHOOK);

    if (CUSTOM.getEntityMetadataName().equalsIgnoreCase(triggerWebhookEvent.getSourceRepoType())) {
      builder.setSourceType(SourceType.CUSTOM_REPO);
    } else if (GITHUB.getEntityMetadataName().equalsIgnoreCase(triggerWebhookEvent.getSourceRepoType())) {
      builder.setSourceType(SourceType.GITHUB_REPO);
    } else if (AZURE.getEntityMetadataName().equalsIgnoreCase(triggerWebhookEvent.getSourceRepoType())) {
      builder.setSourceType(SourceType.AZURE_REPO);
    } else if (GITLAB.getEntityMetadataName().equalsIgnoreCase(triggerWebhookEvent.getSourceRepoType())) {
      builder.setSourceType(SourceType.GITLAB_REPO);
    } else if (BITBUCKET.getEntityMetadataName().equalsIgnoreCase(triggerWebhookEvent.getSourceRepoType())) {
      builder.setSourceType(SourceType.BITBUCKET_REPO);
    } else if (AWS_CODECOMMIT.getEntityMetadataName().equalsIgnoreCase(triggerWebhookEvent.getSourceRepoType())) {
      builder.setSourceType(SourceType.BITBUCKET_REPO);
    }

    ParseWebhookResponse parseWebhookResponse = webhookEventMappingResponse.getParseWebhookResponse();
    if (parseWebhookResponse != null) {
      if (parseWebhookResponse.hasPr()) {
        builder.setParsedPayload(ParsedPayload.newBuilder().setPr(parseWebhookResponse.getPr()).build()).build();
      } else {
        builder.setParsedPayload(ParsedPayload.newBuilder().setPush(parseWebhookResponse.getPush()).build()).build();
      }
    }
    builder.setVersion(version);

    return builder.setType(WEBHOOK).build();
  }

  private TriggerEventResponse triggerPipelineExecution(TriggerWebhookEvent triggerWebhookEvent,
      TriggerDetails triggerDetails, TriggerPayload triggerPayload, String payload) {
    String runtimeInputYaml = null;
    NGTriggerEntity ngTriggerEntity = triggerDetails.getNgTriggerEntity();
    try {
      if (isEmpty(triggerDetails.getNgTriggerConfigV2().getPipelineBranchName())
          && isEmpty(triggerDetails.getNgTriggerConfigV2().getInputSetRefs())) {
        runtimeInputYaml = triggerDetails.getNgTriggerConfigV2().getInputYaml();
      } else {
        SecurityContextBuilder.setContext(
            new ServicePrincipal(AuthorizationServiceHeader.PIPELINE_SERVICE.getServiceId()));
        SourcePrincipalContextBuilder.setSourcePrincipal(
            new ServicePrincipal(AuthorizationServiceHeader.PIPELINE_SERVICE.getServiceId()));
        runtimeInputYaml = triggerExecutionHelper.fetchInputSetYAML(triggerDetails, triggerWebhookEvent);
      }
      PlanExecution response = triggerExecutionHelper.resolveRuntimeInputAndSubmitExecutionRequest(
          triggerDetails, triggerPayload, triggerWebhookEvent, payload);
      return generateEventHistoryForSuccess(
          triggerDetails, runtimeInputYaml, ngTriggerEntity, triggerWebhookEvent, response);
    } catch (Exception e) {
      return generateEventHistoryForError(triggerWebhookEvent, triggerDetails, runtimeInputYaml, ngTriggerEntity, e);
    }
  }

  public TriggerEventResponse generateEventHistoryForError(TriggerWebhookEvent triggerWebhookEvent,
      TriggerDetails triggerDetails, String runtimeInputYaml, NGTriggerEntity ngTriggerEntity, Exception e) {
    log.error(new StringBuilder(512)
                  .append("Exception occurred while requesting pipeline execution using Trigger ")
                  .append(TriggerHelper.getTriggerRef(ngTriggerEntity))
                  .append(". Exception Message: ")
                  .append(e.getMessage())
                  .toString(),
        e);

    TargetExecutionSummary targetExecutionSummary = TriggerEventResponseHelper.prepareTargetExecutionSummary(
        (PlanExecution) null, triggerDetails, runtimeInputYaml);
    return TriggerEventResponseHelper.toResponse(
        INVALID_RUNTIME_INPUT_YAML, triggerWebhookEvent, null, ngTriggerEntity, e.getMessage(), targetExecutionSummary);
  }

  public TriggerEventResponse generateEventHistoryForAuthenticationError(
      TriggerWebhookEvent triggerWebhookEvent, TriggerDetails triggerDetails, NGTriggerEntity ngTriggerEntity) {
    log.error("Trigger Authentication Failed {}", TriggerHelper.getTriggerRef(ngTriggerEntity));
    TargetExecutionSummary targetExecutionSummary =
        TriggerEventResponseHelper.prepareTargetExecutionSummary((PlanExecution) null, triggerDetails, null);
    return TriggerEventResponseHelper.toResponse(
        TRIGGER_AUTHENTICATION_FAILED, triggerWebhookEvent, null, ngTriggerEntity, null, targetExecutionSummary);
  }

  public List<TriggerEventResponse> processTriggersForActivation(
      List<TriggerDetails> mappedTriggers, PollingResponse pollingResponse) {
    List<TriggerEventResponse> responses = new ArrayList<>();
    for (TriggerDetails triggerDetails : mappedTriggers) {
      try {
        responses.add(triggerEventPipelineExecution(triggerDetails, pollingResponse));
      } catch (Exception e) {
        log.error("Error while requesting pipeline execution for Build Trigger: "
            + TriggerHelper.getTriggerRef(triggerDetails.getNgTriggerEntity()));
      }
    }

    return responses;
  }

  public TriggerEventResponse triggerEventPipelineExecution(
      TriggerDetails triggerDetails, PollingResponse pollingResponse) {
    String runtimeInputYaml = null;
    NGTriggerEntity ngTriggerEntity = triggerDetails.getNgTriggerEntity();
    TriggerWebhookEvent pseudoEvent = TriggerWebhookEvent.builder()
                                          .accountId(ngTriggerEntity.getAccountId())
                                          .createdAt(System.currentTimeMillis())
                                          .build();
    try {
      if (isEmpty(triggerDetails.getNgTriggerConfigV2().getPipelineBranchName())
          && isEmpty(triggerDetails.getNgTriggerConfigV2().getInputSetRefs())) {
        runtimeInputYaml = triggerDetails.getNgTriggerConfigV2().getInputYaml();
      } else {
        SecurityContextBuilder.setContext(
            new ServicePrincipal(AuthorizationServiceHeader.PIPELINE_SERVICE.getServiceId()));
        SourcePrincipalContextBuilder.setSourcePrincipal(
            new ServicePrincipal(AuthorizationServiceHeader.PIPELINE_SERVICE.getServiceId()));
        runtimeInputYaml = triggerExecutionHelper.fetchInputSetYAML(triggerDetails, pseudoEvent);
      }

      Type buildType = ngTriggerEntity.getType() == NGTriggerType.ARTIFACT ? Type.ARTIFACT : Type.MANIFEST;
      Builder triggerPayloadBuilder = TriggerPayload.newBuilder().setType(buildType);

      String build = pollingResponse.getBuildInfo().getVersions(0);
      if (buildType == Type.ARTIFACT) {
        triggerPayloadBuilder.setArtifactData(ArtifactData.newBuilder().setBuild(build).build());
      } else if (buildType == Type.MANIFEST) {
        triggerPayloadBuilder.setManifestData(ManifestData.newBuilder().setVersion(build).build());
      }

      PlanExecution response = triggerExecutionHelper.resolveRuntimeInputAndSubmitExecutionReques(
          triggerDetails, triggerPayloadBuilder.build());
      return generateEventHistoryForSuccess(triggerDetails, runtimeInputYaml, ngTriggerEntity, pseudoEvent, response);
    } catch (Exception e) {
      return generateEventHistoryForError(pseudoEvent, triggerDetails, runtimeInputYaml, ngTriggerEntity, e);
    }
  }

  private TriggerEventResponse generateEventHistoryForSuccess(TriggerDetails triggerDetails, String runtimeInputYaml,
      NGTriggerEntity ngTriggerEntity, TriggerWebhookEvent pseudoEvent, PlanExecution response) {
    TargetExecutionSummary targetExecutionSummary =
        TriggerEventResponseHelper.prepareTargetExecutionSummary(response, triggerDetails, runtimeInputYaml);

    log.info(ngTriggerEntity.getTargetType() + " execution was requested successfully for Pipeline: "
        + ngTriggerEntity.getTargetIdentifier() + ", using trigger: " + ngTriggerEntity.getIdentifier());

    return TriggerEventResponseHelper.toResponse(TARGET_EXECUTION_REQUESTED, pseudoEvent, ngTriggerEntity,
        "Pipeline execution was requested successfully", targetExecutionSummary);
  }

  private void authenticateTriggers(
      TriggerWebhookEvent triggerWebhookEvent, WebhookEventMappingResponse webhookEventMappingResponse) {
    List<TriggerDetails> triggersToAuthenticate =
        getTriggersToAuthenticate(triggerWebhookEvent, webhookEventMappingResponse);
    if (isEmpty(triggersToAuthenticate)) {
      return;
    }
    String hashedPayload = getHashedPayload(triggerWebhookEvent);
    if (hashedPayload == null) {
      for (TriggerDetails triggerDetails : triggersToAuthenticate) {
        triggerDetails.setAuthenticated(false);
      }
      return;
    }
    List<WebhookSecretData> webhookSecretData = new ArrayList<>();
    for (TriggerDetails triggerDetails : triggersToAuthenticate) {
      NGTriggerConfigV2 ngTriggerConfigV2 = triggerDetails.getNgTriggerConfigV2();
      NGAccess basicNGAccessObject = BaseNGAccess.builder()
                                         .accountIdentifier(triggerWebhookEvent.getAccountId())
                                         .orgIdentifier(ngTriggerConfigV2.getOrgIdentifier())
                                         .projectIdentifier(ngTriggerConfigV2.getProjectIdentifier())
                                         .build();
      SecretRefData secretRefData =
          SecretRefHelper.createSecretRef(ngTriggerConfigV2.getEncryptedWebhookSecretIdentifier());
      WebhookEncryptedSecretDTO webhookEncryptedSecretDTO =
          WebhookEncryptedSecretDTO.builder().secretRef(secretRefData).build();
      List<EncryptedDataDetail> encryptedDataDetail =
          ngSecretService.getEncryptionDetails(basicNGAccessObject, webhookEncryptedSecretDTO);
      webhookSecretData.add(WebhookSecretData.builder()
                                .webhookEncryptedSecretDTO(webhookEncryptedSecretDTO)
                                .encryptedDataDetails(encryptedDataDetail)
                                .build());
    }
    ResponseData responseData = taskExecutionUtils.executeSyncTask(
        DelegateTaskRequest.builder()
            .accountId(triggerWebhookEvent.getAccountId())
            .executionTimeout(Duration.ofSeconds(60)) // todo: Gather suggestions regarding this timeout value.
            .taskType(TaskType.TRIGGER_AUTHENTICATION_TASK.toString())
            .taskParameters(TriggerAuthenticationTaskParams.builder()
                                .eventPayload(triggerWebhookEvent.getPayload())
                                .gitRepoType(GitRepoType.GITHUB)
                                .hashedPayload(hashedPayload)
                                .webhookSecretData(webhookSecretData)
                                .build())
            .build());

    if (BinaryResponseData.class.isAssignableFrom(responseData.getClass())) {
      BinaryResponseData binaryResponseData = (BinaryResponseData) responseData;
      Object object = kryoSerializer.asInflatedObject(binaryResponseData.getData());
      if (object instanceof TriggerAuthenticationTaskResponse) {
        int index = 0;
        for (Boolean isWebhookAuthenticated :
            ((TriggerAuthenticationTaskResponse) object).getTriggersAuthenticationStatus()) {
          triggersToAuthenticate.get(index).setAuthenticated(isWebhookAuthenticated);
          index++;
        }
      } else if (object instanceof ErrorResponseData) {
        ErrorResponseData errorResponseData = (ErrorResponseData) object;
        log.error("Failed to authenticate triggers. Reason: {}", errorResponseData.getErrorMessage());
        for (TriggerDetails triggerDetails : triggersToAuthenticate) {
          triggerDetails.setAuthenticated(false);
        }
      }
    }
  }

  private List<TriggerDetails> getTriggersToAuthenticate(
      TriggerWebhookEvent triggerWebhookEvent, WebhookEventMappingResponse webhookEventMappingResponse) {
    // Only GitHub events authentication is supported for now
    List<TriggerDetails> triggersToAuthenticate = new ArrayList<>();
    if (GITHUB.name().equalsIgnoreCase(triggerWebhookEvent.getSourceRepoType())) {
      Boolean ngSettingsFFEnabled =
          pmsFeatureFlagService.isEnabled(triggerWebhookEvent.getAccountId(), FeatureName.NG_SETTINGS);
      for (TriggerDetails triggerDetails : webhookEventMappingResponse.getTriggers()) {
        NGTriggerConfigV2 ngTriggerConfigV2 = triggerDetails.getNgTriggerConfigV2();
        if (ngTriggerConfigV2 != null
            && shouldAuthenticateTrigger(triggerWebhookEvent, ngTriggerConfigV2, ngSettingsFFEnabled)) {
          triggersToAuthenticate.add(triggerDetails);
        }
      }
    }
    return triggersToAuthenticate;
  }

  private String getHashedPayload(TriggerWebhookEvent triggerWebhookEvent) {
    String hashedPayload = null;
    for (HeaderConfig headerConfig : triggerWebhookEvent.getHeaders()) {
      if (headerConfig.getKey().equalsIgnoreCase(X_HUB_SIGNATURE_256)) {
        List<String> values = headerConfig.getValues();
        if (isNotEmpty(values) && values.size() == 1) {
          hashedPayload = values.get(0);
        }
        break;
      }
    }
    return hashedPayload;
  }

  private Boolean shouldAuthenticateTrigger(
      TriggerWebhookEvent triggerWebhookEvent, NGTriggerConfigV2 ngTriggerConfigV2, Boolean ngSettingsFFEnabled) {
    if (ngSettingsFFEnabled) {
      String mandatoryAuth = NGRestUtils
                                 .getResponse(settingsClient.getSetting(TRIGGERS_MANDATE_GITHUB_AUTHENTICATION,
                                     triggerWebhookEvent.getAccountId(), ngTriggerConfigV2.getOrgIdentifier(),
                                     ngTriggerConfigV2.getProjectIdentifier()))
                                 .getValue();
      if (mandatoryAuth.equals(MANDATE_GITHUB_AUTHENTICATION_TRUE_VALUE)) {
        return true;
      }
    }
    return isNotEmpty(ngTriggerConfigV2.getEncryptedWebhookSecretIdentifier());
  }
}
