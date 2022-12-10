/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.aws;

import static io.harness.delegate.beans.connector.awsconnector.AwsCredentialType.INHERIT_FROM_DELEGATE;
import static io.harness.delegate.beans.connector.awsconnector.AwsCredentialType.IRSA;
import static io.harness.delegate.beans.connector.awsconnector.AwsCredentialType.MANUAL_CREDENTIALS;
import static io.harness.delegate.beans.connector.scm.awscodecommit.AwsCodeCommitHttpsAuthType.ACCESS_KEY_AND_SECRET_KEY;
import static io.harness.utils.FieldWithPlainTextOrSecretValueHelper.getSecretAsStringFromPlainTextOrSecretRef;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.AwsAccessKeyCredential;
import io.harness.aws.AwsConfig;
import io.harness.aws.CrossAccountAccess;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialType;
import io.harness.delegate.beans.connector.awsconnector.AwsManualConfigSpecDTO;
import io.harness.delegate.beans.connector.awsconnector.CrossAccountAccessDTO;
import io.harness.delegate.beans.connector.scm.awscodecommit.AwsCodeCommitAuthType;
import io.harness.delegate.beans.connector.scm.awscodecommit.AwsCodeCommitAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.awscodecommit.AwsCodeCommitHttpsCredentialsDTO;
import io.harness.delegate.beans.connector.scm.awscodecommit.AwsCodeCommitSecretKeyAccessKeyDTO;
import io.harness.encryption.SecretRefData;
import io.harness.exception.InvalidArgumentsException;
import io.harness.govern.Switch;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.SecretDecryptionService;

import software.wings.beans.AwsCrossAccountAttributes;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;

@OwnedBy(HarnessTeam.DX)
@Singleton
public class AwsNgConfigMapper {
  @Inject private SecretDecryptionService secretDecryptionService;

  private CrossAccountAccess mapCrossAccountAccess(CrossAccountAccessDTO crossAccountAccess) {
    if (crossAccountAccess == null) {
      return null;
    }
    return CrossAccountAccess.builder()
        .crossAccountRoleArn(crossAccountAccess.getCrossAccountRoleArn())
        .externalId(crossAccountAccess.getExternalId())
        .build();
  }

  public AwsInternalConfig createAwsInternalConfig(AwsConnectorDTO awsConnectorDTO) {
    AwsInternalConfig awsInternalConfig = AwsInternalConfig.builder().build();
    if (awsConnectorDTO == null) {
      throw new InvalidArgumentsException("Aws Connector DTO cannot be null");
    }

    AwsCredentialDTO credential = awsConnectorDTO.getCredential();
    if (MANUAL_CREDENTIALS == credential.getAwsCredentialType()) {
      AwsManualConfigSpecDTO awsManualConfigSpecDTO = (AwsManualConfigSpecDTO) credential.getConfig();

      String accessKey = "";

      if (awsManualConfigSpecDTO != null) {
        accessKey = getSecretAsStringFromPlainTextOrSecretRef(
            awsManualConfigSpecDTO.getAccessKey(), awsManualConfigSpecDTO.getAccessKeyRef());
      }

      if (EmptyPredicate.isEmpty(accessKey)) {
        throw new InvalidArgumentsException(Pair.of("accessKey", "Missing or empty"));
      }

      char[] secretKey = null;

      if (awsManualConfigSpecDTO != null && awsManualConfigSpecDTO.getSecretKeyRef() != null) {
        secretKey = awsManualConfigSpecDTO.getSecretKeyRef().getDecryptedValue();
      }

      awsInternalConfig = AwsInternalConfig.builder().accessKey(accessKey.toCharArray()).secretKey(secretKey).build();

    } else if (INHERIT_FROM_DELEGATE == credential.getAwsCredentialType()) {
      awsInternalConfig.setUseEc2IamCredentials(true);
    } else if (IRSA == credential.getAwsCredentialType()) {
      awsInternalConfig.setUseIRSA(true);
    }

    CrossAccountAccessDTO crossAccountAccess = credential.getCrossAccountAccess();
    if (crossAccountAccess != null) {
      awsInternalConfig.setAssumeCrossAccountRole(true);
      awsInternalConfig.setCrossAccountAttributes(AwsCrossAccountAttributes.builder()
                                                      .crossAccountRoleArn(crossAccountAccess.getCrossAccountRoleArn())
                                                      .externalId(crossAccountAccess.getExternalId())
                                                      .build());
    }
    return awsInternalConfig;
  }

