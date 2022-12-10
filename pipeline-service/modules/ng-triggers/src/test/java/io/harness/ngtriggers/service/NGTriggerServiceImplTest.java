/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.service;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.exception.WingsException.USER;
import static io.harness.rule.OwnerRule.ADWAIT;
import static io.harness.rule.OwnerRule.HARSH;
import static io.harness.rule.OwnerRule.MATT;
import static io.harness.rule.OwnerRule.SRIDHAR;
import static io.harness.rule.OwnerRule.VINICIUS;

import static com.fasterxml.jackson.dataformat.yaml.YAMLGenerator.Feature.USE_NATIVE_TYPE_ID;
import static junit.framework.TestCase.assertTrue;
import static junit.framework.TestCase.fail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.HeaderConfig;
import io.harness.category.element.UnitTests;
import io.harness.common.NGTimeConversionHelper;
import io.harness.exception.AccessDeniedException;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.ngtriggers.beans.config.NGTriggerConfigV2;
import io.harness.ngtriggers.beans.dto.TriggerDetails;
import io.harness.ngtriggers.beans.dto.TriggerYamlDiffDTO;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.beans.entity.metadata.NGTriggerMetadata;
import io.harness.ngtriggers.beans.entity.metadata.WebhookMetadata;
import io.harness.ngtriggers.beans.entity.metadata.WebhookRegistrationStatus;
import io.harness.ngtriggers.beans.entity.metadata.catalog.TriggerCatalogItem;
import io.harness.ngtriggers.beans.entity.metadata.catalog.TriggerCatalogType;
import io.harness.ngtriggers.beans.entity.metadata.catalog.TriggerCategory;
import io.harness.ngtriggers.beans.entity.metadata.status.TriggerStatus;
import io.harness.ngtriggers.beans.entity.metadata.status.WebhookAutoRegistrationStatus;
import io.harness.ngtriggers.beans.entity.metadata.status.WebhookInfo;
import io.harness.ngtriggers.beans.source.NGTriggerSourceV2;
import io.harness.ngtriggers.beans.source.NGTriggerType;
import io.harness.ngtriggers.beans.source.scheduled.CronTriggerSpec;
import io.harness.ngtriggers.beans.source.scheduled.ScheduledTriggerConfig;
import io.harness.ngtriggers.beans.source.webhook.v2.WebhookTriggerConfigV2;
import io.harness.ngtriggers.beans.target.TargetType;
import io.harness.ngtriggers.buildtriggers.helpers.BuildTriggerHelper;
import io.harness.ngtriggers.helpers.TriggerCatalogHelper;
import io.harness.ngtriggers.mapper.NGTriggerElementMapper;
import io.harness.ngtriggers.service.impl.NGTriggerServiceImpl;
import io.harness.ngtriggers.utils.PollingSubscriptionHelper;
import io.harness.ngtriggers.validations.TriggerValidationHandler;
import io.harness.outbox.api.OutboxService;
import io.harness.pipeline.remote.PipelineServiceClient;
import io.harness.pms.inputset.MergeInputSetResponseDTOPMS;
import io.harness.pms.rbac.PipelineRbacPermissions;
import io.harness.polling.client.PollingResourceClient;
import io.harness.polling.contracts.GitPollingPayload;
import io.harness.polling.contracts.PollingItem;
import io.harness.polling.contracts.PollingPayloadData;
import io.harness.repositories.spring.NGTriggerRepository;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;
import io.harness.utils.PmsFeatureFlagService;
import io.harness.utils.YamlPipelineUtils;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.google.common.io.Resources;
import com.mongodb.client.result.DeleteResult;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.stubbing.Answer;
import org.springframework.data.mongodb.core.query.Criteria;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(PIPELINE)
public class NGTriggerServiceImplTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @Mock AccessControlClient accessControlClient;
  @Mock NGSettingsClient settingsClient;
  @Mock NGTriggerElementMapper ngTriggerElementMapper;
  @Mock PipelineServiceClient pipelineServiceClient;
  @InjectMocks NGTriggerServiceImpl ngTriggerServiceImpl;
  @Mock BuildTriggerHelper validationHelper;
  @Mock PmsFeatureFlagService pmsFeatureFlagService;
  @Mock NGTriggerRepository ngTriggerRepository;

  @Mock OutboxService outboxService;
  @Mock ExecutorService executorService;
  @Mock PollingSubscriptionHelper pollingSubscriptionHelper;

  @Mock KryoSerializer kryoSerializer;

  @Mock PollingResourceClient pollingResourceClient;

  TriggerValidationHandler triggerValidationHandler;

  @Mock TriggerCatalogHelper triggerCatalogHelper;

  private final String ACCOUNT_ID = "account_id";
  private final String ORG_IDENTIFIER = "orgId";
  private final String PROJ_IDENTIFIER = "projId";
  private final String IDENTIFIER = "first_trigger";
  private final String NAME = "first trigger";
  private final String PIPELINE_IDENTIFIER = "myPipeline";
  private final String WEBHOOK_ID = "webhook_id";

  private final String CONNECTOR_REF = "connector_ref";

  private final String POLLING_DOC_ID = "polling_doc_id";

  private final String X_API_KEY = "x-api-key";
  private final String API_KEY = "pat.kmpySmUISimoRrJL6NL73w.6350538bbfd93f472a549604.iCMeDe82VbCG6YnWw80h";
  ClassLoader classLoader = getClass().getClassLoader();

  String filenameGitSync = "ng-trigger-github-pr-gitsync.yaml";
  WebhookTriggerConfigV2 webhookTriggerConfig;

  String ngTriggerYamlWithGitSync;
  NGTriggerConfigV2 ngTriggerConfig;

  WebhookMetadata metadata;

  NGTriggerMetadata ngTriggerMetadata;

  NGTriggerEntity ngTriggerEntityGitSync = NGTriggerEntity.builder()
                                               .accountId(ACCOUNT_ID)
                                               .orgIdentifier(ORG_IDENTIFIER)
                                               .projectIdentifier(PROJ_IDENTIFIER)
                                               .targetIdentifier(PIPELINE_IDENTIFIER)
                                               .identifier(IDENTIFIER)
                                               .name(NAME)
                                               .targetType(TargetType.PIPELINE)
                                               .type(NGTriggerType.WEBHOOK)
                                               .metadata(ngTriggerMetadata)
                                               .yaml(ngTriggerYamlWithGitSync)
                                               .version(0L)
                                               .build();
  @Before
  public void setup() throws Exception {
    on(ngTriggerServiceImpl).set("ngTriggerElementMapper", ngTriggerElementMapper);

    when(validationHelper.fetchPipelineForTrigger(any())).thenReturn(Optional.empty());
    ngTriggerYamlWithGitSync =
        Resources.toString(Objects.requireNonNull(classLoader.getResource(filenameGitSync)), StandardCharsets.UTF_8);
    ngTriggerConfig = YamlPipelineUtils.read(ngTriggerYamlWithGitSync, NGTriggerConfigV2.class);

    webhookTriggerConfig = (WebhookTriggerConfigV2) ngTriggerConfig.getSource().getSpec();
    metadata = WebhookMetadata.builder().type(webhookTriggerConfig.getType().getValue()).build();

    ngTriggerMetadata = NGTriggerMetadata.builder().webhook(metadata).build();
  }

  @Test
  @Owner(developers = MATT)
  @Category(UnitTests.class)
  public void testCronTriggerFailure() {
    TriggerDetails triggerDetails =
        TriggerDetails.builder()
            .ngTriggerEntity(NGTriggerEntity.builder().identifier("id").name("name").build())
            .ngTriggerConfigV2(
                NGTriggerConfigV2.builder()
                    .source(NGTriggerSourceV2.builder()
                                .type(NGTriggerType.SCHEDULED)
                                .spec(ScheduledTriggerConfig.builder()
                                          .type("Cron")
                                          .spec(CronTriggerSpec.builder().expression("not a cron").build())
                                          .build())
                                .build())
                    .build())
            .build();
    try {
      ngTriggerServiceImpl.validateTriggerConfig(triggerDetails);
      fail("bad cron not caught");
    } catch (Exception e) {
      assertThat(e instanceof IllegalArgumentException).isTrue();
    }
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testEmptyIdentifierTriggerFailure() {
    NGTriggerEntity ngTriggerEntity = NGTriggerEntity.builder().identifier("").name("name").build();
    TriggerDetails triggerDetails = TriggerDetails.builder().ngTriggerEntity(ngTriggerEntity).build();

    assertThatThrownBy(() -> ngTriggerServiceImpl.validateTriggerConfig(triggerDetails))
        .isInstanceOf(InvalidArgumentsException.class);
    assertThatThrownBy(() -> ngTriggerServiceImpl.validateTriggerConfig(triggerDetails))
        .hasMessage("Identifier can not be empty");

    ngTriggerEntity.setIdentifier(null);
    assertThatThrownBy(() -> ngTriggerServiceImpl.validateTriggerConfig(triggerDetails))
        .isInstanceOf(InvalidArgumentsException.class);
    assertThatThrownBy(() -> ngTriggerServiceImpl.validateTriggerConfig(triggerDetails))
        .hasMessage("Identifier can not be empty");

    ngTriggerEntity.setIdentifier("  ");
    assertThatThrownBy(() -> ngTriggerServiceImpl.validateTriggerConfig(triggerDetails))
        .isInstanceOf(InvalidArgumentsException.class);
    assertThatThrownBy(() -> ngTriggerServiceImpl.validateTriggerConfig(triggerDetails))
        .hasMessage("Identifier can not be empty");

    ngTriggerEntity.setIdentifier("a1");
    ngTriggerEntity.setName("");
    assertThatThrownBy(() -> ngTriggerServiceImpl.validateTriggerConfig(triggerDetails))
        .isInstanceOf(InvalidArgumentsException.class);
    assertThatThrownBy(() -> ngTriggerServiceImpl.validateTriggerConfig(triggerDetails))
        .hasMessage("Name can not be empty");
  }

  @Test
  @Owner(developers = MATT)
  @Category(UnitTests.class)
  public void testCronTrigger() {
    TriggerDetails triggerDetails =
        TriggerDetails.builder()
            .ngTriggerEntity(NGTriggerEntity.builder().identifier("id").name("name").build())
            .ngTriggerConfigV2(
                NGTriggerConfigV2.builder()
                    .source(NGTriggerSourceV2.builder()
                                .type(NGTriggerType.SCHEDULED)
                                .spec(ScheduledTriggerConfig.builder()
                                          .type("Cron")
                                          .spec(CronTriggerSpec.builder().expression("20 4 * * *").build())
                                          .build())
                                .build())
                    .build())
            .build();

    ngTriggerServiceImpl.validateTriggerConfig(triggerDetails);
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void testIsBranchExpr() {
    assertThat(ngTriggerServiceImpl.isBranchExpr("<+trigger.branch>")).isEqualTo(true);
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  public void testInputSetValidation() throws Exception {
    TriggerDetails triggerDetails = TriggerDetails.builder()
                                        .ngTriggerEntity(ngTriggerEntityGitSync)
                                        .ngTriggerConfigV2(NGTriggerConfigV2.builder()
                                                               .inputSetRefs(Arrays.asList("inputSet1", "inputSet2"))
                                                               .pipelineBranchName("pipelineBranchName")
                                                               .build())
                                        .build();
    Call<ResponseDTO<MergeInputSetResponseDTOPMS>> mergeInputSetResponseDTOPMS = mock(Call.class);
    when(ngTriggerElementMapper.toTriggerConfigV2(ngTriggerEntityGitSync))
        .thenReturn(triggerDetails.getNgTriggerConfigV2());

    when(pipelineServiceClient.getMergeInputSetFromPipelineTemplate(any(), any(), any(), any(), any(), any()))
        .thenReturn(mergeInputSetResponseDTOPMS);
    when(mergeInputSetResponseDTOPMS.execute())
        .thenReturn(Response.success(
            ResponseDTO.newResponse(MergeInputSetResponseDTOPMS.builder().isErrorResponse(false).build())));
    assertThat(ngTriggerServiceImpl.validateInputSetsInternal(triggerDetails))
        .isEqualTo(MergeInputSetResponseDTOPMS.builder().isErrorResponse(false).build());
  }

  @Test
  @Owner(developers = SRIDHAR)
  @Category(UnitTests.class)
  public void testHardDelete() {
    NGTriggerEntity ngTrigger =
        NGTriggerEntity.builder()
            .accountId(ACCOUNT_ID)
            .enabled(Boolean.TRUE)
            .deleted(Boolean.FALSE)
            .identifier(IDENTIFIER)
            .projectIdentifier(PROJ_IDENTIFIER)
            .targetIdentifier(PIPELINE_IDENTIFIER)
            .orgIdentifier(ORG_IDENTIFIER)
            .metadata(NGTriggerMetadata.builder().webhook(WebhookMetadata.builder().type("Gitlab").build()).build())
            .type(NGTriggerType.WEBHOOK)
            .build();

    Optional<NGTriggerEntity> optionalNGTrigger = Optional.of(ngTrigger);

    when(ngTriggerRepository
             .findByAccountIdAndOrgIdentifierAndProjectIdentifierAndTargetIdentifierAndIdentifierAndDeletedNot(
                 eq(ACCOUNT_ID), eq(ORG_IDENTIFIER), eq(PROJ_IDENTIFIER), eq(PIPELINE_IDENTIFIER), eq(IDENTIFIER),
                 eq(Boolean.TRUE)))
        .thenReturn(optionalNGTrigger);
    when(ngTriggerRepository.hardDelete(any(Criteria.class))).thenReturn(DeleteResult.acknowledged(1));

    Boolean res =
        ngTriggerServiceImpl.delete(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, IDENTIFIER, null);
    assertTrue(res);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = SRIDHAR)
  @Category(UnitTests.class)
  public void testDeleteException() {
    NGTriggerEntity ngTrigger =
        NGTriggerEntity.builder()
            .accountId(ACCOUNT_ID)
            .enabled(Boolean.TRUE)
            .deleted(Boolean.FALSE)
            .identifier(IDENTIFIER)
            .projectIdentifier(PROJ_IDENTIFIER)
            .targetIdentifier(PIPELINE_IDENTIFIER)
            .orgIdentifier(ORG_IDENTIFIER)
            .type(NGTriggerType.WEBHOOK)
            .metadata(NGTriggerMetadata.builder().webhook(WebhookMetadata.builder().type("Gitlab").build()).build())
            .build();

    Optional<NGTriggerEntity> optionalNGTrigger = Optional.of(ngTrigger);

    when(ngTriggerRepository
             .findByAccountIdAndOrgIdentifierAndProjectIdentifierAndTargetIdentifierAndIdentifierAndDeletedNot(
                 eq(ACCOUNT_ID), eq(ORG_IDENTIFIER), eq(PROJ_IDENTIFIER), eq(PIPELINE_IDENTIFIER), eq(IDENTIFIER),
                 eq(Boolean.TRUE)))
        .thenReturn(optionalNGTrigger);
    when(ngTriggerRepository.hardDelete(any(Criteria.class))).thenReturn(DeleteResult.unacknowledged());

    ngTriggerServiceImpl.delete(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, IDENTIFIER, null);
  }
  @Test
  @Owner(developers = SRIDHAR)
  @Category(UnitTests.class)
  public void testHardDeleteWebhookPolling() throws IOException {
    NGTriggerEntity ngTrigger =
        NGTriggerEntity.builder()
            .accountId(ACCOUNT_ID)
            .enabled(Boolean.TRUE)
            .deleted(Boolean.FALSE)
            .identifier(IDENTIFIER)
            .projectIdentifier(PROJ_IDENTIFIER)
            .targetIdentifier(PIPELINE_IDENTIFIER)
            .orgIdentifier(ORG_IDENTIFIER)
            .type(NGTriggerType.WEBHOOK)
            .pollInterval("2m")
            .metadata(NGTriggerMetadata.builder().webhook(WebhookMetadata.builder().type("Gitlab").build()).build())
            .triggerStatus(TriggerStatus.builder()
                               .webhookAutoRegistrationStatus(WebhookAutoRegistrationStatus.builder()
                                                                  .registrationResult(WebhookRegistrationStatus.SUCCESS)
                                                                  .build())
                               .webhookInfo(WebhookInfo.builder().webhookId(WEBHOOK_ID).build())
                               .build())
            .build();

    PollingItem pollingItem = createPollingItem(ngTrigger);
    Call<Boolean> call = mock(Call.class);
    ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
    Optional<NGTriggerEntity> optionalNGTrigger = Optional.of(ngTrigger);
    byte[] bytes = {70};

    when(pmsFeatureFlagService.isEnabled(eq(ACCOUNT_ID), eq(FeatureName.CD_GIT_WEBHOOK_POLLING)))
        .thenReturn(Boolean.TRUE);
    when(ngTriggerRepository
             .findByAccountIdAndOrgIdentifierAndProjectIdentifierAndTargetIdentifierAndIdentifierAndDeletedNot(
                 eq(ACCOUNT_ID), eq(ORG_IDENTIFIER), eq(PROJ_IDENTIFIER), eq(PIPELINE_IDENTIFIER), eq(IDENTIFIER),
                 eq(Boolean.TRUE)))
        .thenReturn(optionalNGTrigger);
    when(ngTriggerRepository.hardDelete(any(Criteria.class))).thenReturn(DeleteResult.acknowledged(1));
    when(executorService.submit(runnableCaptor.capture())).then(executeRunnable(runnableCaptor));
    when(pollingSubscriptionHelper.generatePollingItem(eq(ngTrigger))).thenReturn(pollingItem);
    when(pollingResourceClient.unsubscribe(any())).thenReturn(call);
    when(call.execute()).thenReturn(Response.success(Boolean.TRUE));
    when(kryoSerializer.asBytes(any(PollingItem.class))).thenReturn(bytes);

    Boolean res =
        ngTriggerServiceImpl.delete(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, IDENTIFIER, null);
    assertTrue(res);
  }

  private PollingItem createPollingItem(NGTriggerEntity ngTrigger) {
    return PollingItem.newBuilder()
        .setPollingDocId(POLLING_DOC_ID)
        .setPollingPayloadData(
            PollingPayloadData.newBuilder()
                .setConnectorRef(CONNECTOR_REF)
                .setGitPollPayload(GitPollingPayload.newBuilder()
                                       .setWebhookId(ngTrigger.getTriggerStatus().getWebhookInfo().getWebhookId())
                                       .setPollInterval(NGTimeConversionHelper.convertTimeStringToMinutesZeroAllowed(
                                           ngTrigger.getPollInterval()))
                                       .buildPartial())
                .build())
        .build();
  }

  private static Answer executeRunnable(ArgumentCaptor<Runnable> runnableCaptor) {
    return invocation -> {
      runnableCaptor.getValue().run();
      return null;
    };
  }

  @Test
  @Owner(developers = SRIDHAR)
  @Category(UnitTests.class)
  public void testGetTriggerCatalog() {
    when(triggerCatalogHelper.getTriggerTypeToCategoryMapping(ACCOUNT_ID))
        .thenReturn(
            Arrays.asList(TriggerCatalogItem.builder()
                              .category(TriggerCategory.ARTIFACT)
                              .triggerCatalogType(new ArrayList<>(Collections.singleton(TriggerCatalogType.ACR)))
                              .build(),
                TriggerCatalogItem.builder()
                    .category(TriggerCategory.WEBHOOK)
                    .triggerCatalogType(new ArrayList<>(Collections.singleton(TriggerCatalogType.GITHUB)))
                    .build(),
                TriggerCatalogItem.builder()
                    .category(TriggerCategory.SCHEDULED)
                    .triggerCatalogType(new ArrayList<>(Collections.singleton(TriggerCatalogType.CRON)))
                    .build(),
                TriggerCatalogItem.builder()
                    .category(TriggerCategory.MANIFEST)
                    .triggerCatalogType(new ArrayList<>(Collections.singleton(TriggerCatalogType.HELM_CHART)))
                    .build()));
    List<TriggerCatalogItem> lst = ngTriggerServiceImpl.getTriggerCatalog(ACCOUNT_ID);
    assertThat(lst).isNotNull();
    assertThat(lst.size()).isEqualTo(4);
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testCheckAuthorizationSuccessWhenXApiKeyIsPresent() {
    List<HeaderConfig> headerConfigs = Collections.singletonList(
        HeaderConfig.builder().key(X_API_KEY).values(Collections.singletonList(API_KEY)).build());
    doNothing()
        .when(accessControlClient)
        .checkForAccessOrThrow(ResourceScope.of(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER),
            Resource.of("PIPELINE", IDENTIFIER), PipelineRbacPermissions.PIPELINE_EXECUTE);
    assertThatCode(()
                       -> ngTriggerServiceImpl.checkAuthorization(
                           ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, IDENTIFIER, headerConfigs))
        .doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testCheckAuthorizationFailureWhenXApiKeyIsPresent() {
    List<HeaderConfig> headerConfigs = Collections.singletonList(
        HeaderConfig.builder().key(X_API_KEY).values(Collections.singletonList(API_KEY)).build());
    doThrow(new AccessDeniedException("Error msg", USER))
        .when(accessControlClient)
        .checkForAccessOrThrow(ResourceScope.of(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER),
            Resource.of("PIPELINE", IDENTIFIER), PipelineRbacPermissions.PIPELINE_EXECUTE);
    assertThatThrownBy(()
                           -> ngTriggerServiceImpl.checkAuthorization(
                               ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, IDENTIFIER, headerConfigs))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testGetTriggerYamlDiffTriggerWithExtraInput() throws IOException {
    checkTriggerYamlDiff("trigger-yaml-diff-pipeline-with-input.yaml", "trigger-yaml-diff-trigger-extra-input.yaml",
        "trigger-yaml-diff-expected-new-trigger-with-input.yaml", true);
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testGetTriggerYamlDiffTriggerWithMissingInput() throws IOException {
    checkTriggerYamlDiff("trigger-yaml-diff-pipeline-with-input.yaml", "trigger-yaml-diff-trigger-missing-input.yaml",
        "trigger-yaml-diff-expected-new-trigger-with-input.yaml", true);
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testGetTriggerYamlDiffTriggerWithNoInput() throws IOException {
    checkTriggerYamlDiff("trigger-yaml-diff-pipeline-with-input.yaml", "trigger-yaml-diff-trigger-no-input.yaml",
        "trigger-yaml-diff-expected-new-trigger-with-input.yaml", true);
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testGetTriggerYamlDiffTriggerWithRightInput() throws IOException {
    checkTriggerYamlDiff("trigger-yaml-diff-pipeline-with-input.yaml", "trigger-yaml-diff-trigger-right-input.yaml",
        "trigger-yaml-diff-expected-new-trigger-with-input.yaml", true);
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testGetTriggerYamlDiffTriggerWithWrongInputFormat() throws IOException {
    checkTriggerYamlDiff("trigger-yaml-diff-pipeline-with-input.yaml",
        "trigger-yaml-diff-trigger-wrong-input-format.yaml", "trigger-yaml-diff-expected-new-trigger-with-input.yaml",
        true);
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testGetTriggerYamlDiffWhenPipelineHasNoInput() throws IOException {
    checkTriggerYamlDiff("trigger-yaml-diff-pipeline-no-input.yaml", "trigger-yaml-diff-trigger-extra-input.yaml",
        "trigger-yaml-diff-expected-new-trigger-no-input.yaml", true);
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testGetTriggerYamlDiffWhenTriggerForRemotePipeline() throws IOException {
    checkTriggerYamlDiff("trigger-yaml-diff-pipeline-with-input.yaml", "trigger-yaml-diff-trigger-with-input-set.yaml",
        "trigger-yaml-diff-trigger-with-input-set.yaml", false);
  }

  private void checkTriggerYamlDiff(String filenamePipeline, String filenameTrigger, String filenameNewTrigger,
      Boolean useNullPipelineBranchName) throws IOException {
    String newTriggerYaml =
        Resources.toString(Objects.requireNonNull(classLoader.getResource(filenameNewTrigger)), StandardCharsets.UTF_8);
    String triggerYaml =
        Resources.toString(Objects.requireNonNull(classLoader.getResource(filenameTrigger)), StandardCharsets.UTF_8);
    NGTriggerEntity ngTriggerEntity = NGTriggerEntity.builder()
                                          .accountId(ACCOUNT_ID)
                                          .orgIdentifier(ORG_IDENTIFIER)
                                          .projectIdentifier(PROJ_IDENTIFIER)
                                          .targetIdentifier(PIPELINE_IDENTIFIER)
                                          .identifier(IDENTIFIER)
                                          .name(NAME)
                                          .targetType(TargetType.PIPELINE)
                                          .type(NGTriggerType.WEBHOOK)
                                          .metadata(ngTriggerMetadata)
                                          .yaml(triggerYaml)
                                          .version(0L)
                                          .build();
    TriggerDetails triggerDetails =
        TriggerDetails.builder()
            .ngTriggerEntity(ngTriggerEntity)
            .ngTriggerConfigV2(NGTriggerConfigV2.builder()
                                   .inputSetRefs(Collections.emptyList())
                                   .pipelineBranchName(useNullPipelineBranchName ? null : "pipelineBranchName")
                                   .build())
            .build();
    String pipelineYaml =
        Resources.toString(Objects.requireNonNull(classLoader.getResource(filenamePipeline)), StandardCharsets.UTF_8);
    when(validationHelper.fetchPipelineForTrigger(any())).thenReturn(Optional.ofNullable(pipelineYaml));
    ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory()
                                                     .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)
                                                     .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
                                                     .disable(USE_NATIVE_TYPE_ID));
    objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    when(ngTriggerElementMapper.getObjectMapper()).thenReturn(objectMapper);
    TriggerYamlDiffDTO yamlDiffResponse = ngTriggerServiceImpl.getTriggerYamlDiff(triggerDetails);
    assertThat(yamlDiffResponse.getNewYAML().replace("<+input>", "1")).isEqualTo(newTriggerYaml);
  }
}
