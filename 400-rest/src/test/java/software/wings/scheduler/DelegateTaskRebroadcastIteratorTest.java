/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.scheduler;
import static io.harness.beans.FeatureName.DELEGATE_TASK_REBROADCAST_ITERATOR;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.JENNY;

import static software.wings.utils.WingsTestConstants.DELEGATE_ID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.beans.DelegateTask;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.TaskData;
import io.harness.ff.FeatureFlagService;
import io.harness.iterator.DelegateTaskRebroadcastIterator;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.mongo.iterator.MongoPersistenceIterator.MongoPersistenceIteratorBuilder;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;
import io.harness.version.VersionInfoManager;

import software.wings.WingsBaseTest;
import software.wings.app.DelegateQueueTask;
import software.wings.beans.Account;
import software.wings.beans.DelegateTaskBroadcast;
import software.wings.beans.TaskType;
import software.wings.service.impl.DelegateTaskBroadcastHelper;
import software.wings.service.intfc.AssignDelegateService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.DelegateTaskServiceClassic;

import com.google.inject.Inject;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import org.atmosphere.cpr.Broadcaster;
import org.atmosphere.cpr.BroadcasterFactory;
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
public class DelegateTaskRebroadcastIteratorTest extends WingsBaseTest {
  @Mock PersistenceIteratorFactory persistenceIteratorFactory;
  @InjectMocks @Inject private DelegateTaskRebroadcastIterator delegateTaskRebroadcastIterator;
  @Mock private AssignDelegateService assignDelegateService;
  @Inject private HPersistence persistence;

  @InjectMocks @Inject private DelegateTaskBroadcastHelper broadcastHelper;
  @Mock private FeatureFlagService featureFlagService;
  @Mock private DelegateTaskServiceClassic delegateTaskServiceClassic;
  @Mock private DelegateService delegateService;
  @InjectMocks @Inject DelegateQueueTask delegateQueueTask;
  @Inject private BroadcasterFactory broadcasterFactory;

