/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.expression;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.exception.WingsException.USER;
import static io.harness.metrics.impl.DelegateMetricsServiceImpl.SECRETS_CACHE_HITS;
import static io.harness.metrics.impl.DelegateMetricsServiceImpl.SECRETS_CACHE_INSERTS;
import static io.harness.metrics.impl.DelegateMetricsServiceImpl.SECRETS_CACHE_LOOKUPS;

import static software.wings.beans.ServiceVariableType.ENCRYPTED_TEXT;
import static software.wings.expression.SecretManagerFunctorInterface.obtainConfigFileExpression;
import static software.wings.expression.SecretManagerFunctorInterface.obtainExpression;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.EncryptedData;
import io.harness.beans.FeatureName;
import io.harness.data.encoding.EncodingUtils;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.SecretDetail;
import io.harness.exception.FunctorException;
import io.harness.exception.InvalidRequestException;
import io.harness.expression.functors.ExpressionFunctor;
import io.harness.ff.FeatureFlagService;
import io.harness.metrics.intfc.DelegateMetricsService;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.EncryptedRecordData;
import io.harness.security.encryption.EncryptionConfig;
import io.harness.security.encryption.EncryptionType;

import software.wings.beans.ServiceVariable;
import software.wings.service.intfc.security.ManagerDecryptionService;
import software.wings.service.intfc.security.SecretManager;

import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;
import javax.cache.Cache;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

@Value
@Builder
@Slf4j
@OwnedBy(CDC)
@TargetModule(HarnessModule._940_SECRET_MANAGER_CLIENT)
public class SecretManagerFunctor implements ExpressionFunctor, SecretManagerFunctorInterface {
  private SecretManagerMode mode;
  private FeatureFlagService featureFlagService;
  private ManagerDecryptionService managerDecryptionService;
  private SecretManager secretManager;
  private final Cache<String, EncryptedDataDetails> secretsCache;
  private String accountId;
  private String appId;
  private String envId;
  private String workflowExecutionId;
  private int expressionFunctorToken;
  private final ExecutorService expressionEvaluatorExecutor;
  private final boolean evaluateSync;

  @Default private Map<String, String> evaluatedSecrets = new ConcurrentHashMap<>();
  @Default private Map<String, String> evaluatedDelegateSecrets = new ConcurrentHashMap<>();
  @Default private Map<String, EncryptionConfig> encryptionConfigs = new HashMap<>();
  @Default private Map<String, SecretDetail> secretDetails = new ConcurrentHashMap<>();

  DelegateMetricsService delegateMetricsService;

  @Override
  public Object obtain(String secretName, int token) {
    if (token != expressionFunctorToken) {
      throw new FunctorException("Inappropriate usage of internal functor");
    }
    try {
      if (!evaluateSync) {
        if (expressionEvaluatorExecutor != null) {
          // Offload expression evaluation of secrets to another threadpool.
          return expressionEvaluatorExecutor.submit(() -> obtainInternal(secretName));
        }
      }
      log.debug("Expression evaluation is being processed synchronously");
      return obtainInternal(secretName);
    } catch (Exception ex) {
      throw new FunctorException("Error occurred while evaluating the secret [" + secretName + "]", ex);
    }
  }

  @Override
  public Object obtainConfigFileAsString(String path, String encryptedFileId, int token) {
    if (token != expressionFunctorToken) {
      throw new FunctorException("Inappropriate usage of internal functor");
    }

    String key = format("//text:/%s", path);
    if (evaluatedSecrets.containsKey(key)) {
      return returnConfigFileValue("obtainConfigFileAsString", path, encryptedFileId, evaluatedSecrets.get(key));
    }

    byte[] fileContent = secretManager.getFileContents(accountId, encryptedFileId);
    String text = new String(fileContent, Charset.forName("UTF-8"));
    evaluatedSecrets.put(key, text);
    return returnConfigFileValue("obtainConfigFileAsString", path, encryptedFileId, text);
  }

