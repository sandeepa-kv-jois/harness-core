/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.scheduler;

import static io.harness.beans.DelegateTask.Status.ABORTED;
import static io.harness.beans.DelegateTask.Status.PARKED;
import static io.harness.beans.DelegateTask.Status.QUEUED;
import static io.harness.beans.DelegateTask.Status.STARTED;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.delegate.beans.TaskData.DEFAULT_ASYNC_CALL_TIMEOUT;
import static io.harness.delegate.task.mixin.HttpConnectionExecutionCapabilityGenerator.buildHttpConnectionExecutionCapability;
import static io.harness.rule.OwnerRule.ASHISHSANODIA;
import static io.harness.rule.OwnerRule.JENNY;
import static io.harness.rule.OwnerRule.XINGCHI_JIN;

import static software.wings.service.impl.DelegateSelectionLogsServiceImpl.TASK_VALIDATION_FAILED;
import static software.wings.utils.WingsTestConstants.APP_ID;

import static java.lang.String.format;
import static java.util.Arrays.asList;
import static junit.framework.TestCase.assertEquals;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.beans.Cd1SetupFields;
import io.harness.beans.DelegateTask;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.TaskDataV2;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.HttpConnectionExecutionCapability;
import io.harness.delegate.task.http.HttpTaskParameters;
import io.harness.iterator.FailDelegateTaskIterator;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.iterator.ValidationFailedTaskMessageHelper;
import io.harness.mongo.iterator.MongoPersistenceIterator.MongoPersistenceIteratorBuilder;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.Account;
import software.wings.beans.TaskType;
import software.wings.delegatetasks.validation.core.DelegateConnectionResult;
import software.wings.service.impl.DelegateTaskServiceClassicImpl;
import software.wings.service.intfc.AssignDelegateService;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import io.vavr.collection.Stream;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
@PrepareForTest(PersistenceIteratorFactory.class)
@PowerMockIgnore({"javax.security.*", "javax.net.*"})
public class FailDelegateTaskIteratorTest extends WingsBaseTest {
  @Mock PersistenceIteratorFactory persistenceIteratorFactory;
  @InjectMocks @Inject private FailDelegateTaskIterator failDelegateTaskIterator;
  @Mock private AssignDelegateService assignDelegateService;
  @InjectMocks @Inject private DelegateTaskServiceClassicImpl delegateTaskServiceClassic;
  @Inject private HPersistence persistence;
  @Inject private ValidationFailedTaskMessageHelper validationFailedTaskMessageHelper;

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testRegisterIterators() {
    ArgumentCaptor<MongoPersistenceIteratorBuilder> captor =
        ArgumentCaptor.forClass(MongoPersistenceIteratorBuilder.class);
    failDelegateTaskIterator.createAndStartIterator(PersistenceIteratorFactory.PumpExecutorOptions.builder()
                                                        .name("DelegateTaskFail")
                                                        .poolSize(2)
                                                        .interval(Duration.ofSeconds(30))
                                                        .build(),
        Duration.ofSeconds(30));
    verify(persistenceIteratorFactory, times(1))
        .createPumpIteratorWithDedicatedThreadPool(any(), eq(FailDelegateTaskIterator.class), captor.capture());
    MongoPersistenceIteratorBuilder mongoPersistenceIteratorBuilder = captor.getValue();
    assertThat(mongoPersistenceIteratorBuilder).isNotNull();
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testMarkLongQueuedTasksWithQueuedStatus() {
    Account account = new Account();
    account.setUuid(generateUuid());
    persistence.save(account);
    DelegateTask delegateTask =
        DelegateTask.builder()
            .accountId(account.getUuid())
            .status(QUEUED)
            .waitId(generateUuid())
            .expiry(System.currentTimeMillis() - 10)
            .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, APP_ID)
            .eligibleToExecuteDelegateIds(new LinkedList<>(Arrays.asList("del1", "del2", "del3")))
            .data(TaskData.builder()
                      .async(true)
                      .taskType(TaskType.HTTP.name())
                      .parameters(new Object[] {HttpTaskParameters.builder().url("https://www.google.com").build()})
                      .timeout(1)
                      .build())
            .validationCompleteDelegateIds(ImmutableSet.of("del1", "del2"))
            .build();
    persistence.save(delegateTask);
    failDelegateTaskIterator.markLongQueuedTasksAsFailed(delegateTask);
    assertThat(persistence.createQuery(DelegateTask.class).count()).isEqualTo(0);
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testMarkLongQueuedTasksWithTaskDataV2AndQueuedStatus() {
    Account account = new Account();
    account.setUuid(generateUuid());
    persistence.save(account);
    DelegateTask delegateTask =
        DelegateTask.builder()
            .accountId(account.getUuid())
            .status(QUEUED)
            .waitId(generateUuid())
            .expiry(System.currentTimeMillis() - 10)
            .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, APP_ID)
            .eligibleToExecuteDelegateIds(new LinkedList<>(Arrays.asList("del1", "del2", "del3")))
            .taskDataV2(
                TaskDataV2.builder()
                    .async(true)
                    .taskType(TaskType.HTTP.name())
                    .parameters(new Object[] {HttpTaskParameters.builder().url("https://www.google.com").build()})
                    .timeout(1)
                    .build())
            .validationCompleteDelegateIds(ImmutableSet.of("del1", "del2"))
            .build();
    persistence.save(delegateTask);
    failDelegateTaskIterator.markLongQueuedTasksAsFailed(delegateTask);
    assertThat(persistence.createQuery(DelegateTask.class).count()).isEqualTo(0);
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testMarkLongQueuedTasksWithAbortedStatus() {
    Account account = new Account();
    account.setUuid(generateUuid());
    persistence.save(account);
    DelegateTask delegateTask =
        DelegateTask.builder()
            .accountId(account.getUuid())
            .status(ABORTED)
            .waitId(generateUuid())
            .expiry(System.currentTimeMillis() - 10)
            .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, APP_ID)
            .eligibleToExecuteDelegateIds(new LinkedList<>(Arrays.asList("del1", "del2", "del3")))
            .data(TaskData.builder()
                      .async(true)
                      .taskType(TaskType.HTTP.name())
                      .parameters(new Object[] {HttpTaskParameters.builder().url("https://www.google.com").build()})
                      .timeout(1)
                      .build())
            .validationCompleteDelegateIds(ImmutableSet.of("del1", "del2"))
            .build();
    persistence.save(delegateTask);
    failDelegateTaskIterator.markLongQueuedTasksAsFailed(delegateTask);
    assertThat(persistence.createQuery(DelegateTask.class).get()).isNull();
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testMarkLongQueuedTasksWithTaskDataV2AndAbortedStatus() {
    Account account = new Account();
    account.setUuid(generateUuid());
    persistence.save(account);
    DelegateTask delegateTask =
        DelegateTask.builder()
            .accountId(account.getUuid())
            .status(ABORTED)
            .waitId(generateUuid())
            .expiry(System.currentTimeMillis() - 10)
            .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, APP_ID)
            .eligibleToExecuteDelegateIds(new LinkedList<>(Arrays.asList("del1", "del2", "del3")))
            .taskDataV2(
                TaskDataV2.builder()
                    .async(true)
                    .taskType(TaskType.HTTP.name())
                    .parameters(new Object[] {HttpTaskParameters.builder().url("https://www.google.com").build()})
                    .timeout(1)
                    .build())
            .validationCompleteDelegateIds(ImmutableSet.of("del1", "del2"))
            .build();
    persistence.save(delegateTask);
    failDelegateTaskIterator.markLongQueuedTasksAsFailed(delegateTask);
    assertThat(persistence.createQuery(DelegateTask.class).get()).isNull();
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testMarkLongQueuedTasksWithParkedStatus() {
    Account account = new Account();
    account.setUuid(generateUuid());
    persistence.save(account);
    DelegateTask delegateTask =
        DelegateTask.builder()
            .accountId(account.getUuid())
            .status(PARKED)
            .waitId(generateUuid())
            .expiry(System.currentTimeMillis() - 10)
            .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, APP_ID)
            .eligibleToExecuteDelegateIds(new LinkedList<>(Arrays.asList("del1", "del2", "del3")))
            .data(TaskData.builder()
                      .async(true)
                      .taskType(TaskType.HTTP.name())
                      .parameters(new Object[] {HttpTaskParameters.builder().url("https://www.google.com").build()})
                      .timeout(1)
                      .build())
            .validationCompleteDelegateIds(ImmutableSet.of("del1", "del2"))
            .build();
    persistence.save(delegateTask);
    failDelegateTaskIterator.markLongQueuedTasksAsFailed(delegateTask);
    assertThat(persistence.createQuery(DelegateTask.class).get()).isNull();
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testMarkLongQueuedTasksWithTaskDataV2AndParkedStatus() {
    Account account = new Account();
    account.setUuid(generateUuid());
    persistence.save(account);
    DelegateTask delegateTask =
        DelegateTask.builder()
            .accountId(account.getUuid())
            .status(PARKED)
            .waitId(generateUuid())
            .expiry(System.currentTimeMillis() - 10)
            .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, APP_ID)
            .eligibleToExecuteDelegateIds(new LinkedList<>(Arrays.asList("del1", "del2", "del3")))
            .taskDataV2(
                TaskDataV2.builder()
                    .async(true)
                    .taskType(TaskType.HTTP.name())
                    .parameters(new Object[] {HttpTaskParameters.builder().url("https://www.google.com").build()})
                    .timeout(1)
                    .build())
            .validationCompleteDelegateIds(ImmutableSet.of("del1", "del2"))
            .build();
    persistence.save(delegateTask);
    failDelegateTaskIterator.markLongQueuedTasksAsFailed(delegateTask);
    assertThat(persistence.createQuery(DelegateTask.class).get()).isNull();
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testMarkTimedOutStartedTasksAsFailed() {
    Account account = new Account();
    account.setUuid(generateUuid());
    persistence.save(account);
    DelegateTask delegateTask =
        DelegateTask.builder()
            .accountId(account.getUuid())
            .status(STARTED)
            .waitId(generateUuid())
            .expiry(System.currentTimeMillis() - 10)
            .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, APP_ID)
            .eligibleToExecuteDelegateIds(new LinkedList<>(Arrays.asList("del1", "del2", "del3")))
            .data(TaskData.builder()
                      .async(true)
                      .taskType(TaskType.HTTP.name())
                      .parameters(new Object[] {HttpTaskParameters.builder().url("https://www.google.com").build()})
                      .timeout(1)
                      .build())
            .validationCompleteDelegateIds(ImmutableSet.of("del1", "del2"))
            .build();
    persistence.save(delegateTask);
    failDelegateTaskIterator.markTimedOutTasksAsFailed(delegateTask);
    assertThat(persistence.createQuery(DelegateTask.class).count()).isEqualTo(0);
  }