  @Inject private VersionInfoManager versionInfoManager;

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testRegisterIterators() {
    ArgumentCaptor<MongoPersistenceIteratorBuilder> captor =
        ArgumentCaptor.forClass(MongoPersistenceIteratorBuilder.class);
    delegateTaskRebroadcastIterator.registerIterators(1);
    verify(persistenceIteratorFactory, times(1))
        .createPumpIteratorWithDedicatedThreadPool(any(), eq(DelegateTaskRebroadcastIterator.class), captor.capture());
    MongoPersistenceIteratorBuilder mongoPersistenceIteratorBuilder = captor.getValue();
    assertThat(mongoPersistenceIteratorBuilder).isNotNull();
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testHandle_rebroadcastUnassignedTasks_sync() {
    Account account = new Account();
    account.setUuid(generateUuid());
    persistence.save(account);
    DelegateTask delegateTask = DelegateTask.builder()
                                    .accountId(account.getUuid())
                                    .version(versionInfoManager.getVersionInfo().getVersion())
                                    .status(DelegateTask.Status.QUEUED)
                                    .expiry(System.currentTimeMillis() + 60000)
                                    .data(TaskData.builder().taskType(TaskType.HTTP.name()).async(false).build())
                                    .build();
    Broadcaster broadcaster = mock(Broadcaster.class);
    when(broadcasterFactory.lookup(anyString(), eq(true))).thenReturn(broadcaster);
    delegateTask.setBroadcastCount(0);
    delegateTask.setNextBroadcast(System.currentTimeMillis());
    delegateTask.setEligibleToExecuteDelegateIds(new LinkedList<>(Arrays.asList(DELEGATE_ID)));
    persistence.save(delegateTask);
    when(delegateService.checkDelegateConnected(anyString(), anyString())).thenReturn(true);
    when(featureFlagService.isEnabled(DELEGATE_TASK_REBROADCAST_ITERATOR, account.getUuid())).thenReturn(true);
    delegateTaskRebroadcastIterator.handle(account);

    ArgumentCaptor<DelegateTaskBroadcast> argumentCaptor = ArgumentCaptor.forClass(DelegateTaskBroadcast.class);
    verify(broadcaster, times(1)).broadcast(argumentCaptor.capture());

    DelegateTaskBroadcast delegateTaskBroadcast = argumentCaptor.getValue();
    assertThat(delegateTaskBroadcast).isNotNull();
    assertThat(delegateTaskBroadcast.getVersion()).isEqualTo(delegateTask.getVersion());
    assertThat(delegateTaskBroadcast.getAccountId()).isEqualTo(delegateTask.getAccountId());
    assertThat(delegateTaskBroadcast.getTaskId()).isEqualTo(delegateTask.getUuid());
    assertThat(delegateTaskBroadcast.getBroadcastToDelegatesIds()).isNotEmpty();
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testHandle_rebroadcastUnassignedTasks_async() {
    Account account = new Account();
    account.setUuid(generateUuid());
    persistence.save(account);
    DelegateTask delegateTask = DelegateTask.builder()
                                    .accountId(account.getUuid())
                                    .version(versionInfoManager.getVersionInfo().getVersion())
                                    .status(DelegateTask.Status.QUEUED)
                                    .expiry(System.currentTimeMillis() + 60000)
                                    .data(TaskData.builder().taskType(TaskType.HTTP.name()).async(true).build())
                                    .build();
    Broadcaster broadcaster = mock(Broadcaster.class);
    when(broadcasterFactory.lookup(anyString(), eq(true))).thenReturn(broadcaster);
    delegateTask.setBroadcastCount(0);
    delegateTask.setNextBroadcast(System.currentTimeMillis());
    delegateTask.setEligibleToExecuteDelegateIds(new LinkedList<>(Arrays.asList(DELEGATE_ID)));
    persistence.save(delegateTask);
    when(delegateService.checkDelegateConnected(anyString(), anyString())).thenReturn(true);
    when(featureFlagService.isEnabled(DELEGATE_TASK_REBROADCAST_ITERATOR, account.getUuid())).thenReturn(true);
    delegateTaskRebroadcastIterator.handle(account);

    ArgumentCaptor<DelegateTaskBroadcast> argumentCaptor = ArgumentCaptor.forClass(DelegateTaskBroadcast.class);
    verify(broadcaster, times(1)).broadcast(argumentCaptor.capture());

    DelegateTaskBroadcast delegateTaskBroadcast = argumentCaptor.getValue();
    assertThat(delegateTaskBroadcast).isNotNull();
    assertThat(delegateTaskBroadcast.getVersion()).isEqualTo(delegateTask.getVersion());
    assertThat(delegateTaskBroadcast.getAccountId()).isEqualTo(delegateTask.getAccountId());
    assertThat(delegateTaskBroadcast.getTaskId()).isEqualTo(delegateTask.getUuid());
    assertThat(delegateTaskBroadcast.getBroadcastToDelegatesIds()).isNotEmpty();
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testHandle_updateRebroadcastInterval() {
    Account account = new Account();
    account.setUuid(generateUuid());
    persistence.save(account);
    DelegateTask delegateTask = DelegateTask.builder()
                                    .accountId(account.getUuid())
                                    .version(versionInfoManager.getVersionInfo().getVersion())
                                    .status(DelegateTask.Status.QUEUED)
                                    .expiry(System.currentTimeMillis() + 60000)
                                    .data(TaskData.builder().taskType(TaskType.HTTP.name()).async(true).build())
                                    .build();
    Broadcaster broadcaster = mock(Broadcaster.class);
    when(broadcasterFactory.lookup(anyString(), eq(true))).thenReturn(broadcaster);
    delegateTask.setBroadcastCount(0);
    delegateTask.setNextBroadcast(System.currentTimeMillis());
    delegateTask.setEligibleToExecuteDelegateIds(
        new LinkedList<>(Arrays.asList(DELEGATE_ID, "d1", "d2", "d3", "d4", "d5", "d6", "d7", "d8", "d9")));
    delegateTask.setAlreadyTriedDelegates(Collections.singleton(DELEGATE_ID));
    persistence.save(delegateTask);
    when(delegateService.checkDelegateConnected(anyString(), anyString())).thenReturn(true);
    when(featureFlagService.isEnabled(DELEGATE_TASK_REBROADCAST_ITERATOR, account.getUuid())).thenReturn(true);
    delegateTaskRebroadcastIterator.handle(account);

    ArgumentCaptor<DelegateTaskBroadcast> argumentCaptor = ArgumentCaptor.forClass(DelegateTaskBroadcast.class);
    verify(broadcaster, times(1)).broadcast(argumentCaptor.capture());

    DelegateTaskBroadcast delegateTaskBroadcast = argumentCaptor.getValue();
    assertThat(delegateTaskBroadcast).isNotNull();
    assertThat(delegateTaskBroadcast.getVersion()).isEqualTo(delegateTask.getVersion());
    assertThat(delegateTaskBroadcast.getAccountId()).isEqualTo(delegateTask.getAccountId());
    assertThat(delegateTaskBroadcast.getTaskId()).isEqualTo(delegateTask.getUuid());
    assertThat(delegateTaskBroadcast.getBroadcastToDelegatesIds()).isNotEmpty();

    DelegateTask task = persistence.get(DelegateTask.class, delegateTask.getUuid());
    assertThat(task.getBroadcastRound()).isEqualTo(0);
    assertThat(task.getAlreadyTriedDelegates().size()).isEqualTo(10);
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testHandle_rebroadcastRoundWithOneEligibleDelegate() {
    Account account = new Account();
    account.setUuid(generateUuid());
    persistence.save(account);
    DelegateTask delegateTask = DelegateTask.builder()
                                    .accountId(account.getUuid())
                                    .version(versionInfoManager.getVersionInfo().getVersion())
                                    .status(DelegateTask.Status.QUEUED)
                                    .expiry(System.currentTimeMillis() + 60000)
                                    .data(TaskData.builder().taskType(TaskType.HTTP.name()).async(true).build())
                                    .build();
    Broadcaster broadcaster = mock(Broadcaster.class);
    when(broadcasterFactory.lookup(anyString(), eq(true))).thenReturn(broadcaster);
    delegateTask.setBroadcastCount(0);
    delegateTask.setNextBroadcast(System.currentTimeMillis());
    delegateTask.setEligibleToExecuteDelegateIds(new LinkedList<>(Collections.singletonList(DELEGATE_ID)));
    persistence.save(delegateTask);
    when(delegateService.checkDelegateConnected(anyString(), anyString())).thenReturn(true);
    when(featureFlagService.isEnabled(DELEGATE_TASK_REBROADCAST_ITERATOR, account.getUuid())).thenReturn(true);
    delegateTaskRebroadcastIterator.handle(account);
    DelegateTask task = persistence.get(DelegateTask.class, delegateTask.getUuid());
    // verify broadcast count and broadcast round count got updated
    assertThat(task.getBroadcastCount()).isEqualTo(1);
    assertThat(task.getBroadcastRound()).isEqualTo(0);
    // verify broadcast
    ArgumentCaptor<DelegateTaskBroadcast> argumentCaptor = ArgumentCaptor.forClass(DelegateTaskBroadcast.class);
    verify(broadcaster, times(1)).broadcast(argumentCaptor.capture());
    DelegateTaskBroadcast delegateTaskBroadcast = argumentCaptor.getValue();
    assertThat(delegateTaskBroadcast).isNotNull();
    assertThat(delegateTaskBroadcast.getVersion()).isEqualTo(delegateTask.getVersion());
    assertThat(delegateTaskBroadcast.getAccountId()).isEqualTo(delegateTask.getAccountId());
    assertThat(delegateTaskBroadcast.getTaskId()).isEqualTo(delegateTask.getUuid());
    assertThat(delegateTaskBroadcast.getBroadcastToDelegatesIds()).isNotEmpty();

    task.setNextBroadcast(System.currentTimeMillis());
    persistence.save(task);
    delegateTaskRebroadcastIterator.handle(account);
    // after first round of broadcasting
    DelegateTask taskAfterBroadcast1 = persistence.get(DelegateTask.class, delegateTask.getUuid());
    assertThat(taskAfterBroadcast1.getBroadcastCount()).isEqualTo(2);
    assertThat(taskAfterBroadcast1.getBroadcastRound()).isEqualTo(1);

    taskAfterBroadcast1.setNextBroadcast(System.currentTimeMillis());
    persistence.save(taskAfterBroadcast1);
    delegateTaskRebroadcastIterator.handle(account);
    // after second round of broadcasting
    DelegateTask taskAfterBroadcast2 = persistence.get(DelegateTask.class, delegateTask.getUuid());
    assertThat(taskAfterBroadcast2.getBroadcastCount()).isEqualTo(3);
    assertThat(taskAfterBroadcast2.getBroadcastRound()).isEqualTo(2);
    // verify task got broadcasted
    DelegateTaskBroadcast delegateTaskBroadcast2 = argumentCaptor.getValue();
    assertThat(delegateTaskBroadcast2).isNotNull();
    assertThat(delegateTaskBroadcast2.getTaskId()).isEqualTo(delegateTask.getUuid());
    assertThat(delegateTaskBroadcast2.getBroadcastToDelegatesIds()).isNotEmpty();

    taskAfterBroadcast2.setNextBroadcast(System.currentTimeMillis());
    persistence.save(taskAfterBroadcast2);
    delegateTaskRebroadcastIterator.handle(account);
    // after third round of broadcasting
    DelegateTask taskAfterBroadcast3 = persistence.get(DelegateTask.class, delegateTask.getUuid());
    assertThat(taskAfterBroadcast3.getBroadcastCount()).isEqualTo(4);
    assertThat(taskAfterBroadcast3.getBroadcastRound()).isEqualTo(3);
    // verify task got broadcasted
    DelegateTaskBroadcast delegateTaskBroadcast3 = argumentCaptor.getValue();
    assertThat(delegateTaskBroadcast3).isNotNull();
    assertThat(delegateTaskBroadcast3.getTaskId()).isEqualTo(delegateTask.getUuid());
    assertThat(delegateTaskBroadcast3.getBroadcastToDelegatesIds()).isNotEmpty();

    taskAfterBroadcast3.setNextBroadcast(System.currentTimeMillis());
    persistence.save(taskAfterBroadcast3);
    delegateTaskRebroadcastIterator.handle(account);
    // verify no more broadcasting happens for task and count not get updated
    DelegateTask taskAfterBroadcast4 = persistence.get(DelegateTask.class, delegateTask.getUuid());
    assertThat(taskAfterBroadcast3.getBroadcastCount()).isEqualTo(4);
    assertThat(taskAfterBroadcast3.getBroadcastRound()).isEqualTo(3);
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testHandle_rebroadcastRoundWithLessThanTenEligibleDelegate() {
    Account account = new Account();
    account.setUuid(generateUuid());
    persistence.save(account);
    DelegateTask delegateTask = DelegateTask.builder()
                                    .accountId(account.getUuid())
                                    .version(versionInfoManager.getVersionInfo().getVersion())
                                    .status(DelegateTask.Status.QUEUED)
                                    .expiry(System.currentTimeMillis() + 60000)
                                    .data(TaskData.builder().taskType(TaskType.HTTP.name()).async(true).build())
                                    .build();
    Broadcaster broadcaster = mock(Broadcaster.class);
    when(broadcasterFactory.lookup(anyString(), eq(true))).thenReturn(broadcaster);
    delegateTask.setNextBroadcast(System.currentTimeMillis());
    delegateTask.setEligibleToExecuteDelegateIds(
        new LinkedList<>(Arrays.asList("del1", "del2", "del3", "del4", "del5", "del6")));
    persistence.save(delegateTask);
    when(delegateService.checkDelegateConnected(anyString(), anyString())).thenReturn(true);
    when(featureFlagService.isEnabled(DELEGATE_TASK_REBROADCAST_ITERATOR, account.getUuid())).thenReturn(true);
    delegateTaskRebroadcastIterator.handle(account);
    DelegateTask task = persistence.get(DelegateTask.class, delegateTask.getUuid());
    // verify broadcast count and broadcast round count got updated
    assertThat(task.getBroadcastCount()).isEqualTo(1);
    assertThat(task.getBroadcastRound()).isEqualTo(0);
    // verify broadcast
    ArgumentCaptor<DelegateTaskBroadcast> argumentCaptor = ArgumentCaptor.forClass(DelegateTaskBroadcast.class);
    verify(broadcaster, times(1)).broadcast(argumentCaptor.capture());
    DelegateTaskBroadcast delegateTaskBroadcast = argumentCaptor.getValue();
    assertThat(delegateTaskBroadcast).isNotNull();
    assertThat(delegateTaskBroadcast.getVersion()).isEqualTo(delegateTask.getVersion());
    assertThat(delegateTaskBroadcast.getAccountId()).isEqualTo(delegateTask.getAccountId());
    assertThat(delegateTaskBroadcast.getTaskId()).isEqualTo(delegateTask.getUuid());
    assertThat(delegateTaskBroadcast.getBroadcastToDelegatesIds()).isNotEmpty();

    task.setNextBroadcast(System.currentTimeMillis());
    persistence.save(task);
    delegateTaskRebroadcastIterator.handle(account);
    // after first round of broadcasting
    DelegateTask taskAfterBroadcast1 = persistence.get(DelegateTask.class, delegateTask.getUuid());
    assertThat(taskAfterBroadcast1.getBroadcastCount()).isEqualTo(2);
    assertThat(taskAfterBroadcast1.getBroadcastRound()).isEqualTo(1);

    taskAfterBroadcast1.setNextBroadcast(System.currentTimeMillis());
    persistence.save(taskAfterBroadcast1);
    delegateTaskRebroadcastIterator.handle(account);
    // after second round of broadcasting
    DelegateTask taskAfterBroadcast2 = persistence.get(DelegateTask.class, delegateTask.getUuid());
    assertThat(taskAfterBroadcast2.getBroadcastCount()).isEqualTo(3);
    assertThat(taskAfterBroadcast2.getBroadcastRound()).isEqualTo(2);
    // verify task got broadcasted
    DelegateTaskBroadcast delegateTaskBroadcast2 = argumentCaptor.getValue();
    assertThat(delegateTaskBroadcast2).isNotNull();
    assertThat(delegateTaskBroadcast2.getTaskId()).isEqualTo(delegateTask.getUuid());
    assertThat(delegateTaskBroadcast2.getBroadcastToDelegatesIds()).isNotEmpty();

    taskAfterBroadcast2.setNextBroadcast(System.currentTimeMillis());
    persistence.save(taskAfterBroadcast2);
    delegateTaskRebroadcastIterator.handle(account);
    // after third round of broadcasting
    DelegateTask taskAfterBroadcast3 = persistence.get(DelegateTask.class, delegateTask.getUuid());
    assertThat(taskAfterBroadcast3.getBroadcastCount()).isEqualTo(4);
    assertThat(taskAfterBroadcast3.getBroadcastRound()).isEqualTo(3);
    // verify task got broadcasted
    DelegateTaskBroadcast delegateTaskBroadcast3 = argumentCaptor.getValue();
    assertThat(delegateTaskBroadcast3).isNotNull();
    assertThat(delegateTaskBroadcast3.getTaskId()).isEqualTo(delegateTask.getUuid());
    assertThat(delegateTaskBroadcast3.getBroadcastToDelegatesIds()).isNotEmpty();

    taskAfterBroadcast3.setNextBroadcast(System.currentTimeMillis());
    persistence.save(taskAfterBroadcast3);
    delegateTaskRebroadcastIterator.handle(account);
    // verify no more broadcasting happens for task and count not get updated
    DelegateTask taskAfterBroadcast4 = persistence.get(DelegateTask.class, delegateTask.getUuid());
    assertThat(taskAfterBroadcast3.getBroadcastCount()).isEqualTo(4);
    assertThat(taskAfterBroadcast3.getBroadcastRound()).isEqualTo(3);
  }

  @Test
  @Owner(developers = JENNY)
  @Category(UnitTests.class)
  public void testHandle_rebroadcastRoundWithMoreThanTenEligibleDelegate() {
    Account account = new Account();
    account.setUuid(generateUuid());
    persistence.save(account);
    DelegateTask delegateTask = DelegateTask.builder()
                                    .accountId(account.getUuid())
                                    .version(versionInfoManager.getVersionInfo().getVersion())
                                    .status(DelegateTask.Status.QUEUED)
                                    .expiry(System.currentTimeMillis() + 60000)
                                    .data(TaskData.builder().taskType(TaskType.HTTP.name()).async(true).build())
                                    .build();
    Broadcaster broadcaster = mock(Broadcaster.class);
    when(broadcasterFactory.lookup(anyString(), eq(true))).thenReturn(broadcaster);
    delegateTask.setNextBroadcast(System.currentTimeMillis());
    delegateTask.setEligibleToExecuteDelegateIds(new LinkedList<>(Arrays.asList(
        "del1", "del2", "del3", "del4", "del5", "del6", "del7", "del8", "del9", "del10", "del11", "del12")));
    persistence.save(delegateTask);
    when(delegateService.checkDelegateConnected(anyString(), anyString())).thenReturn(true);
    when(featureFlagService.isEnabled(DELEGATE_TASK_REBROADCAST_ITERATOR, account.getUuid())).thenReturn(true);
    delegateTaskRebroadcastIterator.handle(account);
    DelegateTask task = persistence.get(DelegateTask.class, delegateTask.getUuid());
    // verify broadcast count and broadcast round count got updated
    assertThat(task.getBroadcastCount()).isEqualTo(1);
    assertThat(task.getBroadcastRound()).isEqualTo(0);
    // verify broadcast
    ArgumentCaptor<DelegateTaskBroadcast> argumentCaptor = ArgumentCaptor.forClass(DelegateTaskBroadcast.class);
    verify(broadcaster, times(1)).broadcast(argumentCaptor.capture());
    DelegateTaskBroadcast delegateTaskBroadcast = argumentCaptor.getValue();
    assertThat(delegateTaskBroadcast).isNotNull();
    assertThat(delegateTaskBroadcast.getVersion()).isEqualTo(delegateTask.getVersion());
    assertThat(delegateTaskBroadcast.getAccountId()).isEqualTo(delegateTask.getAccountId());
    assertThat(delegateTaskBroadcast.getTaskId()).isEqualTo(delegateTask.getUuid());
    assertThat(delegateTaskBroadcast.getBroadcastToDelegatesIds()).isNotEmpty();

    task.setNextBroadcast(System.currentTimeMillis());
    persistence.save(task);
    delegateTaskRebroadcastIterator.handle(account);
    // after first round of broadcasting
    DelegateTask taskAfterBroadcast1 = persistence.get(DelegateTask.class, delegateTask.getUuid());
    assertThat(taskAfterBroadcast1.getBroadcastCount()).isEqualTo(2);
    assertThat(taskAfterBroadcast1.getBroadcastRound()).isEqualTo(0);

    taskAfterBroadcast1.setNextBroadcast(System.currentTimeMillis());
    taskAfterBroadcast1.setExpiry(System.currentTimeMillis() + 60000);
    persistence.save(taskAfterBroadcast1);
    delegateTaskRebroadcastIterator.handle(account);
    // after second round of broadcasting
    DelegateTask taskAfterBroadcast2 = persistence.get(DelegateTask.class, delegateTask.getUuid());
    assertThat(taskAfterBroadcast2.getBroadcastCount()).isEqualTo(3);
    assertThat(taskAfterBroadcast2.getBroadcastRound()).isEqualTo(1);
    // verify task got broadcasted
    DelegateTaskBroadcast delegateTaskBroadcast2 = argumentCaptor.getValue();
    assertThat(delegateTaskBroadcast2).isNotNull();
    assertThat(delegateTaskBroadcast2.getTaskId()).isEqualTo(delegateTask.getUuid());
    assertThat(delegateTaskBroadcast2.getBroadcastToDelegatesIds()).isNotEmpty();

    taskAfterBroadcast2.setNextBroadcast(System.currentTimeMillis());
    taskAfterBroadcast2.setExpiry(System.currentTimeMillis() + 60000);
    persistence.save(taskAfterBroadcast2);
    delegateTaskRebroadcastIterator.handle(account);
    // after third round of broadcasting
    DelegateTask taskAfterBroadcast3 = persistence.get(DelegateTask.class, delegateTask.getUuid());
    assertThat(taskAfterBroadcast3.getBroadcastCount()).isEqualTo(4);
    assertThat(taskAfterBroadcast3.getBroadcastRound()).isEqualTo(1);
    // verify task got broadcasted
    DelegateTaskBroadcast delegateTaskBroadcast3 = argumentCaptor.getValue();
    assertThat(delegateTaskBroadcast3).isNotNull();
    assertThat(delegateTaskBroadcast3.getTaskId()).isEqualTo(delegateTask.getUuid());
    assertThat(delegateTaskBroadcast3.getBroadcastToDelegatesIds()).isNotEmpty();

    taskAfterBroadcast3.setNextBroadcast(System.currentTimeMillis());
    persistence.save(taskAfterBroadcast3);
    delegateTaskRebroadcastIterator.handle(account);
    // verify no more broadcasting happens for task and count not get updated
    DelegateTask taskAfterBroadcast4 = persistence.get(DelegateTask.class, delegateTask.getUuid());
    assertThat(taskAfterBroadcast4.getBroadcastCount()).isEqualTo(5);
    assertThat(taskAfterBroadcast4.getBroadcastRound()).isEqualTo(2);

    taskAfterBroadcast4.setNextBroadcast(System.currentTimeMillis());
    persistence.save(taskAfterBroadcast4);
    delegateTaskRebroadcastIterator.handle(account);
    // verify no more broadcasting happens for task and count not get updated
    DelegateTask taskAfterBroadcast5 = persistence.get(DelegateTask.class, delegateTask.getUuid());
    assertThat(taskAfterBroadcast5.getBroadcastCount()).isEqualTo(6);
    assertThat(taskAfterBroadcast5.getBroadcastRound()).isEqualTo(2);

    taskAfterBroadcast5.setNextBroadcast(System.currentTimeMillis());
    persistence.save(taskAfterBroadcast5);
    delegateTaskRebroadcastIterator.handle(account);
    // verify no more broadcasting happens for task and count not get updated
    DelegateTask taskAfterBroadcast6 = persistence.get(DelegateTask.class, delegateTask.getUuid());
    assertThat(taskAfterBroadcast6.getBroadcastCount()).isEqualTo(7);
    assertThat(taskAfterBroadcast6.getBroadcastRound()).isEqualTo(3);

    taskAfterBroadcast6.setNextBroadcast(System.currentTimeMillis());
    persistence.save(taskAfterBroadcast6);
    delegateTaskRebroadcastIterator.handle(account);
    // verify no more broadcasting happens for task and count not get updated
    DelegateTask taskAfterBroadcast7 = persistence.get(DelegateTask.class, delegateTask.getUuid());
    assertThat(taskAfterBroadcast7.getBroadcastCount()).isEqualTo(7);
    assertThat(taskAfterBroadcast7.getBroadcastRound()).isEqualTo(3);
  }
}
