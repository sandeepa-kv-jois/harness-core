/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.enforcement.handlers;

import io.harness.enforcement.handlers.impl.AllAvailableConversionHandlerImpl;
import io.harness.enforcement.handlers.impl.ConversionHandlerImpl;

import com.google.inject.Inject;
import com.google.inject.Singleton;

/**
 * Factory to decide which conversion handler to user based on enforcement feature flag
 */
@Singleton
public class ConversionHandlerFactory {
  private final AllAvailableConversionHandlerImpl allAvailableConversionHandler;
  private final ConversionHandlerImpl conversionHandler;

  @Inject
  public ConversionHandlerFactory(
      AllAvailableConversionHandlerImpl allAvailableConversionHandler, ConversionHandlerImpl conversionHandler) {
    this.allAvailableConversionHandler = allAvailableConversionHandler;
    this.conversionHandler = conversionHandler;
  }

  public ConversionHandler getConversionHandler(boolean featureFlagEnabled) {
    if (featureFlagEnabled) {
      return conversionHandler;
    } else {
      return allAvailableConversionHandler;
    }
  }
}
