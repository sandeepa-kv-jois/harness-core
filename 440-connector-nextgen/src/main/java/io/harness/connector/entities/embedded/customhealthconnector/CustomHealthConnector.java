/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.connector.entities.embedded.customhealthconnector;
import io.harness.annotations.StoreIn;
import io.harness.connector.entities.Connector;
import io.harness.delegate.beans.connector.customhealthconnector.CustomHealthMethod;
import io.harness.ng.DbAliases;

import java.util.List;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.mongodb.morphia.annotations.Entity;
import org.springframework.data.annotation.Persistent;
import org.springframework.data.annotation.TypeAlias;

@Value
@Builder
@EqualsAndHashCode(callSuper = true)
@StoreIn(DbAliases.NG_MANAGER)
@Entity(value = "connectors", noClassnameStored = true)
@Persistent
@TypeAlias("io.harness.connector.entities.embedded.customhealthconnector.CustomHealthConnector")
public class CustomHealthConnector extends Connector {
  String baseURL;
  List<CustomHealthConnectorKeyAndValue> headers;
  List<CustomHealthConnectorKeyAndValue> params;
  CustomHealthMethod method;
  String validationBody;
  String validationPath;
}
