/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.azureconnector;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.encryption.SecretRefData;
import io.harness.encryption.SecretReference;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;

@OwnedBy(HarnessTeam.CDP)
@Value
@Builder
@JsonTypeName(AzureConstants.MANUAL_CONFIG)
@ApiModel("AzureManualDetails")
@Schema(name = "AzureManualDetails", description = "This contains Azure manual credentials connector details")
public class AzureManualDetailsDTO implements AzureCredentialSpecDTO {
  @Schema(description = "Application ID of the Azure App.") @NotNull String clientId;
  @SecretReference
  @ApiModelProperty(dataType = "string")
  @Schema(description = "This is the Harness secret reference for connection to the Azure")
  @NotNull
  SecretRefData secretRef;
  @NotNull
  @Schema(description = "The Azure Active Directory (AAD) directory ID where you created your application.")
  String tenantId;
  @NotNull
  @JsonProperty("type")
  @Schema(description = "The type of secret used for Azure authentication ")
  AzureSecretType secretType;
}
