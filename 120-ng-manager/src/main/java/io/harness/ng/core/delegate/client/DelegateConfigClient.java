/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.delegate.client;

import io.harness.NGCommonEntityConstants;
import io.harness.delegate.beans.DelegateGroup;
import io.harness.delegate.beans.DelegateGroupTags;
import io.harness.rest.RestResponse;

import javax.validation.constraints.NotNull;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.PUT;
import retrofit2.http.Query;

public interface DelegateConfigClient {
  String DELEGATE_SETUP_API = "setup/delegates/ng/v2/tags";

  @PUT(DELEGATE_SETUP_API)
  Call<RestResponse<DelegateGroup>> updateDelegateGroupTags(
      @Query(NGCommonEntityConstants.IDENTIFIER_KEY) @NotNull String groupIdentifier,
      @Query(NGCommonEntityConstants.ACCOUNT_KEY) @NotNull String accountIdentifier,
      @Query(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Query(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier, @Body @NotNull DelegateGroupTags tags);
}
