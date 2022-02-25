/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.service;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.lang.String.format;

import io.harness.batch.processing.config.AzureStorageSyncConfig;
import io.harness.batch.processing.config.BatchMainConfig;
import io.harness.batch.processing.shard.AccountShardService;
import io.harness.batch.processing.tasklet.dto.CloudProviderEntityTags;
import io.harness.ccm.bigQuery.BigQueryService;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.ceazure.CEAzureConnectorDTO;

import software.wings.beans.Account;

import com.azure.core.management.AzureEnvironment;
import com.azure.core.management.profile.AzureProfile;
import com.azure.identity.ClientSecretCredential;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.azure.resourcemanager.AzureResourceManager;
import com.azure.resourcemanager.resources.fluent.models.TagsResourceInner;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Singleton
@Slf4j
public class AzureAccountTagsCollectionService extends CloudProviderEntityTagsCollectionService {
  @Autowired private BatchMainConfig mainConfig;
  @Autowired private AccountShardService accountShardService;
  @Autowired BigQueryService bigQueryService;

  public void update() {
    List<Account> accounts = accountShardService.getCeEnabledAccounts();
    log.info("accounts size: {}", accounts.size());
    for (Account account : accounts) {
      log.info("Fetching connectors for accountName {}, accountId {}", account.getAccountName(), account.getUuid());
      List<ConnectorResponseDTO> nextGenConnectorResponses =
          getNextGenConnectorResponses(account.getUuid(), ConnectorType.CE_AZURE);
      for (ConnectorResponseDTO connector : nextGenConnectorResponses) {
        ConnectorInfoDTO connectorInfo = connector.getConnector();
        CEAzureConnectorDTO ceAzureConnectorDTO = (CEAzureConnectorDTO) connectorInfo.getConnectorConfig();
        try {
          processAndInsertTags(ceAzureConnectorDTO, account);
        } catch (Exception e) {
          log.warn("Exception processing aws tags for connectorId: {} for CCM accountId: {}",
              connectorInfo.getIdentifier(), account.getUuid(), e);
        }
      }
    }
  }

  public void processAndInsertTags(CEAzureConnectorDTO ceAzureConnectorDTO, Account account) {
    String tableName = createBQTable(account); // This can be moved to connector creation part
    AzureStorageSyncConfig azureStorageSyncConfig = mainConfig.getAzureStorageSyncConfig();
    log.info("azureTenantID: {} azureSubscriptionId: {}, storage: {}", ceAzureConnectorDTO.getTenantId(),
        ceAzureConnectorDTO.getSubscriptionId(), ceAzureConnectorDTO.getBillingExportSpec().getStorageAccountName());

    ClientSecretCredential clientSecretCredential = new ClientSecretCredentialBuilder()
                                                        .clientId(azureStorageSyncConfig.getAzureAppClientId())
                                                        .clientSecret(azureStorageSyncConfig.getAzureAppClientSecret())
                                                        .tenantId(ceAzureConnectorDTO.getTenantId())
                                                        .build();

    AzureProfile profile = new AzureProfile(AzureEnvironment.AZURE);
    AzureResourceManager azure = AzureResourceManager.authenticate(clientSecretCredential, profile)
                                     .withSubscription(ceAzureConnectorDTO.getSubscriptionId());
    TagsResourceInner tagsResourceInner =
        azure.genericResources().manager().serviceClient().getTagOperations().getAtScope(
            format("/subscriptions/%s/", ceAzureConnectorDTO.getSubscriptionId()));
    Map<String, String> azureTags = tagsResourceInner.properties().tags();
    System.out.println(azureTags);
    List<CloudProviderEntityTags> cloudProviderEntityTags = new ArrayList<>();
    if (isNotEmpty(azureTags)) {
      for (Map.Entry<String, String> entry : azureTags.entrySet()) {
        cloudProviderEntityTags.add(
            CloudProviderEntityTags.builder().key(entry.getKey()).value(entry.getValue()).build());
      }
    }
    insertInBQ(
        tableName, "AZURE", ceAzureConnectorDTO.getSubscriptionId(), "subscription", "", cloudProviderEntityTags);
  }
}
