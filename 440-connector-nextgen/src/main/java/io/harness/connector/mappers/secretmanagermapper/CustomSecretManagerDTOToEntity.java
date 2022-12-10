/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.mappers.secretmanagermapper;

import io.harness.connector.entities.embedded.customsecretmanager.CustomSecretManagerConnector;
import io.harness.connector.mappers.ConnectorDTOToEntityMapper;
import io.harness.delegate.beans.connector.customsecretmanager.CustomSecretManagerConnectorDTO;
import io.harness.encryption.SecretRefHelper;

public class CustomSecretManagerDTOToEntity
    implements ConnectorDTOToEntityMapper<CustomSecretManagerConnectorDTO, CustomSecretManagerConnector> {
  @Override
  public CustomSecretManagerConnector toConnectorEntity(CustomSecretManagerConnectorDTO connectorDTO) {
    boolean onDelegate = Boolean.TRUE.equals(connectorDTO.getOnDelegate());
    return CustomSecretManagerConnector.builder()
        .isDefault(connectorDTO.isDefault())
        .connectorRef(SecretRefHelper.getSecretConfigString(connectorDTO.getConnectorRef()))
        .template(connectorDTO.getTemplate())
        .host(connectorDTO.getHost())
        .workingDirectory(connectorDTO.getWorkingDirectory())
        .onDelegate(onDelegate)
        .build();
  }
}