  @Override
  public Object obtainConfigFileAsBase64(String path, String encryptedFileId, int token) {
    if (token != expressionFunctorToken) {
      throw new FunctorException("Inappropriate usage of internal functor");
    }

    String key = format("//base64:/%s", path);
    if (evaluatedSecrets.containsKey(key)) {
      return returnConfigFileValue("obtainConfigFileAsBase64", path, encryptedFileId, evaluatedSecrets.get(key));
    }

    byte[] fileContent = secretManager.getFileContents(accountId, encryptedFileId);
    String encodeBase64 = EncodingUtils.encodeBase64(fileContent);
    evaluatedSecrets.put(key, encodeBase64);
    return returnConfigFileValue("obtainConfigFileAsBase64", path, encryptedFileId, encodeBase64);
  }

  private Object returnConfigFileValue(String method, String path, String encryptedFileId, Object value) {
    if (mode == SecretManagerMode.DRY_RUN) {
      return obtainConfigFileExpression(method, path, encryptedFileId, expressionFunctorToken);
    } else if (mode == SecretManagerMode.CHECK_FOR_SECRETS) {
      return format(SecretManagerPreviewFunctor.SECRET_NAME_FORMATTER, path);
    }
    return value;
  }

  private Object returnSecretValue(String secretName, Object value) {
    if (mode == SecretManagerMode.DRY_RUN) {
      return obtainExpression(secretName, expressionFunctorToken);
    } else if (mode == SecretManagerMode.CHECK_FOR_SECRETS) {
      return format(SecretManagerPreviewFunctor.SECRET_NAME_FORMATTER, secretName);
    }
    return value;
  }

