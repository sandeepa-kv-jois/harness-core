/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.tiserviceclient;

import static io.harness.rule.OwnerRule.AMAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;
import static org.mockito.Mockito.when;

import io.harness.beans.steps.CIStepInfoType;
import io.harness.category.element.UnitTests;
import io.harness.ci.beans.entities.CIExecutionConfig;
import io.harness.ci.config.CIExecutionServiceConfig;
import io.harness.execution.CIExecutionConfigService;
import io.harness.execution.DeprecatedImageInfo;
import io.harness.executionplan.CIExecutionTestBase;
import io.harness.repositories.CIExecutionConfigRepository;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;

@Slf4j
public class CIExecutionConfigServiceTest extends CIExecutionTestBase {
  @Mock CIExecutionConfigRepository cIExecutionConfigRepository;
  @Inject CIExecutionConfigService ciExecutionConfigService;
  @Inject CIExecutionServiceConfig ciExecutionServiceConfig;

  @Before
  public void setUp() {
    on(ciExecutionConfigService).set("configRepository", cIExecutionConfigRepository);
  }

  @Test
  @Owner(developers = AMAN)
  @Category(UnitTests.class)
  public void getAddonImageTest() {
    CIExecutionConfig executionConfig = CIExecutionConfig.builder()
                                            .accountIdentifier("acct")
                                            .buildAndPushDockerRegistryImage("dockerImage")
                                            .addOnImage("addon:1.3.4")
                                            .liteEngineImage("le:1,4.4")
                                            .build();
    when(cIExecutionConfigRepository.findFirstByAccountIdentifier("acct")).thenReturn(Optional.of(executionConfig));
    when(cIExecutionConfigRepository.findFirstByAccountIdentifier("acct1")).thenReturn(Optional.empty());
    String customAddonImage = ciExecutionConfigService.getAddonImage("acct");
    String defaultAddonImage = ciExecutionConfigService.getAddonImage("acct1");
    assertThat(customAddonImage).isEqualTo("addon:1.3.4");
    assertThat(defaultAddonImage).isEqualTo(ciExecutionServiceConfig.getAddonImage());
  }

  @Test
  @Owner(developers = AMAN)
  @Category(UnitTests.class)
  public void getLEImageTest() {
    CIExecutionConfig executionConfig = CIExecutionConfig.builder()
                                            .accountIdentifier("acct")
                                            .buildAndPushDockerRegistryImage("dockerImage")
                                            .addOnImage("addon:1.3.4")
                                            .liteEngineImage("le:1.4.4")
                                            .build();
    when(cIExecutionConfigRepository.findFirstByAccountIdentifier("acct")).thenReturn(Optional.of(executionConfig));
    when(cIExecutionConfigRepository.findFirstByAccountIdentifier("acct1")).thenReturn(Optional.empty());
    String customAddonImage = ciExecutionConfigService.getLiteEngineImage("acct");
    String defaultAddonImage = ciExecutionConfigService.getAddonImage("acct1");
    assertThat(customAddonImage).isEqualTo("le:1.4.4");
    assertThat(defaultAddonImage).isEqualTo(ciExecutionServiceConfig.getAddonImage());
  }

