/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.api.monitoredService;

import io.harness.cvng.beans.change.ChangeSourceType;
import io.harness.cvng.core.beans.change.ChangeSummaryDTO;
import io.harness.cvng.core.beans.monitoredService.ChangeSourceDTO;
import io.harness.cvng.core.beans.params.MonitoredServiceParams;
import io.harness.cvng.core.beans.params.ServiceEnvironmentParams;
import io.harness.cvng.core.entities.changeSource.ChangeSource;
import io.harness.cvng.core.entities.changeSource.HarnessCDCurrentGenChangeSource;
import io.harness.cvng.core.entities.changeSource.KubernetesChangeSource;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public interface ChangeSourceService {
  void create(MonitoredServiceParams monitoredServiceParams, Set<ChangeSourceDTO> changeSourceDTOs);

  Set<ChangeSourceDTO> get(ServiceEnvironmentParams environmentParams, List<String> identifiers);
  ChangeSource get(ServiceEnvironmentParams serviceEnvironmentParams, String identifier);

  List<ChangeSource> getEntityByType(ServiceEnvironmentParams environmentParams, ChangeSourceType changeSourceType);

  void delete(ServiceEnvironmentParams environmentParams, List<String> identifiers);

  void update(MonitoredServiceParams monitoredServiceParams, Set<ChangeSourceDTO> changeSourceDTOs);

  void enqueueDataCollectionTask(KubernetesChangeSource changeSource);

  ChangeSummaryDTO getChangeSummary(ServiceEnvironmentParams serviceEnvironmentParams,
      List<String> changeSourceIdentifiers, Instant startTime, Instant endTime);

  void handleCurrentGenEvents(HarnessCDCurrentGenChangeSource changeSource);

  void generateDemoData(ChangeSource entity);
}
