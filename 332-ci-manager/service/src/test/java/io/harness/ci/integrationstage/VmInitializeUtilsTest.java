/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.integrationstage;

import static io.harness.rule.OwnerRule.DEV_MITTAL;
import static io.harness.rule.OwnerRule.SHUBHAM;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.beans.steps.stepinfo.InitializeStepInfo;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.category.element.UnitTests;
import io.harness.ci.executionplan.CIExecutionTestBase;
import io.harness.cimanager.stages.IntegrationStageConfig;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;

public class VmInitializeUtilsTest extends CIExecutionTestBase {
  @InjectMocks private VmInitializeUtils vmInitializeUtils;

  @Test
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void validateStageConfig() {
    IntegrationStageConfig integrationStageConfig = VmInitializeTaskHelper.getIntegrationStageConfig();
    String accountId = "test";

    vmInitializeUtils.validateStageConfig(integrationStageConfig, accountId);
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testValidateStageConfigWithStepGroup() throws Exception {
    IntegrationStageConfig integrationStageConfig = VmInitializeTaskHelper.getIntegrationStageConfigWithStepGroup();
    String accountId = "test";

    vmInitializeUtils.validateStageConfig(integrationStageConfig, accountId);
  }

  @Test
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void testLinuxOS() {
    InitializeStepInfo initializeStepInfo = VmInitializeTaskHelper.getInitializeStepWithLinuxPoolName();
    OSType os = VmInitializeUtils.getOS(initializeStepInfo.getInfrastructure());

    assertThat(os).isEqualTo(OSType.Linux);
  }

  @Test
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void testMacOS() {
    InitializeStepInfo initializeStepInfo = VmInitializeTaskHelper.getInitializeStepWithMacPoolName();
    OSType os = VmInitializeUtils.getOS(initializeStepInfo.getInfrastructure());

    assertThat(os).isEqualTo(OSType.MacOS);
  }

  @Test
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void testWorkDir() {
    assertThat(vmInitializeUtils.getWorkDir(OSType.MacOS)).isEqualTo("/tmp/harness");
    assertThat(vmInitializeUtils.getWorkDir(OSType.Linux)).isEqualTo("/harness");
  }

  @Test
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void testGetVolumeToMountPath() {
    ParameterField<List<String>> sharedPaths = ParameterField.createValueField(Arrays.asList("/shared1", "/shared2"));

    Map<String, String> expected = new HashMap<>();
    expected.put("harness", "/tmp/harness");
    expected.put("addon", "/tmp/addon");
    expected.put("shared-0", "/shared1");
    expected.put("shared-1", "/shared2");

    Map<String, String> volToMountPath = vmInitializeUtils.getVolumeToMountPath(sharedPaths, OSType.MacOS);
    assertThat(volToMountPath).isEqualTo(expected);
  }

  @Test
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void testGetVolumeToMountPathLinux() {
    ParameterField<List<String>> sharedPaths = ParameterField.createValueField(Arrays.asList("/shared1", "/shared2"));

    Map<String, String> expected = new HashMap<>();
    expected.put("harness", "/harness");
    expected.put("addon", "/addon");
    expected.put("shared-0", "/shared1");
    expected.put("shared-1", "/shared2");

    Map<String, String> volToMountPath = vmInitializeUtils.getVolumeToMountPath(sharedPaths, OSType.Linux);
    assertThat(volToMountPath).isEqualTo(expected);
  }
}