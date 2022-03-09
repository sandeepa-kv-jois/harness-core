/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.azure;

import static io.harness.azure.model.AzureConstants.SUBSCRIPTION_ID_NULL_VALIDATION_MSG;
import static io.harness.eraro.ErrorCode.AZURE_CLIENT_EXCEPTION;
import static io.harness.exception.WingsException.USER;
import static io.harness.network.Http.getOkHttpClientBuilder;

import static com.google.common.base.Charsets.UTF_8;
import static java.lang.String.format;
import static org.apache.commons.codec.binary.Base64.encodeBase64String;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.azure.client.AzureBlueprintRestClient;
import io.harness.azure.client.AzureContainerRegistryRestClient;
import io.harness.azure.client.AzureManagementRestClient;
import io.harness.azure.context.AzureClientContext;
import io.harness.azure.model.AzureConfig;
import io.harness.azure.model.AzureConstants;
import io.harness.exception.AzureClientException;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidCredentialsException;
import io.harness.exception.InvalidRequestException;
import io.harness.network.Http;

import com.google.inject.Singleton;
import com.microsoft.aad.adal4j.AuthenticationException;
import com.microsoft.azure.AzureEnvironment;
import com.microsoft.azure.credentials.ApplicationTokenCredentials;
import com.microsoft.azure.management.Azure;
import com.microsoft.rest.LogLevel;
import com.microsoft.rest.ServiceResponseBuilder;
import com.microsoft.rest.serializer.JacksonAdapter;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.ResponseBody;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava.RxJavaCallAdapterFactory;
import retrofit2.converter.jackson.JacksonConverterFactory;

@Singleton
@Slf4j
public class AzureClient {
  protected JacksonAdapter azureJacksonAdapter;
  protected ServiceResponseBuilder.Factory serviceResponseFactory;

  public AzureClient() {
    azureJacksonAdapter = new JacksonAdapter();
    serviceResponseFactory = new ServiceResponseBuilder.Factory();
  }

  protected Azure getAzureClientByContext(AzureClientContext context) {
    AzureConfig azureConfig = context.getAzureConfig();
    String subscriptionId = context.getSubscriptionId();
    return getAzureClient(azureConfig, subscriptionId);
  }

  protected Azure getAzureClientWithDefaultSubscription(AzureConfig azureConfig) {
    try {
      ApplicationTokenCredentials credentials =
          new ApplicationTokenCredentials(azureConfig.getClientId(), azureConfig.getTenantId(),
              String.valueOf(azureConfig.getKey()), getAzureEnvironment(azureConfig.getAzureEnvironmentType()));

      return Azure.configure().withLogLevel(LogLevel.NONE).authenticate(credentials).withDefaultSubscription();
    } catch (Exception e) {
      handleAzureAuthenticationException(e);
    }
    return null;
  }

  protected Azure getAzureClient(AzureConfig azureConfig, String subscriptionId) {
    if (isBlank(subscriptionId)) {
      throw new IllegalArgumentException(SUBSCRIPTION_ID_NULL_VALIDATION_MSG);
    }

    try {
      ApplicationTokenCredentials credentials =
          new ApplicationTokenCredentials(azureConfig.getClientId(), azureConfig.getTenantId(),
              String.valueOf(azureConfig.getKey()), getAzureEnvironment(azureConfig.getAzureEnvironmentType()));

      return Azure.configure().withLogLevel(LogLevel.NONE).authenticate(credentials).withSubscription(subscriptionId);
    } catch (Exception e) {
      handleAzureAuthenticationException(e);
      throw new InvalidRequestException("Failed to connect to Azure cluster. " + ExceptionUtils.getMessage(e), USER);
    }
  }

  protected AzureEnvironment getAzureEnvironment(AzureEnvironmentType azureEnvironmentType) {
    if (azureEnvironmentType == null) {
      return AzureEnvironment.AZURE;
    }

    switch (azureEnvironmentType) {
      case AZURE_US_GOVERNMENT:
        return AzureEnvironment.AZURE_US_GOVERNMENT;

      case AZURE:
      default:
        return AzureEnvironment.AZURE;
    }
  }

