/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.encryptors;

import static io.harness.rule.OwnerRule.PIYUSH;
import static io.harness.rule.OwnerRule.UTKARSH;
import static io.harness.rule.OwnerRule.VIKAS_M;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.concurrent.HTimeLimiter;
import io.harness.data.structure.UUIDGenerator;
import io.harness.encryptors.clients.AwsSecretsManagerEncryptor;
import io.harness.exception.SecretManagementDelegateException;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedRecord;
import io.harness.security.encryption.EncryptedRecordData;
import io.harness.security.encryption.EncryptionType;

import software.wings.beans.AwsSecretsManagerConfig;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.auth.STSAssumeRoleSessionCredentialsProvider;
import com.amazonaws.services.secretsmanager.AWSSecretsManager;
import com.amazonaws.services.secretsmanager.model.AWSSecretsManagerException;
import com.amazonaws.services.secretsmanager.model.CreateSecretRequest;
import com.amazonaws.services.secretsmanager.model.CreateSecretResult;
import com.amazonaws.services.secretsmanager.model.GetSecretValueRequest;
import com.amazonaws.services.secretsmanager.model.GetSecretValueResult;
import com.amazonaws.services.secretsmanager.model.ResourceNotFoundException;
import com.amazonaws.services.secretsmanager.model.Tag;
import com.amazonaws.services.secretsmanager.model.UpdateSecretRequest;
import com.amazonaws.services.secretsmanager.model.UpdateSecretResult;
import com.google.common.util.concurrent.UncheckedTimeoutException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class AwsSecretsManagerEncryptorTest extends CategoryTest {
  private AwsSecretsManagerEncryptor awsSecretsManagerEncryptor;
  private AwsSecretsManagerConfig awsSecretsManagerConfig;
  private AWSSecretsManager awsSecretsManager;

  @Before
  public void setup() {
    awsSecretsManagerEncryptor = spy(new AwsSecretsManagerEncryptor(HTimeLimiter.create()));
    awsSecretsManagerConfig = AwsSecretsManagerConfig.builder()
                                  .accountId(UUIDGenerator.generateUuid())
                                  .name(UUIDGenerator.generateUuid())
                                  .uuid(UUIDGenerator.generateUuid())
                                  .encryptionType(EncryptionType.AWS_SECRETS_MANAGER)
                                  .accessKey(UUIDGenerator.generateUuid())
                                  .secretKey(UUIDGenerator.generateUuid())
                                  .region("us-east-1")
                                  .secretNamePrefix(UUIDGenerator.generateUuid())
                                  .isDefault(false)
                                  .build();
    awsSecretsManager = mock(AWSSecretsManager.class);
    when(awsSecretsManagerEncryptor.getAwsSecretsManagerClient(awsSecretsManagerConfig)).thenReturn(awsSecretsManager);
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testCreateSecret() {
    String plainTextValue = UUIDGenerator.generateUuid();
    String secretName = UUIDGenerator.generateUuid();
    String fullSecretName = awsSecretsManagerConfig.getSecretNamePrefix() + "/" + secretName;
    // Create the secret with proper tags.
    CreateSecretRequest createSecretRequest = new CreateSecretRequest()
                                                  .withName(fullSecretName)
                                                  .withSecretString(plainTextValue)
                                                  .withTags(new Tag().withKey("createdBy").withValue("Harness"));
    CreateSecretResult createSecretResult =
        new CreateSecretResult().withName(fullSecretName).withARN(UUIDGenerator.generateUuid());
    when(awsSecretsManager.createSecret(createSecretRequest)).thenReturn(createSecretResult);
    GetSecretValueRequest getSecretRequest = new GetSecretValueRequest().withSecretId(fullSecretName);
    when(awsSecretsManager.getSecretValue(getSecretRequest))
        .thenThrow(new ResourceNotFoundException("Secret not found mock exception"));

    EncryptedRecord encryptedRecord = awsSecretsManagerEncryptor.createSecret(
        awsSecretsManagerConfig.getAccountId(), secretName, plainTextValue, awsSecretsManagerConfig);
    assertThat(encryptedRecord.getEncryptedValue()).isEqualTo(createSecretResult.getARN().toCharArray());
    assertThat(encryptedRecord.getEncryptionKey()).isEqualTo(fullSecretName);
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void testCreateSecret_throwsAwsSecretManagerException() {
    String plainTextValue = UUIDGenerator.generateUuid();
    String secretName = UUIDGenerator.generateUuid();
    String fullSecretName = awsSecretsManagerConfig.getSecretNamePrefix() + "/" + secretName;
    // Create the secret with proper tags.
    CreateSecretRequest createSecretRequest = new CreateSecretRequest()
                                                  .withName(fullSecretName)
                                                  .withSecretString(plainTextValue)
                                                  .withTags(new Tag().withKey("createdBy").withValue("Harness"));
    when(awsSecretsManager.createSecret(createSecretRequest))
        .thenThrow(new AWSSecretsManagerException("Mock AWS exception"));
    GetSecretValueRequest getSecretRequest = new GetSecretValueRequest().withSecretId(fullSecretName);
    when(awsSecretsManager.getSecretValue(getSecretRequest))
        .thenThrow(new ResourceNotFoundException("Secret not found mock exception"));

    try {
      awsSecretsManagerEncryptor.createSecret(
          awsSecretsManagerConfig.getAccountId(), secretName, plainTextValue, awsSecretsManagerConfig);
    } catch (AWSSecretsManagerException ex) {
      assertThat(ex.getErrorMessage()).isEqualTo("Mock AWS exception");
    }
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void testCreateSecret_throwsTimeOutException() {
    String plainTextValue = UUIDGenerator.generateUuid();
    String secretName = UUIDGenerator.generateUuid();
    String fullSecretName = awsSecretsManagerConfig.getSecretNamePrefix() + "/" + secretName;
    // Create the secret with proper tags.
    CreateSecretRequest createSecretRequest = new CreateSecretRequest()
                                                  .withName(fullSecretName)
                                                  .withSecretString(plainTextValue)
                                                  .withTags(new Tag().withKey("createdBy").withValue("Harness"));
    when(awsSecretsManager.createSecret(createSecretRequest)).thenThrow(new UncheckedTimeoutException("Time out"));
    GetSecretValueRequest getSecretRequest = new GetSecretValueRequest().withSecretId(fullSecretName);
    when(awsSecretsManager.getSecretValue(getSecretRequest))
        .thenThrow(new ResourceNotFoundException("Secret not found mock exception"));

    try {
      awsSecretsManagerEncryptor.createSecret(
          awsSecretsManagerConfig.getAccountId(), secretName, plainTextValue, awsSecretsManagerConfig);
    } catch (SecretManagementDelegateException ex) {
      assertThat(ex.getMessage())
          .isEqualTo("After 3 tries, delegate(s) is not able to establish connection to AWS services.");
    }
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void testCreateSecret_throwsException() {
    String plainTextValue = UUIDGenerator.generateUuid();
    String secretName = UUIDGenerator.generateUuid();
    String fullSecretName = awsSecretsManagerConfig.getSecretNamePrefix() + "/" + secretName;
    // Create the secret with proper tags.
    CreateSecretRequest createSecretRequest = new CreateSecretRequest()
                                                  .withName(fullSecretName)
                                                  .withSecretString(plainTextValue)
                                                  .withTags(new Tag().withKey("createdBy").withValue("Harness"));
    when(awsSecretsManager.createSecret(createSecretRequest))
        .thenThrow(new RuntimeException("Random runtime exception"));
    GetSecretValueRequest getSecretRequest = new GetSecretValueRequest().withSecretId(fullSecretName);
    when(awsSecretsManager.getSecretValue(getSecretRequest))
        .thenThrow(new ResourceNotFoundException("Secret not found mock exception"));

    try {
      awsSecretsManagerEncryptor.createSecret(
          awsSecretsManagerConfig.getAccountId(), secretName, plainTextValue, awsSecretsManagerConfig);
    } catch (SecretManagementDelegateException ex) {
      assertThat(ex.getMessage()).isEqualTo("Secret creation failed after 3 retries. Random runtime exception");
    }
  }

  @Test
  @Owner(developers = PIYUSH)
  @Category(UnitTests.class)
  public void testCreateSecret_AssumeIAMRole() {
    String plainTextValue = UUIDGenerator.generateUuid();
    String secretName = UUIDGenerator.generateUuid();
    String fullSecretName = awsSecretsManagerConfig.getSecretNamePrefix() + "/" + secretName;
    // Create the secret with proper tags.
    CreateSecretRequest createSecretRequest = new CreateSecretRequest()
                                                  .withName(fullSecretName)
                                                  .withSecretString(plainTextValue)
                                                  .withTags(new Tag().withKey("createdBy").withValue("Harness"));
    CreateSecretResult createSecretResult =
        new CreateSecretResult().withName(fullSecretName).withARN(UUIDGenerator.generateUuid());
    when(awsSecretsManager.createSecret(createSecretRequest)).thenReturn(createSecretResult);
    GetSecretValueRequest getSecretRequest = new GetSecretValueRequest().withSecretId(fullSecretName);
    when(awsSecretsManager.getSecretValue(getSecretRequest))
        .thenThrow(new ResourceNotFoundException("Secret not found mock exception"));
    awsSecretsManagerConfig.setAssumeIamRoleOnDelegate(true);
    awsSecretsManagerConfig.setAccessKey(null);
    awsSecretsManagerConfig.setSecretKey(null);
    EncryptedRecord encryptedRecord = awsSecretsManagerEncryptor.createSecret(
        awsSecretsManagerConfig.getAccountId(), secretName, plainTextValue, awsSecretsManagerConfig);
    assertThat(encryptedRecord.getEncryptedValue()).isEqualTo(createSecretResult.getARN().toCharArray());
    assertThat(encryptedRecord.getEncryptionKey()).isEqualTo(fullSecretName);
  }

  @Test
  @Owner(developers = PIYUSH)
  @Category(UnitTests.class)
  public void testCreateSecret_AssumeSTSRole() {
    String plainTextValue = UUIDGenerator.generateUuid();
    String secretName = UUIDGenerator.generateUuid();
    String fullSecretName = awsSecretsManagerConfig.getSecretNamePrefix() + "/" + secretName;
    // Create the secret with proper tags.
    CreateSecretRequest createSecretRequest = new CreateSecretRequest()
                                                  .withName(fullSecretName)
                                                  .withSecretString(plainTextValue)
                                                  .withTags(new Tag().withKey("createdBy").withValue("Harness"));
    CreateSecretResult createSecretResult =
        new CreateSecretResult().withName(fullSecretName).withARN(UUIDGenerator.generateUuid());
    when(awsSecretsManager.createSecret(createSecretRequest)).thenReturn(createSecretResult);
    GetSecretValueRequest getSecretRequest = new GetSecretValueRequest().withSecretId(fullSecretName);
    when(awsSecretsManager.getSecretValue(getSecretRequest))
        .thenThrow(new ResourceNotFoundException("Secret not found mock exception"));
    awsSecretsManagerConfig.setAssumeStsRoleOnDelegate(true);
    awsSecretsManagerConfig.setAccessKey(null);
    awsSecretsManagerConfig.setSecretKey(null);
    EncryptedRecord encryptedRecord = awsSecretsManagerEncryptor.createSecret(
        awsSecretsManagerConfig.getAccountId(), secretName, plainTextValue, awsSecretsManagerConfig);
    assertThat(encryptedRecord.getEncryptedValue()).isEqualTo(createSecretResult.getARN().toCharArray());
    assertThat(encryptedRecord.getEncryptionKey()).isEqualTo(fullSecretName);
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testUpdateSecret() {
    String plainTextValue = UUIDGenerator.generateUuid();
    String secretName = UUIDGenerator.generateUuid();
    String fullSecretName = awsSecretsManagerConfig.getSecretNamePrefix() + "/" + secretName;
    // Create the secret with proper tags.
    UpdateSecretRequest updateSecretRequest =
        new UpdateSecretRequest().withSecretId(fullSecretName).withSecretString(plainTextValue);
    UpdateSecretResult updateSecretResult =
        new UpdateSecretResult().withName(fullSecretName).withARN(UUIDGenerator.generateUuid());
    when(awsSecretsManager.updateSecret(updateSecretRequest)).thenReturn(updateSecretResult);
    GetSecretValueRequest getSecretRequest = new GetSecretValueRequest().withSecretId(fullSecretName);
    GetSecretValueResult getSecretValueResult =
        new GetSecretValueResult().withSecretString(UUIDGenerator.generateUuid());
    when(awsSecretsManager.getSecretValue(getSecretRequest)).thenReturn(getSecretValueResult);

    EncryptedRecord oldRecord = EncryptedRecordData.builder()
                                    .name(UUIDGenerator.generateUuid())
                                    .encryptionKey(UUIDGenerator.generateUuid())
                                    .encryptedValue(UUIDGenerator.generateUuid().toCharArray())
                                    .build();

    EncryptedRecord encryptedRecord = awsSecretsManagerEncryptor.updateSecret(
        awsSecretsManagerConfig.getAccountId(), secretName, plainTextValue, oldRecord, awsSecretsManagerConfig);
    assertThat(encryptedRecord.getEncryptedValue()).isEqualTo(updateSecretResult.getARN().toCharArray());
    assertThat(encryptedRecord.getEncryptionKey()).isEqualTo(fullSecretName);
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void testUpdateSecret_throwsAwsSecretManagerException() {
    String plainTextValue = UUIDGenerator.generateUuid();
    String secretName = UUIDGenerator.generateUuid();
    String fullSecretName = awsSecretsManagerConfig.getSecretNamePrefix() + "/" + secretName;
    // Create the secret with proper tags.
    UpdateSecretRequest updateSecretRequest =
        new UpdateSecretRequest().withSecretId(fullSecretName).withSecretString(plainTextValue);
    when(awsSecretsManager.updateSecret(updateSecretRequest))
        .thenThrow(new AWSSecretsManagerException("Mock AWS exception"));
    GetSecretValueRequest getSecretRequest = new GetSecretValueRequest().withSecretId(fullSecretName);
    GetSecretValueResult getSecretValueResult =
        new GetSecretValueResult().withSecretString(UUIDGenerator.generateUuid());
    when(awsSecretsManager.getSecretValue(getSecretRequest)).thenReturn(getSecretValueResult);

    EncryptedRecord oldRecord = EncryptedRecordData.builder()
                                    .name(UUIDGenerator.generateUuid())
                                    .encryptionKey(UUIDGenerator.generateUuid())
                                    .encryptedValue(UUIDGenerator.generateUuid().toCharArray())
                                    .build();

    try {
      awsSecretsManagerEncryptor.updateSecret(
          awsSecretsManagerConfig.getAccountId(), secretName, plainTextValue, oldRecord, awsSecretsManagerConfig);
    } catch (AWSSecretsManagerException ex) {
      assertThat(ex.getErrorMessage()).isEqualTo("Mock AWS exception");
    }
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void testUpdateSecret_throwsTimeOutException() {
    String plainTextValue = UUIDGenerator.generateUuid();
    String secretName = UUIDGenerator.generateUuid();
    String fullSecretName = awsSecretsManagerConfig.getSecretNamePrefix() + "/" + secretName;
    // Create the secret with proper tags.
    UpdateSecretRequest updateSecretRequest =
        new UpdateSecretRequest().withSecretId(fullSecretName).withSecretString(plainTextValue);
    when(awsSecretsManager.updateSecret(updateSecretRequest)).thenThrow(new UncheckedTimeoutException("Time out"));
    GetSecretValueRequest getSecretRequest = new GetSecretValueRequest().withSecretId(fullSecretName);
    GetSecretValueResult getSecretValueResult =
        new GetSecretValueResult().withSecretString(UUIDGenerator.generateUuid());
    when(awsSecretsManager.getSecretValue(getSecretRequest)).thenReturn(getSecretValueResult);

    EncryptedRecord oldRecord = EncryptedRecordData.builder()
                                    .name(UUIDGenerator.generateUuid())
                                    .encryptionKey(UUIDGenerator.generateUuid())
                                    .encryptedValue(UUIDGenerator.generateUuid().toCharArray())
                                    .build();

    try {
      awsSecretsManagerEncryptor.updateSecret(
          awsSecretsManagerConfig.getAccountId(), secretName, plainTextValue, oldRecord, awsSecretsManagerConfig);
    } catch (SecretManagementDelegateException ex) {
      assertThat(ex.getMessage())
          .isEqualTo("After 3 tries, delegate(s) is not able to establish connection to AWS services.");
    }
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void testUpdateSecret_throwsException() {
    String plainTextValue = UUIDGenerator.generateUuid();
    String secretName = UUIDGenerator.generateUuid();
    String fullSecretName = awsSecretsManagerConfig.getSecretNamePrefix() + "/" + secretName;
    // Create the secret with proper tags.
    UpdateSecretRequest updateSecretRequest =
        new UpdateSecretRequest().withSecretId(fullSecretName).withSecretString(plainTextValue);
    when(awsSecretsManager.updateSecret(updateSecretRequest))
        .thenThrow(new RuntimeException("Random runtime exception"));
    GetSecretValueRequest getSecretRequest = new GetSecretValueRequest().withSecretId(fullSecretName);
    GetSecretValueResult getSecretValueResult =
        new GetSecretValueResult().withSecretString(UUIDGenerator.generateUuid());
    when(awsSecretsManager.getSecretValue(getSecretRequest)).thenReturn(getSecretValueResult);

    EncryptedRecord oldRecord = EncryptedRecordData.builder()
                                    .name(UUIDGenerator.generateUuid())
                                    .encryptionKey(UUIDGenerator.generateUuid())
                                    .encryptedValue(UUIDGenerator.generateUuid().toCharArray())
                                    .build();

    try {
      awsSecretsManagerEncryptor.updateSecret(
          awsSecretsManagerConfig.getAccountId(), secretName, plainTextValue, oldRecord, awsSecretsManagerConfig);
    } catch (SecretManagementDelegateException ex) {
      assertThat(ex.getMessage()).isEqualTo("Secret update failed after 3 retries. Random runtime exception");
    }
  }

  @Test
  @Owner(developers = PIYUSH)
  @Category(UnitTests.class)
  public void testUpdateSecret_AssumeIAMRole() {
    String plainTextValue = UUIDGenerator.generateUuid();
    String secretName = UUIDGenerator.generateUuid();
    String fullSecretName = awsSecretsManagerConfig.getSecretNamePrefix() + "/" + secretName;
    awsSecretsManagerConfig.setAssumeIamRoleOnDelegate(true);
    awsSecretsManagerConfig.setAccessKey(null);
    awsSecretsManagerConfig.setSecretKey(null);
    // Create the secret with proper tags.
    UpdateSecretRequest updateSecretRequest =
        new UpdateSecretRequest().withSecretId(fullSecretName).withSecretString(plainTextValue);
    UpdateSecretResult updateSecretResult =
        new UpdateSecretResult().withName(fullSecretName).withARN(UUIDGenerator.generateUuid());
    when(awsSecretsManager.updateSecret(updateSecretRequest)).thenReturn(updateSecretResult);
    GetSecretValueRequest getSecretRequest = new GetSecretValueRequest().withSecretId(fullSecretName);
    GetSecretValueResult getSecretValueResult =
        new GetSecretValueResult().withSecretString(UUIDGenerator.generateUuid());
    when(awsSecretsManager.getSecretValue(getSecretRequest)).thenReturn(getSecretValueResult);

    EncryptedRecord oldRecord = EncryptedRecordData.builder()
                                    .name(UUIDGenerator.generateUuid())
                                    .encryptionKey(UUIDGenerator.generateUuid())
                                    .encryptedValue(UUIDGenerator.generateUuid().toCharArray())
                                    .build();

    EncryptedRecord encryptedRecord = awsSecretsManagerEncryptor.updateSecret(
        awsSecretsManagerConfig.getAccountId(), secretName, plainTextValue, oldRecord, awsSecretsManagerConfig);
    assertThat(encryptedRecord.getEncryptedValue()).isEqualTo(updateSecretResult.getARN().toCharArray());
    assertThat(encryptedRecord.getEncryptionKey()).isEqualTo(fullSecretName);
  }

  @Test
  @Owner(developers = PIYUSH)
  @Category(UnitTests.class)
  public void testUpdateSecret_AssumeSTSRole() {
    String plainTextValue = UUIDGenerator.generateUuid();
    String secretName = UUIDGenerator.generateUuid();
    String fullSecretName = awsSecretsManagerConfig.getSecretNamePrefix() + "/" + secretName;
    awsSecretsManagerConfig.setAssumeStsRoleOnDelegate(true);
    awsSecretsManagerConfig.setAccessKey(null);
    awsSecretsManagerConfig.setSecretKey(null);
    // Create the secret with proper tags.
    UpdateSecretRequest updateSecretRequest =
        new UpdateSecretRequest().withSecretId(fullSecretName).withSecretString(plainTextValue);
    UpdateSecretResult updateSecretResult =
        new UpdateSecretResult().withName(fullSecretName).withARN(UUIDGenerator.generateUuid());
    when(awsSecretsManager.updateSecret(updateSecretRequest)).thenReturn(updateSecretResult);
    GetSecretValueRequest getSecretRequest = new GetSecretValueRequest().withSecretId(fullSecretName);
    GetSecretValueResult getSecretValueResult =
        new GetSecretValueResult().withSecretString(UUIDGenerator.generateUuid());
    when(awsSecretsManager.getSecretValue(getSecretRequest)).thenReturn(getSecretValueResult);

    EncryptedRecord oldRecord = EncryptedRecordData.builder()
                                    .name(UUIDGenerator.generateUuid())
                                    .encryptionKey(UUIDGenerator.generateUuid())
                                    .encryptedValue(UUIDGenerator.generateUuid().toCharArray())
                                    .build();

    EncryptedRecord encryptedRecord = awsSecretsManagerEncryptor.updateSecret(
        awsSecretsManagerConfig.getAccountId(), secretName, plainTextValue, oldRecord, awsSecretsManagerConfig);
    assertThat(encryptedRecord.getEncryptedValue()).isEqualTo(updateSecretResult.getARN().toCharArray());
    assertThat(encryptedRecord.getEncryptionKey()).isEqualTo(fullSecretName);
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void renameSecret() {
    String secretName = UUIDGenerator.generateUuid();
    String plainTextValue = UUIDGenerator.generateUuid();
    String fullSecretName = awsSecretsManagerConfig.getSecretNamePrefix() + "/" + secretName;
    CreateSecretRequest createSecretRequest = new CreateSecretRequest()
                                                  .withName(fullSecretName)
                                                  .withSecretString(plainTextValue)
                                                  .withTags(new Tag().withKey("createdBy").withValue("Harness"));
    CreateSecretResult createSecretResult =
        new CreateSecretResult().withName(fullSecretName).withARN(UUIDGenerator.generateUuid());
    when(awsSecretsManager.createSecret(createSecretRequest)).thenReturn(createSecretResult);

    GetSecretValueRequest getSecretRequest = new GetSecretValueRequest().withSecretId(fullSecretName);
    when(awsSecretsManager.getSecretValue(getSecretRequest))
        .thenThrow(new ResourceNotFoundException("Secret not found"));

    EncryptedRecord oldRecord = EncryptedRecordData.builder()
                                    .name(UUIDGenerator.generateUuid())
                                    .encryptionKey(UUIDGenerator.generateUuid())
                                    .encryptedValue(UUIDGenerator.generateUuid().toCharArray())
                                    .build();

    String oldFullName = oldRecord.getEncryptionKey();
    GetSecretValueRequest getSecretRequestOld = new GetSecretValueRequest().withSecretId(oldFullName);
    GetSecretValueResult getSecretValueResult = new GetSecretValueResult().withSecretString(plainTextValue);
    when(awsSecretsManager.getSecretValue(getSecretRequestOld)).thenReturn(getSecretValueResult);

    EncryptedRecord encryptedRecord = awsSecretsManagerEncryptor.renameSecret(
        awsSecretsManagerConfig.getAccountId(), secretName, oldRecord, awsSecretsManagerConfig);
    assertThat(encryptedRecord).isNotNull();
    assertThat(encryptedRecord.getEncryptionKey()).isEqualTo(fullSecretName);
    assertThat(encryptedRecord.getEncryptedValue()).isEqualTo(createSecretResult.getARN().toCharArray());
  }

  @Test
  @Owner(developers = PIYUSH)
  @Category(UnitTests.class)
  public void renameSecret_AssumeIAMRole() {
    String secretName = UUIDGenerator.generateUuid();
    String plainTextValue = UUIDGenerator.generateUuid();
    String fullSecretName = awsSecretsManagerConfig.getSecretNamePrefix() + "/" + secretName;
    awsSecretsManagerConfig.setAssumeIamRoleOnDelegate(true);
    awsSecretsManagerConfig.setAccessKey(null);
    awsSecretsManagerConfig.setSecretKey(null);
    CreateSecretRequest createSecretRequest = new CreateSecretRequest()
                                                  .withName(fullSecretName)
                                                  .withSecretString(plainTextValue)
                                                  .withTags(new Tag().withKey("createdBy").withValue("Harness"));
    CreateSecretResult createSecretResult =
        new CreateSecretResult().withName(fullSecretName).withARN(UUIDGenerator.generateUuid());
    when(awsSecretsManager.createSecret(createSecretRequest)).thenReturn(createSecretResult);

    GetSecretValueRequest getSecretRequest = new GetSecretValueRequest().withSecretId(fullSecretName);
    when(awsSecretsManager.getSecretValue(getSecretRequest))
        .thenThrow(new ResourceNotFoundException("Secret not found"));

    EncryptedRecord oldRecord = EncryptedRecordData.builder()
                                    .name(UUIDGenerator.generateUuid())
                                    .encryptionKey(UUIDGenerator.generateUuid())
                                    .encryptedValue(UUIDGenerator.generateUuid().toCharArray())
                                    .build();

    String oldFullName = oldRecord.getEncryptionKey();
    GetSecretValueRequest getSecretRequestOld = new GetSecretValueRequest().withSecretId(oldFullName);
    GetSecretValueResult getSecretValueResult = new GetSecretValueResult().withSecretString(plainTextValue);
    when(awsSecretsManager.getSecretValue(getSecretRequestOld)).thenReturn(getSecretValueResult);

    EncryptedRecord encryptedRecord = awsSecretsManagerEncryptor.renameSecret(
        awsSecretsManagerConfig.getAccountId(), secretName, oldRecord, awsSecretsManagerConfig);
    assertThat(encryptedRecord).isNotNull();
    assertThat(encryptedRecord.getEncryptionKey()).isEqualTo(fullSecretName);
    assertThat(encryptedRecord.getEncryptedValue()).isEqualTo(createSecretResult.getARN().toCharArray());
  }

  @Test
  @Owner(developers = PIYUSH)
  @Category(UnitTests.class)
  public void renameSecret_AssumeSTSRole() {
    String secretName = UUIDGenerator.generateUuid();
    String plainTextValue = UUIDGenerator.generateUuid();
    String fullSecretName = awsSecretsManagerConfig.getSecretNamePrefix() + "/" + secretName;
    awsSecretsManagerConfig.setAssumeStsRoleOnDelegate(true);
    awsSecretsManagerConfig.setAccessKey(null);
    awsSecretsManagerConfig.setSecretKey(null);
    CreateSecretRequest createSecretRequest = new CreateSecretRequest()
                                                  .withName(fullSecretName)
                                                  .withSecretString(plainTextValue)
                                                  .withTags(new Tag().withKey("createdBy").withValue("Harness"));
    CreateSecretResult createSecretResult =
        new CreateSecretResult().withName(fullSecretName).withARN(UUIDGenerator.generateUuid());
    when(awsSecretsManager.createSecret(createSecretRequest)).thenReturn(createSecretResult);

    GetSecretValueRequest getSecretRequest = new GetSecretValueRequest().withSecretId(fullSecretName);
    when(awsSecretsManager.getSecretValue(getSecretRequest))
        .thenThrow(new ResourceNotFoundException("Secret not found"));

    EncryptedRecord oldRecord = EncryptedRecordData.builder()
                                    .name(UUIDGenerator.generateUuid())
                                    .encryptionKey(UUIDGenerator.generateUuid())
                                    .encryptedValue(UUIDGenerator.generateUuid().toCharArray())
                                    .build();

    String oldFullName = oldRecord.getEncryptionKey();
    GetSecretValueRequest getSecretRequestOld = new GetSecretValueRequest().withSecretId(oldFullName);
    GetSecretValueResult getSecretValueResult = new GetSecretValueResult().withSecretString(plainTextValue);
    when(awsSecretsManager.getSecretValue(getSecretRequestOld)).thenReturn(getSecretValueResult);

    EncryptedRecord encryptedRecord = awsSecretsManagerEncryptor.renameSecret(
        awsSecretsManagerConfig.getAccountId(), secretName, oldRecord, awsSecretsManagerConfig);
    assertThat(encryptedRecord).isNotNull();
    assertThat(encryptedRecord.getEncryptionKey()).isEqualTo(fullSecretName);
    assertThat(encryptedRecord.getEncryptedValue()).isEqualTo(createSecretResult.getARN().toCharArray());
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testCreateSecret_shouldThrowException() {
    String plainTextValue = UUIDGenerator.generateUuid();
    String secretName = UUIDGenerator.generateUuid();

    when(awsSecretsManager.createSecret(any())).thenThrow(new AWSSecretsManagerException("Dummy exception"));
    when(awsSecretsManager.getSecretValue(any()))
        .thenThrow(new ResourceNotFoundException("Secret not found mock exception"));

    try {
      awsSecretsManagerEncryptor.createSecret(
          awsSecretsManagerConfig.getAccountId(), secretName, plainTextValue, awsSecretsManagerConfig);
      fail("Create Secret should have failed");
    } catch (AWSSecretsManagerException e) {
      assertThat(e.getErrorMessage()).isEqualTo("Dummy exception");
    }
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testUpdateSecret_shouldThrowException() {
    String plainTextValue = UUIDGenerator.generateUuid();
    String secretName = UUIDGenerator.generateUuid();

    when(awsSecretsManager.createSecret(any())).thenThrow(new AWSSecretsManagerException("Dummy exception"));
    when(awsSecretsManager.getSecretValue(any()))
        .thenThrow(new ResourceNotFoundException("Secret not found mock exception"));

    try {
      awsSecretsManagerEncryptor.updateSecret(awsSecretsManagerConfig.getAccountId(), secretName, plainTextValue,
          mock(EncryptedRecord.class), awsSecretsManagerConfig);
      fail("Update Secret should have failed");
    } catch (AWSSecretsManagerException e) {
      assertThat(e.getErrorMessage()).isEqualTo("Dummy exception");
    }
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testRenameSecret_shouldThrowException() {
    String secretName = UUIDGenerator.generateUuid();
    when(awsSecretsManager.getSecretValue(any()))
        .thenThrow(new ResourceNotFoundException("Secret not found mock exception"));
    EncryptedRecord encryptedRecord = EncryptedRecordData.builder()
                                          .encryptedValue(UUIDGenerator.generateUuid().toCharArray())
                                          .encryptionKey(UUIDGenerator.generateUuid())
                                          .name(UUIDGenerator.generateUuid())
                                          .build();
    try {
      awsSecretsManagerEncryptor.renameSecret(
          awsSecretsManagerConfig.getAccountId(), secretName, encryptedRecord, awsSecretsManagerConfig);
      fail("Rename Secret should have failed");
    } catch (AWSSecretsManagerException e) {
      assertThat(e.getErrorMessage()).isEqualTo("Secret not found mock exception");
      assertThat(e).isInstanceOf(ResourceNotFoundException.class);
    }
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testFetchSecret() {
    String key = UUIDGenerator.generateUuid();
    String value = UUIDGenerator.generateUuid();
    String fullSecretName = awsSecretsManagerConfig.getSecretNamePrefix() + "/" + UUIDGenerator.generateUuid();
    String fullSecretNameAndKey = fullSecretName + "#" + key;
    String result = "{\"" + key + "\":\"" + value + "\"}";
    GetSecretValueRequest getSecretRequest = new GetSecretValueRequest().withSecretId(fullSecretName);
    GetSecretValueResult getSecretValueResult = new GetSecretValueResult().withSecretString(result);
    when(awsSecretsManager.getSecretValue(getSecretRequest)).thenReturn(getSecretValueResult);
    EncryptedRecord encryptedRecord = EncryptedRecordData.builder().path(fullSecretNameAndKey).build();
    char[] returnedValue = awsSecretsManagerEncryptor.fetchSecretValue(
        awsSecretsManagerConfig.getAccountId(), encryptedRecord, awsSecretsManagerConfig);
    assertThat(returnedValue).isEqualTo(value.toCharArray());
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testFetchSecret_throwsAwsSecretManagerException() {
    String path = UUIDGenerator.generateUuid();
    GetSecretValueRequest getSecretRequest = new GetSecretValueRequest().withSecretId(path);
    when(awsSecretsManager.getSecretValue(getSecretRequest))
        .thenThrow(new ResourceNotFoundException("Secret not found mock exception"));
    EncryptedRecord encryptedRecord = EncryptedRecordData.builder().path(path).build();
    try {
      awsSecretsManagerEncryptor.fetchSecretValue(
          awsSecretsManagerConfig.getAccountId(), encryptedRecord, awsSecretsManagerConfig);
      fail("fetch secret value should throw exception");
    } catch (AWSSecretsManagerException e) {
      assertThat(e.getErrorMessage()).isEqualTo("Secret not found mock exception");
      assertThat(e).isInstanceOf(ResourceNotFoundException.class);
    }
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void testFetchSecret_throwsTimeOutException() {
    String path = UUIDGenerator.generateUuid();
    GetSecretValueRequest getSecretRequest = new GetSecretValueRequest().withSecretId(path);
    when(awsSecretsManager.getSecretValue(getSecretRequest)).thenThrow(new UncheckedTimeoutException("Time out"));
    EncryptedRecord encryptedRecord = EncryptedRecordData.builder().path(path).build();
    try {
      awsSecretsManagerEncryptor.fetchSecretValue(
          awsSecretsManagerConfig.getAccountId(), encryptedRecord, awsSecretsManagerConfig);
      fail("fetch secret value should throw exception");
    } catch (SecretManagementDelegateException ex) {
      assertThat(ex.getMessage())
          .isEqualTo("After 3 tries, delegate(s) is not able to establish connection to AWS services.");
    }
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void testFetchSecret_throwsException() {
    String path = UUIDGenerator.generateUuid();
    GetSecretValueRequest getSecretRequest = new GetSecretValueRequest().withSecretId(path);
    when(awsSecretsManager.getSecretValue(getSecretRequest))
        .thenThrow(new RuntimeException("Random runtime exception"));
    EncryptedRecord encryptedRecord = EncryptedRecordData.builder().path(path).build();
    try {
      awsSecretsManagerEncryptor.fetchSecretValue(
          awsSecretsManagerConfig.getAccountId(), encryptedRecord, awsSecretsManagerConfig);
      fail("fetch secret value should throw exception");
    } catch (SecretManagementDelegateException ex) {
      assertThat(ex.getMessage()).isEqualTo("Fetching secret failed after 3 retries. Random runtime exception");
    }
  }

  @Test
  @Owner(developers = PIYUSH)
  @Category(UnitTests.class)
  public void testCredentialProviderTypeSelection_shouldPass() {
    System.setProperty("AWS_ACCESS_KEY_ID", "AKIAWQ5IKSASTHBLFKEU");
    System.setProperty("AWS_SECRET_ACCESS_KEY", "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");
    awsSecretsManagerConfig.setAssumeIamRoleOnDelegate(true);
    AWSCredentialsProvider provider = awsSecretsManagerEncryptor.getAwsCredentialsProvider(awsSecretsManagerConfig);
    assertThat(provider).isInstanceOf(DefaultAWSCredentialsProviderChain.class);

    awsSecretsManagerConfig.setAssumeIamRoleOnDelegate(false);
    awsSecretsManagerConfig.setAssumeStsRoleOnDelegate(true);
    awsSecretsManagerConfig.setRoleArn("arn:aws:iam::123456789012:user/JohnDoe");
    provider = awsSecretsManagerEncryptor.getAwsCredentialsProvider(awsSecretsManagerConfig);
    assertThat(provider).isInstanceOf(STSAssumeRoleSessionCredentialsProvider.class);

    awsSecretsManagerConfig.setAssumeStsRoleOnDelegate(false);
    awsSecretsManagerConfig.setAssumeStsRoleOnDelegate(false);
    awsSecretsManagerConfig.setRoleArn(null);
    provider = awsSecretsManagerEncryptor.getAwsCredentialsProvider(awsSecretsManagerConfig);
    assertThat(provider).isInstanceOf(AWSStaticCredentialsProvider.class);
  }

  @Test
  @Owner(developers = PIYUSH)
  @Category(UnitTests.class)
  public void testCredentialProviderTypeSelection_shouldFail() {
    AWSCredentialsProvider provider = null;
    awsSecretsManagerConfig.setAssumeIamRoleOnDelegate(false);
    awsSecretsManagerConfig.setAssumeStsRoleOnDelegate(true);
    try {
      provider = awsSecretsManagerEncryptor.getAwsCredentialsProvider(awsSecretsManagerConfig);
      fail("AssumeSTSRole worked without RoleARN");
    } catch (SecretManagementDelegateException e) {
      assertThat(e.getMessage()).contains("You must provide RoleARN if AssumeStsRole is selected");
    }

    awsSecretsManagerConfig.setAssumeStsRoleOnDelegate(false);
    awsSecretsManagerConfig.setAssumeStsRoleOnDelegate(false);
    awsSecretsManagerConfig.setRoleArn(null);
    awsSecretsManagerConfig.setAccessKey(null);
    try {
      provider = awsSecretsManagerEncryptor.getAwsCredentialsProvider(awsSecretsManagerConfig);
      fail("AccessKey was null and AssumeRole was also not set");
    } catch (SecretManagementDelegateException e) {
      assertThat(e.getMessage()).contains("You must provide an AccessKey if AssumeIAMRole is not enabled");
    }

    awsSecretsManagerConfig.setAccessKey("AKIAWQ5IKSASTHBLFKEU");
    awsSecretsManagerConfig.setSecretKey(null);
    try {
      provider = awsSecretsManagerEncryptor.getAwsCredentialsProvider(awsSecretsManagerConfig);
      fail("AccessKey was null and AssumeRole was also not set");
    } catch (SecretManagementDelegateException e) {
      assertThat(e.getMessage()).contains("You must provide a SecretKey if AssumeIAMRole is not enabled");
    }
  }
}
