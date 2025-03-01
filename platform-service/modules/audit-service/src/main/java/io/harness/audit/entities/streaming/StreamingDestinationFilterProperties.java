/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.audit.entities.streaming;

import io.harness.spec.server.audit.v1.model.StreamingDestinationDTO.StatusEnum;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class StreamingDestinationFilterProperties {
  String searchTerm;
  StatusEnum status;
}