  @Test
  @Owner(developers = AMAN)
  @Category(UnitTests.class)
  public void getDeprecatedImages_NoDeprecatedImagesTest() {
    CIExecutionConfig executionConfig = CIExecutionConfig.builder()
                                            .accountIdentifier("acct")
                                            .buildAndPushDockerRegistryImage("bpdr:1.2.3")
                                            .addOnImage("harness/ci-addon:1.2.0")
                                            .liteEngineImage("harness/ci-lite-engine:1.2.0")
                                            .gitCloneImage("gc:1.2.3")
                                            .buildAndPushDockerRegistryImage("bpdr:1.2.3")
                                            .buildAndPushECRImage("bpecr:1.2.3")
                                            .buildAndPushGCRImage("bpgcr:1.2.3")
                                            .gcsUploadImage("gcsupload:1.2.3")
                                            .s3UploadImage("s3upload:1.2.3")
                                            .artifactoryUploadTag("art:1.2.3")
                                            .securityImage("sc:1.2.3")
                                            .cacheGCSTag("cachegcs:1.2.3")
                                            .cacheS3Tag("caches3:1.2.3")
                                            .gcsUploadImage("gcsUpload:1.2.3")
                                            .build();
    when(cIExecutionConfigRepository.findFirstByAccountIdentifier("acct")).thenReturn(Optional.of(executionConfig));
    List<DeprecatedImageInfo> deprecatedImageInfos =
        Arrays.asList(DeprecatedImageInfo.builder().tag("CacheS3Image").version("caches3:1.2.3").build(),
            DeprecatedImageInfo.builder().tag("ArtifactoryUploadImage").version("art:1.2.3").build(),
            DeprecatedImageInfo.builder().tag("CacheGCSImage").version("cachegcs:1.2.3").build(),
            DeprecatedImageInfo.builder().tag("S3UploadImage").version("s3upload:1.2.3").build(),
            DeprecatedImageInfo.builder().tag("CacheS3Image").version("caches3:1.2.3").build(),
            DeprecatedImageInfo.builder().tag("GCSUploadImage").version("gcsUpload:1.2.3").build(),
            DeprecatedImageInfo.builder().tag("SecurityImage").version("gcsUpload:1.2.3").build(),
            DeprecatedImageInfo.builder().tag("BuildAndPushDockerImage").version("bpdr:1.2.3").build(),
            DeprecatedImageInfo.builder().tag("GitCloneImage").version("gc:1.2.3").build(),
            DeprecatedImageInfo.builder().tag("BuildAndPushECRConfigImage").version("bpecr:1.2.3").build(),
            DeprecatedImageInfo.builder().tag("BuildAndPushGCRConfigImage").version("bpgcr:1.2.3").build());
    assertThat(ciExecutionConfigService.getDeprecatedTags("acct")).isEqualTo(Arrays.asList());
  }

  @Test
  @Owner(developers = AMAN)
  @Category(UnitTests.class)
  public void getDeprecatedImages() {
    CIExecutionConfig executionConfig = CIExecutionConfig.builder()
                                            .accountIdentifier("acct")
                                            .buildAndPushDockerRegistryImage("bpdr:1.2.2")
                                            .addOnImage("harness/ci-addon:1.0.0")
                                            .liteEngineImage("harness/ci-lite-engine:1.0.0")
                                            .gitCloneImage("gc:1.2.2")
                                            .buildAndPushDockerRegistryImage("bpdr:1.2.2")
                                            .buildAndPushECRImage("bpecr:1.2.2")
                                            .buildAndPushGCRImage("bpgcr:1.2.2")
                                            .gcsUploadImage("gcsupload:1.2.2")
                                            .s3UploadImage("s3upload:1.2.2")
                                            .artifactoryUploadTag("art:1.2.2")
                                            .securityImage("sc:1.2.2")
                                            .cacheGCSTag("cachegcs:1.2.2")
                                            .cacheS3Tag("caches3:1.2.2")
                                            .gcsUploadImage("gcsUpload:1.2.2")
                                            .build();
    when(cIExecutionConfigRepository.findFirstByAccountIdentifier("acct")).thenReturn(Optional.of(executionConfig));
    List<DeprecatedImageInfo> deprecatedImageInfos =
        Arrays.asList(DeprecatedImageInfo.builder().tag("AddonImage").version("harness/ci-addon:1.0.0").build(),
            DeprecatedImageInfo.builder().tag("LiteEngineImage").version("harness/ci-lite-engine:1.0.0").build(),
            DeprecatedImageInfo.builder().tag("CacheS3Image").version("caches3:1.2.2").build(),
            DeprecatedImageInfo.builder().tag("ArtifactoryUploadImage").version("art:1.2.2").build(),
            DeprecatedImageInfo.builder().tag("CacheGCSImage").version("cachegcs:1.2.2").build(),
            DeprecatedImageInfo.builder().tag("S3UploadImage").version("s3upload:1.2.2").build(),
            DeprecatedImageInfo.builder().tag("CacheS3Image").version("caches3:1.2.2").build(),
            DeprecatedImageInfo.builder().tag("GCSUploadImage").version("gcsUpload:1.2.2").build(),
            DeprecatedImageInfo.builder().tag("SecurityImage").version("gcsUpload:1.2.2").build(),
            DeprecatedImageInfo.builder().tag("BuildAndPushDockerImage").version("bpdr:1.2.2").build(),
            DeprecatedImageInfo.builder().tag("GitCloneImage").version("gc:1.2.2").build(),
            DeprecatedImageInfo.builder().tag("BuildAndPushECRConfigImage").version("bpecr:1.2.2").build(),
            DeprecatedImageInfo.builder().tag("BuildAndPushGCRConfigImage").version("bpgcr:1.2.2").build());
    assertThat(ciExecutionConfigService.getDeprecatedTags("acct")).isEqualTo(deprecatedImageInfos);
  }

