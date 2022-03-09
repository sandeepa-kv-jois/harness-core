/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.MigratedEntityMapping;
import io.harness.beans.MigratedEntityMapping.MigratedEntityMappingKeys;
import io.harness.data.structure.EmptyPredicate;
import io.harness.encryption.Scope;
import io.harness.persistence.HPersistence;

import software.wings.ngmigration.CgBasicInfo;
import software.wings.ngmigration.NGYamlFile;

import com.google.inject.Inject;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import org.mongodb.morphia.query.Query;

@OwnedBy(HarnessTeam.CDC)
public class MigratorMappingService {
  @Inject private NgMigrationFactory ngMigrationFactory;
  @Inject private HPersistence hPersistence;

  public static String getFullyQualifiedIdentifier(String accountId, String org, String project, String identifier) {
    StringBuilder stringBuilder = new StringBuilder();
    stringBuilder.append(accountId).append('/');
    if (StringUtils.isNotBlank(org)) {
      stringBuilder.append(org).append('/');
    }
    if (StringUtils.isNotBlank(project)) {
      stringBuilder.append(project).append('/');
    }
    return stringBuilder.append(identifier).toString();
  }

  public static Scope getScope(String org, String project) {
    if (StringUtils.isNotBlank(project)) {
      return Scope.PROJECT;
    }
    if (StringUtils.isNotBlank(org)) {
      return Scope.ORG;
    }
    return Scope.ACCOUNT;
  }

  public void mapCgNgEntity(NGYamlFile yamlFile) {
    MigratedEntityMapping mapping = ngMigrationFactory.getMethod(yamlFile.getType()).generateMappingEntity(yamlFile);
    if (!doesMappingExist(yamlFile)) {
      hPersistence.save(mapping);
    }
  }

  public boolean doesMappingExist(NGYamlFile yamlFile) {
    CgBasicInfo basicInfo = yamlFile.getCgBasicInfo();
    MigratedEntityMapping mapping = ngMigrationFactory.getMethod(yamlFile.getType()).generateMappingEntity(yamlFile);
    Query<MigratedEntityMapping> query =
        hPersistence.createQuery(MigratedEntityMapping.class)
            .filter(MigratedEntityMappingKeys.accountId, basicInfo.getAccountId())
            .filter(MigratedEntityMappingKeys.cgEntityId, basicInfo.getId())
            .filter(MigratedEntityMappingKeys.entityType, basicInfo.getType().name())
            .filter(MigratedEntityMappingKeys.accountIdentifier, basicInfo.getAccountId())
            .filter(MigratedEntityMappingKeys.identifier, mapping.getIdentifier())
            .filter(
                MigratedEntityMappingKeys.scope, getScope(mapping.getOrgIdentifier(), mapping.getProjectIdentifier()));
    if (StringUtils.isNotBlank(basicInfo.getAppId())) {
      query.filter(MigratedEntityMappingKeys.appId, basicInfo.getAppId());
    }
    return EmptyPredicate.isNotEmpty(query.asList());
  }

  // Returns all entities mappable in scope.
  // For example - If we are looking to get possible matches in PROJECT, we will look into PROJECT, ORG & ACCOUNT.
  // For example - If we are looking to get possible matches in ORG, we will look into only ORG & ACCOUNT.
  // For example - If we are looking to get possible matches in ACCOUNT, we will look into only ACCOUNT.
  public List<MigratedEntityMapping> getPossibleMappedEntities(
      CgBasicInfo basicInfo, String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    Query<MigratedEntityMapping> query = hPersistence.createQuery(MigratedEntityMapping.class)
                                             .filter(MigratedEntityMappingKeys.accountId, basicInfo.getAccountId())
                                             .filter(MigratedEntityMappingKeys.cgEntityId, basicInfo.getId())
                                             .filter(MigratedEntityMappingKeys.entityType, basicInfo.getType().name());
    if (StringUtils.isNotBlank(basicInfo.getAppId())) {
      query.filter(MigratedEntityMappingKeys.appId, basicInfo.getAppId());
    }
    List<MigratedEntityMapping> entities = query.asList();
    Stream<MigratedEntityMapping> stream =
        entities.stream().filter(entity -> entity.getAccountIdentifier().equals(accountIdentifier));
    if (StringUtils.isNotBlank(orgIdentifier)) {
      stream = stream.filter(
          entity -> StringUtils.isBlank(entity.getOrgIdentifier()) || entity.getOrgIdentifier().equals(orgIdentifier));
    } else {
      stream = stream.filter(entity -> StringUtils.isBlank(entity.getOrgIdentifier()));
    }

    if (StringUtils.isNotBlank(projectIdentifier)) {
      stream = stream.filter(entity
          -> StringUtils.isBlank(entity.getProjectIdentifier())
              || entity.getProjectIdentifier().equals(projectIdentifier));
    } else {
      stream = stream.filter(entity -> StringUtils.isBlank(entity.getProjectIdentifier()));
    }
    return stream.collect(Collectors.toList());
  }
}
