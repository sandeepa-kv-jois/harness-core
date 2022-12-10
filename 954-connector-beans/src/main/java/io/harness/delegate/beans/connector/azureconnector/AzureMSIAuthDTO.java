/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.azureconnector;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.azureconnector.outcome.AzureMSIAuthOutcomeDTO;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;

@OwnedBy(HarnessTeam.CDP)
@JsonIgnoreProperties(ignoreUnknown = true)
@ApiModel("AzureMSIAuth")
@Schema(name = "AzureMSIAuth", description = "This contains azure MSI auth details")
public interface AzureMSIAuthDTO {
  default AzureMSIAuthOutcomeDTO toOutcome() {
    return null;
  }
}
