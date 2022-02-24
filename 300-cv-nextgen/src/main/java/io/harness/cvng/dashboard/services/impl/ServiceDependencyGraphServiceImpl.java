/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.dashboard.services.impl;

import static io.harness.annotations.dev.HarnessTeam.CV;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cvng.analysis.beans.Risk;
import io.harness.cvng.client.NextGenService;
import io.harness.cvng.core.beans.monitoredService.RiskData;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.entities.MonitoredService;
import io.harness.cvng.core.entities.ServiceDependency;
import io.harness.cvng.core.services.api.monitoredService.MonitoredServiceService;
import io.harness.cvng.core.services.api.monitoredService.ServiceDependencyService;
import io.harness.cvng.dashboard.beans.ServiceDependencyGraphDTO;
import io.harness.cvng.dashboard.beans.ServiceDependencyGraphDTO.Edge;
import io.harness.cvng.dashboard.beans.ServiceDependencyGraphDTO.ServiceSummaryDetails;
import io.harness.cvng.dashboard.services.api.HeatMapService;
import io.harness.cvng.dashboard.services.api.ServiceDependencyGraphService;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(CV)
public class ServiceDependencyGraphServiceImpl implements ServiceDependencyGraphService {
  @Inject private ServiceDependencyService serviceDependencyService;
  @Inject private MonitoredServiceService monitoredServiceService;
  @Inject private NextGenService nextGenService;
  @Inject private HeatMapService heatMapService;

  @Override
  public ServiceDependencyGraphDTO getDependencyGraph(@NonNull ProjectParams projectParams,
      @Nullable String serviceIdentifier, @Nullable String environmentIdentifier,
      @NonNull boolean servicesAtRiskFilter) {
    if (serviceIdentifier == null || environmentIdentifier == null) {
      return getDependencyGraph(projectParams, null, servicesAtRiskFilter);
    }
    List<MonitoredService> monitoredServices =
        monitoredServiceService.list(projectParams, serviceIdentifier, environmentIdentifier);
    return getDependencyGraph(projectParams, monitoredServices.get(0).getIdentifier(), servicesAtRiskFilter);
  }

  @Override
  public ServiceDependencyGraphDTO getDependencyGraph(@NonNull ProjectParams projectParams,
      @Nullable String monitoredServiceIdentifier, @NonNull boolean servicesAtRiskFilter) {
    List<MonitoredService> monitoredServices;
    if (monitoredServiceIdentifier != null) {
      monitoredServices = monitoredServiceService.list(projectParams, Arrays.asList(monitoredServiceIdentifier));
    } else {
      monitoredServices = monitoredServiceService.list(projectParams, Collections.emptyList());
    }
    Set<String> monitoredServiceIdentifiers =
        monitoredServices.stream().map(MonitoredService::getIdentifier).collect(Collectors.toSet());
    List<ServiceDependency> serviceDependencies =
        serviceDependencyService.getServiceDependencies(projectParams, new ArrayList<>(monitoredServiceIdentifiers));

    // Get nodes for dependent services
    serviceDependencies.forEach(
        serviceDependency -> monitoredServiceIdentifiers.add(serviceDependency.getFromMonitoredServiceIdentifier()));
    monitoredServices = monitoredServiceService.list(projectParams, new ArrayList<>(monitoredServiceIdentifiers));

    Set<String> serviceIdentifiers =
        monitoredServices.stream().map(MonitoredService::getServiceIdentifier).collect(Collectors.toSet());
    Set<String> environmentIdentifiers =
        monitoredServices.stream().map(MonitoredService::getEnvironmentIdentifier).collect(Collectors.toSet());

    Map<String, RiskData> latestHealthScores =
        heatMapService.getLatestHealthScore(projectParams, new ArrayList<>(monitoredServiceIdentifiers));

    ServiceDependencyGraphDTO serviceDependencyGraphDTO =
        constructGraphUsingMonitoredServiceIdentifier(monitoredServices, serviceDependencies, latestHealthScores,
            nextGenService.getServiceIdNameMap(projectParams, new ArrayList<>(serviceIdentifiers)),
            nextGenService.getEnvironmentIdNameMap(projectParams, new ArrayList<>(environmentIdentifiers)));

    if (servicesAtRiskFilter) {
      List<ServiceSummaryDetails> unHealthyServiceSummaryDetails =
          serviceDependencyGraphDTO.getNodes()
              .stream()
              .filter(x -> x.getRiskData().getHealthScore() != null && x.getRiskData().getHealthScore() <= 25)
              .collect(Collectors.toList());
      Map<String, ServiceSummaryDetails> serviceSummaryDetailsMap = unHealthyServiceSummaryDetails.stream().collect(
          Collectors.toMap(ServiceSummaryDetails::getIdentifierRef, x -> x));
      List<Edge> unHealthyEdges = serviceDependencyGraphDTO.getEdges()
                                      .stream()
                                      .filter(x
                                          -> serviceSummaryDetailsMap.containsKey(x.getFrom())
                                              && serviceSummaryDetailsMap.containsKey(x.getTo()))
                                      .collect(Collectors.toList());
      serviceDependencyGraphDTO.setNodes(unHealthyServiceSummaryDetails);
      serviceDependencyGraphDTO.setEdges(unHealthyEdges);
    }

    return serviceDependencyGraphDTO;
  }

  private ServiceDependencyGraphDTO constructGraphUsingMonitoredServiceIdentifier(
      List<MonitoredService> monitoredServices, List<ServiceDependency> serviceDependencies,
      Map<String, RiskData> latestHealthScores, Map<String, String> serviceIdNameMap,
      Map<String, String> environmentIdNameMap) {
    Map<String, MonitoredService> monitoredServiceMap =
        monitoredServices.stream().collect(Collectors.toMap(x -> x.getIdentifier(), x -> x));

    List<ServiceSummaryDetails> nodes = new ArrayList<>();
    monitoredServiceMap.forEach((key, value) -> {
      String serviceName = null;
      String environmentName = null;

      if (serviceIdNameMap.containsKey(value.getServiceIdentifier())) {
        serviceName = serviceIdNameMap.get(value.getServiceIdentifier());
      }
      if (environmentIdNameMap.containsKey(value.getServiceIdentifier())) {
        environmentName = environmentIdNameMap.get(value.getServiceIdentifier());
      }
      RiskData riskData = RiskData.builder().riskStatus(Risk.NO_DATA).build();
      if (latestHealthScores.containsKey(key)) {
        riskData = latestHealthScores.get(key);
      }

      nodes.add(ServiceSummaryDetails.builder()
                    .identifierRef(value.getIdentifier())
                    .serviceRef(value.getServiceIdentifier())
                    .serviceName(serviceName)
                    .environmentRef(value.getEnvironmentIdentifier())
                    .environmentName(environmentName)
                    .riskData(riskData)
                    .riskLevel(riskData.getRiskStatus())
                    .type(value.getType())
                    .build());
    });

    Set<Edge> edges =
        serviceDependencies.stream()
            .map(x -> new Edge(x.getFromMonitoredServiceIdentifier(), x.getToMonitoredServiceIdentifier()))
            .collect(Collectors.toSet());
    return ServiceDependencyGraphDTO.builder().nodes(nodes).edges(new ArrayList<>(edges)).build();
  }
}
