/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.connector;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.jira.JiraConnectorDTO;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.service.MigratorUtility;

import software.wings.beans.JiraConfig;
import software.wings.beans.SettingAttribute;
import software.wings.ngmigration.CgEntityId;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

@OwnedBy(HarnessTeam.CDC)
public class JiraConnectorImpl implements BaseConnector {
  @Override
  public List<String> getSecretIds(SettingAttribute settingAttribute) {
    return Collections.singletonList(((JiraConfig) settingAttribute.getValue()).getEncryptedPassword());
  }

  @Override
  public ConnectorType getConnectorType(SettingAttribute settingAttribute) {
    return ConnectorType.JIRA;
  }

  @Override
  public ConnectorConfigDTO getConfigDTO(
      SettingAttribute settingAttribute, Set<CgEntityId> childEntities, Map<CgEntityId, NGYamlFile> migratedEntities) {
    JiraConfig jiraConfig = (JiraConfig) settingAttribute.getValue();
    return JiraConnectorDTO.builder()
        .jiraUrl(jiraConfig.getBaseUrl())
        .delegateSelectors(toSet(jiraConfig.getDelegateSelectors()))
        .username(jiraConfig.getUsername())
        .passwordRef(MigratorUtility.getSecretRef(migratedEntities, jiraConfig.getEncryptedPassword()))
        .build();
  }
}
