/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.template.utils;

import io.harness.beans.Scope;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.ScmException;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.template.entity.TemplateEntity;

import lombok.experimental.UtilityClass;

@UtilityClass
public class TemplateUtils {
  public Scope buildScope(TemplateEntity templateEntity) {
    return Scope.of(templateEntity.getAccountIdentifier(), templateEntity.getOrgIdentifier(),
        templateEntity.getProjectIdentifier());
  }

  public boolean isInlineEntity(GitEntityInfo gitEntityInfo) {
    return StoreType.INLINE.equals(gitEntityInfo.getStoreType()) || gitEntityInfo.getStoreType() == null;
  }

  public boolean isRemoteEntity(GitEntityInfo gitEntityInfo) {
    if (gitEntityInfo == null) {
      return false;
    }
    return StoreType.REMOTE.equals(gitEntityInfo.getStoreType());
  }

  public ScmException getScmException(Throwable ex) {
    while (ex != null) {
      if (ex instanceof ScmException) {
        return (ScmException) ex;
      }
      ex = ex.getCause();
    }
    return null;
  }

  public void setupGitParentEntityDetails(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String repoFromTemplate, String connectorFromTemplate) {
    GitEntityInfo gitEntityInfo = GitAwareContextHelper.getGitRequestParamsInfo();
    if (null != gitEntityInfo) {
      // Set Parent's Repo
      if (EmptyPredicate.isNotEmpty(repoFromTemplate)) {
        gitEntityInfo.setParentEntityRepoName(repoFromTemplate);
      } else if (!GitAwareContextHelper.isNullOrDefault(gitEntityInfo.getRepoName())) {
        gitEntityInfo.setParentEntityRepoName(gitEntityInfo.getRepoName());
      }

      // Set Parent's ConnectorRef
      if (EmptyPredicate.isNotEmpty(connectorFromTemplate)) {
        gitEntityInfo.setParentEntityConnectorRef(connectorFromTemplate);
      } else if (!GitAwareContextHelper.isNullOrDefault(gitEntityInfo.getConnectorRef())) {
        gitEntityInfo.setParentEntityConnectorRef(gitEntityInfo.getConnectorRef());
      }
      // Set Parent's Org identifier
      if (!GitAwareContextHelper.isNullOrDefault(orgIdentifier)) {
        gitEntityInfo.setParentEntityOrgIdentifier(orgIdentifier);
      }
      // Set Parent's Project Identifier
      if (!GitAwareContextHelper.isNullOrDefault(projectIdentifier)) {
        gitEntityInfo.setParentEntityProjectIdentifier(projectIdentifier);
      }
      gitEntityInfo.setParentEntityAccountIdentifier(accountIdentifier);
      GitAwareContextHelper.updateGitEntityContext(gitEntityInfo);
    }
  }
}
