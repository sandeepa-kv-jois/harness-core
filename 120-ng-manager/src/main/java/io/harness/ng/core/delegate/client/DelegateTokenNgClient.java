/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.delegate.client;

import io.harness.NGCommonEntityConstants;
import io.harness.delegate.beans.DelegateTokenDetails;
import io.harness.delegate.beans.DelegateTokenStatus;
import io.harness.rest.RestResponse;

import java.util.List;
import javax.validation.constraints.NotNull;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Query;

public interface DelegateTokenNgClient {
  String DELEGATE_TOKEN_NG_API = "delegate-token/ng";

  @POST(DELEGATE_TOKEN_NG_API)
  Call<RestResponse<DelegateTokenDetails>> createToken(
      @Query(NGCommonEntityConstants.ACCOUNT_KEY) @NotNull String accountIdentifier,
      @Query(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Query(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Query("tokenName") @NotNull String tokenName);

  @PUT(DELEGATE_TOKEN_NG_API)
  Call<RestResponse<DelegateTokenDetails>> revokeToken(
      @Query(NGCommonEntityConstants.ACCOUNT_KEY) @NotNull String accountIdentifier,
      @Query(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Query(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Query("tokenName") @NotNull String tokenName);

  @GET(DELEGATE_TOKEN_NG_API)
  Call<RestResponse<List<DelegateTokenDetails>>> getTokens(
      @Query(NGCommonEntityConstants.ACCOUNT_KEY) @NotNull String accountIdentifier,
      @Query(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Query(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Query("status") DelegateTokenStatus status);
}
