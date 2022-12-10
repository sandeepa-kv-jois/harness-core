/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.steps;

import static io.harness.beans.steps.CIStepInfoType.CIStepExecEnvironment.CI_LITE_ENGINE;
import static io.harness.beans.steps.CIStepInfoType.CIStepExecEnvironment.CI_MANAGER;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

public enum CIStepInfoType {
  BUILD(CI_LITE_ENGINE, "Build"),
  TEST(CI_LITE_ENGINE, "Test"),
  SETUP_ENV(CI_MANAGER, "SetupEnv"),
  CLEANUP(CI_MANAGER, "Cleanup"),
  RUN(CI_LITE_ENGINE, "Run"),
  BACKGROUND(CI_LITE_ENGINE, "Background"),
  PLUGIN(CI_LITE_ENGINE, "Plugin"),
  GIT_CLONE(CI_LITE_ENGINE, "GitClone"),
  INITIALIZE_TASK(CI_LITE_ENGINE, "liteEngineTask"),
  RUN_TESTS(CI_LITE_ENGINE, "RunTests"),
  ACR(CI_LITE_ENGINE, "BuildAndPushACR"),
  ECR(CI_LITE_ENGINE, "BuildAndPushECR"),
  GCR(CI_LITE_ENGINE, "BuildAndPushGCR"),
  DOCKER(CI_LITE_ENGINE, "BuildAndPushDockerRegistry"),
  UPLOAD_GCS(CI_LITE_ENGINE, "GCSUpload"),
  UPLOAD_S3(CI_LITE_ENGINE, "S3Upload"),
  SAVE_CACHE_GCS(CI_LITE_ENGINE, "SaveCacheGCS"),
  RESTORE_CACHE_GCS(CI_LITE_ENGINE, "RestoreCacheGCS"),
  SAVE_CACHE_S3(CI_LITE_ENGINE, "SaveCacheS3"),
  RESTORE_CACHE_S3(CI_LITE_ENGINE, "RestoreCacheS3"),
  UPLOAD_ARTIFACTORY(CI_LITE_ENGINE, "ArtifactoryUpload"),
  ACTION(CI_LITE_ENGINE, "Action"),
  SECURITY(CI_LITE_ENGINE, "Security"),
  AQUA_TRIVY(CI_LITE_ENGINE, "AquaTrivy"),
  AWS_ECR(CI_LITE_ENGINE, "AWSECR"),
  BANDIT(CI_LITE_ENGINE, "Bandit"),
  BLACKDUCK(CI_LITE_ENGINE, "BlackDuck"),
  BRAKEMAN(CI_LITE_ENGINE, "Brakeman"),
  BURP(CI_LITE_ENGINE, "Burp"),
  CHECKMARX(CI_LITE_ENGINE, "Checkmarx"),
  CLAIR(CI_LITE_ENGINE, "Clair"),
  DATA_THEOREM(CI_LITE_ENGINE, "DataTheorem"),
  DOCKER_CONTENT_TRUST(CI_LITE_ENGINE, "DockerContentTrust"),
  EXTERNAL(CI_LITE_ENGINE, "External"),
  FORTIFY_ON_DEMAND(CI_LITE_ENGINE, "FortifyOnDemand"),
  GRYPE(CI_LITE_ENGINE, "Grype"),
  JFROG_XRAY(CI_LITE_ENGINE, "JfrogXray"),
  MEND(CI_LITE_ENGINE, "Mend"),
  METASPLOIT(CI_LITE_ENGINE, "Metasploit"),
  NESSUS(CI_LITE_ENGINE, "Nessus"),
  NEXUS_IQ(CI_LITE_ENGINE, "NexusIQ"),
  NIKTO(CI_LITE_ENGINE, "Nikto"),
  NMAP(CI_LITE_ENGINE, "Nmap"),
  OPENVAS(CI_LITE_ENGINE, "Openvas"),
  OWASP(CI_LITE_ENGINE, "Owasp"),
  PRISMA_CLOUD(CI_LITE_ENGINE, "PrismaCloud"),
  PROWLER(CI_LITE_ENGINE, "Prowler"),
  QUALYS(CI_LITE_ENGINE, "Qualys"),
  REAPSAW(CI_LITE_ENGINE, "Reapsaw"),
  SHIFT_LEFT(CI_LITE_ENGINE, "ShiftLeft"),
  SNIPER(CI_LITE_ENGINE, "Sniper"),
  SNYK(CI_LITE_ENGINE, "Snyk"),
  SONARQUBE(CI_LITE_ENGINE, "Sonarqube"),
  SYSDIG(CI_LITE_ENGINE, "Sysdig"),
  TENABLE(CI_LITE_ENGINE, "Tenable"),
  VERACODE(CI_LITE_ENGINE, "Veracode"),
  ZAP(CI_LITE_ENGINE, "Zap"),
  BITRISE(CI_LITE_ENGINE, "Bitrise"),
  SCRIPT(CI_LITE_ENGINE, "script"),
  PLUGIN_V1(CI_LITE_ENGINE, "plugin");

  @Getter private final CIStepExecEnvironment ciStepExecEnvironment;
  private final String displayName;

  @JsonValue
  public String getDisplayName() {
    return displayName;
  }

  CIStepInfoType(CIStepExecEnvironment ciStepExecEnvironment, String displayName) {
    this.ciStepExecEnvironment = ciStepExecEnvironment;
    this.displayName = displayName;
  }
  public enum CIStepExecEnvironment { CI_MANAGER, CI_LITE_ENGINE }
}
