/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.entities.embedded.awscodecommitconnector;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.TypeAlias;

@Value
@Builder
@FieldNameConstants(innerTypeName = "AwsCodeCommitSecretKeyAccessKeyKeys")
@FieldDefaults(level = AccessLevel.PRIVATE)
@TypeAlias("io.harness.connector.entities.embedded.awscodecommitconnector.AwsCodeCommitSecretKeyAccessKey")
public class AwsCodeCommitSecretKeyAccessKey implements AwsCodeCommitHttpsCredential {
  String secretKeyRef;
  String accessKey;
  String accessKeyRef;
}
