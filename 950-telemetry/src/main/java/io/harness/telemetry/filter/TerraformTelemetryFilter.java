/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.telemetry.filter;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.telemetry.Destination.AMPLITUDE;

import static io.github.resilience4j.ratelimiter.RateLimiter.decorateConsumer;
import static java.time.Duration.ofHours;
import static java.time.Duration.ofNanos;
import static javax.ws.rs.Priorities.AUTHENTICATION;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.telemetry.Category;
import io.harness.telemetry.TelemetryOption;
import io.harness.telemetry.TelemetryReporter;
import io.harness.telemetry.utils.TelemetryDataUtils;

import com.google.inject.Singleton;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import java.util.Collections;
import java.util.HashMap;
import java.util.Optional;
import java.util.function.Consumer;
import javax.annotation.Priority;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
@OwnedBy(PL)
@Singleton
@Priority(AUTHENTICATION - 1)
@Slf4j
public class TerraformTelemetryFilter implements ContainerRequestFilter {
  public static final String USER_AGENT = "User-Agent";
  public static final String ACCOUNT_IDENTIFIER = "account_identifier";
  public static final String API_ENDPOINT = "api_endpoint";
  public static final String API_TYPE = "api_type";
  public static final String TERRAFORM_API_TELEMETRY = "terraform-api-telemetry";
  public static final String TERRAFORM_TELEMETRY_RATE_LIMITER_NAME = "terraform-telemetry-rate-limiter";
  public static final String API_PATTERN = "api_pattern";
  public static final int DEFAULT_RATE_LIMIT = 50;
  public static final int DEFAULT_RATE_LIMIT_PERIOD_HOURS = 1;
  public static final int DEFAULT_RATE_LIMIT_TIMEOUT_NANOS = 1;

  private final Consumer<Consumer<TelemetryReporter>> rateLimitedConsumer;

  public TerraformTelemetryFilter(TelemetryReporter telemetryReporter) {
    this(telemetryReporter,
        RateLimiterConfig.custom()
            .limitForPeriod(DEFAULT_RATE_LIMIT)
            .limitRefreshPeriod(ofHours(DEFAULT_RATE_LIMIT_PERIOD_HOURS))
            .timeoutDuration(ofNanos(DEFAULT_RATE_LIMIT_TIMEOUT_NANOS))
            .build());
  }

  public TerraformTelemetryFilter(TelemetryReporter telemetryReporter, RateLimiterConfig rateLimiterConfig) {
    RateLimiterRegistry rateLimiterRegistry = RateLimiterRegistry.of(rateLimiterConfig);

    RateLimiter rphRateLimiter =
        rateLimiterRegistry.rateLimiter(TERRAFORM_TELEMETRY_RATE_LIMITER_NAME, rateLimiterConfig);

    rateLimitedConsumer = decorateConsumer(rphRateLimiter, consumer -> consumer.accept(telemetryReporter));
  }

  @Override
  public void filter(ContainerRequestContext containerRequestContext) {
    Optional<String> userAgentOptional = getUserAgentFromHeaders(containerRequestContext);
    Optional<String> accountIdentifierOptional = getAccountIdentifierFromUri(containerRequestContext);
    HashMap<String, Object> properties = new HashMap<>();

    if (accountIdentifierOptional.isPresent() && userAgentOptional.isPresent()
        && userAgentOptional.get().contains("terraform-provider-harness-platform")) {
      properties.put(USER_AGENT, "Terraform");
      properties.put(ACCOUNT_IDENTIFIER, accountIdentifierOptional.get());
      properties.put(API_ENDPOINT, containerRequestContext.getUriInfo().getPath());
      properties.put(API_TYPE, containerRequestContext.getMethod());
      properties.put(API_PATTERN, TelemetryDataUtils.getApiPattern(containerRequestContext));

      try {
        rateLimitedConsumer.accept(reporter
            -> reporter.sendTrackEvent(TERRAFORM_API_TELEMETRY, null, accountIdentifierOptional.get(), properties,
                Collections.singletonMap(AMPLITUDE, true), Category.GLOBAL,
                TelemetryOption.builder().sendForCommunity(false).build()));
      } catch (RequestNotPermitted requestNotPermitted) {
        log.info("Dropping Terraform telemetry data due to rate limiting : account={}, endpoint={}",
            properties.get(ACCOUNT_IDENTIFIER), properties.get(API_ENDPOINT));
      } catch (Exception exception) {
        log.error("Error occurred while sending Terraform telemetry data : account={}, endpoint={}, exception={}",
            properties.get(ACCOUNT_IDENTIFIER), properties.get(API_ENDPOINT), exception);
      }
    }
  }

  private Optional<String> getUserAgentFromHeaders(ContainerRequestContext containerRequestContext) {
    String apiKey = containerRequestContext.getHeaderString(USER_AGENT);
    return StringUtils.isEmpty(apiKey) ? Optional.empty() : Optional.of(apiKey);
  }

  private Optional<String> getAccountIdentifierFromUri(ContainerRequestContext containerRequestContext) {
    String accountIdentifier =
        containerRequestContext.getUriInfo().getQueryParameters().getFirst(NGCommonEntityConstants.ACCOUNT_KEY);
    if (StringUtils.isEmpty(accountIdentifier)) {
      accountIdentifier =
          containerRequestContext.getUriInfo().getPathParameters().getFirst(NGCommonEntityConstants.ACCOUNT_KEY);
    }
    return StringUtils.isEmpty(accountIdentifier) ? Optional.empty() : Optional.of(accountIdentifier);
  }
}
