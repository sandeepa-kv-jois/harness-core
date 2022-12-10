/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.azure.appservice.webapp.ng;

import static io.harness.expression.Expression.ALLOW_SECRETS;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;
import io.harness.delegate.beans.connector.azureconnector.AzureConnectorDTO;
import io.harness.expression.Expression;
import io.harness.reflection.ExpressionReflectionUtils.NestedAnnotationResolver;
import io.harness.security.encryption.EncryptedDataDetail;

import java.util.Collections;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@OwnedBy(HarnessTeam.CDP)
public class AzureWebAppInfraDelegateConfig implements NestedAnnotationResolver {
  private AzureConnectorDTO azureConnectorDTO;
  @Expression(ALLOW_SECRETS) private String appName;
  @Expression(ALLOW_SECRETS) private String subscription;
  @Expression(ALLOW_SECRETS) private String resourceGroup;
  @Expression(ALLOW_SECRETS) private String deploymentSlot;
  private List<EncryptedDataDetail> encryptionDataDetails;

  @NotNull
  public List<DecryptableEntity> getDecryptableEntities() {
    if (azureConnectorDTO != null) {
      return azureConnectorDTO.getDecryptableEntities();
    }

    return Collections.emptyList();
  }
}