  @Test
  @Owner(developers = AMAN)
  @Category(UnitTests.class)
  public void getLE_LEIsDeprecatedTest() {
    CIExecutionConfig executionConfig = CIExecutionConfig.builder()
                                            .accountIdentifier("acct")
                                            .buildAndPushDockerRegistryImage("bpdr:1.2.3")
                                            .addOnImage("harness/ci-addon:1.0.1")
                                            .liteEngineImage("harness/ci-lite-engine:1.0.1")
                                            .gitCloneImage("gc:1.2.3")
                                            .buildAndPushDockerRegistryImage("bpdr:1.2.3")
                                            .buildAndPushECRImage("bpecr:1.2.3")
                                            .buildAndPushGCRImage("bpgcr:1.2.3")
                                            .gcsUploadImage("gcsupload:1.2.3")
                                            .s3UploadImage("s3upload:1.2.3")
                                            .artifactoryUploadTag("art:1.2.3")
                                            .securityImage("sc:1.2.3")
                                            .cacheGCSTag("cachegcs:1.2.3")
                                            .cacheS3Tag("caches3:1.2.3")
                                            .gcsUploadImage("gcsUpload:1.2.3")
                                            .build();
    when(cIExecutionConfigRepository.findFirstByAccountIdentifier("acct")).thenReturn(Optional.of(executionConfig));
    List<DeprecatedImageInfo> deprecatedImageInfos =
        Arrays.asList(DeprecatedImageInfo.builder().tag("AddonImage").version("harness/ci-addon:1.0.1").build(),
            DeprecatedImageInfo.builder().tag("LiteEngineImage").version("harness/ci-lite-engine:1.0.1").build());
    assertThat(ciExecutionConfigService.getDeprecatedTags("acct")).isEqualTo(deprecatedImageInfos);
  }

  @Test
  @Owner(developers = AMAN)
  @Category(UnitTests.class)
  public void getLE_LEIsNotDeprecatedTest() {
    CIExecutionConfig executionConfig = CIExecutionConfig.builder()
                                            .accountIdentifier("acct")
                                            .buildAndPushDockerRegistryImage("bpdr:1.2.3")
                                            .addOnImage("harness/ci-addon:1.2.0")
                                            .liteEngineImage("harness/ci-lite-engine:1.2.0")
                                            .gitCloneImage("gc:1.2.3")
                                            .buildAndPushDockerRegistryImage("bpdr:1.2.3")
                                            .buildAndPushECRImage("bpecr:1.2.3")
                                            .buildAndPushGCRImage("bpgcr:1.2.3")
                                            .gcsUploadImage("gcsupload:1.2.3")
                                            .s3UploadImage("s3upload:1.2.3")
                                            .artifactoryUploadTag("art:1.2.3")
                                            .securityImage("sc:1.2.3")
                                            .cacheGCSTag("cachegcs:1.2.3")
                                            .cacheS3Tag("caches3:1.2.3")
                                            .gcsUploadImage("gcsUpload:1.2.3")
                                            .build();
    when(cIExecutionConfigRepository.findFirstByAccountIdentifier("acct")).thenReturn(Optional.of(executionConfig));
    List<DeprecatedImageInfo> deprecatedImageInfos = Arrays.asList();
    assertThat(ciExecutionConfigService.getDeprecatedTags("acct")).isEqualTo(deprecatedImageInfos);
  }