  private Object obtainInternal(String secretName) {
    boolean updateSecretUsage = !SecretManagerMode.DRY_RUN.equals(mode);
    if (evaluatedSecrets.containsKey(secretName)) {
      return returnSecretValue(secretName, evaluatedSecrets.get(secretName));
    }
    if (evaluatedDelegateSecrets.containsKey(secretName)) {
      return returnSecretValue(secretName, evaluatedDelegateSecrets.get(secretName));
    }

    EncryptedData encryptedData = secretManager.getSecretMappedToAppByName(accountId, appId, envId, secretName);
    if (encryptedData == null) {
      encryptedData = secretManager.getSecretByName(accountId, secretName);

      // check if secret exists in account to throw appropriate error message
      if (encryptedData != null) {
        throw new InvalidRequestException(
            "Secret with name [" + secretName + "] is not scoped to this application or environment", USER);
      }
      throw new InvalidRequestException("No secret found with name + [" + secretName + "]", USER);
    }
    ServiceVariable serviceVariable = ServiceVariable.builder()
                                          .accountId(accountId)
                                          .type(ENCRYPTED_TEXT)
                                          .encryptedValue(encryptedData.getUuid())
                                          .secretTextName(secretName)
                                          .build();

    List<EncryptedDataDetail> encryptedDataDetails = null;

    if (secretsCache != null) {
      delegateMetricsService.recordDelegateMetricsPerAccount(accountId, SECRETS_CACHE_LOOKUPS);
      EncryptedDataDetails cachedValue = secretsCache.get(encryptedData.getUuid());
      if (cachedValue != null) {
        // Cache hit.
        delegateMetricsService.recordDelegateMetricsPerAccount(accountId, SECRETS_CACHE_HITS);
        encryptedDataDetails = cachedValue.getEncryptedDataDetailList();
      }
    }

    if (isEmpty(encryptedDataDetails)) {
      // Cache miss.
      encryptedDataDetails =
          secretManager.getEncryptionDetails(serviceVariable, appId, workflowExecutionId, updateSecretUsage);

      if (EmptyPredicate.isEmpty(encryptedDataDetails)) {
        throw new InvalidRequestException("No secret found with identifier + [" + secretName + "]", USER);
      }

      // Skip caching secrets for HashiCorp vault.
      List<EncryptedDataDetail> encryptedDataDetailsToCache =
          encryptedDataDetails.stream()
              .filter(encryptedDataDetail
                  -> encryptedDataDetail.getEncryptedData().getEncryptionType() != EncryptionType.VAULT)
              .collect(Collectors.toList());
      if (isNotEmpty(encryptedDataDetailsToCache)) {
        EncryptedDataDetails objectToCache =
            EncryptedDataDetails.builder().encryptedDataDetailList(encryptedDataDetailsToCache).build();
        secretsCache.put(encryptedData.getUuid(), objectToCache);
        delegateMetricsService.recordDelegateMetricsPerAccount(accountId, SECRETS_CACHE_INSERTS);
      }
    }

    boolean enabled = featureFlagService.isEnabled(FeatureName.THREE_PHASE_SECRET_DECRYPTION, accountId);

    List<EncryptedDataDetail> localEncryptedDetails =
        encryptedDataDetails.stream()
            .filter(encryptedDataDetail
                -> !enabled || encryptedDataDetail.getEncryptedData().getEncryptionType() == EncryptionType.LOCAL)
            .collect(Collectors.toList());

    if (isNotEmpty(localEncryptedDetails)) {
      managerDecryptionService.decrypt(serviceVariable, localEncryptedDetails);
      String secretValue = new String(serviceVariable.getValue());
      evaluatedSecrets.put(secretName, secretValue);
      return returnSecretValue(secretName, secretValue);
    }

    List<EncryptedDataDetail> nonLocalEncryptedDetails =
        encryptedDataDetails.stream()
            .filter(encryptedDataDetail
                -> encryptedDataDetail.getEncryptedData().getEncryptionType() != EncryptionType.LOCAL)
            .collect(Collectors.toList());

    if (nonLocalEncryptedDetails.size() != 1) {
      throw new InvalidRequestException("More than one encrypted records associated with + [" + secretName + "]", USER);
    }

    EncryptedDataDetail encryptedDataDetail = nonLocalEncryptedDetails.get(0);

    String encryptionConfigUuid = encryptedDataDetail.getEncryptionConfig().getUuid();

    encryptionConfigs.put(encryptionConfigUuid, encryptedDataDetail.getEncryptionConfig());
    if (isEmpty(encryptionConfigUuid)) {
      log.warn("Got encryptionConfigUuid as null, name: {}, isGlobalKms {}, type: {}",
          encryptedDataDetail.getEncryptionConfig().getName(), encryptedDataDetail.getEncryptionConfig().isGlobalKms(),
          encryptedDataDetail.getEncryptionConfig().getType());
    }

    SecretDetail secretDetail = SecretDetail.builder()
                                    .configUuid(encryptionConfigUuid)
                                    .encryptedRecord(EncryptedRecordData.builder()
                                                         .uuid(encryptedData.getUuid())
                                                         .name(encryptedData.getName())
                                                         .path(encryptedData.getPath())
                                                         .parameters(encryptedData.getParameters())
                                                         .encryptionKey(encryptedData.getEncryptionKey())
                                                         .encryptedValue(encryptedData.getEncryptedValue())
                                                         .kmsId(encryptedData.getKmsId())
                                                         .encryptionType(encryptedData.getEncryptionType())
                                                         .backupEncryptedValue(encryptedData.getBackupEncryptedValue())
                                                         .backupEncryptionKey(encryptedData.getBackupEncryptionKey())
                                                         .backupKmsId(encryptedData.getBackupKmsId())
                                                         .backupEncryptionType(encryptedData.getBackupEncryptionType())
                                                         .base64Encoded(encryptedData.isBase64Encoded())
                                                         .build())
                                    .build();

    String secretDetailsUuid = generateUuid();

    secretDetails.put(secretDetailsUuid, secretDetail);
    evaluatedDelegateSecrets.put(
        secretName, "${secretDelegate.obtain(\"" + secretDetailsUuid + "\", " + expressionFunctorToken + ")}");
    return returnSecretValue(secretName, evaluatedDelegateSecrets.get(secretName));
  }
}
