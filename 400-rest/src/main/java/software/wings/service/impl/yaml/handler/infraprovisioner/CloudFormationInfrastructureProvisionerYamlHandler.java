/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.yaml.handler.infraprovisioner;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.exception.WingsException.USER;
import static io.harness.validation.Validator.notNullCheck;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;

import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.exception.HarnessException;
import io.harness.ff.FeatureFlagService;

import software.wings.beans.CloudFormationInfrastructureProvisioner;
import software.wings.beans.CloudFormationInfrastructureProvisioner.Yaml;
import software.wings.beans.GitFileConfig;
import software.wings.beans.InfrastructureProvisionerType;
import software.wings.beans.SettingAttribute;
import software.wings.beans.yaml.ChangeContext;
import software.wings.service.impl.GitFileConfigHelperService;
import software.wings.service.intfc.SettingsService;

import com.google.inject.Inject;
import java.util.List;

@OwnedBy(CDP)
public class CloudFormationInfrastructureProvisionerYamlHandler
    extends InfrastructureProvisionerYamlHandler<Yaml, CloudFormationInfrastructureProvisioner> {
  @Inject SettingsService settingsService;
  @Inject private GitFileConfigHelperService gitFileConfigHelperService;
  @Inject private FeatureFlagService featureFlagService;

  protected String getSourceRepoSettingId(String appId, String sourceRepoSettingName) {
    SettingAttribute settingAttribute = settingsService.getSettingAttributeByName(GLOBAL_APP_ID, sourceRepoSettingName);
    notNullCheck("Invalid Source Repo Setting:" + sourceRepoSettingName, settingAttribute, USER);
    return settingAttribute.getUuid();
  }

  protected String getSourceRepoSettingName(String appId, String sourceRepoSettingId) {
    SettingAttribute settingAttribute = settingsService.get(GLOBAL_APP_ID, sourceRepoSettingId);
    notNullCheck("Invalid Source Repo Setting:" + sourceRepoSettingId, settingAttribute, USER);
    return settingAttribute.getName();
  }

  @Override
  public Yaml toYaml(CloudFormationInfrastructureProvisioner bean, String appId) {
    Yaml yaml = Yaml.builder().build();
    super.toYaml(yaml, bean);
    yaml.setType(InfrastructureProvisionerType.CLOUD_FORMATION.name());
    yaml.setSourceType(bean.getSourceType());
    yaml.setTemplateBody(bean.getTemplateBody());
    yaml.setTemplateFilePath(bean.getTemplateFilePath());
    yaml.setGitFileConfig(getGitFileConfig(bean.getAccountId(), bean.getGitFileConfig()));
    return yaml;
  }

  private GitFileConfig getGitFileConfig(final String accountId, GitFileConfig gitFileConfig) {
    if (featureFlagService.isEnabled(FeatureName.YAML_GIT_CONNECTOR_NAME, accountId)) {
      return gitFileConfigHelperService.getGitFileConfigForToYaml(gitFileConfig);
    }
    return gitFileConfig;
  }

  @Override
  public CloudFormationInfrastructureProvisioner upsertFromYaml(
      ChangeContext<Yaml> changeContext, List<ChangeContext> changeSetContext) throws HarnessException {
    String yamlFilePath = changeContext.getChange().getFilePath();
    String accountId = changeContext.getChange().getAccountId();
    String appId = yamlHelper.getAppId(accountId, yamlFilePath);
    notNullCheck("Couldn't retrieve app from yaml:" + yamlFilePath, appId, USER);

    CloudFormationInfrastructureProvisioner current = CloudFormationInfrastructureProvisioner.builder().build();
    toBean(current, changeContext, appId);

    String name = yamlHelper.getNameFromYamlFilePath(changeContext.getChange().getFilePath());
    CloudFormationInfrastructureProvisioner previous =
        (CloudFormationInfrastructureProvisioner) infrastructureProvisionerService.getByName(appId, name);

    current.setSyncFromGit(changeContext.getChange().isSyncFromGit());
    if (previous != null) {
      current.setUuid(previous.getUuid());
      current = (CloudFormationInfrastructureProvisioner) infrastructureProvisionerService.update(current);
    } else {
      current = (CloudFormationInfrastructureProvisioner) infrastructureProvisionerService.save(current);
    }

    changeContext.setEntity(current);
    return current;
  }

  private void toBean(CloudFormationInfrastructureProvisioner bean, ChangeContext<Yaml> changeContext, String appId)
      throws HarnessException {
    Yaml yaml = changeContext.getYaml();
    String accountId = changeContext.getChange().getAccountId();
    String yamlFilePath = changeContext.getChange().getFilePath();
    super.toBean(changeContext, bean, appId, yamlFilePath);
    bean.setTemplateFilePath(yaml.getTemplateFilePath());
    bean.setTemplateBody(yaml.getTemplateBody());
    bean.setSourceType(yaml.getSourceType());
    bean.setGitFileConfig(setGitFileConfig(accountId, appId, yaml.getGitFileConfig()));
  }

  private GitFileConfig setGitFileConfig(final String accountId, final String appId, GitFileConfig gitFileConfig) {
    // make connectorId optional in Yaml
    if (gitFileConfig != null && isBlank(gitFileConfig.getConnectorId())) {
      return gitFileConfigHelperService.getGitFileConfigFromYaml(accountId, appId, gitFileConfig);
    }
    return gitFileConfig;
  }

  @Override
  public Class getYamlClass() {
    return Yaml.class;
  }

  @Override
  public CloudFormationInfrastructureProvisioner get(String accountId, String yamlFilePath) {
    return (CloudFormationInfrastructureProvisioner) yamlHelper.getInfrastructureProvisioner(accountId, yamlFilePath);
  }
}