  @Test
  @Owner(developers = AMAN)
  @Category(UnitTests.class)
  public void getLE_AddonIsDeprecatedLENotDeprecatedTest() {
    CIExecutionConfig executionConfig = CIExecutionConfig.builder()
                                            .accountIdentifier("acct")
                                            .buildAndPushDockerRegistryImage("bpdr:1.2.3")
                                            .addOnImage("harness/ci-addon:1.1.0")
                                            .liteEngineImage("harness/ci-lite-engine:1.2.0")
                                            .gitCloneImage("gc:1.2.3")
                                            .buildAndPushDockerRegistryImage("bpdr:1.2.3")
                                            .buildAndPushECRImage("bpecr:1.2.3")
                                            .buildAndPushGCRImage("bpgcr:1.2.3")
                                            .gcsUploadImage("gcsupload:1.2.3")
                                            .s3UploadImage("s3upload:1.2.3")
                                            .artifactoryUploadTag("art:1.2.3")
                                            .securityImage("sc:1.2.3")
                                            .cacheGCSTag("cachegcs:1.2.3")
                                            .cacheS3Tag("caches3:1.2.3")
                                            .gcsUploadImage("gcsUpload:1.2.3")
                                            .build();
    when(cIExecutionConfigRepository.findFirstByAccountIdentifier("acct")).thenReturn(Optional.of(executionConfig));
    List<DeprecatedImageInfo> deprecatedImageInfos =
        Arrays.asList(DeprecatedImageInfo.builder().tag("AddonImage").version("harness/ci-addon:1.1.0").build());
    assertThat(ciExecutionConfigService.getDeprecatedTags("acct")).isEqualTo(deprecatedImageInfos);
  }

  @Test
  @Owner(developers = AMAN)
  @Category(UnitTests.class)
  public void getPluginVersionTestCustomConfig_ShouldPass() {
    CIExecutionConfig executionConfig = CIExecutionConfig.builder()
                                            .accountIdentifier("acct")
                                            .buildAndPushDockerRegistryImage("bpdr:1.2.4")
                                            .addOnImage("harness/ci-addon:1.1.0")
                                            .liteEngineImage("harness/ci-lite-engine:1.2.0")
                                            .gitCloneImage("gc:abc")
                                            .buildAndPushECRImage("bpecr:1.2.3")
                                            .buildAndPushGCRImage("bpgcr:1.2.3")
                                            .gcsUploadImage("gcsupload:1.2.3")
                                            .s3UploadImage("s3upload:1.2.3")
                                            .artifactoryUploadTag("art:1.2.3")
                                            .securityImage("sc:1.2.3")
                                            .cacheGCSTag("cachegcs:1.2.3")
                                            .cacheS3Tag("caches3:1.2.3")
                                            .gcsUploadImage("gcsUpload:1.2.3")
                                            .build();
    when(cIExecutionConfigRepository.findFirstByAccountIdentifier("acct")).thenReturn(Optional.of(executionConfig));
    assertThat(ciExecutionConfigService.getPluginVersion(CIStepInfoType.GIT_CLONE, "acct").getImage())
        .isEqualTo("gc:abc");
    assertThat(ciExecutionConfigService.getPluginVersion(CIStepInfoType.DOCKER, "acct").getImage())
        .isEqualTo("bpdr:1.2.4");
  }

  @Test
  @Owner(developers = AMAN)
  @Category(UnitTests.class)
  public void getPluginVersionTestDefaultConfig_ShouldPass() {
    CIExecutionConfig executionConfig = CIExecutionConfig.builder()
                                            .accountIdentifier("acct")
                                            .buildAndPushDockerRegistryImage("bpdr:1.2.4")
                                            .addOnImage("harness/ci-addon:1.1.0")
                                            .liteEngineImage("harness/ci-lite-engine:1.2.0")
                                            .gitCloneImage("gc:abc")
                                            .buildAndPushECRImage("bpecr:1.2.3")
                                            .buildAndPushGCRImage("bpgcr:1.2.3")
                                            .gcsUploadImage("gcsupload:1.2.3")
                                            .s3UploadImage("s3upload:1.2.3")
                                            .artifactoryUploadTag("art:1.2.3")
                                            .securityImage("sc:1.2.3")
                                            .cacheGCSTag("cachegcs:1.2.3")
                                            .cacheS3Tag("caches3:1.2.3")
                                            .build();
    when(cIExecutionConfigRepository.findFirstByAccountIdentifier("acct")).thenReturn(Optional.empty());
    assertThat(ciExecutionConfigService.getPluginVersion(CIStepInfoType.GIT_CLONE, "acct").getImage())
        .isEqualTo("gc:1.2.3");
    assertThat(ciExecutionConfigService.getPluginVersion(CIStepInfoType.DOCKER, "acct").getImage())
        .isEqualTo("bpdr:1.2.3");
  }
}
