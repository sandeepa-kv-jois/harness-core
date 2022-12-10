/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.task.tas;

import static io.harness.exception.WingsException.ExecutionContext.MANAGER;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.ConnectivityStatus;
import io.harness.connector.ConnectorValidationResult;
import io.harness.connector.task.ConnectorValidationHandler;
import io.harness.delegate.beans.connector.ConnectorValidationParams;
import io.harness.delegate.beans.connector.tasconnector.TasConnectorDTO;
import io.harness.delegate.beans.connector.tasconnector.TasTaskParams;
import io.harness.delegate.beans.connector.tasconnector.TasValidationParams;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.exceptionmanager.ExceptionManager;
import io.harness.exception.ngexception.ConnectorValidationException;
import io.harness.pcf.CfDeploymentManager;
import io.harness.pcf.model.CfRequestConfig;
import io.harness.pcf.model.CloudFoundryConfig;
import io.harness.security.encryption.EncryptedDataDetail;

import com.google.inject.Inject;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class TasValidationHandler implements ConnectorValidationHandler {
  @Inject protected CfDeploymentManager cfDeploymentManager;
  @Inject private TasNgConfigMapper ngConfigMapper;
  @Inject ExceptionManager exceptionManager;
  @Override
  public ConnectorValidationResult validate(
      ConnectorValidationParams connectorValidationParams, String accountIdentifier) {
    try {
      final TasValidationParams tasValidationParams = (TasValidationParams) connectorValidationParams;
      final TasConnectorDTO tasConnectorDTO = tasValidationParams.getTasConnectorDTO();
      final List<EncryptedDataDetail> encryptedDataDetails = tasValidationParams.getEncryptionDetails();
      return validateInternal(tasConnectorDTO, encryptedDataDetails);
    } catch (Exception e) {
      throw exceptionManager.processException(e, MANAGER, log);
    }
  }

  public ConnectorValidationResult validate(TasTaskParams tasTaskParams, List<EncryptedDataDetail> encryptionDetails) {
    final TasConnectorDTO tasConnectorDTO = tasTaskParams.getTasConnectorDTO();
    return validateInternal(tasConnectorDTO, encryptionDetails);
  }

  private ConnectorValidationResult validateInternal(
      TasConnectorDTO tasConnectorDTO, List<EncryptedDataDetail> encryptedDataDetails) {
    CloudFoundryConfig cfConfig = ngConfigMapper.mapTasConfigWithDecryption(tasConnectorDTO, encryptedDataDetails);
    reformatEndpointURL(cfConfig);
    return handleValidateTask(cfConfig);
  }

  private void reformatEndpointURL(CloudFoundryConfig cfConfig) {
    String endpointUrl = cfConfig.getEndpointUrl();
    String httpPrefix = "https://";
    if (endpointUrl.length() >= 8 && httpPrefix.equals(endpointUrl.substring(0, 8))) {
      cfConfig.setEndpointUrl(endpointUrl.substring(httpPrefix.length()));
    }
  }

  private ConnectorValidationResult handleValidateTask(CloudFoundryConfig cfConfig) {
    try {
      // todo: ask about limit pcf threads FF
      cfDeploymentManager.getOrganizations(CfRequestConfig.builder()
                                               .userName(String.valueOf(cfConfig.getUserName()))
                                               .password(String.valueOf(cfConfig.getPassword()))
                                               .endpointUrl(cfConfig.getEndpointUrl())
                                               .timeOutIntervalInMins(2)
                                               .build());
      return ConnectorValidationResult.builder()
          .status(ConnectivityStatus.SUCCESS)
          .testedAt(System.currentTimeMillis())
          .build();
    } catch (Exception e) {
      String errorMessage = "Testing connection to Tas has Failed: ";
      throw NestedExceptionUtils.hintWithExplanationException("Failed to validate connection for Tas connector",
          "Please check you Tas connector configuration.",
          new ConnectorValidationException(errorMessage + e.getMessage()));
    }
  }
}