  public AwsConfig mapAwsConfigWithDecryption(
      AwsCredentialDTO credential, AwsCredentialType awsCredentialType, List<EncryptedDataDetail> encryptionDetails) {
    AwsConfig awsConfig = null;
    switch (awsCredentialType) {
      case INHERIT_FROM_DELEGATE:
        awsConfig = AwsConfig.builder()
                        .isEc2IamCredentials(true)
                        .crossAccountAccess(mapCrossAccountAccess(credential.getCrossAccountAccess()))
                        .build();
        break;
      case MANUAL_CREDENTIALS:
        final AwsManualConfigSpecDTO config = (AwsManualConfigSpecDTO) credential.getConfig();
        secretDecryptionService.decrypt(config, encryptionDetails);
        final SecretRefData secretKeyRef = config.getSecretKeyRef();
        awsConfig = AwsConfig.builder()
                        .crossAccountAccess(mapCrossAccountAccess(credential.getCrossAccountAccess()))
                        .awsAccessKeyCredential(AwsAccessKeyCredential.builder()
                                                    .accessKey(getSecretAsStringFromPlainTextOrSecretRef(
                                                        config.getAccessKey(), config.getAccessKeyRef()))
                                                    .secretKey(getDecryptedValueWithNullCheck(secretKeyRef))
                                                    .build())
                        .build();
        break;
      case IRSA:
        awsConfig = AwsConfig.builder()
                        .isIRSA(true)
                        .crossAccountAccess(mapCrossAccountAccess(credential.getCrossAccountAccess()))
                        .build();
        break;
      default:
        Switch.unhandled(awsCredentialType);
    }
    return awsConfig;
  }

  public AwsConfig mapAwsCodeCommit(
      AwsCodeCommitAuthenticationDTO authentication, List<EncryptedDataDetail> encryptionDetails) {
    AwsConfig awsConfig = null;

    if (authentication.getAuthType() == AwsCodeCommitAuthType.HTTPS) {
      final AwsCodeCommitHttpsCredentialsDTO credentials =
          (AwsCodeCommitHttpsCredentialsDTO) authentication.getCredentials();
      if (credentials.getType() == ACCESS_KEY_AND_SECRET_KEY) {
        AwsCodeCommitSecretKeyAccessKeyDTO secretKeyAccessKeyDTO =
            (AwsCodeCommitSecretKeyAccessKeyDTO) credentials.getHttpCredentialsSpec();

        secretDecryptionService.decrypt(secretKeyAccessKeyDTO, encryptionDetails);
        final SecretRefData secretKeyRef = secretKeyAccessKeyDTO.getSecretKeyRef();
        String accessKey = secretKeyAccessKeyDTO.getAccessKey();
        SecretRefData accessKeyRef = secretKeyAccessKeyDTO.getAccessKeyRef();
        awsConfig = AwsConfig.builder()
                        .awsAccessKeyCredential(
                            AwsAccessKeyCredential.builder()
                                .accessKey(getSecretAsStringFromPlainTextOrSecretRef(accessKey, accessKeyRef))
                                .secretKey(getDecryptedValueWithNullCheck(secretKeyRef))
                                .build())
                        .build();
      }
    }
    return awsConfig;
  }

  @VisibleForTesting
  char[] getDecryptedValueWithNullCheck(SecretRefData passwordRef) {
    if (passwordRef != null) {
      return passwordRef.getDecryptedValue();
    }
    return null;
  }
}
