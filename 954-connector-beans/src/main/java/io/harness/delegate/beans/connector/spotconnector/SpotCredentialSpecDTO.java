/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.spotconnector;

import io.harness.beans.DecryptableEntity;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonSubTypes(
    { @JsonSubTypes.Type(value = SpotPermanentTokenConfigSpecDTO.class, name = SpotConstants.PERMANENT_TOKEN_CONFIG) })
@ApiModel("SpotCredentialSpec")
@Schema(name = "SpotCredentialSpec", description = "This contains Spot connector credential spec")
public interface SpotCredentialSpecDTO extends DecryptableEntity {}
