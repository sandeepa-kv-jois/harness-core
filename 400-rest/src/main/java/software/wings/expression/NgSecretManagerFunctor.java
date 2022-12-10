/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.expression;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.exception.WingsException.USER;
import static io.harness.metrics.impl.DelegateMetricsServiceImpl.SECRETS_CACHE_HITS;
import static io.harness.metrics.impl.DelegateMetricsServiceImpl.SECRETS_CACHE_INSERTS;
import static io.harness.metrics.impl.DelegateMetricsServiceImpl.SECRETS_CACHE_LOOKUPS;
import static io.harness.reflection.ReflectionUtils.getFieldByName;
import static io.harness.security.SimpleEncryption.CHARSET;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.DecryptableEntity;
import io.harness.beans.IdentifierRef;
import io.harness.beans.SecretManagerConfig;
import io.harness.data.encoding.EncodingUtils;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.SecretDetail;
import io.harness.delegate.beans.ci.pod.SecretVariableDTO;
import io.harness.encryption.SecretRefData;
import io.harness.exception.FunctorException;
import io.harness.exception.InvalidRequestException;
import io.harness.expression.functors.ExpressionFunctor;
import io.harness.metrics.intfc.DelegateMetricsService;
import io.harness.ng.core.BaseNGAccess;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.SimpleEncryption;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.EncryptionConfig;
import io.harness.security.encryption.EncryptionType;
import io.harness.utils.IdentifierRefHelper;

import software.wings.service.intfc.security.SecretManager;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;
import javax.cache.Cache;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDP)
@Value
@Builder
@Slf4j
@TargetModule(HarnessModule._950_NG_CORE)
public class NgSecretManagerFunctor implements ExpressionFunctor, NgSecretManagerFunctorInterface {
  int expressionFunctorToken;
  String accountId;
  String orgId;
  String projectId;
  SecretManager secretManager;
  Cache<String, EncryptedDataDetails> secretsCache;
  SecretManagerClientService ngSecretService;
  SecretManagerMode mode;
  private final ExecutorService expressionEvaluatorExecutor;
  private final boolean evaluateSync;

  @Builder.Default Map<String, String> evaluatedSecrets = new ConcurrentHashMap<>();
  @Builder.Default Map<String, String> evaluatedDelegateSecrets = new ConcurrentHashMap<>();
  @Builder.Default Map<String, EncryptionConfig> encryptionConfigs = new ConcurrentHashMap<>();
  @Builder.Default Map<String, SecretDetail> secretDetails = new ConcurrentHashMap<>();

  DelegateMetricsService delegateMetricsService;

  @Override
  public Object obtain(String secretIdentifier, int token) {
    if (token != expressionFunctorToken) {
      throw new FunctorException("Inappropriate usage of internal functor");
    }
    try {
      if (!evaluateSync) {
        if (expressionEvaluatorExecutor != null) {
          // Offload expression evaluation of secrets to another threadpool.
          return expressionEvaluatorExecutor.submit(() -> obtainInternal(secretIdentifier));
        }
      }
      log.warn("Expression evaluation is being processed synchronously");
      return obtainInternal(secretIdentifier);
    } catch (Exception ex) {
      throw new FunctorException("Error occurred while evaluating the secret [" + secretIdentifier + "]", ex);
    }
  }

  private Object returnValue(String secretIdentifier, Object value) {
    if (mode == SecretManagerMode.DRY_RUN) {
      return "${ngSecretManager.obtain(\"" + secretIdentifier + "\", " + expressionFunctorToken + ")}";
    } else if (mode == SecretManagerMode.CHECK_FOR_SECRETS) {
      return format("<<<%s>>>", secretIdentifier);
    }
    return value;
  }

