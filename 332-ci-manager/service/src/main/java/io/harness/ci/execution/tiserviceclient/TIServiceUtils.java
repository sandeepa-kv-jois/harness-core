/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.tiserviceclient;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ci.beans.entities.TIServiceConfig;
import io.harness.exception.GeneralException;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import retrofit2.Call;
import retrofit2.Response;

@Getter
@Setter
@Slf4j
@Singleton
@OwnedBy(HarnessTeam.CI)
public class TIServiceUtils {
  private final TIServiceClient tiServiceClient;
  private final TIServiceConfig tiServiceConfig;

  @Inject
  public TIServiceUtils(TIServiceClient tiServiceClient, TIServiceConfig tiServiceConfig) {
    this.tiServiceClient = tiServiceClient;
    this.tiServiceConfig = tiServiceConfig;
  }

  @NotNull
  public String getTIServiceToken(String accountID) {
    log.info("Initiating token request to TI service: {}", getInternalUrl());
    Call<String> tokenCall = tiServiceClient.generateToken(accountID, tiServiceConfig.getGlobalToken());
    Response<String> response = null;
    try {
      response = tokenCall.execute();
    } catch (IOException e) {
      throw new GeneralException("Token request to TI service call failed", e);
    }

    // Received error from the server
    if (!response.isSuccessful()) {
      String errorBody = null;
      try {
        errorBody = response.errorBody().string();
      } catch (IOException e) {
        log.error("Could not read error body {}", response.errorBody());
      }

      throw new GeneralException(String.format(
          "Could not fetch token from TI service. status code = %s, message = %s, response = %s", response.code(),
          response.message() == null ? "null" : response.message(), response.errorBody() == null ? "null" : errorBody));
    }
    return response.body();
  }

  private String getInternalUrl() {
    if (!isEmpty(tiServiceConfig.getInternalUrl())) {
      return tiServiceConfig.getInternalUrl();
    }
    return tiServiceConfig.getBaseUrl();
  }
}
