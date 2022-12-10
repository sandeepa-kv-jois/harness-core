/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.freeze.beans;

import io.harness.pms.yaml.ParameterField;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModelProperty;
import java.util.List;
import lombok.Data;

@Data
public class FreezeNotifications {
  String name;
  boolean enabled;

  List<FreezeEvent> events;

  @ApiModelProperty(dataType = "io.harness.freeze.beans.FreezeNotificationChannelWrapper")
  @JsonProperty("notificationMethod")
  ParameterField<FreezeNotificationChannelWrapper> notificationChannelWrapper;
}