  private Object obtainInternal(String secretIdentifier) {
    if (evaluatedSecrets.containsKey(secretIdentifier)) {
      return returnValue(secretIdentifier, evaluatedSecrets.get(secretIdentifier));
    }
    if (evaluatedDelegateSecrets.containsKey(secretIdentifier)) {
      return returnValue(secretIdentifier, evaluatedDelegateSecrets.get(secretIdentifier));
    }

    IdentifierRef secretIdentifierRef =
        IdentifierRefHelper.getIdentifierRef(secretIdentifier, accountId, orgId, projectId);
    SecretVariableDTO secretVariableDTO = SecretVariableDTO.builder()
                                              .name(secretIdentifierRef.getIdentifier())
                                              .secret(SecretRefData.builder()
                                                          .identifier(secretIdentifierRef.getIdentifier())
                                                          .scope(secretIdentifierRef.getScope())
                                                          .build())
                                              .type(SecretVariableDTO.Type.TEXT)
                                              .build();

    int keyHash = SecretsCacheKey.builder()
                      .accountIdentifier(accountId)
                      .orgIdentifier(orgId)
                      .projectIdentifier(projectId)
                      .secretVariableDTO(secretVariableDTO)
                      .build()
                      .hashCode();

    List<EncryptedDataDetail> encryptedDataDetails = null;
    if (secretsCache != null) {
      delegateMetricsService.recordDelegateMetricsPerAccount(accountId, SECRETS_CACHE_LOOKUPS);
      EncryptedDataDetails cachedValue = secretsCache.get(String.valueOf(keyHash));
      if (cachedValue != null) {
        // Cache hit.
        delegateMetricsService.recordDelegateMetricsPerAccount(accountId, SECRETS_CACHE_HITS);
        encryptedDataDetails = cachedValue.getEncryptedDataDetailList();
      }
    }

    if (isEmpty(encryptedDataDetails)) {
      // Cache miss.
      encryptedDataDetails = ngSecretService.getEncryptionDetails(
          BaseNGAccess.builder().accountIdentifier(accountId).orgIdentifier(orgId).projectIdentifier(projectId).build(),
          secretVariableDTO);

      if (EmptyPredicate.isEmpty(encryptedDataDetails)) {
        throw new InvalidRequestException("No secret found with identifier + [" + secretIdentifier + "]", USER);
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
        secretsCache.put(String.valueOf(keyHash), objectToCache);
        delegateMetricsService.recordDelegateMetricsPerAccount(accountId, SECRETS_CACHE_INSERTS);
      }
    }

    List<EncryptedDataDetail> localEncryptedDetails =
        encryptedDataDetails.stream()
            .filter(encryptedDataDetail
                -> encryptedDataDetail.getEncryptedData().getEncryptionType() == EncryptionType.LOCAL)
            .collect(Collectors.toList());

    if (isNotEmpty(localEncryptedDetails)) {
      // ToDo Vikas said that we can have decrypt here for now. Later on it will be moved to proper service.
      decryptLocal(secretVariableDTO, localEncryptedDetails);
      final String secretValue = new String(secretVariableDTO.getSecret().getDecryptedValue());
      evaluatedSecrets.put(secretIdentifier, secretValue);
      return returnValue(secretIdentifier, secretValue);
    }

    List<EncryptedDataDetail> nonLocalEncryptedDetails =
        encryptedDataDetails.stream()
            .filter(encryptedDataDetail
                -> encryptedDataDetail.getEncryptedData().getEncryptionType() != EncryptionType.LOCAL)
            .collect(Collectors.toList());

    if (nonLocalEncryptedDetails.size() != 1) {
      throw new InvalidRequestException(
          "More than one encrypted records associated with + [" + secretIdentifier + "]", USER);
    }

    EncryptedDataDetail encryptedDataDetail = nonLocalEncryptedDetails.get(0);
    String encryptionConfigUuid = generateUuid();
    SecretManagerConfig encryptionConfig = (SecretManagerConfig) encryptedDataDetail.getEncryptionConfig();
    encryptionConfig.setUuid(encryptionConfigUuid);

    encryptionConfigs.put(encryptionConfigUuid, encryptionConfig);

    SecretDetail secretDetail = SecretDetail.builder()
                                    .configUuid(encryptionConfigUuid)
                                    .encryptedRecord(encryptedDataDetail.getEncryptedData())
                                    .build();
    String secretDetailsUuid = generateUuid();
    secretDetails.put(secretDetailsUuid, secretDetail);

    evaluatedDelegateSecrets.put(
        secretIdentifier, "${secretDelegate.obtain(\"" + secretDetailsUuid + "\", " + expressionFunctorToken + ")}");

    return returnValue(secretIdentifier, evaluatedDelegateSecrets.get(secretIdentifier));
  }

  private void decryptLocal(DecryptableEntity decryptableEntity, List<EncryptedDataDetail> encryptedDataDetails) {
    if (isEmpty(encryptedDataDetails)) {
      return;
    }

    encryptedDataDetails.stream()
        .filter(
            encryptedDataDetail -> encryptedDataDetail.getEncryptedData().getEncryptionType() == EncryptionType.LOCAL)
        .forEach(encryptedDataDetail -> {
          SimpleEncryption encryption = new SimpleEncryption(encryptedDataDetail.getEncryptedData().getEncryptionKey());
          char[] decryptChars = encryption.decryptChars(encryptedDataDetail.getEncryptedData().getEncryptedValue());
          if (encryptedDataDetail.getEncryptedData().isBase64Encoded()) {
            byte[] decodedBytes = EncodingUtils.decodeBase64(decryptChars);
            decryptChars = CHARSET.decode(ByteBuffer.wrap(decodedBytes)).array();
          }

          Field f = getFieldByName(decryptableEntity.getClass(), encryptedDataDetail.getFieldName());

          if (f != null) {
            f.setAccessible(true);
            try {
              SecretRefData secretRefData = (SecretRefData) f.get(decryptableEntity);
              secretRefData.setDecryptedValue(decryptChars);
            } catch (IllegalAccessException e) {
              throw new InvalidRequestException("Decryption failed for  " + encryptedDataDetail.toString(), e);
            }
          }
        });
  }
}

@Builder
@EqualsAndHashCode
class SecretsCacheKey {
  String accountIdentifier;
  String orgIdentifier;
  String projectIdentifier;
  SecretVariableDTO secretVariableDTO;
}
