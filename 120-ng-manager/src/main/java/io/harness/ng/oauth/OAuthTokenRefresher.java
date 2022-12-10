/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.oauth;

import static io.harness.authorization.AuthorizationServiceHeader.NG_MANAGER;
import static io.harness.connector.entities.embedded.gitlabconnector.GitlabConnector.GitlabConnectorKeys;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.mongo.iterator.MongoPersistenceIterator.SchedulingType.REGULAR;

import static java.time.Duration.ofMinutes;
import static java.time.Duration.ofSeconds;

import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.entities.Connector.ConnectorKeys;
import io.harness.connector.entities.embedded.gitlabconnector.GitlabApiAccess;
import io.harness.connector.entities.embedded.gitlabconnector.GitlabConnector;
import io.harness.connector.helper.DecryptionHelper;
import io.harness.connector.mappers.ConnectorMapper;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabConnectorDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabHttpCredentialsDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabOauthDTO;
import io.harness.encryption.Scope;
import io.harness.encryption.SecretRefData;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitsync.interceptor.GitSyncBranchContext;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.manage.GlobalContextManager;
import io.harness.mongo.iterator.MongoPersistenceIterator;
import io.harness.mongo.iterator.MongoPersistenceIterator.Handler;
import io.harness.mongo.iterator.filter.SpringFilterExpander;
import io.harness.mongo.iterator.provider.SpringPersistenceProvider;
import io.harness.ng.NextGenConfiguration;
import io.harness.ng.core.BaseNGAccess;
import io.harness.ng.core.NGAccess;
import io.harness.ng.core.api.SecretCrudService;
import io.harness.ng.core.dto.secrets.SecretDTOV2;
import io.harness.ng.core.dto.secrets.SecretResponseWrapper;
import io.harness.ng.core.dto.secrets.SecretTextSpecDTO;
import io.harness.ng.core.models.Secret;
import io.harness.product.ci.scm.proto.RefreshTokenResponse;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.dto.Principal;
import io.harness.security.dto.ServicePrincipal;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.service.ScmClient;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;

@Singleton
@Slf4j
public class OAuthTokenRefresher implements Handler<GitlabConnector> {
  PersistenceIteratorFactory persistenceIteratorFactory;
  @Inject private ConnectorMapper connectorMapper;
  @Inject private SecretManagerClientService ngSecretService;
  @Inject private ScmClient scmClient;
  private final MongoTemplate mongoTemplate;
  @Inject DecryptionHelper decryptionHelper;
  @Inject private SecretCrudService ngSecretCrudService;
  @Inject NextGenConfiguration configuration;

  @Inject
  public OAuthTokenRefresher(PersistenceIteratorFactory persistenceIteratorFactory, MongoTemplate mongoTemplate) {
    this.persistenceIteratorFactory = persistenceIteratorFactory;
    this.mongoTemplate = mongoTemplate;
  }

  private GitlabOauthDTO getGitlabOauthDecrypted(GitlabConnector entity) {
    GitlabApiAccess apiAccess = entity.getGitlabApiAccess();

    ConnectorResponseDTO connectorDTO = connectorMapper.writeDTO(entity);

    GitlabConnectorDTO gitlabConnectorDTO = (GitlabConnectorDTO) connectorDTO.getConnector().getConnectorConfig();

    GitlabHttpCredentialsDTO gitlabHttpCredentialsDTO =
        (GitlabHttpCredentialsDTO) gitlabConnectorDTO.getAuthentication().getCredentials();
    GitlabOauthDTO gitlabOauthDTO = (GitlabOauthDTO) gitlabHttpCredentialsDTO.getHttpCredentialsSpec();
    List<EncryptedDataDetail> encryptionDetails = getEncryptionDetails(
        gitlabOauthDTO, entity.getAccountIdentifier(), entity.getOrgIdentifier(), entity.getProjectIdentifier());

    return (GitlabOauthDTO) decryptionHelper.decrypt(gitlabOauthDTO, encryptionDetails);
  }

  private SecretDTOV2 getSecretSecretValue(GitlabConnector entity, SecretRefData token) {
    String orgIdentifier = null;
    String projectIdentifier = null;

    if (token.getScope() != Scope.ACCOUNT) {
      orgIdentifier = entity.getOrgIdentifier();
      projectIdentifier = entity.getProjectIdentifier();
    }

    SecretResponseWrapper tokenWrapper =
        ngSecretCrudService.get(entity.getAccountIdentifier(), orgIdentifier, projectIdentifier, token.getIdentifier())
            .orElse(null);

    if (tokenWrapper == null) {
      log.info("[OAuth refresh]" + entity.toString() + "Error in secret");
      return null;
    }

    return tokenWrapper.getSecret();
  }

  private void updateSecretSecretValue(GitlabConnector entity, SecretDTOV2 secretDTOV2, String newSecret) {
    SecretTextSpecDTO secretSpecDTO = (SecretTextSpecDTO) secretDTOV2.getSpec();
    secretSpecDTO.setValue(newSecret);
    secretDTOV2.setSpec(secretSpecDTO);

    Secret secret = Secret.fromDTO(secretDTOV2);
    ngSecretCrudService.update(entity.getAccountIdentifier(), secret.getOrgIdentifier(), secret.getProjectIdentifier(),
        secretDTOV2.getIdentifier(), secretDTOV2);
  }

