/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.accountsetting;

import static org.springframework.data.mongodb.core.query.Update.update;

import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.accountsetting.dto.AccountSettingType;
import io.harness.ng.core.accountsetting.entities.AccountSettings;
import io.harness.ng.core.accountsetting.entities.AccountSettings.AccountSettingsKeys;

import com.google.inject.Inject;
import com.mongodb.client.result.UpdateResult;
import java.util.List;
import javax.ws.rs.NotFoundException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class AccountSettingCustomRepositoryImpl implements AccountSettingCustomRepository {
  private MongoTemplate mongoTemplate;
  @Override
  public List<AccountSettings> findAll(
      String accountId, String orgIdentifier, String projectIdentifier, AccountSettingType type) {
    Criteria criteria = Criteria.where(AccountSettingsKeys.accountIdentifier)
                            .is(accountId)
                            .and(AccountSettingsKeys.orgIdentifier)
                            .is(orgIdentifier)
                            .and(AccountSettingsKeys.projectIdentifier)
                            .is(projectIdentifier);
    if (type != null) {
      criteria = criteria.and(AccountSettingsKeys.type).is(type);
    }
    Query query = new Query(criteria);

    return mongoTemplate.find(query, AccountSettings.class);
  }

  @Override
  public AccountSettings upsert(AccountSettings accountSettings, String accountIdentifier) {
    Criteria criteria = Criteria.where(AccountSettingsKeys.accountIdentifier)
                            .is(accountIdentifier)
                            .and(AccountSettingsKeys.orgIdentifier)
                            .is(accountSettings.getOrgIdentifier())
                            .and(AccountSettingsKeys.projectIdentifier)
                            .is(accountSettings.getProjectIdentifier())
                            .and(AccountSettingsKeys.type)
                            .is(accountSettings.getType());
    Update update = update(AccountSettingsKeys.config, accountSettings.getConfig());

    UpdateResult upsert = mongoTemplate.upsert(new Query(criteria), update, AccountSettings.class);
    return accountSettings;
  }

  @Override
  public AccountSettings findByScopeIdentifiersAndType(
      String accountId, String orgIdentifier, String projectIdentifier, AccountSettingType type) {
    Criteria criteria = Criteria.where(AccountSettingsKeys.accountIdentifier)
                            .is(accountId)
                            .and(AccountSettingsKeys.orgIdentifier)
                            .is(orgIdentifier)
                            .and(AccountSettingsKeys.projectIdentifier)
                            .is(projectIdentifier)
                            .and(AccountSettingsKeys.type)
                            .is(type);
    final AccountSettings result = mongoTemplate.findOne(new Query(criteria), AccountSettings.class);

    if (result == null) {
      log.error("AccountSettings for account {} in project {}, org {}, type {} not found", accountId, orgIdentifier,
          projectIdentifier, type);
      throw new NotFoundException(String.format("[%s] Settings for account [%s] not found", type, accountId));
    }
    return result;
  }

  @Override
  public AccountSettings updateAccountSetting(AccountSettings accountSettings, String accountIdentifier) {
    Criteria criteria = Criteria.where(AccountSettingsKeys.accountIdentifier)
                            .is(accountIdentifier)
                            .and(AccountSettingsKeys.orgIdentifier)
                            .is(accountSettings.getOrgIdentifier())
                            .and(AccountSettingsKeys.projectIdentifier)
                            .is(accountSettings.getProjectIdentifier())
                            .and(AccountSettingsKeys.type)
                            .is(accountSettings.getType());

    Update update = update(AccountSettingsKeys.config, accountSettings.getConfig());

    final AccountSettings existingRecord =
        mongoTemplate.findAndModify(new Query(criteria), update, AccountSettings.class);
    if (existingRecord == null) {
      throw new NotFoundException(String.format(
          "AccountSettings for account [%s] in project [%s], org [%s] type [%s] not found", accountIdentifier,
          accountSettings.getOrgIdentifier(), accountSettings.getProjectIdentifier(), accountSettings.getType()));
    }
    return existingRecord;
  }

  @Override
  public void insertAll(List<AccountSettings> accountSettingsList) {
    try {
      mongoTemplate.insertAll(accountSettingsList);
    } catch (Exception ex) {
      throw new InvalidRequestException(String.format("Failed to insert default account settings"), ex);
    }
  }
}
