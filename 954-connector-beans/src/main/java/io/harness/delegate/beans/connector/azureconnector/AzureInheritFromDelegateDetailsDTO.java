/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.azureconnector;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.azureconnector.outcome.AzureCredentialSpecOutcomeDTO;
import io.harness.delegate.beans.connector.azureconnector.outcome.AzureInheritFromDelegateDetailsOutcomeDTO;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;

@OwnedBy(HarnessTeam.CDP)
@Value
@Builder
@JsonTypeName(AzureConstants.INHERIT_FROM_DELEGATE)
@ApiModel("AzureInheritFromDelegateDetails")
@Schema(name = "AzureInheritFromDelegateDetails",
    description = "This contains Azure inherit from delegate credentials connector details")
@JsonDeserialize(using = AzureInheritFromDelegateDetailsDTODeserializer.class)
public class AzureInheritFromDelegateDetailsDTO implements AzureCredentialSpecDTO {
  @NotNull @JsonProperty("auth") @Schema(description = "The auth azure details ") AzureMSIAuthDTO authDTO;

  @Override
  public AzureCredentialSpecOutcomeDTO toOutcome() {
    return AzureInheritFromDelegateDetailsOutcomeDTO.builder().auth(this.authDTO.toOutcome()).build();
  }
}
