/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.serializer.kryo;

import io.harness.delegate.task.executioncapability.BatchCapabilityCheckTaskParameters;
import io.harness.delegate.task.executioncapability.BatchCapabilityCheckTaskResponse;
import io.harness.delegate.task.winrm.AuthenticationScheme;
import io.harness.serializer.KryoRegistrar;

import software.wings.beans.AppDynamicsConfig;
import software.wings.beans.command.ExecutionLogCallback;
import software.wings.beans.trigger.WebHookTriggerResponseData;
import software.wings.beans.trigger.WebhookTriggerParameters;
import software.wings.delegatetasks.DelegateStateType;
import software.wings.delegatetasks.cv.DataCollectionException;
import software.wings.delegatetasks.cv.beans.CustomLogResponseMapper;
import software.wings.service.impl.analysis.CustomLogDataCollectionInfo;
import software.wings.service.impl.analysis.DataCollectionTaskResult;
import software.wings.service.impl.analysis.LogElement;
import software.wings.service.impl.analysis.SetupTestNodeData;
import software.wings.service.impl.analysis.TimeSeriesMlAnalysisType;
import software.wings.service.impl.appdynamics.AppdynamicsDataCollectionInfo;
import software.wings.service.impl.appdynamics.AppdynamicsSetupTestNodeData;
import software.wings.service.impl.newrelic.NewRelicMetricDataRecord;
import software.wings.service.intfc.analysis.ClusterLevel;

import com.esotericsoftware.kryo.Kryo;

public class DelegateTasksKryoRegistrar implements KryoRegistrar {
  @Override
  public void register(Kryo kryo) {
    kryo.register(ExecutionLogCallback.class, 5044);
    kryo.register(DataCollectionException.class, 7298);
    kryo.register(BatchCapabilityCheckTaskParameters.class, 8200);
    kryo.register(BatchCapabilityCheckTaskResponse.class, 8201);
    kryo.register(WebhookTriggerParameters.class, 8550);
    kryo.register(WebHookTriggerResponseData.class, 8552);
    kryo.register(AuthenticationScheme.class, 8600);
    kryo.register(AppDynamicsConfig.class, 5074);
    kryo.register(CustomLogDataCollectionInfo.class, 5492);
    kryo.register(DataCollectionTaskResult.DataCollectionTaskStatus.class, 5185);
    kryo.register(DataCollectionTaskResult.class, 5184);
    kryo.register(LogElement.class, 5486);
    kryo.register(SetupTestNodeData.class, 5530);
    kryo.register(SetupTestNodeData.Instance.class, 7470);
    kryo.register(AppdynamicsSetupTestNodeData.class, 5531);
    kryo.register(AppdynamicsDataCollectionInfo.class, 5168);
    kryo.register(TimeSeriesMlAnalysisType.class, 5347);
    kryo.register(CustomLogResponseMapper.class, 5493);
    kryo.register(NewRelicMetricDataRecord.class, 7347);
    kryo.register(ClusterLevel.class, 7348);
    kryo.register(DelegateStateType.class, 8601);
  }
}