  @Test
  @Owner(developers = ASHISHSANODIA)
  @Category(UnitTests.class)
  public void testMarkTimedOutStartedTasksWithAtaskDataV2AsFailed() {
    Account account = new Account();
    account.setUuid(generateUuid());
    persistence.save(account);
    DelegateTask delegateTask =
        DelegateTask.builder()
            .accountId(account.getUuid())
            .status(STARTED)
            .waitId(generateUuid())
            .expiry(System.currentTimeMillis() - 10)
            .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, APP_ID)
            .eligibleToExecuteDelegateIds(new LinkedList<>(Arrays.asList("del1", "del2", "del3")))
            .taskDataV2(
                TaskDataV2.builder()
                    .async(true)
                    .taskType(TaskType.HTTP.name())
                    .parameters(new Object[] {HttpTaskParameters.builder().url("https://www.google.com").build()})
                    .timeout(1)
                    .build())
            .validationCompleteDelegateIds(ImmutableSet.of("del1", "del2"))
            .build();
    persistence.save(delegateTask);
    failDelegateTaskIterator.markTimedOutTasksAsFailed(delegateTask);
    assertThat(persistence.createQuery(DelegateTask.class).count()).isEqualTo(0);
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testHandle_testEndTasksWithCorruptedRecord() {
    Account account = new Account();
    account.setUuid(generateUuid());
    persistence.save(account);
    DelegateTask delegateTask =
        DelegateTask.builder()
            .accountId(account.getUuid())
            .waitId(generateUuid())
            .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, APP_ID)
            .eligibleToExecuteDelegateIds(new LinkedList<>(Arrays.asList("del1", "del2", "del3")))
            .expiry(System.currentTimeMillis() - 10)
            .data(TaskData.builder()
                      .async(true)
                      .taskType(TaskType.HTTP.name())
                      .parameters(new Object[] {HttpTaskParameters.builder().url("https://www.google.com").build()})
                      .timeout(1)
                      .build())
            .validationCompleteDelegateIds(ImmutableSet.of("del1", "del2"))
            .build();
    persistence.save(delegateTask);
    failDelegateTaskIterator.endTasks(asList(delegateTask.getUuid()));
    assertThat(persistence.createQuery(DelegateTask.class).get()).isNull();
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testHandle_withAllDelegatesValidationCompleted() {
    Account account = new Account();
    account.setUuid(generateUuid());
    persistence.save(account);
    DelegateTask delegateTask =
        DelegateTask.builder()
            .accountId(account.getUuid())
            .waitId(generateUuid())
            .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, APP_ID)
            .eligibleToExecuteDelegateIds(new LinkedList<>(Arrays.asList("del1", "del2", "del3")))
            .data(TaskData.builder()
                      .async(true)
                      .taskType(TaskType.HTTP.name())
                      .parameters(new Object[] {HttpTaskParameters.builder().url("https://www.google.com").build()})
                      .timeout(DEFAULT_ASYNC_CALL_TIMEOUT)
                      .build())
            .validationCompleteDelegateIds(ImmutableSet.of("del1", "del2", "del3"))
            .status(STARTED)
            .build();
    persistence.save(delegateTask);
    failDelegateTaskIterator.failValidationCompletedQueuedTask(delegateTask);
    assertThat(persistence.createQuery(DelegateTask.class).get()).isNotNull();
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testHandle_withDelegatesPendingValidation() {
    Account account = new Account();
    account.setUuid(generateUuid());
    persistence.save(account);
    DelegateTask delegateTask =
        DelegateTask.builder()
            .accountId(account.getUuid())
            .waitId(generateUuid())
            .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, APP_ID)
            .eligibleToExecuteDelegateIds(new LinkedList<>(Arrays.asList("del1", "del2", "del3")))
            .status(QUEUED)
            .data(TaskData.builder()
                      .async(true)
                      .taskType(TaskType.HTTP.name())
                      .parameters(new Object[] {HttpTaskParameters.builder().url("https://www.google.com").build()})
                      .timeout(DEFAULT_ASYNC_CALL_TIMEOUT)
                      .build())
            .validationCompleteDelegateIds(ImmutableSet.of("del1", "del2"))
            .build();
    persistence.save(delegateTask);
    failDelegateTaskIterator.failValidationCompletedQueuedTask(delegateTask);
    assertThat(persistence.createQuery(DelegateTask.class).get()).isNotNull();
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testHandle_withAllDelegatesValidationCompleted_ButFoundConnectedWhitelistedOnes() {
    Account account = new Account();
    account.setUuid(generateUuid());
    persistence.save(account);
    long validationStarted = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(3);
    DelegateTask delegateTask =
        DelegateTask.builder()
            .accountId(account.getUuid())
            .status(QUEUED)
            .waitId(generateUuid())
            .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, APP_ID)
            .validationStartedAt(validationStarted)
            .eligibleToExecuteDelegateIds(new LinkedList<>(Arrays.asList("del1", "del2", "del3")))
            .data(TaskData.builder()
                      .async(true)
                      .taskType(TaskType.HTTP.name())
                      .parameters(new Object[] {HttpTaskParameters.builder().url("https://www.google.com").build()})
                      .timeout(DEFAULT_ASYNC_CALL_TIMEOUT)
                      .build())
            .validationCompleteDelegateIds(ImmutableSet.of("del1", "del2"))
            .build();
    persistence.save(delegateTask);
    when(assignDelegateService.connectedWhitelistedDelegates(delegateTask)).thenReturn(Arrays.asList("del1"));
    failDelegateTaskIterator.failValidationCompletedQueuedTask(delegateTask);
    assertThat(persistence.createQuery(DelegateTask.class).get()).isNotNull();
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testHandle_withAllDelegateValidationCompleted() {
    Account account = new Account();
    account.setUuid(generateUuid());
    persistence.save(account);
    long validationStarted = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(3);
    DelegateTask delegateTask =
        DelegateTask.builder()
            .accountId(account.getUuid())
            .waitId(generateUuid())
            .status(QUEUED)
            .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, APP_ID)
            .validationStartedAt(validationStarted)
            .eligibleToExecuteDelegateIds(new LinkedList<>(Arrays.asList("del1", "del2", "del3")))
            .data(TaskData.builder()
                      .async(true)
                      .taskType(TaskType.HTTP.name())
                      .parameters(new Object[] {HttpTaskParameters.builder().url("https://www.google.com").build()})
                      .timeout(1)
                      .build())
            .validationCompleteDelegateIds(ImmutableSet.of("del1", "del2", "del3"))
            .build();
    persistence.save(delegateTask);
    failDelegateTaskIterator.failValidationCompletedQueuedTask(delegateTask);
    assertThat(persistence.createQuery(DelegateTask.class).get()).isNull();
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testHandle_withAllDelegateValidationCompleted_ButFoundConnectedWhitelistedOnes() {
    Account account = new Account();
    account.setUuid(generateUuid());
    persistence.save(account);
    long validationStarted = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(3);
    DelegateTask delegateTask =
        DelegateTask.builder()
            .accountId(account.getUuid())
            .waitId(generateUuid())
            .status(QUEUED)
            .validationStartedAt(validationStarted)
            .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, APP_ID)
            .eligibleToExecuteDelegateIds(new LinkedList<>(Arrays.asList("del1", "del2", "del3")))
            .data(TaskData.builder()
                      .async(true)
                      .taskType(TaskType.HTTP.name())
                      .parameters(new Object[] {HttpTaskParameters.builder().url("https://www.google.com").build()})
                      .timeout(DEFAULT_ASYNC_CALL_TIMEOUT)
                      .build())
            .validationCompleteDelegateIds(ImmutableSet.of("del1", "del2"))
            .build();
    persistence.save(delegateTask);
    when(assignDelegateService.connectedWhitelistedDelegates(delegateTask)).thenReturn(Arrays.asList("del1"));
    failDelegateTaskIterator.failValidationCompletedQueuedTask(delegateTask);
    assertThat(persistence.createQuery(DelegateTask.class).get()).isNotNull();
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testHandle_withFewDelegatesCompletedValidation() {
    Account account = new Account();
    account.setUuid(generateUuid());
    persistence.save(account);
    long validationStarted = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(3);
    DelegateTask delegateTask =
        DelegateTask.builder()
            .accountId(account.getUuid())
            .waitId(generateUuid())
            .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, APP_ID)
            .status(QUEUED)
            .eligibleToExecuteDelegateIds(new LinkedList<>(Arrays.asList("del1", "del2", "del3")))
            .data(TaskData.builder()
                      .async(true)
                      .taskType(TaskType.HTTP.name())
                      .parameters(new Object[] {HttpTaskParameters.builder().url("https://www.google.com").build()})
                      .timeout(DEFAULT_ASYNC_CALL_TIMEOUT)
                      .build())
            .validationCompleteDelegateIds(ImmutableSet.of("del1", "del2"))
            .validationStartedAt(validationStarted)
            .build();
    persistence.save(delegateTask);
    failDelegateTaskIterator.failValidationCompletedQueuedTask(delegateTask);
    assertThat(persistence.createQuery(DelegateTask.class).get()).isNotNull();
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testHandle_verifyValidationTimeOut() {
    Account account = new Account();
    account.setUuid(generateUuid());
    persistence.save(account);
    long validationStarted = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(2) + TimeUnit.SECONDS.toMillis(5);
    DelegateTask delegateTask =
        DelegateTask.builder()
            .accountId(account.getUuid())
            .waitId(generateUuid())
            .status(QUEUED)
            .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, APP_ID)
            .validationStartedAt(validationStarted)
            .eligibleToExecuteDelegateIds(new LinkedList<>(Arrays.asList("del1", "del2", "del3")))
            .data(TaskData.builder()
                      .async(true)
                      .taskType(TaskType.HTTP.name())
                      .parameters(new Object[] {HttpTaskParameters.builder().url("https://www.google.com").build()})
                      .timeout(1)
                      .build())
            .validationCompleteDelegateIds(ImmutableSet.of("del1", "del2", "del3"))
            .build();
    persistence.save(delegateTask);
    failDelegateTaskIterator.failValidationCompletedQueuedTask(delegateTask);
    assertThat(persistence.createQuery(DelegateTask.class).get()).isNotNull();
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testHandle_verifyNoRaceBetweenAcquireTaskAndFailIterator() {
    Account account = new Account();
    account.setUuid(generateUuid());
    persistence.save(account);
    long validationStarted = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(2);
    DelegateTask delegateTask =
        DelegateTask.builder()
            .accountId(account.getUuid())
            .waitId(generateUuid())
            .status(QUEUED)
            .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, APP_ID)
            .validationStartedAt(validationStarted)
            .eligibleToExecuteDelegateIds(new LinkedList<>(Arrays.asList("del1", "del2", "del3")))
            .data(TaskData.builder()
                      .async(true)
                      .taskType(TaskType.HTTP.name())
                      .parameters(new Object[] {HttpTaskParameters.builder().url("https://www.google.com").build()})
                      .timeout(1)
                      .build())
            .validationCompleteDelegateIds(ImmutableSet.of("del1", "del2", "del3"))
            .build();
    persistence.save(delegateTask);
    final Thread iteratorThread =
        new Thread(() -> { failDelegateTaskIterator.failValidationCompletedQueuedTask(delegateTask); });
    final Thread acquireThread = new Thread(() -> {
      delegateTask.setStatus(STARTED);
      persistence.save(delegateTask);
    });
    acquireThread.start();
    iteratorThread.start();
    assertThat(persistence.createQuery(DelegateTask.class).get()).isNotNull();
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testHandle_withAllDelegateValidationCompleted_ButNotTimedOut() {
    Account account = new Account();
    account.setUuid(generateUuid());
    persistence.save(account);
    long validationStarted = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(3);
    long taskExpiry = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(5);
    HttpConnectionExecutionCapability matchingExecutionCapability =
        buildHttpConnectionExecutionCapability("https//aws.amazon.com", null);
    DelegateTask delegateTask =
        DelegateTask.builder()
            .accountId(account.getUuid())
            .waitId(generateUuid())
            .status(QUEUED)
            .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, APP_ID)
            .expiry(taskExpiry)
            .validationStartedAt(validationStarted)
            .eligibleToExecuteDelegateIds(new LinkedList<>(Arrays.asList("del1", "del2", "del3")))
            .executionCapabilities(Arrays.asList(matchingExecutionCapability))
            .data(TaskData.builder()
                      .async(true)
                      .taskType(TaskType.HTTP.name())
                      .parameters(new Object[] {HttpTaskParameters.builder().url("https://www.amazon.com").build()})
                      .timeout(1)
                      .build())
            .validationCompleteDelegateIds(ImmutableSet.of("del1", "del2", "del3"))
            .build();
    persistence.save(delegateTask);
    failDelegateTaskIterator.handle(delegateTask);
    assertThat(persistence.createQuery(DelegateTask.class).get()).isNull();
  }

  @Test
  @Owner(developers = XINGCHI_JIN)
  @Category(UnitTests.class)
  public void testGenerateValidationErrorMessage() {
    long validationStarted = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(3);
    long taskExpiry = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(5);
    String accountId = generateUuid();

    final List<String> urls = Arrays.asList("https://del1-has", "https://del2-has");
    final List<String> urlsWithNoDelegateCanExecute = Arrays.asList("http://none-has", "http://none-has-1");
    List<ExecutionCapability> capabilities =
        urls.stream().map(url -> buildHttpConnectionExecutionCapability(url, null)).collect(Collectors.toList());
    List<ExecutionCapability> noneCapabilities = urlsWithNoDelegateCanExecute.stream()
                                                     .map(url -> buildHttpConnectionExecutionCapability(url, null))
                                                     .collect(Collectors.toList());
    List<ExecutionCapability> allCapabilities = Stream.concat(capabilities, noneCapabilities).toJavaList();
    Set<String> eligibleDelegateIds = ImmutableSet.of("del1", "del2");
    Set<String> allDelegateIds = ImmutableSet.of("del1", "del2", "del3");

    DelegateTask delegateTask =
        DelegateTask.builder()
            .accountId(accountId)
            .waitId(generateUuid())
            .status(QUEUED)
            .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, APP_ID)
            .expiry(taskExpiry)
            .validationStartedAt(validationStarted)
            .eligibleToExecuteDelegateIds(new LinkedList<>(eligibleDelegateIds))
            .executionCapabilities(allCapabilities)
            .data(TaskData.builder()
                      .async(true)
                      .taskType(TaskType.HTTP.name())
                      .parameters(urls.stream().map(url -> HttpTaskParameters.builder().url(url).build()).toArray())
                      .timeout(1)
                      .build())
            .validationCompleteDelegateIds(eligibleDelegateIds)
            .build();
    allDelegateIds.forEach(id -> {
      switch (id) {
        case "del1":
          DelegateConnectionResult connectionResult1 = DelegateConnectionResult.builder()
                                                           .accountId(accountId)
                                                           .delegateId(id)
                                                           .criteria(capabilities.get(0).fetchCapabilityBasis())
                                                           .validated(true)
                                                           .build();
          persistence.save(connectionResult1);
          break;
        case "del2":
          DelegateConnectionResult connectionResult2 = DelegateConnectionResult.builder()
                                                           .accountId(accountId)
                                                           .delegateId(id)
                                                           .criteria(capabilities.get(1).fetchCapabilityBasis())
                                                           .validated(true)
                                                           .build();
          persistence.save(connectionResult2);
          connectionResult2 = DelegateConnectionResult.builder()
                                  .accountId(accountId)
                                  .delegateId(id)
                                  .criteria(capabilities.get(0).fetchCapabilityBasis())
                                  .validated(false)
                                  .build();
          persistence.save(connectionResult2);
          break;
        case "del3":
          capabilities.forEach(capability
              -> persistence.save(DelegateConnectionResult.builder()
                                      .accountId(accountId)
                                      .delegateId(id)
                                      .criteria(capability.fetchCapabilityBasis())
                                      .validated(true)
                                      .build()));
          break;
        default:
          break;
      }
    });
    final String errorMsg = validationFailedTaskMessageHelper.generateValidationError(delegateTask);
    final String expectedMsg = format("%s [ %s ]", TASK_VALIDATION_FAILED,
        noneCapabilities.stream()
            .map(ExecutionCapability::fetchCapabilityBasis)
            .sorted(Collections.reverseOrder())
            .collect(Collectors.joining(", ")));
    assertEquals(expectedMsg, errorMsg);
  }
}
