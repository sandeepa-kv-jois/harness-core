/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.scmerrorhandling.handlers.ado;

import static io.harness.rule.OwnerRule.BHAVYA;
import static io.harness.rule.OwnerRule.MOHIT_GARG;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.ScmBadRequestException;
import io.harness.exception.ScmConflictException;
import io.harness.exception.ScmUnauthorizedException;
import io.harness.exception.ScmUnexpectedException;
import io.harness.exception.WingsException;
import io.harness.gitsync.GitSyncTestBase;
import io.harness.gitsync.common.scmerrorhandling.dtos.ErrorMetadata;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.MockitoAnnotations;

public class AdoUpdateFileScmApiErrorHandlerTest extends GitSyncTestBase {
  @Inject AdoUpdateFileScmApiErrorHandler errorHandler;

  private static final String errorMessage = "errorMessage";

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = MOHIT_GARG)
  @Category(UnitTests.class)
  public void testHandleErrorOnUnauthorizedResponse() {
    try {
      errorHandler.handleError(203, errorMessage, ErrorMetadata.builder().build());
    } catch (Exception ex) {
      WingsException exception = ExceptionUtils.cause(ScmUnauthorizedException.class, ex);
      assertThat(exception).isNotNull();
      assertThat(exception.getMessage()).isEqualTo(errorMessage);
    }
  }

  @Test
  @Owner(developers = MOHIT_GARG)
  @Category(UnitTests.class)
  public void testHandleErrorOnResourceNotFoundResponse() {
    try {
      errorHandler.handleError(404, errorMessage, ErrorMetadata.builder().build());
    } catch (Exception ex) {
      WingsException exception = ExceptionUtils.cause(ScmBadRequestException.class, ex);
      assertThat(exception).isNotNull();
      assertThat(exception.getMessage()).isEqualTo(errorMessage);
    }
  }

  @Test
  @Owner(developers = MOHIT_GARG)
  @Category(UnitTests.class)
  public void testHandleErrorOnBadRequestEntityResponse() {
    try {
      errorHandler.handleError(400, errorMessage, ErrorMetadata.builder().build());
    } catch (Exception ex) {
      WingsException exception = ExceptionUtils.cause(ScmBadRequestException.class, ex);
      assertThat(exception).isNotNull();
      assertThat(exception.getMessage()).isEqualTo(errorMessage);
    }
  }

  @Test
  @Owner(developers = MOHIT_GARG)
  @Category(UnitTests.class)
  public void testHandleErrorWhenUnexpectedStatusCode() {
    try {
      errorHandler.handleError(405, errorMessage, ErrorMetadata.builder().build());
    } catch (Exception ex) {
      WingsException exception = ExceptionUtils.cause(ScmUnexpectedException.class, ex);
      assertThat(exception).isNotNull();
      assertThat(exception.getMessage()).isEqualTo(errorMessage);
    }
  }

  @Test
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  public void testHandleErrorWhenConflictStatusCode() {
    try {
      errorHandler.handleError(409, errorMessage, ErrorMetadata.builder().build());
    } catch (Exception ex) {
      WingsException exception = ExceptionUtils.cause(ScmConflictException.class, ex);
      assertThat(exception).isNotNull();
      assertThat(exception.getMessage()).isEqualTo(errorMessage);
    }
  }
}