  private void updateContext() {
    Principal principal = SecurityContextBuilder.getPrincipal();
    if (principal == null) {
      principal = new ServicePrincipal(NG_MANAGER.getServiceId());
      SecurityContextBuilder.setContext(principal);
    }
    final GitEntityInfo emptyInfo = GitEntityInfo.builder().build();
    try (GlobalContextManager.GlobalContextGuard guard = GlobalContextManager.ensureGlobalContextGuard()) {
      GlobalContextManager.upsertGlobalContextRecord(GitSyncBranchContext.builder().gitBranchInfo(emptyInfo).build());
    }
  }

  @Override
  public void handle(GitlabConnector entity) {
    log.info("[OAuth refresh] Working on: {}", entity.getAccountIdentifier() + " , " + entity.getIdentifier());

    try {
      updateContext();
      GitlabOauthDTO gitlabOauthDTO = getGitlabOauthDecrypted(entity);

      SecretDTOV2 tokenDTO = getSecretSecretValue(entity, gitlabOauthDTO.getTokenRef());
      SecretDTOV2 refreshTokenDTO = getSecretSecretValue(entity, gitlabOauthDTO.getRefreshTokenRef());

      if (tokenDTO == null || refreshTokenDTO == null) {
        log.error("[OAuth refresh] Error getting refresh/access token for connector: ", entity.getName());
        return;
      }

      RefreshTokenResponse refreshTokenResponse = null;
      String clientId = configuration.getGitlabConfig().getClientId();
      String clientSecret = configuration.getGitlabConfig().getClientSecret();

      String clientIdShort = clientId.substring(0, Math.min(clientId.length(), 3));
      String clientSecretShort = clientSecret.substring(0, Math.min(clientSecret.length(), 3));

      try {
        refreshTokenResponse = scmClient.refreshToken(null, clientId, clientSecret, "https://gitlab.com/oauth/token",
            String.valueOf(gitlabOauthDTO.getRefreshTokenRef().getDecryptedValue()));
      } catch (Exception e) {
        log.error(
            "[OAuth refresh] Error from SCM for refreshing token for connector:{}, clientID short:{}, client Secret short:{}, Account:{}, Error:{}",
            entity.getIdentifier(), clientIdShort, clientSecretShort, entity.getAccountIdentifier(), e.getMessage());
        return;
      }

      log.info("[OAuth refresh]:" + entity.getName() + "-Got new access & refresh token");

      updateSecretSecretValue(entity, tokenDTO, refreshTokenResponse.getAccessToken());
      updateSecretSecretValue(entity, refreshTokenDTO, refreshTokenResponse.getRefreshToken());

    } catch (Exception e) {
      log.error("[OAuth refresh] Error in refreshing token for connector:" + entity.getIdentifier(), e);
    }
  }

  public void registerIterators(int threadPoolSize) {
    log.info("[OAuth refresh] Register Enabled:{}, Frequency:{}, clientID:{}, clientSecret{}",
        configuration.isOauthRefreshEnabled(), configuration.getOauthRefreshFrequency(),
        configuration.getGitlabConfig().getClientId(), configuration.getGitlabConfig().getClientSecret());

    if (configuration.isOauthRefreshEnabled()) {
      SpringFilterExpander springFilterExpander = getFilterQuery();

      persistenceIteratorFactory.createPumpIteratorWithDedicatedThreadPool(
          PersistenceIteratorFactory.PumpExecutorOptions.builder()
              .name(this.getClass().getName())
              .poolSize(threadPoolSize)
              .interval(ofSeconds(10))
              .build(),
          GitlabConnector.class,
          MongoPersistenceIterator.<GitlabConnector, SpringFilterExpander>builder()
              .clazz(GitlabConnector.class)
              .fieldName(GitlabConnectorKeys.nextTokenRenewIteration)
              .targetInterval(ofMinutes(configuration.getOauthRefreshFrequency()))
              .acceptableExecutionTime(ofMinutes(1))
              .acceptableNoAlertDelay(ofMinutes(1))
              .filterExpander(springFilterExpander)
              .handler(this)
              .schedulingType(REGULAR)
              .persistenceProvider(new SpringPersistenceProvider<>(mongoTemplate))
              .redistribute(true));
    }
  }

  private SpringFilterExpander getFilterQuery() {
    return query -> {
      Criteria criteria =
          Criteria.where(ConnectorKeys.type).is(ConnectorType.GITLAB).and("authenticationDetails.type").is("OAUTH");

      query.addCriteria(criteria);
    };
  }

  List<EncryptedDataDetail> getEncryptionDetails(
      GitlabOauthDTO gitlabOauthDTO, String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    NGAccess ngAccess = BaseNGAccess.builder()
                            .accountIdentifier(accountIdentifier)
                            .orgIdentifier(orgIdentifier)
                            .projectIdentifier(projectIdentifier)
                            .build();

    List<EncryptedDataDetail> encryptedDataDetails = new ArrayList<>();

    List<EncryptedDataDetail> authenticationEncryptedDataDetails =
        ngSecretService.getEncryptionDetails(ngAccess, gitlabOauthDTO);
    if (isNotEmpty(authenticationEncryptedDataDetails)) {
      encryptedDataDetails.addAll(authenticationEncryptedDataDetails);
    }

    return encryptedDataDetails;
  }
}
