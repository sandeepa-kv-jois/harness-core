/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.service.impl;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.delegate.beans.DelegateTaskResponse.ResponseCode;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;
import static io.harness.metrics.impl.DelegateMetricsServiceImpl.DELEGATE_TASK_RESPONSE;

import static java.lang.System.currentTimeMillis;

import io.harness.beans.DelegateTask;
import io.harness.beans.DelegateTask.DelegateTaskKeys;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.DelegateProgressData;
import io.harness.delegate.beans.DelegateSyncTaskResponse;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.TaskDataV2;
import io.harness.delegate.task.tasklogging.TaskLogContext;
import io.harness.exception.InvalidArgumentsException;
import io.harness.logging.AutoLogContext;
import io.harness.logging.DelegateDriverLogContext;
import io.harness.metrics.intfc.DelegateMetricsService;
import io.harness.observer.RemoteObserverInformer;
import io.harness.observer.Subject;
import io.harness.persistence.HIterator;
import io.harness.persistence.HPersistence;
import io.harness.reflection.ReflectionUtils;
import io.harness.serializer.KryoSerializer;
import io.harness.service.dto.RetryDelegate;
import io.harness.service.intfc.DelegateCache;
import io.harness.service.intfc.DelegateCallbackRegistry;
import io.harness.service.intfc.DelegateCallbackService;
import io.harness.service.intfc.DelegateTaskRetryObserver;
import io.harness.service.intfc.DelegateTaskService;
import io.harness.version.VersionInfoManager;
import io.harness.waiter.WaitNotifyEngine;

import software.wings.beans.SerializationFormat;
import software.wings.beans.TaskType;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.validation.executable.ValidateOnExecution;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.mongodb.morphia.query.Query;

@Singleton
@ValidateOnExecution
@Slf4j
public class DelegateTaskServiceImpl implements DelegateTaskService {
  @Inject private HPersistence persistence;
  @Inject private VersionInfoManager versionInfoManager;
  @Inject private WaitNotifyEngine waitNotifyEngine;
  @Inject private DelegateCallbackRegistry delegateCallbackRegistry;
  @Inject private KryoSerializer kryoSerializer;
  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer referenceFalseKryoSerializer;

  @Getter private Subject<DelegateTaskRetryObserver> retryObserverSubject = new Subject<>();
  @Inject private RemoteObserverInformer remoteObserverInformer;

  @Inject private DelegateMetricsService delegateMetricsService;

  @Inject private DelegateCache delegateCache;

  @Override
  public boolean isTaskTypeSupportedByAllDelegates(String accountId, String taskType) {
    Set<String> supportedTaskTypes = delegateCache.getDelegateSupportedTaskTypes(accountId);
    return supportedTaskTypes.contains(taskType);
  }

  @Override
  public void touchExecutingTasks(String accountId, String delegateId, List<String> delegateTaskIds) {
    // Touch currently executing tasks.
    if (EmptyPredicate.isEmpty(delegateTaskIds)) {
      return;
    }

    log.debug("Updating tasks");

    Query<DelegateTask> delegateTaskQuery = persistence.createQuery(DelegateTask.class)
                                                .filter(DelegateTaskKeys.accountId, accountId)
                                                .field(DelegateTaskKeys.uuid)
                                                .in(delegateTaskIds)
                                                .filter(DelegateTaskKeys.delegateId, delegateId)
                                                .filter(DelegateTaskKeys.status, DelegateTask.Status.STARTED)
                                                .project(DelegateTaskKeys.uuid, true)
                                                .project(DelegateTaskKeys.data_timeout, true);

    // TODO: it seems like mongo 4.2 supports update based on another field. Change this when we fully migrate to it.
    long now = currentTimeMillis();
    try (HIterator<DelegateTask> iterator = new HIterator<>(delegateTaskQuery.fetch())) {
      for (DelegateTask delegateTask : iterator) {
        persistence.update(delegateTask,
            persistence.createUpdateOperations(DelegateTask.class)
                .set(DelegateTaskKeys.expiry, now + delegateTask.getData().getTimeout()));
      }
    }
  }

