/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.dtos;

import io.swagger.annotations.ApiModel;
import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@ApiModel("NGPipelineErrorResponse")
public class NGPipelineErrorResponseDTO {
  @Builder.Default List<NGPipelineErrorDTO> errors = new ArrayList<>();
}
