/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.azure.artifact;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.expression.Expression.ALLOW_SECRETS;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.expression.Expression;
import io.harness.reflection.ExpressionReflectionUtils.NestedAnnotationResolver;
import io.harness.security.encryption.EncryptedDataDetail;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@OwnedBy(CDP)
public class AzurePackageArtifactConfig implements AzureArtifactConfig, NestedAnnotationResolver {
  private ConnectorConfigDTO connectorConfig;
  private ArtifactSourceType sourceType;
  @Expression(ALLOW_SECRETS) private AzureArtifactRequestDetails artifactDetails;

  private List<EncryptedDataDetail> encryptedDataDetails;

  @Override
  public AzureArtifactType getArtifactType() {
    return AzureArtifactType.PACKAGE;
  }
}