  @Override
  public void processDelegateResponse(
      String accountId, String delegateId, String taskId, DelegateTaskResponse response) {
    if (response == null) {
      throw new InvalidArgumentsException(Pair.of("args", "response cannot be null"));
    }

    Query<DelegateTask> taskQuery = persistence.createQuery(DelegateTask.class)
                                        .filter(DelegateTaskKeys.accountId, response.getAccountId())
                                        .filter(DelegateTaskKeys.uuid, taskId);

    DelegateTask delegateTask = taskQuery.get();
    copyTaskDataV2ToTaskData(delegateTask);
    if (delegateTask != null) {
      try (AutoLogContext ignore = new TaskLogContext(taskId, delegateTask.getData().getTaskType(),
               TaskType.valueOf(delegateTask.getData().getTaskType()).getTaskGroup().name(), OVERRIDE_ERROR)) {
        if (response.getResponseCode() == ResponseCode.RETRY_ON_OTHER_DELEGATE) {
          RetryDelegate retryDelegate =
              RetryDelegate.builder().delegateId(delegateId).delegateTask(delegateTask).taskQuery(taskQuery).build();

          RetryDelegate delegateTaskRetry =
              retryObserverSubject.fireProcess(DelegateTaskRetryObserver::onPossibleRetry, retryDelegate);

          if (delegateTaskRetry.isRetryPossible()) {
            return;
          }
        }
        log.info("Response received for task: {} from Delegate: {}", taskId, delegateId);
        handleResponse(delegateTask, taskQuery, response);

        retryObserverSubject.fireInform(DelegateTaskRetryObserver::onTaskResponseProcessed, delegateTask, delegateId);
        remoteObserverInformer.sendEvent(ReflectionUtils.getMethod(DelegateTaskRetryObserver.class,
                                             "onTaskResponseProcessed", DelegateTask.class, String.class),
            DelegateTaskServiceImpl.class, delegateTask, delegateId);
      }
    } else {
      log.warn("No delegate task found");
    }
  }

  private void copyTaskDataV2ToTaskData(DelegateTask delegateTask) {
    if (delegateTask != null && delegateTask.getTaskDataV2() != null) {
      TaskDataV2 taskDataV2 = delegateTask.getTaskDataV2();

      TaskData taskData =
          TaskData.builder()
              .data(taskDataV2.getData())
              .taskType(taskDataV2.getTaskType())
              .async(taskDataV2.isAsync())
              .parked(taskDataV2.isParked())
              .parameters(taskDataV2.getParameters())
              .timeout(taskDataV2.getTimeout())
              .expressionFunctorToken(taskDataV2.getExpressionFunctorToken())
              .expressions(taskDataV2.getExpressions())
              .serializationFormat(SerializationFormat.valueOf(taskDataV2.getSerializationFormat().name()))
              .build();
      delegateTask.setData(taskData);
    }
  }

  private String getVersion() {
    return versionInfoManager.getVersionInfo().getVersion();
  }

  @Override
  public void handleResponse(DelegateTask delegateTask, Query<DelegateTask> taskQuery, DelegateTaskResponse response) {
    copyTaskDataV2ToTaskData(delegateTask);

    if (delegateTask.getDriverId() == null) {
      handleInprocResponse(delegateTask, response);
    } else {
      handleDriverResponse(delegateTask, response);
    }

    if (taskQuery != null) {
      persistence.deleteOnServer(taskQuery);
    }

    delegateMetricsService.recordDelegateTaskResponseMetrics(delegateTask, response, DELEGATE_TASK_RESPONSE);
  }

  @Override
  public void handleResponseV2(
      DelegateTask delegateTask, Query<DelegateTask> taskQuery, DelegateTaskResponse response) {
    if (delegateTask.getDriverId() == null) {
      handleInprocResponseV2(delegateTask, response);
    } else {
      handleDriverResponseV2(delegateTask, response);
    }

    if (taskQuery != null) {
      persistence.deleteOnServer(taskQuery);
    }

    delegateMetricsService.recordDelegateTaskResponseMetrics(delegateTask, response, DELEGATE_TASK_RESPONSE);
  }

  @Override
  public void publishTaskProgressResponse(
      String accountId, String driverId, String delegateTaskId, DelegateProgressData responseData) {
    DelegateCallbackService delegateCallbackService = delegateCallbackRegistry.obtainDelegateCallbackService(driverId);
    if (delegateCallbackService == null) {
      return;
    }
    delegateCallbackService.publishTaskProgressResponse(
        delegateTaskId, generateUuid(), kryoSerializer.asDeflatedBytes(responseData));
  }

