/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.serializer.morphia;

import io.harness.morphia.MorphiaRegistrar;
import io.harness.morphia.MorphiaRegistrarHelperPut;

import software.wings.beans.AppDynamicsConfig;
import software.wings.delegatetasks.cv.beans.CustomLogResponseMapper;
import software.wings.delegatetasks.validation.DelegateConnectionResult;
import software.wings.service.impl.analysis.CustomLogDataCollectionInfo;
import software.wings.service.impl.analysis.DataCollectionTaskResult;
import software.wings.service.impl.newrelic.NewRelicMetricDataRecord;

import java.util.Set;

public class DelegateTasksMorphiaRegistrar implements MorphiaRegistrar {
  @Override
  public void registerClasses(Set<Class> set) {
    set.add(NewRelicMetricDataRecord.class);
    set.add(DelegateConnectionResult.class);
  }

  @Override
  public void registerImplementationClasses(MorphiaRegistrarHelperPut h, MorphiaRegistrarHelperPut w) {
    w.put("service.impl.analysis.DataCollectionTaskResult", DataCollectionTaskResult.class);
    w.put("service.impl.analysis.CustomLogDataCollectionInfo", CustomLogDataCollectionInfo.class);
    w.put("delegatetasks.cv.beans.CustomLogResponseMapper", CustomLogResponseMapper.class);
    w.put("beans.AppDynamicsConfig", AppDynamicsConfig.class);
  }
}
