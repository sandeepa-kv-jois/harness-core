/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.beans.internal;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;

import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.Setter;
import lombok.Value;
import lombok.experimental.NonFinal;

@OwnedBy(CDC)
@Value
@Builder
public class EdgeListInternal {
  @NonFinal @Setter String parentId;
  List<String> prevIds;
  List<String> nextIds;

  @Builder.Default List<String> edges = new ArrayList<>();
}
