/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cistatus.service.gitlab;

import static java.lang.String.format;

import io.harness.cistatus.StatusCreationResponse;
import io.harness.exception.InvalidRequestException;
import io.harness.network.Http;
import io.harness.security.encryption.EncryptedDataDetail;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.inject.Singleton;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

@Singleton
@Slf4j
public class GitlabServiceImpl implements GitlabService {
  private final int EXP_TIME = 5 * 60 * 1000;
  public static final String DESC = "description";
  public static final String STATE = "state";
  public static final String CONTEXT = "context";
  public static final String TARGET_URL = "target_url";
  private static final String SEPARATOR = "/";

  @Override
  public JSONObject mergePR(String apiUrl, String slug, String token, String prNumber, Boolean deleteSourceBranch) {
    try {
      Response<Object> response = getGitlabRestClient(GitlabConfig.builder().gitlabUrl(apiUrl).build())
                                      .mergePR(token, slug, prNumber, deleteSourceBranch)
                                      .execute();
      JSONObject json = new JSONObject();
      if (response.isSuccessful()) {
        json.put("sha", ((LinkedHashMap) response.body()).get("sha"));
        return json;
      } else {
        log.error(
            "Failed to merge PR for gitlab url {} and prNum {}. Response {} ", apiUrl, prNumber, response.errorBody());
        return json;
      }
    } catch (Exception e) {
      log.error("Failed to merge PR for gitlab url {} and prNum {} ", apiUrl, prNumber, e);
      return new JSONObject();
    }
  }

  @Override
  public boolean sendStatus(GitlabConfig gitlabConfig, String userName, String token,
      List<EncryptedDataDetail> encryptionDetails, String sha, String owner, String repo,
      Map<String, Object> bodyObjectMap) {
    log.info("Sending status {} for sha {}", bodyObjectMap.get(STATE), sha);

    try {
      Response<StatusCreationResponse> statusCreationResponseResponse =
          getGitlabRestClient(gitlabConfig)
              .createStatus(getAuthToken(token), owner + SEPARATOR + repo, sha, (String) bodyObjectMap.get(STATE),
                  (String) bodyObjectMap.get(CONTEXT), (String) bodyObjectMap.get(DESC),
                  (String) bodyObjectMap.get(TARGET_URL))
              .execute();

      if (!statusCreationResponseResponse.isSuccessful()) {
        log.error("Failed to send status for gitlab url {} and sha {} error {}, message {}",
            gitlabConfig.getGitlabUrl(), sha, statusCreationResponseResponse.errorBody().string(),
            statusCreationResponseResponse.message());
      }

      return statusCreationResponseResponse.isSuccessful();
    } catch (Exception e) {
      throw new InvalidRequestException(
          format("Failed to send status for Gitlab url %s and sha %s ", gitlabConfig.getGitlabUrl(), sha), e);
    }
  }

  @VisibleForTesting
  public GitlabRestClient getGitlabRestClient(GitlabConfig gitlabConfig) {
    try {
      String gitlabUrl = gitlabConfig.getGitlabUrl();
      Preconditions.checkNotNull(gitlabUrl, "Gitlab api url is null");
      if (!gitlabUrl.endsWith("/")) {
        gitlabUrl = gitlabUrl + "/";
      }
      Retrofit retrofit = new Retrofit.Builder()
                              .baseUrl(gitlabUrl)
                              .addConverterFactory(JacksonConverterFactory.create())
                              .client(Http.getUnsafeOkHttpClient(gitlabUrl))
                              .build();
      return retrofit.create(GitlabRestClient.class);
    } catch (InvalidRequestException e) {
      throw e;
    } catch (Exception e) {
      throw new InvalidRequestException(
          "Failed to post commit status request to gitlab :" + gitlabConfig.getGitlabUrl(), e);
    }
  }

  private String getAuthToken(String authToken) {
    return format("Bearer %s", authToken);
  }
}