  @Override
  public Optional<DelegateTask> fetchDelegateTask(String accountId, String taskId) {
    return Optional.ofNullable(persistence.createQuery(DelegateTask.class)
                                   .filter(DelegateTaskKeys.accountId, accountId)
                                   .filter(DelegateTaskKeys.uuid, taskId)
                                   .get());
  }

  @VisibleForTesting
  void handleDriverResponse(DelegateTask delegateTask, DelegateTaskResponse response) {
    if (delegateTask == null || response == null) {
      return;
    }

    try (DelegateDriverLogContext driverLogContext =
             new DelegateDriverLogContext(delegateTask.getDriverId(), OVERRIDE_ERROR);
         TaskLogContext taskLogContext = new TaskLogContext(delegateTask.getUuid(), OVERRIDE_ERROR)) {
      log.debug("Processing task response...");

      DelegateCallbackService delegateCallbackService =
          delegateCallbackRegistry.obtainDelegateCallbackService(delegateTask.getDriverId());
      if (delegateCallbackService == null) {
        log.info(
            "Failed to obtain Delegate callback service for the given task. Skipping processing of task response.");
        return;
      }

      if (delegateTask.getData().isAsync()) {
        delegateCallbackService.publishAsyncTaskResponse(
            delegateTask.getUuid(), kryoSerializer.asDeflatedBytes(response.getResponse()));
      } else {
        delegateCallbackService.publishSyncTaskResponse(
            delegateTask.getUuid(), kryoSerializer.asDeflatedBytes(response.getResponse()));
      }
    } catch (Exception ex) {
      log.error("Failed publishing task response", ex);
    }
  }

  @VisibleForTesting
  void handleDriverResponseV2(DelegateTask delegateTask, DelegateTaskResponse response) {
    if (delegateTask == null || response == null) {
      return;
    }

    try (DelegateDriverLogContext driverLogContext =
             new DelegateDriverLogContext(delegateTask.getDriverId(), OVERRIDE_ERROR);
         TaskLogContext taskLogContext = new TaskLogContext(delegateTask.getUuid(), OVERRIDE_ERROR)) {
      log.debug("Processing task response...");

      DelegateCallbackService delegateCallbackService =
          delegateCallbackRegistry.obtainDelegateCallbackService(delegateTask.getDriverId());
      if (delegateCallbackService == null) {
        log.info(
            "Failed to obtain Delegate callback service for the given task. Skipping processing of task response.");
        return;
      }
      boolean async = delegateTask.getTaskDataV2() != null ? delegateTask.getTaskDataV2().isAsync()
                                                           : delegateTask.getData().isAsync();
      if (async) {
        delegateCallbackService.publishAsyncTaskResponse(
            delegateTask.getUuid(), kryoSerializer.asDeflatedBytes(response.getResponse()));
      } else {
        delegateCallbackService.publishSyncTaskResponse(
            delegateTask.getUuid(), kryoSerializer.asDeflatedBytes(response.getResponse()));
      }
    } catch (Exception ex) {
      log.error("Failed publishing task response", ex);
    }
  }

  private void handleInprocResponse(DelegateTask delegateTask, DelegateTaskResponse response) {
    if (delegateTask.getData().isAsync()) {
      String waitId = delegateTask.getWaitId();
      if (waitId != null) {
        waitNotifyEngine.doneWith(waitId, response.getResponse());
      } else {
        log.error("Async task has no wait ID");
      }
    } else {
      persistence.save(DelegateSyncTaskResponse.builder()
                           .uuid(delegateTask.getUuid())
                           .responseData(kryoSerializer.asDeflatedBytes(response.getResponse()))
                           .build());
    }
  }

  private void handleInprocResponseV2(DelegateTask delegateTask, DelegateTaskResponse response) {
    boolean async = delegateTask.getTaskDataV2() != null ? delegateTask.getTaskDataV2().isAsync()
                                                         : delegateTask.getData().isAsync();
    if (async) {
      String waitId = delegateTask.getWaitId();
      if (waitId != null) {
        waitNotifyEngine.doneWith(waitId, response.getResponse());
      } else {
        log.error("Async task has no wait ID");
      }
    } else {
      persistence.save(DelegateSyncTaskResponse.builder()
                           .uuid(delegateTask.getUuid())
                           .usingKryoWithoutReference(true)
                           .responseData(kryoSerializer.asDeflatedBytes(response.getResponse()))
                           .build());
    }
  }
}
