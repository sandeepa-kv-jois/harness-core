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
import io.harness.expression.Expression;

import java.util.Map;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@OwnedBy(CDP)
public class NexusAzureArtifactRequestDetails implements AzureArtifactRequestDetails {
  private String identifier;
  @Expression(ALLOW_SECRETS) private String artifactUrl;
  private String repositoryFormat;
  private Map<String, String> metadata;
  private boolean certValidationRequired;

  @Override
  public String getArtifactName() {
    return artifactUrl;
  }
}
