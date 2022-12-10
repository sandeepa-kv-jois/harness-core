/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.event.pollingevent;

import static io.harness.authorization.AuthorizationServiceHeader.PIPELINE_SERVICE;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.EventsFrameworkConstants;
import io.harness.eventsframework.api.Consumer;
import io.harness.pms.events.base.PmsAbstractRedisConsumer;
import io.harness.queue.QueueController;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.dto.ServicePrincipal;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import javax.cache.Cache;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@OwnedBy(HarnessTeam.PIPELINE)
public class PollingEventStreamConsumer extends PmsAbstractRedisConsumer<PollingEventStreamListener> {
  @Inject
  public PollingEventStreamConsumer(@Named(EventsFrameworkConstants.POLLING_EVENTS_STREAM) Consumer redisConsumer,
      PollingEventStreamListener pollingEventListener, QueueController queueController,
      @Named("pmsEventsCache") Cache<String, Integer> eventsCache) {
    super(redisConsumer, pollingEventListener, eventsCache, queueController);
  }

  @Override
  public void preThreadHandler() {
    SecurityContextBuilder.setContext(new ServicePrincipal(PIPELINE_SERVICE.getServiceId()));
  }

  @Override
  public void postThreadCompletion() {
    SecurityContextBuilder.unsetCompleteContext();
  }
}
