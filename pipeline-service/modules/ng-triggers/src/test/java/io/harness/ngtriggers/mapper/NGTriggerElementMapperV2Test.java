/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.mapper;

import static io.harness.ngtriggers.beans.source.NGTriggerType.ARTIFACT;
import static io.harness.ngtriggers.beans.source.NGTriggerType.MANIFEST;
import static io.harness.ngtriggers.beans.source.NGTriggerType.SCHEDULED;
import static io.harness.ngtriggers.beans.source.NGTriggerType.WEBHOOK;
import static io.harness.ngtriggers.beans.source.artifact.BuildStoreType.GCS;
import static io.harness.ngtriggers.beans.source.artifact.BuildStoreType.HTTP;
import static io.harness.ngtriggers.beans.source.artifact.BuildStoreType.S3;
import static io.harness.ngtriggers.conditionchecker.ConditionOperator.CONTAINS;
import static io.harness.ngtriggers.conditionchecker.ConditionOperator.ENDS_WITH;
import static io.harness.ngtriggers.conditionchecker.ConditionOperator.EQUALS;
import static io.harness.ngtriggers.conditionchecker.ConditionOperator.IN;
import static io.harness.ngtriggers.conditionchecker.ConditionOperator.NOT_EQUALS;
import static io.harness.ngtriggers.conditionchecker.ConditionOperator.NOT_IN;
import static io.harness.ngtriggers.conditionchecker.ConditionOperator.STARTS_WITH;
import static io.harness.rule.OwnerRule.ADWAIT;
import static io.harness.rule.OwnerRule.BUHA;
import static io.harness.rule.OwnerRule.MATT;
import static io.harness.rule.OwnerRule.NAMAN;
import static io.harness.rule.OwnerRule.PIYUSH_BHUWALKA;
import static io.harness.rule.OwnerRule.RAGHAV_GUPTA;
import static io.harness.rule.OwnerRule.ROHITKARELIA;
import static io.harness.rule.OwnerRule.RUTVIJ_MEHTA;
import static io.harness.rule.OwnerRule.SRIDHAR;

