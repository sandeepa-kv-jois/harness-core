package io.harness.gitsync.common.helper;

import static io.harness.rule.OwnerRule.BHAVYA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.errorhandling.NGErrorHelper;
import io.harness.exception.DelegateServiceDriverException;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

public class GitConnectivityExceptionHelperTest extends CategoryTest {
  private final String errorMessage = "Unknown error";

  @Test
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  public void testGetErrorMessage_whenDelegateDriverExceptionIsThrown() {
    Exception ex = new DelegateServiceDriverException(errorMessage);
    assertThat(GitConnectivityExceptionHelper.getErrorMessage(ex))
        .isEqualTo(
            GitConnectivityExceptionHelper.CONNECTIVITY_ERROR + "DelegateServiceDriverException: " + errorMessage);
  }

  @Test
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  public void testGetErrorMessage_whenNullPointerExceptionIsThrown() {
    Exception ex = new NullPointerException(errorMessage);
    assertThat(GitConnectivityExceptionHelper.getErrorMessage(ex)).isEqualTo(NGErrorHelper.DEFAULT_ERROR_MESSAGE);
  }
}
