package io.harness.engine.pms.resume.publisher;

import static io.harness.rule.OwnerRule.SAHIL;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.execution.NodeExecution;
import io.harness.pms.execution.utils.NodeProjectionUtils;
import io.harness.reflection.ReflectionUtils;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
@PrepareForTest(NodeExecution.class)
public class ResumeMetadataTest extends CategoryTest {
  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testIfStepParametersSentAreResolvedOne() {
    NodeExecution nodeExecution = PowerMockito.mock(NodeExecution.class);

    ResumeMetadata.fromNodeExecution(nodeExecution);

    assertThat(ReflectionUtils.getAllDeclaredAndInheritedFields(ResumeMetadata.class).size())
        .isEqualTo(NodeProjectionUtils.fieldsForResume.size() - 1);
    Mockito.verify(nodeExecution).getNode();
    Mockito.verify(nodeExecution).getMode();
    Mockito.verify(nodeExecution).getUuid();
    Mockito.verify(nodeExecution).getAmbiance();
    Mockito.verify(nodeExecution).getResolvedStepParametersBytes();
    Mockito.verify(nodeExecution).obtainLatestExecutableResponse();
    Mockito.verifyNoMoreInteractions(nodeExecution);
  }
}