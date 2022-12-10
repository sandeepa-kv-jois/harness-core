/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.cvng.client;

import io.harness.cvng.beans.errortracking.ErrorTrackingNotificationData;
import io.harness.cvng.notification.beans.ErrorTrackingEventType;

import com.google.inject.Inject;
import java.util.List;

public class ErrorTrackingServiceImpl implements ErrorTrackingService {
  @Inject private ErrorTrackingClient errorTrackingClient;
  @Inject private RequestExecutor requestExecutor;

  @Override
  public ErrorTrackingNotificationData getNotificationData(String orgIdentifier, String accountId,
      String projectIdentifier, String serviceIdentifier, String environmentIdentifier,
      List<ErrorTrackingEventType> eventTypes) {
    return requestExecutor.execute(errorTrackingClient.getNotificationData(
        orgIdentifier, accountId, projectIdentifier, serviceIdentifier, environmentIdentifier, eventTypes));
  }
}
