package io.harness.consumers;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.PRASHANT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import io.harness.OrchestrationVisualizationTestBase;
import io.harness.category.element.UnitTests;
import io.harness.eventsframework.api.Consumer;
import io.harness.rule.Owner;
import io.harness.service.GraphGenerationService;

import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

public class GraphUpdateDispatcherTest extends OrchestrationVisualizationTestBase {
  @Mock private Consumer consumer;
  @Mock private GraphGenerationService graphGenerationService;

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void testMessageAck() {
    String planExecutionId = generateUuid();
    String mid1 = generateUuid();
    String mid2 = generateUuid();
    String mid3 = generateUuid();
    List<String> messageIds = ImmutableList.of(mid1, mid2, mid3);
    GraphUpdateDispatcher dispatcher = GraphUpdateDispatcher.builder()
                                           .planExecutionId(planExecutionId)
                                           .messageIds(messageIds)
                                           .startTs(System.currentTimeMillis())
                                           .consumer(consumer)
                                           .graphGenerationService(graphGenerationService)
                                           .build();
    when(graphGenerationService.updateGraph(planExecutionId)).thenReturn(true);
    dispatcher.run();
    ArgumentCaptor<String> stringArgumentCaptor = ArgumentCaptor.forClass(String.class);
    verify(consumer, times(3)).acknowledge(stringArgumentCaptor.capture());
    assertThat(stringArgumentCaptor.getAllValues()).containsExactlyInAnyOrder(mid1, mid2, mid3);
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void testNoMessageAck() {
    String planExecutionId = generateUuid();
    String mid1 = generateUuid();
    String mid2 = generateUuid();
    String mid3 = generateUuid();
    List<String> messageIds = ImmutableList.of(mid1, mid2, mid3);
    GraphUpdateDispatcher dispatcher = GraphUpdateDispatcher.builder()
                                           .planExecutionId(planExecutionId)
                                           .messageIds(messageIds)
                                           .startTs(System.currentTimeMillis())
                                           .consumer(consumer)
                                           .graphGenerationService(graphGenerationService)
                                           .build();
    when(graphGenerationService.updateGraph(planExecutionId)).thenReturn(false);
    dispatcher.run();
    verifyZeroInteractions(consumer);
  }
}