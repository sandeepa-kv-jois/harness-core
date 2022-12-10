/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.data.stepparameters;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.pms.data.OrchestrationMap;
import io.harness.pms.serializer.recaster.RecastOrchestrationUtils;

import java.util.Map;

@OwnedBy(HarnessTeam.PIPELINE)
public class PmsStepParameters extends OrchestrationMap {
  public PmsStepParameters() {}

  public PmsStepParameters(Map<String, Object> map) {
    super(map);
  }

  public static PmsStepParameters parse(String json) {
    if (EmptyPredicate.isEmpty(json)) {
      return null;
    }
    return new PmsStepParameters(RecastOrchestrationUtils.fromJson(json));
  }

  public static PmsStepParameters parse(Map<String, Object> map) {
    if (EmptyPredicate.isEmpty(map)) {
      return null;
    }

    return new PmsStepParameters(map);
  }

  public String getValue(String key) {
    if (this.containsKey(key)) {
      return this.get(key).toString();
    }
    return null;
  }
}
