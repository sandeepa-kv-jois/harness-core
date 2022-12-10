/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.beans.elk;

import static io.harness.annotations.dev.HarnessTeam.CV;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cvng.beans.DataCollectionRequest;
import io.harness.delegate.beans.connector.elkconnector.ELKConnectorDTO;
import io.harness.delegate.beans.cvng.elk.elkUtils;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.HashMap;
import java.util.Map;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@JsonIgnoreProperties(ignoreUnknown = true)
@NoArgsConstructor
@OwnedBy(CV)
public abstract class ELKDataCollectionRequest extends DataCollectionRequest<ELKConnectorDTO> {
  @Override
  public Map<String, Object> fetchDslEnvVariables() {
    return new HashMap<>();
  }
  @Override
  public Map<String, String> collectionHeaders() {
    return elkUtils.collectionHeaders(getConnectorConfigDTO());
  }

  @Override
  public String getBaseUrl() {
    return getConnectorConfigDTO().getUrl();
  }
}