import static com.fasterxml.jackson.dataformat.yaml.YAMLGenerator.Feature.USE_NATIVE_TYPE_ID;
import static java.util.Arrays.asList;
import static junit.framework.TestCase.assertNotNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.ngtriggers.beans.config.NGTriggerConfigV2;
import io.harness.ngtriggers.beans.dto.NGTriggerDetailsResponseDTO;
import io.harness.ngtriggers.beans.dto.NGTriggerResponseDTO;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.beans.entity.TriggerEventHistory;
import io.harness.ngtriggers.beans.source.ManifestType;
import io.harness.ngtriggers.beans.source.NGTriggerSourceV2;
import io.harness.ngtriggers.beans.source.NGTriggerSpecV2;
import io.harness.ngtriggers.beans.source.WebhookTriggerType;
import io.harness.ngtriggers.beans.source.artifact.AcrSpec;
import io.harness.ngtriggers.beans.source.artifact.ArtifactTriggerConfig;
import io.harness.ngtriggers.beans.source.artifact.ArtifactType;
import io.harness.ngtriggers.beans.source.artifact.ArtifactTypeSpec;
import io.harness.ngtriggers.beans.source.artifact.ArtifactoryRegistrySpec;
import io.harness.ngtriggers.beans.source.artifact.BuildAware;
import io.harness.ngtriggers.beans.source.artifact.BuildStoreTypeSpec;
import io.harness.ngtriggers.beans.source.artifact.DockerRegistrySpec;
import io.harness.ngtriggers.beans.source.artifact.EcrSpec;
import io.harness.ngtriggers.beans.source.artifact.GcrSpec;
import io.harness.ngtriggers.beans.source.artifact.HelmManifestSpec;
import io.harness.ngtriggers.beans.source.artifact.ManifestTriggerConfig;
import io.harness.ngtriggers.beans.source.artifact.ManifestTypeSpec;
import io.harness.ngtriggers.beans.source.artifact.store.BuildStore;
import io.harness.ngtriggers.beans.source.artifact.store.GcsBuildStoreTypeSpec;
import io.harness.ngtriggers.beans.source.artifact.store.HttpBuildStoreTypeSpec;
import io.harness.ngtriggers.beans.source.artifact.store.S3BuildStoreTypeSpec;
import io.harness.ngtriggers.beans.source.scheduled.CronTriggerSpec;
import io.harness.ngtriggers.beans.source.scheduled.ScheduledTriggerConfig;
import io.harness.ngtriggers.beans.source.scheduled.ScheduledTriggerSpec;
import io.harness.ngtriggers.beans.source.webhook.v2.TriggerEventDataCondition;
import io.harness.ngtriggers.beans.source.webhook.v2.WebhookTriggerConfigV2;
import io.harness.ngtriggers.beans.source.webhook.v2.awscodecommit.AwsCodeCommitSpec;
import io.harness.ngtriggers.beans.source.webhook.v2.awscodecommit.event.AwsCodeCommitTriggerEvent;
import io.harness.ngtriggers.beans.source.webhook.v2.azurerepo.AzureRepoSpec;
import io.harness.ngtriggers.beans.source.webhook.v2.azurerepo.action.AzureRepoIssueCommentAction;
import io.harness.ngtriggers.beans.source.webhook.v2.azurerepo.action.AzureRepoPRAction;
import io.harness.ngtriggers.beans.source.webhook.v2.azurerepo.event.AzureRepoTriggerEvent;
import io.harness.ngtriggers.beans.source.webhook.v2.bitbucket.BitbucketSpec;
import io.harness.ngtriggers.beans.source.webhook.v2.bitbucket.action.BitbucketPRAction;
import io.harness.ngtriggers.beans.source.webhook.v2.bitbucket.action.BitbucketPRCommentAction;
import io.harness.ngtriggers.beans.source.webhook.v2.bitbucket.event.BitbucketTriggerEvent;
import io.harness.ngtriggers.beans.source.webhook.v2.custom.CustomTriggerSpec;
import io.harness.ngtriggers.beans.source.webhook.v2.github.GithubSpec;
import io.harness.ngtriggers.beans.source.webhook.v2.github.action.GithubIssueCommentAction;
import io.harness.ngtriggers.beans.source.webhook.v2.github.action.GithubPRAction;
import io.harness.ngtriggers.beans.source.webhook.v2.github.event.GithubTriggerEvent;
import io.harness.ngtriggers.beans.source.webhook.v2.gitlab.GitlabSpec;
import io.harness.ngtriggers.beans.source.webhook.v2.gitlab.action.GitlabMRCommentAction;
import io.harness.ngtriggers.beans.source.webhook.v2.gitlab.action.GitlabPRAction;
import io.harness.ngtriggers.beans.source.webhook.v2.gitlab.event.GitlabTriggerEvent;
import io.harness.repositories.spring.TriggerEventHistoryRepository;
import io.harness.rule.Owner;
import io.harness.utils.PmsFeatureFlagService;
import io.harness.webhook.WebhookConfigProvider;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.google.common.io.Resources;
import com.google.inject.Inject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Objects;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class NGTriggerElementMapperV2Test extends CategoryTest {
  private String ngTriggerYaml_github_pr;
  private String ngTriggerYaml_github_push;
  private String ngTriggerYaml_github_issue_comment;

  private String ngTriggerYaml_gitlab_pr;
  private String ngTriggerYaml_gitlab_push;
  private String ngTriggerYaml_gitlab_mr_comment;

  private String ngTriggerYaml_bitbucket_pr;
  private String ngTriggerYaml_bitbucket_push;
  private String ngTriggerYaml_bitbucket_pr_comment;

  private String ngTriggerYaml_azurerepo_pr;
  private String ngTriggerYaml_azurerepo_push;
  private String ngTriggerYaml_azurerepo_issue_comment;

  private String ngTriggerYaml_awscodecommit_push;
  private String ngTriggerYaml_custom;
  private String ngTriggerYaml_cron;
  private String ngTriggerYaml_artifact_gcr;
  private String ngTriggerYaml_artifact_ecr;
  private String ngTriggerYaml_artifact_acr;
  private String ngTriggerYaml_helm_S3;
  private String ngTriggerYaml_helm_gcs;
  private String ngTriggerYaml_helm_http;
  private String ngTriggerYaml_artifact_dockerregistry;
  private String ngTriggerYaml_artifact_artifactorygenericregistry;
  private String ngTriggerYaml_artifact_artifactorydockerregistry;
  private String ngTriggerYaml_manifest;

  private String ngTriggerYaml_gitpolling;

  private List<TriggerEventDataCondition> payloadConditions;
  private List<TriggerEventDataCondition> headerConditions;
  private static final String inputYaml = "pipeline:\n"
      + "  identifier: secrethttp1\n"
      + "  stages:\n"
      + "    - stage:\n"
      + "        identifier: qaStage\n"
      + "        spec:\n"
      + "          infrastructure:\n"
      + "            infrastructureDefinition:\n"
      + "              spec:\n"
      + "                releaseName: releaseName1";
  private static final String JEXL = "true";
  private static final String REPO = "myrepo";
  private static final String CONN = "conn";
  @Mock private TriggerEventHistoryRepository triggerEventHistoryRepository;
  @Mock private WebhookConfigProvider webhookConfigProvider;
  @Mock PmsFeatureFlagService pmsFeatureFlagService;
  @InjectMocks @Inject private NGTriggerElementMapper ngTriggerElementMapper;

  @Before
  public void setUp() throws IOException {
    MockitoAnnotations.initMocks(this);
    ClassLoader classLoader = getClass().getClassLoader();
    ngTriggerYaml_github_push = Resources.toString(
        Objects.requireNonNull(classLoader.getResource("ng-trigger-github-push-v2.yaml")), StandardCharsets.UTF_8);
    ngTriggerYaml_github_pr = Resources.toString(
        Objects.requireNonNull(classLoader.getResource("ng-trigger-github-pr-v2.yaml")), StandardCharsets.UTF_8);
    ngTriggerYaml_github_issue_comment =
        Resources.toString(Objects.requireNonNull(classLoader.getResource("ng-trigger-github-issue-comment-v2.yaml")),
            StandardCharsets.UTF_8);
    ngTriggerYaml_gitlab_pr = Resources.toString(
        Objects.requireNonNull(classLoader.getResource("ng-trigger-gitlab-pr-v2.yaml")), StandardCharsets.UTF_8);
    ngTriggerYaml_gitpolling = Resources.toString(
        Objects.requireNonNull(classLoader.getResource("ng-trigger-gitpolling.yaml")), StandardCharsets.UTF_8);
    ngTriggerYaml_gitlab_push = Resources.toString(
        Objects.requireNonNull(classLoader.getResource("ng-trigger-gitlab-push-v2.yaml")), StandardCharsets.UTF_8);
    ngTriggerYaml_gitlab_mr_comment =
        Resources.toString(Objects.requireNonNull(classLoader.getResource("ng-trigger-gitlab-mr-comment-v2.yaml")),
            StandardCharsets.UTF_8);
    ngTriggerYaml_bitbucket_pr = Resources.toString(
        Objects.requireNonNull(classLoader.getResource("ng-trigger-bitbucket-pr-v2.yaml")), StandardCharsets.UTF_8);
    ngTriggerYaml_bitbucket_push = Resources.toString(
        Objects.requireNonNull(classLoader.getResource("ng-trigger-bitbucket-push-v2.yaml")), StandardCharsets.UTF_8);
    ngTriggerYaml_bitbucket_pr_comment =
        Resources.toString(Objects.requireNonNull(classLoader.getResource("ng-trigger-bitbucket-pr-comment-v2.yaml")),
            StandardCharsets.UTF_8);
    ngTriggerYaml_azurerepo_pr = Resources.toString(
        Objects.requireNonNull(classLoader.getResource("ng-trigger-azurerepo-pr-v2.yaml")), StandardCharsets.UTF_8);
    ngTriggerYaml_azurerepo_push = Resources.toString(
        Objects.requireNonNull(classLoader.getResource("ng-trigger-azurerepo-push-v2.yaml")), StandardCharsets.UTF_8);
    ngTriggerYaml_azurerepo_issue_comment = Resources.toString(
        Objects.requireNonNull(classLoader.getResource("ng-trigger-azurerepo-issue-comment-v2.yaml")),
        StandardCharsets.UTF_8);
    ngTriggerYaml_awscodecommit_push =
        Resources.toString(Objects.requireNonNull(classLoader.getResource("ng-trigger-awscodecommit-push-v2.yaml")),
            StandardCharsets.UTF_8);
    ngTriggerYaml_custom = Resources.toString(
        Objects.requireNonNull(classLoader.getResource("ng-trigger-custom-v2.yaml")), StandardCharsets.UTF_8);
    ngTriggerYaml_cron = Resources.toString(
        Objects.requireNonNull(classLoader.getResource("ng-trigger-cron-v2.yaml")), StandardCharsets.UTF_8);

    ngTriggerYaml_artifact_gcr = Resources.toString(
        Objects.requireNonNull(classLoader.getResource("ng-trigger-artifact-gcr.yaml")), StandardCharsets.UTF_8);
    ngTriggerYaml_artifact_ecr = Resources.toString(
        Objects.requireNonNull(classLoader.getResource("ng-trigger-artifact-ecr.yaml")), StandardCharsets.UTF_8);
    ngTriggerYaml_artifact_acr = Resources.toString(
        Objects.requireNonNull(classLoader.getResource("ng-trigger-artifact-acr.yaml")), StandardCharsets.UTF_8);

    ngTriggerYaml_helm_S3 = Resources.toString(
        Objects.requireNonNull(classLoader.getResource("ng-trigger-manifest-helm-s3.yaml")), StandardCharsets.UTF_8);
    ngTriggerYaml_helm_gcs = Resources.toString(
        Objects.requireNonNull(classLoader.getResource("ng-trigger-manifest-helm-gcs.yaml")), StandardCharsets.UTF_8);
    ngTriggerYaml_helm_http = Resources.toString(
        Objects.requireNonNull(classLoader.getResource("ng-trigger-manifest-helm-http.yaml")), StandardCharsets.UTF_8);
    ngTriggerYaml_artifact_dockerregistry =
        Resources.toString(Objects.requireNonNull(classLoader.getResource("ng-trigger-artifact-dockerregistry.yaml")),
            StandardCharsets.UTF_8);
    ngTriggerYaml_artifact_artifactorygenericregistry =
        Resources.toString(Objects.requireNonNull(classLoader.getResource("ng-trigger-artifact-artifactory.yaml")),
            StandardCharsets.UTF_8);
    ngTriggerYaml_artifact_artifactorydockerregistry = Resources.toString(
        Objects.requireNonNull(classLoader.getResource("ng-trigger-artifact-artifactory-docker.yaml")),
        StandardCharsets.UTF_8);

    ngTriggerYaml_manifest = Resources.toString(
        Objects.requireNonNull(classLoader.getResource("ng-trigger-manifest.yaml")), StandardCharsets.UTF_8);

    payloadConditions = asList(TriggerEventDataCondition.builder().key("k1").operator(EQUALS).value("v1").build(),
        TriggerEventDataCondition.builder().key("k2").operator(NOT_EQUALS).value("v2").build(),
        TriggerEventDataCondition.builder().key("k3").operator(IN).value("v3,c").build(),
        TriggerEventDataCondition.builder().key("k4").operator(NOT_IN).value("v4").build(),
        TriggerEventDataCondition.builder().key("k5").operator(STARTS_WITH).value("v5").build(),
        TriggerEventDataCondition.builder().key("k6").operator(ENDS_WITH).value("v6").build(),
        TriggerEventDataCondition.builder().key("k7").operator(CONTAINS).value("v7").build());
    headerConditions = asList(TriggerEventDataCondition.builder().key("h1").operator(EQUALS).value("v1").build());

    doReturn("https://app.harness.io/ng/api")
        .doReturn("https://app.harness.io/ng/api/")
        .doReturn("https://app.harness.io/ng/api/#")
        .doReturn("https://app.harness.io/ng/api")
        .doReturn(null)
        .when(webhookConfigProvider)
        .getWebhookApiBaseUrl();

    doReturn("https://app.harness.io/pipeline/api").when(webhookConfigProvider).getCustomApiBaseUrl();
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testGitubPR() throws Exception {
    NGTriggerConfigV2 ngTriggerConfigV2 = ngTriggerElementMapper.toTriggerConfigV2(ngTriggerYaml_github_pr);
    assertRootLevelProperties(ngTriggerConfigV2);

    NGTriggerSourceV2 ngTriggerSourceV2 = ngTriggerConfigV2.getSource();
    assertThat(ngTriggerSourceV2).isNotNull();
    assertThat(ngTriggerSourceV2.getType()).isEqualTo(WEBHOOK);
    NGTriggerSpecV2 ngTriggerSpecV2 = ngTriggerSourceV2.getSpec();
    assertThat(WebhookTriggerConfigV2.class.isAssignableFrom(ngTriggerSpecV2.getClass())).isTrue();
    WebhookTriggerConfigV2 webhookTriggerConfigV2 = (WebhookTriggerConfigV2) ngTriggerSpecV2;
    assertThat(webhookTriggerConfigV2.getType()).isEqualTo(WebhookTriggerType.GITHUB);
    assertThat(GithubSpec.class.isAssignableFrom(webhookTriggerConfigV2.getSpec().getClass())).isTrue();
    GithubSpec githubSpec = (GithubSpec) webhookTriggerConfigV2.getSpec();
    assertThat(githubSpec.getType()).isEqualTo(GithubTriggerEvent.PULL_REQUEST);
    assertThat(githubSpec.fetchPayloadAware().fetchPayloadConditions()).containsAll(payloadConditions);
    assertThat(githubSpec.fetchPayloadAware().fetchHeaderConditions()).containsAll(headerConditions);
    assertThat(githubSpec.fetchPayloadAware().fetchJexlCondition()).isEqualTo(JEXL);
    assertThat(githubSpec.fetchGitAware().fetchRepoName()).isEqualTo(REPO);
    assertThat(githubSpec.fetchGitAware().fetchConnectorRef()).isEqualTo(CONN);
    assertThat(githubSpec.fetchGitAware().fetchAutoAbortPreviousExecutions()).isTrue();
    assertThat(githubSpec.fetchGitAware().fetchEvent()).isEqualTo(GithubTriggerEvent.PULL_REQUEST);
    assertThat(githubSpec.fetchGitAware().fetchActions()).containsAll(asList(GithubPRAction.OPEN, GithubPRAction.EDIT));
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testGitubPush() throws Exception {
    NGTriggerConfigV2 ngTriggerConfigV2 = ngTriggerElementMapper.toTriggerConfigV2(ngTriggerYaml_github_push);

    assertRootLevelProperties(ngTriggerConfigV2);

    NGTriggerSourceV2 ngTriggerSourceV2 = ngTriggerConfigV2.getSource();
    assertThat(ngTriggerSourceV2).isNotNull();
    assertThat(ngTriggerSourceV2.getType()).isEqualTo(WEBHOOK);
    NGTriggerSpecV2 ngTriggerSpecV2 = ngTriggerSourceV2.getSpec();
    assertThat(WebhookTriggerConfigV2.class.isAssignableFrom(ngTriggerSpecV2.getClass())).isTrue();
    WebhookTriggerConfigV2 webhookTriggerConfigV2 = (WebhookTriggerConfigV2) ngTriggerSpecV2;
    assertThat(webhookTriggerConfigV2.getType()).isEqualTo(WebhookTriggerType.GITHUB);
    assertThat(GithubSpec.class.isAssignableFrom(webhookTriggerConfigV2.getSpec().getClass())).isTrue();
    GithubSpec githubSpec = (GithubSpec) webhookTriggerConfigV2.getSpec();
    assertThat(githubSpec.getType()).isEqualTo(GithubTriggerEvent.PUSH);
    assertThat(githubSpec.fetchPayloadAware().fetchPayloadConditions()).containsAll(payloadConditions);
    assertThat(githubSpec.fetchPayloadAware().fetchHeaderConditions()).containsAll(headerConditions);
    assertThat(githubSpec.fetchPayloadAware().fetchJexlCondition()).isEqualTo(JEXL);
    assertThat(githubSpec.fetchGitAware().fetchRepoName()).isEqualTo(REPO);
    assertThat(githubSpec.fetchGitAware().fetchConnectorRef()).isEqualTo(CONN);
    assertThat(githubSpec.fetchGitAware().fetchAutoAbortPreviousExecutions()).isTrue();
    assertThat(githubSpec.fetchGitAware().fetchEvent()).isEqualTo(GithubTriggerEvent.PUSH);
    assertThat(githubSpec.fetchGitAware().fetchActions()).isEmpty();
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testGitubIssueComment() throws Exception {
    NGTriggerConfigV2 ngTriggerConfigV2 = ngTriggerElementMapper.toTriggerConfigV2(ngTriggerYaml_github_issue_comment);

    assertRootLevelProperties(ngTriggerConfigV2);

    NGTriggerSourceV2 ngTriggerSourceV2 = ngTriggerConfigV2.getSource();
    assertThat(ngTriggerSourceV2).isNotNull();
    assertThat(ngTriggerSourceV2.getType()).isEqualTo(WEBHOOK);
    NGTriggerSpecV2 ngTriggerSpecV2 = ngTriggerSourceV2.getSpec();
    assertThat(WebhookTriggerConfigV2.class.isAssignableFrom(ngTriggerSpecV2.getClass())).isTrue();
    WebhookTriggerConfigV2 webhookTriggerConfigV2 = (WebhookTriggerConfigV2) ngTriggerSpecV2;
    assertThat(webhookTriggerConfigV2.getType()).isEqualTo(WebhookTriggerType.GITHUB);
    assertThat(GithubSpec.class.isAssignableFrom(webhookTriggerConfigV2.getSpec().getClass())).isTrue();
    GithubSpec githubSpec = (GithubSpec) webhookTriggerConfigV2.getSpec();
    assertThat(githubSpec.getType()).isEqualTo(GithubTriggerEvent.ISSUE_COMMENT);
    assertThat(githubSpec.fetchPayloadAware().fetchPayloadConditions()).containsAll(payloadConditions);
    assertThat(githubSpec.fetchPayloadAware().fetchHeaderConditions()).containsAll(headerConditions);
    assertThat(githubSpec.fetchPayloadAware().fetchJexlCondition()).isEqualTo(JEXL);
    assertThat(githubSpec.fetchGitAware().fetchRepoName()).isEqualTo(REPO);
    assertThat(githubSpec.fetchGitAware().fetchConnectorRef()).isEqualTo(CONN);
    assertThat(githubSpec.fetchGitAware().fetchAutoAbortPreviousExecutions()).isTrue();
    assertThat(githubSpec.fetchGitAware().fetchEvent()).isEqualTo(GithubTriggerEvent.ISSUE_COMMENT);
    assertThat(githubSpec.fetchGitAware().fetchActions())
        .containsAll(asList(GithubIssueCommentAction.CREATE, GithubIssueCommentAction.DELETE));
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testGitlabPR() throws Exception {
    NGTriggerConfigV2 ngTriggerConfigV2 = ngTriggerElementMapper.toTriggerConfigV2(ngTriggerYaml_gitlab_pr);

    assertRootLevelProperties(ngTriggerConfigV2);

    NGTriggerSourceV2 ngTriggerSourceV2 = ngTriggerConfigV2.getSource();
    assertThat(ngTriggerSourceV2).isNotNull();
    assertThat(ngTriggerSourceV2.getType()).isEqualTo(WEBHOOK);
    NGTriggerSpecV2 ngTriggerSpecV2 = ngTriggerSourceV2.getSpec();
    assertThat(WebhookTriggerConfigV2.class.isAssignableFrom(ngTriggerSpecV2.getClass())).isTrue();
    WebhookTriggerConfigV2 webhookTriggerConfigV2 = (WebhookTriggerConfigV2) ngTriggerSpecV2;
    assertThat(webhookTriggerConfigV2.getType()).isEqualTo(WebhookTriggerType.GITLAB);
    assertThat(GitlabSpec.class.isAssignableFrom(webhookTriggerConfigV2.getSpec().getClass())).isTrue();
    GitlabSpec spec = (GitlabSpec) webhookTriggerConfigV2.getSpec();
    assertThat(spec.getType()).isEqualTo(GitlabTriggerEvent.MERGE_REQUEST);
    assertThat(spec.fetchPayloadAware().fetchPayloadConditions()).containsAll(payloadConditions);
    assertThat(spec.fetchPayloadAware().fetchHeaderConditions()).containsAll(headerConditions);
    assertThat(spec.fetchPayloadAware().fetchJexlCondition()).isEqualTo(JEXL);
    assertThat(spec.fetchGitAware().fetchRepoName()).isEqualTo(REPO);
    assertThat(spec.fetchGitAware().fetchConnectorRef()).isEqualTo(CONN);
    assertThat(spec.fetchGitAware().fetchAutoAbortPreviousExecutions()).isTrue();
    assertThat(spec.fetchGitAware().fetchEvent()).isEqualTo(GitlabTriggerEvent.MERGE_REQUEST);
    assertThat(spec.fetchGitAware().fetchActions()).containsAll(asList(GitlabPRAction.OPEN, GitlabPRAction.MERGE));
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testGitlabPush() throws Exception {
    NGTriggerConfigV2 ngTriggerConfigV2 = ngTriggerElementMapper.toTriggerConfigV2(ngTriggerYaml_gitlab_push);

    assertRootLevelProperties(ngTriggerConfigV2);

    NGTriggerSourceV2 ngTriggerSourceV2 = ngTriggerConfigV2.getSource();
    assertThat(ngTriggerSourceV2).isNotNull();
    assertThat(ngTriggerSourceV2.getType()).isEqualTo(WEBHOOK);
    NGTriggerSpecV2 ngTriggerSpecV2 = ngTriggerSourceV2.getSpec();
    assertThat(WebhookTriggerConfigV2.class.isAssignableFrom(ngTriggerSpecV2.getClass())).isTrue();
    WebhookTriggerConfigV2 webhookTriggerConfigV2 = (WebhookTriggerConfigV2) ngTriggerSpecV2;
    assertThat(webhookTriggerConfigV2.getType()).isEqualTo(WebhookTriggerType.GITLAB);
    assertThat(GitlabSpec.class.isAssignableFrom(webhookTriggerConfigV2.getSpec().getClass())).isTrue();
    GitlabSpec spec = (GitlabSpec) webhookTriggerConfigV2.getSpec();
    assertThat(spec.getType()).isEqualTo(GitlabTriggerEvent.PUSH);
    assertThat(spec.fetchPayloadAware().fetchPayloadConditions()).containsAll(payloadConditions);
    assertThat(spec.fetchPayloadAware().fetchHeaderConditions()).containsAll(headerConditions);
    assertThat(spec.fetchPayloadAware().fetchJexlCondition()).isEqualTo(JEXL);
    assertThat(spec.fetchGitAware().fetchRepoName()).isEqualTo(REPO);
    assertThat(spec.fetchGitAware().fetchConnectorRef()).isEqualTo(CONN);
    assertThat(spec.fetchGitAware().fetchAutoAbortPreviousExecutions()).isTrue();
    assertThat(spec.fetchGitAware().fetchEvent()).isEqualTo(GitlabTriggerEvent.PUSH);
    assertThat(spec.fetchGitAware().fetchActions()).isEmpty();
  }

  @Test
  @Owner(developers = RUTVIJ_MEHTA)
  @Category(UnitTests.class)
  public void testGitlabMRComment() throws Exception {
    NGTriggerConfigV2 ngTriggerConfigV2 = ngTriggerElementMapper.toTriggerConfigV2(ngTriggerYaml_gitlab_mr_comment);

    assertRootLevelProperties(ngTriggerConfigV2);

    NGTriggerSourceV2 ngTriggerSourceV2 = ngTriggerConfigV2.getSource();
    assertThat(ngTriggerSourceV2).isNotNull();
    assertThat(ngTriggerSourceV2.getType()).isEqualTo(WEBHOOK);
    NGTriggerSpecV2 ngTriggerSpecV2 = ngTriggerSourceV2.getSpec();
    assertThat(WebhookTriggerConfigV2.class.isAssignableFrom(ngTriggerSpecV2.getClass())).isTrue();
    WebhookTriggerConfigV2 webhookTriggerConfigV2 = (WebhookTriggerConfigV2) ngTriggerSpecV2;
    assertThat(webhookTriggerConfigV2.getType()).isEqualTo(WebhookTriggerType.GITLAB);
    assertThat(GitlabSpec.class.isAssignableFrom(webhookTriggerConfigV2.getSpec().getClass())).isTrue();
    GitlabSpec gitlabSpec = (GitlabSpec) webhookTriggerConfigV2.getSpec();
    assertThat(gitlabSpec.getType()).isEqualTo(GitlabTriggerEvent.MR_COMMENT);
    assertThat(gitlabSpec.fetchPayloadAware().fetchPayloadConditions()).containsAll(payloadConditions);
    assertThat(gitlabSpec.fetchPayloadAware().fetchHeaderConditions()).containsAll(headerConditions);
    assertThat(gitlabSpec.fetchPayloadAware().fetchJexlCondition()).isEqualTo(JEXL);
    assertThat(gitlabSpec.fetchGitAware().fetchRepoName()).isEqualTo(REPO);
    assertThat(gitlabSpec.fetchGitAware().fetchConnectorRef()).isEqualTo(CONN);
    assertThat(gitlabSpec.fetchGitAware().fetchAutoAbortPreviousExecutions()).isTrue();
    assertThat(gitlabSpec.fetchGitAware().fetchEvent()).isEqualTo(GitlabTriggerEvent.MR_COMMENT);
    assertThat(gitlabSpec.fetchGitAware().fetchActions()).containsAll(asList(GitlabMRCommentAction.CREATE));
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testBitbucketPR() throws Exception {
    NGTriggerConfigV2 ngTriggerConfigV2 = ngTriggerElementMapper.toTriggerConfigV2(ngTriggerYaml_bitbucket_pr);

    assertRootLevelProperties(ngTriggerConfigV2);

    NGTriggerSourceV2 ngTriggerSourceV2 = ngTriggerConfigV2.getSource();
    assertThat(ngTriggerSourceV2).isNotNull();
    assertThat(ngTriggerSourceV2.getType()).isEqualTo(WEBHOOK);
    NGTriggerSpecV2 ngTriggerSpecV2 = ngTriggerSourceV2.getSpec();
    assertThat(WebhookTriggerConfigV2.class.isAssignableFrom(ngTriggerSpecV2.getClass())).isTrue();
    WebhookTriggerConfigV2 webhookTriggerConfigV2 = (WebhookTriggerConfigV2) ngTriggerSpecV2;
    assertThat(webhookTriggerConfigV2.getType()).isEqualTo(WebhookTriggerType.BITBUCKET);
    assertThat(BitbucketSpec.class.isAssignableFrom(webhookTriggerConfigV2.getSpec().getClass())).isTrue();
    BitbucketSpec spec = (BitbucketSpec) webhookTriggerConfigV2.getSpec();
    assertThat(spec.getType()).isEqualTo(BitbucketTriggerEvent.PULL_REQUEST);
    assertThat(spec.fetchPayloadAware().fetchPayloadConditions()).containsAll(payloadConditions);
    assertThat(spec.fetchPayloadAware().fetchHeaderConditions()).containsAll(headerConditions);
    assertThat(spec.fetchPayloadAware().fetchJexlCondition()).isEqualTo(JEXL);
    assertThat(spec.fetchGitAware().fetchRepoName()).isEqualTo(REPO);
    assertThat(spec.fetchGitAware().fetchConnectorRef()).isEqualTo(CONN);
    assertThat(spec.fetchGitAware().fetchAutoAbortPreviousExecutions()).isTrue();
    assertThat(spec.fetchGitAware().fetchEvent()).isEqualTo(BitbucketTriggerEvent.PULL_REQUEST);
    assertThat(spec.fetchGitAware().fetchActions())
        .containsAll(asList(BitbucketPRAction.UPDATE, BitbucketPRAction.DECLINE));
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testBitbucketPush() throws Exception {
    NGTriggerConfigV2 ngTriggerConfigV2 = ngTriggerElementMapper.toTriggerConfigV2(ngTriggerYaml_bitbucket_push);

    assertRootLevelProperties(ngTriggerConfigV2);

    NGTriggerSourceV2 ngTriggerSourceV2 = ngTriggerConfigV2.getSource();
    assertThat(ngTriggerSourceV2).isNotNull();
    assertThat(ngTriggerSourceV2.getType()).isEqualTo(WEBHOOK);
    NGTriggerSpecV2 ngTriggerSpecV2 = ngTriggerSourceV2.getSpec();
    assertThat(WebhookTriggerConfigV2.class.isAssignableFrom(ngTriggerSpecV2.getClass())).isTrue();
    WebhookTriggerConfigV2 webhookTriggerConfigV2 = (WebhookTriggerConfigV2) ngTriggerSpecV2;
    assertThat(webhookTriggerConfigV2.getType()).isEqualTo(WebhookTriggerType.BITBUCKET);
    assertThat(BitbucketSpec.class.isAssignableFrom(webhookTriggerConfigV2.getSpec().getClass())).isTrue();
    BitbucketSpec spec = (BitbucketSpec) webhookTriggerConfigV2.getSpec();
    assertThat(spec.getType()).isEqualTo(BitbucketTriggerEvent.PUSH);
    assertThat(spec.fetchPayloadAware().fetchPayloadConditions()).containsAll(payloadConditions);
    assertThat(spec.fetchPayloadAware().fetchHeaderConditions()).containsAll(headerConditions);
    assertThat(spec.fetchPayloadAware().fetchJexlCondition()).isEqualTo(JEXL);
    assertThat(spec.fetchGitAware().fetchRepoName()).isEqualTo(REPO);
    assertThat(spec.fetchGitAware().fetchConnectorRef()).isEqualTo(CONN);
    assertThat(spec.fetchGitAware().fetchAutoAbortPreviousExecutions()).isTrue();
    assertThat(spec.fetchGitAware().fetchEvent()).isEqualTo(BitbucketTriggerEvent.PUSH);
    assertThat(spec.fetchGitAware().fetchActions()).isEmpty();
  }

  @Test
  @Owner(developers = RUTVIJ_MEHTA)
  @Category(UnitTests.class)
  public void testBitbucketPRComment() throws Exception {
    NGTriggerConfigV2 ngTriggerConfigV2 = ngTriggerElementMapper.toTriggerConfigV2(ngTriggerYaml_bitbucket_pr_comment);

    assertRootLevelProperties(ngTriggerConfigV2);

    NGTriggerSourceV2 ngTriggerSourceV2 = ngTriggerConfigV2.getSource();
    assertThat(ngTriggerSourceV2).isNotNull();
    assertThat(ngTriggerSourceV2.getType()).isEqualTo(WEBHOOK);
    NGTriggerSpecV2 ngTriggerSpecV2 = ngTriggerSourceV2.getSpec();
    assertThat(WebhookTriggerConfigV2.class.isAssignableFrom(ngTriggerSpecV2.getClass())).isTrue();
    WebhookTriggerConfigV2 webhookTriggerConfigV2 = (WebhookTriggerConfigV2) ngTriggerSpecV2;
    assertThat(webhookTriggerConfigV2.getType()).isEqualTo(WebhookTriggerType.BITBUCKET);
    assertThat(BitbucketSpec.class.isAssignableFrom(webhookTriggerConfigV2.getSpec().getClass())).isTrue();
    BitbucketSpec bitbucketSpec = (BitbucketSpec) webhookTriggerConfigV2.getSpec();
    assertThat(bitbucketSpec.getType()).isEqualTo(BitbucketTriggerEvent.PR_COMMENT);
    assertThat(bitbucketSpec.fetchPayloadAware().fetchPayloadConditions()).containsAll(payloadConditions);
    assertThat(bitbucketSpec.fetchPayloadAware().fetchHeaderConditions()).containsAll(headerConditions);
    assertThat(bitbucketSpec.fetchPayloadAware().fetchJexlCondition()).isEqualTo(JEXL);
    assertThat(bitbucketSpec.fetchGitAware().fetchRepoName()).isEqualTo(REPO);
    assertThat(bitbucketSpec.fetchGitAware().fetchConnectorRef()).isEqualTo(CONN);
    assertThat(bitbucketSpec.fetchGitAware().fetchAutoAbortPreviousExecutions()).isTrue();
    assertThat(bitbucketSpec.fetchGitAware().fetchEvent()).isEqualTo(BitbucketTriggerEvent.PR_COMMENT);
    assertThat(bitbucketSpec.fetchGitAware().fetchActions())
        .containsAll(
            asList(BitbucketPRCommentAction.CREATE, BitbucketPRCommentAction.EDIT, BitbucketPRCommentAction.DELETE));
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testAzureRepoPR() throws Exception {
    NGTriggerConfigV2 ngTriggerConfigV2 = ngTriggerElementMapper.toTriggerConfigV2(ngTriggerYaml_azurerepo_pr);

    assertRootLevelProperties(ngTriggerConfigV2);

    NGTriggerSourceV2 ngTriggerSourceV2 = ngTriggerConfigV2.getSource();
    assertThat(ngTriggerSourceV2).isNotNull();
    assertThat(ngTriggerSourceV2.getType()).isEqualTo(WEBHOOK);
    NGTriggerSpecV2 ngTriggerSpecV2 = ngTriggerSourceV2.getSpec();
    assertThat(WebhookTriggerConfigV2.class.isAssignableFrom(ngTriggerSpecV2.getClass())).isTrue();
    WebhookTriggerConfigV2 webhookTriggerConfigV2 = (WebhookTriggerConfigV2) ngTriggerSpecV2;
    assertThat(webhookTriggerConfigV2.getType()).isEqualTo(WebhookTriggerType.AZURE);
    assertThat(AzureRepoSpec.class.isAssignableFrom(webhookTriggerConfigV2.getSpec().getClass())).isTrue();
    AzureRepoSpec spec = (AzureRepoSpec) webhookTriggerConfigV2.getSpec();
    assertThat(spec.getType()).isEqualTo(AzureRepoTriggerEvent.PULL_REQUEST);
    assertThat(spec.fetchPayloadAware().fetchPayloadConditions()).containsAll(payloadConditions);
    assertThat(spec.fetchPayloadAware().fetchHeaderConditions()).containsAll(headerConditions);
    assertThat(spec.fetchPayloadAware().fetchJexlCondition()).isEqualTo(JEXL);
    assertThat(spec.fetchGitAware().fetchRepoName()).isEqualTo(REPO);
    assertThat(spec.fetchGitAware().fetchConnectorRef()).isEqualTo(CONN);
    assertThat(spec.fetchGitAware().fetchEvent()).isEqualTo(AzureRepoTriggerEvent.PULL_REQUEST);
    assertThat(spec.fetchGitAware().fetchActions())
        .containsAll(asList(AzureRepoPRAction.UPDATE, AzureRepoPRAction.CREATE, AzureRepoPRAction.MERGE));
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testAzureRepoPush() throws Exception {
    NGTriggerConfigV2 ngTriggerConfigV2 = ngTriggerElementMapper.toTriggerConfigV2(ngTriggerYaml_azurerepo_push);

    assertRootLevelProperties(ngTriggerConfigV2);

    NGTriggerSourceV2 ngTriggerSourceV2 = ngTriggerConfigV2.getSource();
    assertThat(ngTriggerSourceV2).isNotNull();
    assertThat(ngTriggerSourceV2.getType()).isEqualTo(WEBHOOK);
    NGTriggerSpecV2 ngTriggerSpecV2 = ngTriggerSourceV2.getSpec();
    assertThat(WebhookTriggerConfigV2.class.isAssignableFrom(ngTriggerSpecV2.getClass())).isTrue();
    WebhookTriggerConfigV2 webhookTriggerConfigV2 = (WebhookTriggerConfigV2) ngTriggerSpecV2;
    assertThat(webhookTriggerConfigV2.getType()).isEqualTo(WebhookTriggerType.AZURE);
    assertThat(AzureRepoSpec.class.isAssignableFrom(webhookTriggerConfigV2.getSpec().getClass())).isTrue();
    AzureRepoSpec spec = (AzureRepoSpec) webhookTriggerConfigV2.getSpec();
    assertThat(spec.getType()).isEqualTo(AzureRepoTriggerEvent.PUSH);
    assertThat(spec.fetchPayloadAware().fetchPayloadConditions()).containsAll(payloadConditions);
    assertThat(spec.fetchPayloadAware().fetchHeaderConditions()).containsAll(headerConditions);
    assertThat(spec.fetchPayloadAware().fetchJexlCondition()).isEqualTo(JEXL);
    assertThat(spec.fetchGitAware().fetchRepoName()).isEqualTo(REPO);
    assertThat(spec.fetchGitAware().fetchConnectorRef()).isEqualTo(CONN);
    assertThat(spec.fetchGitAware().fetchAutoAbortPreviousExecutions()).isTrue();
    assertThat(spec.fetchGitAware().fetchEvent()).isEqualTo(AzureRepoTriggerEvent.PUSH);
    assertThat(spec.fetchGitAware().fetchActions()).isEmpty();
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testGAzureRepoIssueComment() throws Exception {
    NGTriggerConfigV2 ngTriggerConfigV2 =
        ngTriggerElementMapper.toTriggerConfigV2(ngTriggerYaml_azurerepo_issue_comment);

    assertRootLevelProperties(ngTriggerConfigV2);

    NGTriggerSourceV2 ngTriggerSourceV2 = ngTriggerConfigV2.getSource();
    assertThat(ngTriggerSourceV2).isNotNull();
    assertThat(ngTriggerSourceV2.getType()).isEqualTo(WEBHOOK);
    NGTriggerSpecV2 ngTriggerSpecV2 = ngTriggerSourceV2.getSpec();
    assertThat(WebhookTriggerConfigV2.class.isAssignableFrom(ngTriggerSpecV2.getClass())).isTrue();
    WebhookTriggerConfigV2 webhookTriggerConfigV2 = (WebhookTriggerConfigV2) ngTriggerSpecV2;
    assertThat(webhookTriggerConfigV2.getType()).isEqualTo(WebhookTriggerType.AZURE);
    assertThat(AzureRepoSpec.class.isAssignableFrom(webhookTriggerConfigV2.getSpec().getClass())).isTrue();
    AzureRepoSpec azureRepoSpec = (AzureRepoSpec) webhookTriggerConfigV2.getSpec();
    assertThat(azureRepoSpec.getType()).isEqualTo(AzureRepoTriggerEvent.ISSUE_COMMENT);
    assertThat(azureRepoSpec.fetchPayloadAware().fetchPayloadConditions()).containsAll(payloadConditions);
    assertThat(azureRepoSpec.fetchPayloadAware().fetchHeaderConditions()).containsAll(headerConditions);
    assertThat(azureRepoSpec.fetchPayloadAware().fetchJexlCondition()).isEqualTo(JEXL);
    assertThat(azureRepoSpec.fetchGitAware().fetchRepoName()).isEqualTo(REPO);
    assertThat(azureRepoSpec.fetchGitAware().fetchConnectorRef()).isEqualTo(CONN);
    assertThat(azureRepoSpec.fetchGitAware().fetchAutoAbortPreviousExecutions()).isTrue();
    assertThat(azureRepoSpec.fetchGitAware().fetchEvent()).isEqualTo(AzureRepoTriggerEvent.ISSUE_COMMENT);
    assertThat(azureRepoSpec.fetchGitAware().fetchActions())
        .containsAll(asList(
            AzureRepoIssueCommentAction.CREATE, AzureRepoIssueCommentAction.EDIT, AzureRepoIssueCommentAction.DELETE));
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testAwsCodeCommitPush() throws Exception {
    NGTriggerConfigV2 ngTriggerConfigV2 = ngTriggerElementMapper.toTriggerConfigV2(ngTriggerYaml_awscodecommit_push);

    assertRootLevelProperties(ngTriggerConfigV2);

    NGTriggerSourceV2 ngTriggerSourceV2 = ngTriggerConfigV2.getSource();
    assertThat(ngTriggerSourceV2).isNotNull();
    assertThat(ngTriggerSourceV2.getType()).isEqualTo(WEBHOOK);
    NGTriggerSpecV2 ngTriggerSpecV2 = ngTriggerSourceV2.getSpec();
    assertThat(WebhookTriggerConfigV2.class.isAssignableFrom(ngTriggerSpecV2.getClass())).isTrue();
    WebhookTriggerConfigV2 webhookTriggerConfigV2 = (WebhookTriggerConfigV2) ngTriggerSpecV2;
    assertThat(webhookTriggerConfigV2.getType()).isEqualTo(WebhookTriggerType.AWS_CODECOMMIT);
    assertThat(AwsCodeCommitSpec.class.isAssignableFrom(webhookTriggerConfigV2.getSpec().getClass())).isTrue();
    AwsCodeCommitSpec spec = (AwsCodeCommitSpec) webhookTriggerConfigV2.getSpec();
    assertThat(spec.getType()).isEqualTo(AwsCodeCommitTriggerEvent.PUSH);
    assertThat(spec.fetchPayloadAware().fetchPayloadConditions()).containsAll(payloadConditions);
    assertThat(spec.fetchPayloadAware().fetchHeaderConditions()).isEmpty();
    assertThat(spec.fetchPayloadAware().fetchJexlCondition()).isEqualTo(JEXL);
    assertThat(spec.fetchGitAware().fetchRepoName()).isEqualTo(REPO);
    assertThat(spec.fetchGitAware().fetchConnectorRef()).isEqualTo(CONN);
    assertThat(spec.fetchGitAware().fetchAutoAbortPreviousExecutions()).isFalse();
    assertThat(spec.fetchGitAware().fetchEvent()).isEqualTo(AwsCodeCommitTriggerEvent.PUSH);
    assertThat(spec.fetchGitAware().fetchActions()).isEmpty();
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testCustomPush() throws Exception {
    NGTriggerConfigV2 ngTriggerConfigV2 = ngTriggerElementMapper.toTriggerConfigV2(ngTriggerYaml_custom);

    assertRootLevelProperties(ngTriggerConfigV2);

    NGTriggerSourceV2 ngTriggerSourceV2 = ngTriggerConfigV2.getSource();
    assertThat(ngTriggerSourceV2).isNotNull();
    assertThat(ngTriggerSourceV2.getType()).isEqualTo(WEBHOOK);
    NGTriggerSpecV2 ngTriggerSpecV2 = ngTriggerSourceV2.getSpec();
    assertThat(WebhookTriggerConfigV2.class.isAssignableFrom(ngTriggerSpecV2.getClass())).isTrue();
    WebhookTriggerConfigV2 webhookTriggerConfigV2 = (WebhookTriggerConfigV2) ngTriggerSpecV2;
    assertThat(webhookTriggerConfigV2.getType()).isEqualTo(WebhookTriggerType.CUSTOM);
    assertThat(CustomTriggerSpec.class.isAssignableFrom(webhookTriggerConfigV2.getSpec().getClass())).isTrue();
    CustomTriggerSpec spec = (CustomTriggerSpec) webhookTriggerConfigV2.getSpec();
    assertThat(spec.fetchPayloadAware().fetchPayloadConditions()).containsAll(payloadConditions);
    assertThat(spec.fetchPayloadAware().fetchHeaderConditions()).containsAll(headerConditions);
    assertThat(spec.fetchPayloadAware().fetchJexlCondition()).isEqualTo(JEXL);
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testCron() throws Exception {
    NGTriggerConfigV2 ngTriggerConfigV2 = ngTriggerElementMapper.toTriggerConfigV2(ngTriggerYaml_cron);

    assertRootLevelProperties(ngTriggerConfigV2);

    NGTriggerSourceV2 ngTriggerSourceV2 = ngTriggerConfigV2.getSource();
    assertThat(ngTriggerSourceV2).isNotNull();
    assertThat(ngTriggerSourceV2.getType()).isEqualTo(SCHEDULED);
    NGTriggerSpecV2 ngTriggerSpecV2 = ngTriggerSourceV2.getSpec();
    assertThat(ScheduledTriggerConfig.class.isAssignableFrom(ngTriggerSpecV2.getClass())).isTrue();
    ScheduledTriggerConfig scheduledTriggerConfig = (ScheduledTriggerConfig) ngTriggerSpecV2;
    assertThat(scheduledTriggerConfig.getType()).isEqualTo("Cron");
    ScheduledTriggerSpec scheduledTriggerSpec = scheduledTriggerConfig.getSpec();
    assertThat(CronTriggerSpec.class.isAssignableFrom(scheduledTriggerSpec.getClass())).isTrue();
    CronTriggerSpec cronTriggerSpec = (CronTriggerSpec) scheduledTriggerSpec;
    assertThat(cronTriggerSpec.getExpression()).isEqualTo("20 4 * * *");
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testArtifactGcr() throws Exception {
    NGTriggerConfigV2 ngTriggerConfigV2 = ngTriggerElementMapper.toTriggerConfigV2(ngTriggerYaml_artifact_gcr);

    assertRootLevelPropertiesForBuildTriggers(ngTriggerConfigV2);

    NGTriggerSourceV2 ngTriggerSourceV2 = ngTriggerConfigV2.getSource();
    assertCommonPathForArtifactTriggers(ngTriggerSourceV2, ArtifactType.GCR);

    ArtifactTriggerConfig artifactTriggerConfig = (ArtifactTriggerConfig) ngTriggerSourceV2.getSpec();
    ArtifactTypeSpec artifactTypeSpec = artifactTriggerConfig.getSpec();
    GcrSpec gcrSpec = (GcrSpec) artifactTypeSpec;
    assertThat(gcrSpec.getImagePath()).isEqualTo("test1");
    assertThat(gcrSpec.getRegistryHostname()).isEqualTo("us.gcr.io");
    assertThat(gcrSpec.getTag()).isEqualTo("<+trigger.artifact.build>");
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testArtifactEcr() throws Exception {
    NGTriggerConfigV2 ngTriggerConfigV2 = ngTriggerElementMapper.toTriggerConfigV2(ngTriggerYaml_artifact_ecr);

    assertRootLevelPropertiesForBuildTriggers(ngTriggerConfigV2);

    NGTriggerSourceV2 ngTriggerSourceV2 = ngTriggerConfigV2.getSource();
    assertCommonPathForArtifactTriggers(ngTriggerSourceV2, ArtifactType.ECR);

    ArtifactTriggerConfig artifactTriggerConfig = (ArtifactTriggerConfig) ngTriggerSourceV2.getSpec();
    ArtifactTypeSpec artifactTypeSpec = artifactTriggerConfig.getSpec();
    EcrSpec ecrSpec = (EcrSpec) artifactTypeSpec;
    assertThat(ecrSpec.getImagePath()).isEqualTo("test1");
    assertThat(ecrSpec.getRegion()).isEqualTo("us-east-1");
    assertThat(ecrSpec.getTag()).isEqualTo("<+trigger.artifact.build>");
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testManifestHelmS3() throws Exception {
    NGTriggerConfigV2 ngTriggerConfigV2 = ngTriggerElementMapper.toTriggerConfigV2(ngTriggerYaml_helm_S3);

    assertRootLevelPropertiesForBuildTriggers(ngTriggerConfigV2);

    NGTriggerSourceV2 ngTriggerSourceV2 = ngTriggerConfigV2.getSource();
    assertCommonPathForManifestTriggers(ngTriggerSourceV2, ManifestType.HELM_MANIFEST);

    ManifestTriggerConfig manifestTriggerConfig = (ManifestTriggerConfig) ngTriggerSourceV2.getSpec();
    HelmManifestSpec manifestTypeSpec = (HelmManifestSpec) manifestTriggerConfig.getSpec();

    assertThat(manifestTypeSpec.getChartName()).isEqualTo("chart1");
    assertThat(manifestTypeSpec.getChartVersion()).isEqualTo("<+trigger.manifest.version>");

    BuildStore store = manifestTypeSpec.getStore();
    assertThat(store.getType() == S3);
    assertThat(store.getSpec().fetchConnectorRef()).isEqualTo("account.conn");

    BuildStoreTypeSpec spec = store.getSpec();
    assertThat(S3BuildStoreTypeSpec.class.isAssignableFrom(spec.getClass()));

    S3BuildStoreTypeSpec s3BuildStoreTypeSpec = (S3BuildStoreTypeSpec) spec;
    assertThat(s3BuildStoreTypeSpec.getBucketName()).isEqualTo("bucket1");
    assertThat(s3BuildStoreTypeSpec.getConnectorRef()).isEqualTo("account.conn");
    assertThat(s3BuildStoreTypeSpec.getFolderPath()).isEqualTo("path1");
    assertThat(s3BuildStoreTypeSpec.getRegion()).isEqualTo("us-west-1");
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testManifestHelmGcs() throws Exception {
    NGTriggerConfigV2 ngTriggerConfigV2 = ngTriggerElementMapper.toTriggerConfigV2(ngTriggerYaml_helm_gcs);

    assertRootLevelPropertiesForBuildTriggers(ngTriggerConfigV2);

    NGTriggerSourceV2 ngTriggerSourceV2 = ngTriggerConfigV2.getSource();
    assertCommonPathForManifestTriggers(ngTriggerSourceV2, ManifestType.HELM_MANIFEST);

    ManifestTriggerConfig manifestTriggerConfig = (ManifestTriggerConfig) ngTriggerSourceV2.getSpec();
    HelmManifestSpec manifestTypeSpec = (HelmManifestSpec) manifestTriggerConfig.getSpec();

    assertThat(manifestTypeSpec.getChartName()).isEqualTo("chart1");
    assertThat(manifestTypeSpec.getChartVersion()).isEqualTo("<+trigger.manifest.version>");

    BuildStore store = manifestTypeSpec.getStore();
    assertThat(store.getType() == GCS);
    assertThat(store.getSpec().fetchConnectorRef()).isEqualTo("account.conn");

    BuildStoreTypeSpec spec = store.getSpec();
    assertThat(S3BuildStoreTypeSpec.class.isAssignableFrom(spec.getClass()));

    GcsBuildStoreTypeSpec gcsBuildStoreTypeSpec = (GcsBuildStoreTypeSpec) spec;
    assertThat(gcsBuildStoreTypeSpec.getBucketName()).isEqualTo("bucket1");
    assertThat(gcsBuildStoreTypeSpec.getConnectorRef()).isEqualTo("account.conn");
    assertThat(gcsBuildStoreTypeSpec.getFolderPath()).isEqualTo("path1");
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testManifestHelmHttp() throws Exception {
    NGTriggerConfigV2 ngTriggerConfigV2 = ngTriggerElementMapper.toTriggerConfigV2(ngTriggerYaml_helm_http);

    assertRootLevelPropertiesForBuildTriggers(ngTriggerConfigV2);

    NGTriggerSourceV2 ngTriggerSourceV2 = ngTriggerConfigV2.getSource();
    assertCommonPathForManifestTriggers(ngTriggerSourceV2, ManifestType.HELM_MANIFEST);

    ManifestTriggerConfig manifestTriggerConfig = (ManifestTriggerConfig) ngTriggerSourceV2.getSpec();
    HelmManifestSpec manifestTypeSpec = (HelmManifestSpec) manifestTriggerConfig.getSpec();

    assertThat(manifestTypeSpec.getChartName()).isEqualTo("chart1");
    assertThat(manifestTypeSpec.getChartVersion()).isEqualTo("<+trigger.manifest.version>");

    BuildStore store = manifestTypeSpec.getStore();
    assertThat(store.getType() == HTTP);
    assertThat(store.getSpec().fetchConnectorRef()).isEqualTo("account.conn");

    BuildStoreTypeSpec spec = store.getSpec();
    assertThat(HttpBuildStoreTypeSpec.class.isAssignableFrom(spec.getClass()));

    HttpBuildStoreTypeSpec httpBuildStoreTypeSpec = (HttpBuildStoreTypeSpec) spec;
    assertThat(httpBuildStoreTypeSpec.getConnectorRef()).isEqualTo("account.conn");
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testArtifactDockerRegistry() throws Exception {
    NGTriggerConfigV2 ngTriggerConfigV2 =
        ngTriggerElementMapper.toTriggerConfigV2(ngTriggerYaml_artifact_dockerregistry);

    assertRootLevelPropertiesForBuildTriggers(ngTriggerConfigV2);

    NGTriggerSourceV2 ngTriggerSourceV2 = ngTriggerConfigV2.getSource();
    assertCommonPathForArtifactTriggers(ngTriggerSourceV2, ArtifactType.DOCKER_REGISTRY);

    ArtifactTriggerConfig artifactTriggerConfig = (ArtifactTriggerConfig) ngTriggerSourceV2.getSpec();
    ArtifactTypeSpec artifactTypeSpec = artifactTriggerConfig.getSpec();
    DockerRegistrySpec dockerRegistrySpec = (DockerRegistrySpec) artifactTypeSpec;
    assertThat(dockerRegistrySpec.getImagePath()).isEqualTo("test1");
    assertThat(dockerRegistrySpec.getTag()).isEqualTo("<+trigger.artifact.build>");
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testArtifactArtifactoryGenericRegistry() throws Exception {
    NGTriggerConfigV2 ngTriggerConfigV2 =
        ngTriggerElementMapper.toTriggerConfigV2(ngTriggerYaml_artifact_artifactorygenericregistry);

    assertRootLevelPropertiesForBuildTriggers(ngTriggerConfigV2);

    NGTriggerSourceV2 ngTriggerSourceV2 = ngTriggerConfigV2.getSource();
    assertCommonPathForArtifactTriggers(ngTriggerSourceV2, ArtifactType.ARTIFACTORY_REGISTRY);

    ArtifactTriggerConfig artifactTriggerConfig = (ArtifactTriggerConfig) ngTriggerSourceV2.getSpec();
    ArtifactTypeSpec artifactTypeSpec = artifactTriggerConfig.getSpec();
    ArtifactoryRegistrySpec artifactoryRegistrySpec = (ArtifactoryRegistrySpec) artifactTypeSpec;
    assertThat(artifactoryRegistrySpec.getArtifactDirectory()).isEqualTo("artifactstest");
    assertThat(artifactoryRegistrySpec.getArtifactPath()).isEqualTo("<+trigger.artifact.build>");
    assertThat(artifactoryRegistrySpec.getRepository()).isEqualTo("automation-repo-do-not-delete");
    assertThat(artifactoryRegistrySpec.getRepositoryFormat()).isEqualTo("generic");
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testArtifactArtifactoryDockerRegistry() throws Exception {
    NGTriggerConfigV2 ngTriggerConfigV2 =
        ngTriggerElementMapper.toTriggerConfigV2(ngTriggerYaml_artifact_artifactorydockerregistry);

    assertRootLevelPropertiesForBuildTriggers(ngTriggerConfigV2);

    NGTriggerSourceV2 ngTriggerSourceV2 = ngTriggerConfigV2.getSource();
    assertCommonPathForArtifactTriggers(ngTriggerSourceV2, ArtifactType.ARTIFACTORY_REGISTRY);

    ArtifactTriggerConfig artifactTriggerConfig = (ArtifactTriggerConfig) ngTriggerSourceV2.getSpec();
    ArtifactTypeSpec artifactTypeSpec = artifactTriggerConfig.getSpec();
    ArtifactoryRegistrySpec artifactoryRegistrySpec = (ArtifactoryRegistrySpec) artifactTypeSpec;
    assertThat(artifactoryRegistrySpec.getRepositoryUrl()).isEqualTo("url");
    assertThat(artifactoryRegistrySpec.getArtifactPath()).isEqualTo("path");
    assertThat(artifactoryRegistrySpec.getRepository()).isEqualTo("automation-repo-do-not-delete");
    assertThat(artifactoryRegistrySpec.getRepositoryFormat()).isEqualTo("docker");
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testArtifactAcr() throws Exception {
    NGTriggerConfigV2 ngTriggerConfigV2 = ngTriggerElementMapper.toTriggerConfigV2(ngTriggerYaml_artifact_acr);

    assertRootLevelPropertiesForBuildTriggers(ngTriggerConfigV2);

    NGTriggerSourceV2 ngTriggerSourceV2 = ngTriggerConfigV2.getSource();
    assertCommonPathForArtifactTriggers(ngTriggerSourceV2, ArtifactType.ACR);

    ArtifactTriggerConfig artifactTriggerConfig = (ArtifactTriggerConfig) ngTriggerSourceV2.getSpec();
    ArtifactTypeSpec artifactTypeSpec = artifactTriggerConfig.getSpec();
    AcrSpec acrSpec = (AcrSpec) artifactTypeSpec;
    assertThat(acrSpec.getSubscriptionId()).isEqualTo("test-subscriptionId");
    assertThat(acrSpec.getRegistry()).isEqualTo("test-registry");
    assertThat(acrSpec.getRepository()).isEqualTo("test-repository");
    assertThat(acrSpec.getTag()).isEqualTo("<+trigger.artifact.build>");
  }

  private void assertCommonPathForArtifactTriggers(NGTriggerSourceV2 ngTriggerSourceV2, ArtifactType artifactType) {
    assertThat(ngTriggerSourceV2).isNotNull();
    assertThat(ngTriggerSourceV2.getType()).isEqualTo(ARTIFACT);
    NGTriggerSpecV2 ngTriggerSpecV2 = ngTriggerSourceV2.getSpec();

    assertThat(BuildAware.class.isAssignableFrom(ngTriggerSpecV2.getClass())).isTrue();
    BuildAware buildAware = (BuildAware) ngTriggerSpecV2;
    assertThat(buildAware.fetchbuildRef()).isEqualTo("primary");
    assertThat(buildAware.fetchStageRef()).isEqualTo("dev");
    assertThat(buildAware.fetchBuildType()).isEqualTo(artifactType.getValue());

    assertThat(ArtifactTriggerConfig.class.isAssignableFrom(ngTriggerSpecV2.getClass())).isTrue();
    ArtifactTriggerConfig artifactTriggerConfig = (ArtifactTriggerConfig) ngTriggerSpecV2;
    assertThat(artifactTriggerConfig.getArtifactRef()).isEqualTo("primary");
    assertThat(artifactTriggerConfig.getStageIdentifier()).isEqualTo("dev");
    assertThat(artifactTriggerConfig.getType() == artifactType).isTrue();

    ArtifactTypeSpec artifactTypeSpec = artifactTriggerConfig.getSpec();
    assertThat(artifactTypeSpec.fetchConnectorRef()).isEqualTo("account.conn");
    assertThat(artifactTypeSpec.fetchBuildType()).isEqualTo(artifactType.getValue());
    List<TriggerEventDataCondition> triggerEventDataConditions = artifactTypeSpec.fetchEventDataConditions();
    assertThat(triggerEventDataConditions).isNotEmpty();
    assertThat(triggerEventDataConditions.size()).isEqualTo(1);
    assertThat(triggerEventDataConditions.get(0).getKey()).isEqualTo("build");
    assertThat(triggerEventDataConditions.get(0).getOperator().getValue()).isEqualTo("Regex");
    assertThat(triggerEventDataConditions.get(0).getValue()).isEqualTo("release.*");
  }

  private void assertCommonPathForManifestTriggers(NGTriggerSourceV2 ngTriggerSourceV2, ManifestType manifestType) {
    assertThat(ngTriggerSourceV2).isNotNull();
    assertThat(ngTriggerSourceV2.getType()).isEqualTo(MANIFEST);
    NGTriggerSpecV2 ngTriggerSpecV2 = ngTriggerSourceV2.getSpec();

    assertThat(BuildAware.class.isAssignableFrom(ngTriggerSpecV2.getClass())).isTrue();
    BuildAware buildAware = (BuildAware) ngTriggerSpecV2;
    assertThat(buildAware.fetchbuildRef()).isEqualTo("man1");
    assertThat(buildAware.fetchStageRef()).isEqualTo("dev");
    assertThat(buildAware.fetchBuildType()).isEqualTo(manifestType.getValue());

    assertThat(ManifestTriggerConfig.class.isAssignableFrom(ngTriggerSpecV2.getClass())).isTrue();
    ManifestTriggerConfig triggerConfig = (ManifestTriggerConfig) ngTriggerSpecV2;
    assertThat(triggerConfig.getManifestRef()).isEqualTo("man1");
    assertThat(triggerConfig.getStageIdentifier()).isEqualTo("dev");
    assertThat(triggerConfig.getType() == manifestType).isTrue();

    ManifestTypeSpec manifestTypeSpec = triggerConfig.getSpec();
    assertThat(manifestTypeSpec.fetchBuildType()).isEqualTo(manifestType.getValue());
    List<TriggerEventDataCondition> triggerEventDataConditions = manifestTypeSpec.fetchEventDataConditions();
    assertThat(triggerEventDataConditions).isNotEmpty();
    assertThat(triggerEventDataConditions.size()).isEqualTo(1);
    assertThat(triggerEventDataConditions.get(0).getKey()).isEqualTo("version");
    assertThat(triggerEventDataConditions.get(0).getOperator().getValue()).isEqualTo("Regex");
    assertThat(triggerEventDataConditions.get(0).getValue()).isEqualTo("release.*");
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testYamlConversion() throws Exception {
    String yamlV0 = Resources.toString(
        Objects.requireNonNull(getClass().getClassLoader().getResource("ng-trigger-v0.yaml")), StandardCharsets.UTF_8);

    NGTriggerConfigV2 ngTriggerConfigV2 = ngTriggerElementMapper.toTriggerConfigV2(NGTriggerEntity.builder()
                                                                                       .accountId("acc")
                                                                                       .orgIdentifier("org")
                                                                                       .projectIdentifier("proj")
                                                                                       .identifier("first_trigger")
                                                                                       .ymlVersion(null)
                                                                                       .yaml(yamlV0)
                                                                                       .build());

    ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory()
                                                     .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)
                                                     .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
                                                     .disable(USE_NATIVE_TYPE_ID));
    objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    String s = objectMapper.writeValueAsString(ngTriggerConfigV2);

    NGTriggerConfigV2 ngTriggerConfigV3 = ngTriggerElementMapper.toTriggerConfigV2(s);
    int i = 0;
  }

  @Test
  @Owner(developers = MATT)
  @Category(UnitTests.class)
  public void testTriggerEntityCronHasNextIterations() {
    NGTriggerEntity ngTriggerEntity =
        ngTriggerElementMapper.toTriggerDetails("accId", "orgId", "projId", ngTriggerYaml_cron, false)
            .getNgTriggerEntity();
    assertThat(ngTriggerEntity.getNextIterations()).isNotEmpty();
    // all elements snap to the nearest minute -- in other words,  now is not an element.
    for (long nextIteration : ngTriggerEntity.getNextIterations()) {
      assertThat(nextIteration % 60000).isEqualTo(0);
    }
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testToTriggerEntityWithWrongIdentifier() {
    assertThatThrownBy(()
                           -> ngTriggerElementMapper.toTriggerEntity(
                               "accId", "orgId", "projId", "not_first_trigger", ngTriggerYaml_gitlab_pr, false))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testPrepareExecutionDataArray() throws Exception {
    String sDate0 = "23-Dec-1998 02:37:50";
    String sDate1 = "24-Dec-1998 02:37:50";
    String sDate2 = "24-Dec-1998 12:37:50";
    String sDate3 = "25-Dec-1998 14:37:50";
    String sDate4 = "25-Dec-1998 15:37:50";
    String sDate5 = "25-Dec-1998 16:37:50";
    String sDate6 = "25-Dec-1998 20:37:50";
    String sDate7 = "26-Dec-1998 01:37:50";
    String sDate8 = "26-Dec-1998 11:12:50";
    String sDate9 = "26-Dec-1998 21:37:50";
    String sDate10 = "26-Dec-1998 22:37:50";
    String sDate11 = "26-Dec-1998 23:37:50";
    String sDate12 = "26-Dec-1998 23:47:50";
    String sDate13 = "27-Dec-1998 02:37:50";
    String sDate14 = "27-Dec-1998 21:37:50";
    String sDate15 = "29-Dec-1998 23:37:50";
    String sDate16 = "29-Dec-1998 13:37:50";
    String sDate17 = "29-Dec-1998 14:37:50";
    String sDate18 = "29-Dec-1998 15:37:50";
    String sDate19 = "29-Dec-1998 16:37:50";
    String sDate20 = "30-Dec-1998 17:37:50";
    String sDate21 = "30-Dec-1998 18:37:50";

    SimpleDateFormat formatter = new SimpleDateFormat("dd-MMM-yyyy HH:mm:ss");
    List<TriggerEventHistory> triggerEventHistories = asList(generateEventHistoryWithTimestamp(formatter, sDate0),
        generateEventHistoryWithTimestamp(formatter, sDate1), generateEventHistoryWithTimestamp(formatter, sDate2),
        generateEventHistoryWithTimestamp(formatter, sDate3), generateEventHistoryWithTimestamp(formatter, sDate4),
        generateEventHistoryWithTimestamp(formatter, sDate5), generateEventHistoryWithTimestamp(formatter, sDate6),
        generateEventHistoryWithTimestamp(formatter, sDate7), generateEventHistoryWithTimestamp(formatter, sDate8),
        generateEventHistoryWithTimestamp(formatter, sDate9), generateEventHistoryWithTimestamp(formatter, sDate10),
        generateEventHistoryWithTimestamp(formatter, sDate11), generateEventHistoryWithTimestamp(formatter, sDate12),
        generateEventHistoryWithTimestamp(formatter, sDate13), generateEventHistoryWithTimestamp(formatter, sDate14),
        generateEventHistoryWithTimestamp(formatter, sDate15), generateEventHistoryWithTimestamp(formatter, sDate16),
        generateEventHistoryWithTimestamp(formatter, sDate17), generateEventHistoryWithTimestamp(formatter, sDate18),
        generateEventHistoryWithTimestamp(formatter, sDate19), generateEventHistoryWithTimestamp(formatter, sDate20),
        generateEventHistoryWithTimestamp(formatter, sDate21));

    Integer[] executionData = ngTriggerElementMapper.prepareExecutionDataArray(
        formatter.parse("30-Dec-1998 21:37:50").getTime(), triggerEventHistories);
    assertThat(executionData).containsExactlyInAnyOrder(2, 5, 0, 2, 6, 4, 2);
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testToResponseDTO() {
    when(pmsFeatureFlagService.isEnabled(any(), eq(FeatureName.CD_GIT_WEBHOOK_POLLING))).thenReturn(Boolean.FALSE);
    NGTriggerEntity ngTriggerEntity =
        ngTriggerElementMapper.toTriggerDetails("accId", "org", "proj", ngTriggerYaml_gitlab_pr, false)
            .getNgTriggerEntity();
    NGTriggerResponseDTO responseDTO = ngTriggerElementMapper.toResponseDTO(ngTriggerEntity);
    assertThat(responseDTO.getAccountIdentifier()).isEqualTo(ngTriggerEntity.getAccountId());
    assertThat(responseDTO.getTargetIdentifier()).isEqualTo(ngTriggerEntity.getTargetIdentifier());
    assertThat(responseDTO.getOrgIdentifier()).isEqualTo(ngTriggerEntity.getOrgIdentifier());
    assertThat(responseDTO.getProjectIdentifier()).isEqualTo(ngTriggerEntity.getProjectIdentifier());
    assertThat(responseDTO.getYaml()).isEqualTo(ngTriggerEntity.getYaml());
    assertThat(responseDTO.getType()).isEqualTo(ngTriggerEntity.getType());
    assertThat(responseDTO.getName()).isEqualTo(ngTriggerEntity.getName());
    assertThat(responseDTO.getIdentifier()).isEqualTo(ngTriggerEntity.getIdentifier());
    assertThat(responseDTO.isEnabled()).isEqualTo(ngTriggerEntity.getEnabled());
    assertThat(responseDTO.getDescription()).isEqualTo(ngTriggerEntity.getDescription());
  }

  @Test
  @Owner(developers = SRIDHAR)
  @Category(UnitTests.class)
  public void testToResponseDTOGitPolling() {
    when(pmsFeatureFlagService.isEnabled(any(), eq(FeatureName.CD_GIT_WEBHOOK_POLLING))).thenReturn(Boolean.TRUE);
    NGTriggerEntity ngTriggerEntity =
        ngTriggerElementMapper.toTriggerDetails("accId", "org", "proj", ngTriggerYaml_gitpolling, false)
            .getNgTriggerEntity();
    NGTriggerResponseDTO responseDTO = ngTriggerElementMapper.toResponseDTO(ngTriggerEntity);
    assertThat(responseDTO.getAccountIdentifier()).isEqualTo(ngTriggerEntity.getAccountId());
    assertThat(responseDTO.getTargetIdentifier()).isEqualTo(ngTriggerEntity.getTargetIdentifier());
    assertThat(responseDTO.getOrgIdentifier()).isEqualTo(ngTriggerEntity.getOrgIdentifier());
    assertThat(responseDTO.getProjectIdentifier()).isEqualTo(ngTriggerEntity.getProjectIdentifier());
    assertThat(responseDTO.getYaml()).isEqualTo(ngTriggerEntity.getYaml());
    assertThat(responseDTO.getType()).isEqualTo(ngTriggerEntity.getType());
    assertThat(responseDTO.getName()).isEqualTo(ngTriggerEntity.getName());
    assertThat(responseDTO.getIdentifier()).isEqualTo(ngTriggerEntity.getIdentifier());
    assertThat(responseDTO.isEnabled()).isEqualTo(ngTriggerEntity.getEnabled());
    assertThat(responseDTO.getDescription()).isEqualTo(ngTriggerEntity.getDescription());
    assertNotNull(ngTriggerEntity.getMetadata().getBuildMetadata());
    assertThat(ngTriggerEntity.getMetadata().getBuildMetadata().getType()).isEqualTo(WEBHOOK);
    assertNotNull(ngTriggerEntity.getMetadata().getBuildMetadata().getPollingConfig().getSignature());
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testGetWebhookUrl() {
    NGTriggerEntity ngTriggerEntity =
        ngTriggerElementMapper.toTriggerDetails("accId", "org", "proj", ngTriggerYaml_gitlab_pr, false)
            .getNgTriggerEntity();
    NGTriggerDetailsResponseDTO ngTriggerDetailsResponseDTO =
        ngTriggerElementMapper.toNGTriggerDetailsResponseDTO(ngTriggerEntity, false, true, false);
    // baseUrl: "https://app.harness.io/pipeline/api"
    assertThat(ngTriggerDetailsResponseDTO.getWebhookUrl())
        .isEqualTo("https://app.harness.io/ng/api/webhook?accountIdentifier=accId");

    // baseUrl: "https://app.harness.io/pipeline/api/"
    ngTriggerEntity =
        ngTriggerElementMapper.toTriggerDetails("accId", "orgId", "projId", ngTriggerYaml_gitlab_pr, false)
            .getNgTriggerEntity();
    ngTriggerDetailsResponseDTO =
        ngTriggerElementMapper.toNGTriggerDetailsResponseDTO(ngTriggerEntity, false, true, true);
    assertThat(ngTriggerDetailsResponseDTO.getWebhookUrl())
        .isEqualTo("https://app.harness.io/ng/api/webhook?accountIdentifier=accId");

    // baseUrl: "https://app.harness.io/pipeline/api/#"
    ngTriggerDetailsResponseDTO =
        ngTriggerElementMapper.toNGTriggerDetailsResponseDTO(ngTriggerEntity, false, true, false);
    assertThat(ngTriggerDetailsResponseDTO.getWebhookUrl())
        .isEqualTo("https://app.harness.io/ng/api/webhook?accountIdentifier=accId");

    ngTriggerEntity = ngTriggerElementMapper.toTriggerDetails("accId", "org", "proj", ngTriggerYaml_custom, false)
                          .getNgTriggerEntity();
    ngTriggerDetailsResponseDTO =
        ngTriggerElementMapper.toNGTriggerDetailsResponseDTO(ngTriggerEntity, false, true, true);
    // baseUrl: "https://app.harness.io/pipeline/api"
    assertThat(ngTriggerDetailsResponseDTO.getWebhookUrl())
        .isEqualTo(
            "https://app.harness.io/pipeline/api/webhook/custom/v2?accountIdentifier=accId&orgIdentifier=org&projectIdentifier=proj&pipelineIdentifier=pipeline&triggerIdentifier=first_trigger");

    ngTriggerEntity.setType(SCHEDULED);
    ngTriggerDetailsResponseDTO =
        ngTriggerElementMapper.toNGTriggerDetailsResponseDTO(ngTriggerEntity, false, true, true);
    assertThat(ngTriggerDetailsResponseDTO.getWebhookUrl()).isEmpty();
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testArtifactTriggerToResponseDTO() {
    NGTriggerEntity ngTriggerEntity =
        ngTriggerElementMapper.toTriggerDetails("accId", "org", "proj", ngTriggerYaml_artifact_gcr, false)
            .getNgTriggerEntity();
    NGTriggerResponseDTO responseDTO = ngTriggerElementMapper.toResponseDTO(ngTriggerEntity);
    assertThat(responseDTO.getYaml()).isEqualTo(ngTriggerEntity.getYaml());
    assertThat(responseDTO.getType()).isEqualTo(ngTriggerEntity.getType());
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testManifestTriggerToResponseDTO() {
    NGTriggerEntity ngTriggerEntity =
        ngTriggerElementMapper.toTriggerDetails("accId", "org", "proj", ngTriggerYaml_manifest, false)
            .getNgTriggerEntity();
    NGTriggerResponseDTO responseDTO = ngTriggerElementMapper.toResponseDTO(ngTriggerEntity);
    assertThat(responseDTO.getYaml()).isEqualTo(ngTriggerEntity.getYaml());
    assertThat(responseDTO.getType()).isEqualTo(ngTriggerEntity.getType());
  }

  private TriggerEventHistory generateEventHistoryWithTimestamp(SimpleDateFormat formatter6, String sDate1)
      throws ParseException {
    return TriggerEventHistory.builder().createdAt(formatter6.parse(sDate1).getTime()).build();
  }

  private void assertRootLevelProperties(NGTriggerConfigV2 ngTriggerConfigV2) {
    assertThat(ngTriggerConfigV2).isNotNull();
    assertThat(ngTriggerConfigV2.getIdentifier()).isEqualTo("first_trigger");
    assertThat(ngTriggerConfigV2.getEnabled()).isTrue();
    assertThat(ngTriggerConfigV2.getInputYaml()).isEqualTo(inputYaml);
    assertThat(ngTriggerConfigV2.getPipelineIdentifier()).isEqualTo("pipeline");
    assertThat(ngTriggerConfigV2.getOrgIdentifier()).isEqualTo("org");
    assertThat(ngTriggerConfigV2.getProjectIdentifier()).isEqualTo("proj");
    assertThat(ngTriggerConfigV2.getName()).isEqualTo("first trigger");
  }

  private void assertRootLevelPropertiesForBuildTriggers(NGTriggerConfigV2 ngTriggerConfigV2) {
    assertThat(ngTriggerConfigV2).isNotNull();
    assertThat(ngTriggerConfigV2.getIdentifier()).isEqualTo("first_trigger");
    assertThat(ngTriggerConfigV2.getName()).isEqualTo("first trigger");
    assertThat(ngTriggerConfigV2.getEnabled()).isTrue();
    assertThat(ngTriggerConfigV2.getPipelineIdentifier()).isEqualTo("pipeline");
    assertThat(ngTriggerConfigV2.getOrgIdentifier()).isEqualTo("org");
    assertThat(ngTriggerConfigV2.getProjectIdentifier()).isEqualTo("proj");
  }
}
