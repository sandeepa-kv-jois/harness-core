package io.harness.gitsync.fullsync.utils;

import static io.harness.NGCommonEntityConstants.ACCOUNT_KEY;
import static io.harness.NGCommonEntityConstants.PROJECT_KEY;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.BHAVYA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PL)
public class FullSyncLogContextHelperTest extends CategoryTest {
  private static final String ACCOUNT_ID = "acc";
  private static final String ORG_ID = "org";
  private static final String PROJECT_ID = "proj";
  private static final String MESSAGE_ID = "message";

  @Test
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  public void testGetContext() {
    Map<String, String> logContext = FullSyncLogContextHelper.getContext(ACCOUNT_ID, ORG_ID, null, MESSAGE_ID);
    assertThat(logContext.get(ACCOUNT_KEY)).isEqualTo(ACCOUNT_ID);
    assertThat(logContext.get(PROJECT_KEY)).isEqualTo(null);
  }
}