  protected void handleAzureAuthenticationException(Exception e) {
    log.error("HandleAzureAuthenticationException: Exception:" + e);

    Throwable e1 = e;
    while (e1.getCause() != null) {
      e1 = e1.getCause();
      if (e1 instanceof AuthenticationException) {
        throw new InvalidCredentialsException("Invalid Azure credentials." + e1.getMessage(), USER);
      }
    }
  }

  protected void handleInvalidCertException(Exception e) {
    log.error("HandleInvalidCertException: Exception:" + e);

    Throwable e1 = e;
    while (e1.getCause() != null) {
      e1 = e1.getCause();
      if (e1 instanceof InvalidKeyException) {
        throw new InvalidCredentialsException("Invalid Azure credentials." + e1.getMessage(), USER);
      }
    }
  }

  protected void handleAzureErrorResponse(ResponseBody response, okhttp3.Response rawResponse) {
    String errorJSONMsg;
    try {
      errorJSONMsg = response != null ? new String(response.bytes()) : rawResponse.toString();
    } catch (IOException e) {
      errorJSONMsg = rawResponse.toString();
      log.error("Unable to parse Azure REST error response: ", e);
    }

    throw new AzureClientException(errorJSONMsg, AZURE_CLIENT_EXCEPTION, USER);
  }

  protected AzureManagementRestClient getAzureManagementRestClient(AzureEnvironmentType azureEnvironmentType) {
    String url = getAzureEnvironment(azureEnvironmentType).resourceManagerEndpoint();
    return getAzureRestClient(url, AzureManagementRestClient.class);
  }

  protected AzureBlueprintRestClient getAzureBlueprintRestClient(AzureEnvironmentType azureEnvironmentType) {
    String url = getAzureEnvironment(azureEnvironmentType).resourceManagerEndpoint();
    return getAzureRestClient(url, AzureBlueprintRestClient.class);
  }

  protected AzureContainerRegistryRestClient getAzureContainerRegistryRestClient(final String repositoryHost) {
    String repositoryHostUrl = buildRepositoryHostUrl(repositoryHost);
    return getAzureRestClient(repositoryHostUrl, AzureContainerRegistryRestClient.class);
  }

  protected <T> T getAzureRestClient(String url, Class<T> clazz) {
    OkHttpClient okHttpClient = getOkHttpClientBuilder()
                                    .connectTimeout(AzureConstants.REST_CLIENT_CONNECT_TIMEOUT, TimeUnit.SECONDS)
                                    .readTimeout(AzureConstants.REST_CLIENT_READ_TIMEOUT, TimeUnit.SECONDS)
                                    .proxy(Http.checkAndGetNonProxyIfApplicable(url))
                                    .retryOnConnectionFailure(true)
                                    .build();
    Retrofit retrofit = new Retrofit.Builder()
                            .client(okHttpClient)
                            .baseUrl(url)
                            .addCallAdapterFactory(RxJavaCallAdapterFactory.create())
                            .addConverterFactory(JacksonConverterFactory.create())
                            .build();
    return retrofit.create(clazz);
  }

  protected String getAzureBearerAuthToken(AzureConfig azureConfig) {
    try {
      AzureEnvironment azureEnvironment = getAzureEnvironment(azureConfig.getAzureEnvironmentType());
      ApplicationTokenCredentials credentials = new ApplicationTokenCredentials(
          azureConfig.getClientId(), azureConfig.getTenantId(), new String(azureConfig.getKey()), azureEnvironment);

      String token = credentials.getToken(azureEnvironment.managementEndpoint());
      return "Bearer " + token;
    } catch (Exception e) {
      handleAzureAuthenticationException(e);
    }
    return null;
  }

  protected String getAzureBasicAuthHeader(final String username, final String password) {
    return "Basic " + encodeBase64String(format("%s:%s", username, password).getBytes(UTF_8));
  }

  private String buildRepositoryHostUrl(String repositoryHost) {
    return format("https://%s%s", repositoryHost, repositoryHost.endsWith("/") ? "" : "/");
  }
}
