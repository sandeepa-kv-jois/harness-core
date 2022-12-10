/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.preflight.connector;

import io.harness.pms.preflight.PreFlightEntityErrorInfo;
import io.harness.pms.preflight.PreFlightStatus;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ConnectorCheckResponse {
  String connectorIdentifier;
  PreFlightEntityErrorInfo errorInfo;
  String fqn;
  String stageName;
  String stageIdentifier;
  String stepName;
  String stepIdentifier;
  PreFlightStatus status;
}
