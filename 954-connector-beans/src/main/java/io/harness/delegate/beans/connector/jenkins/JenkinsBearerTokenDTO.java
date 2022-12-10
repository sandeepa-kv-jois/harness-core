/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.jenkins;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.encryption.SecretRefData;
import io.harness.secret.SecretReference;
import io.harness.validation.OneOfField;

import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

@OwnedBy(CDC)
@Data
@Builder
@OneOfField(fields = {"tokenRef"})
@Schema(name = "JenkinsBearerTokenDTO", description = "This entity contains the details of the Jenkins Bearer token")
public class JenkinsBearerTokenDTO implements JenkinsAuthCredentialsDTO {
  @ApiModelProperty(dataType = "string") @NotNull @SecretReference SecretRefData tokenRef;
}
