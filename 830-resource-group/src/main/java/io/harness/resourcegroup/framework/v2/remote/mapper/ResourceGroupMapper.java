/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.resourcegroup.framework.v2.remote.mapper;

import static io.harness.NGConstants.HARNESS_BLUE;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.ng.core.mapper.TagMapper.convertToList;
import static io.harness.ng.core.mapper.TagMapper.convertToMap;

import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.OwnedBy;
import io.harness.resourcegroup.beans.ScopeFilterType;
import io.harness.resourcegroup.v1.model.DynamicResourceSelector;
import io.harness.resourcegroup.v1.model.ResourceSelector;
import io.harness.resourcegroup.v1.model.ResourceSelectorByScope;
import io.harness.resourcegroup.v1.model.StaticResourceSelector;
import io.harness.resourcegroup.v2.model.ResourceFilter;
import io.harness.resourcegroup.v2.model.ResourceGroup;
import io.harness.resourcegroup.v2.model.ScopeSelector;
import io.harness.resourcegroup.v2.remote.dto.ResourceGroupDTO;
import io.harness.resourcegroupclient.remote.v2.ResourceGroupResponse;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(PL)
public class ResourceGroupMapper {
  public static ResourceGroup fromDTO(ResourceGroupDTO resourceGroupDTO) {
    if (resourceGroupDTO == null) {
      return null;
    }
    ResourceGroup resourceGroup =
        ResourceGroup.builder()
            .accountIdentifier(resourceGroupDTO.getAccountIdentifier())
            .orgIdentifier(resourceGroupDTO.getOrgIdentifier())
            .projectIdentifier(resourceGroupDTO.getProjectIdentifier())
            .identifier(resourceGroupDTO.getIdentifier())
            .name(resourceGroupDTO.getName())
            .color(isBlank(resourceGroupDTO.getColor()) ? HARNESS_BLUE : resourceGroupDTO.getColor())
            .tags(convertToList(resourceGroupDTO.getTags()))
            .description(resourceGroupDTO.getDescription())
            .allowedScopeLevels(resourceGroupDTO.getAllowedScopeLevels() == null
                    ? new HashSet<>()
                    : resourceGroupDTO.getAllowedScopeLevels())
            .includedScopes(
                resourceGroupDTO.getIncludedScopes() == null ? new ArrayList<>() : resourceGroupDTO.getIncludedScopes())
            .build();

    if (resourceGroupDTO.getResourceFilter() != null) {
      resourceGroup.setResourceFilter(resourceGroupDTO.getResourceFilter());
    }

    return resourceGroup;
  }

  public static ResourceGroupDTO toDTO(ResourceGroup resourceGroup) {
    if (resourceGroup == null) {
      return null;
    }
    return ResourceGroupDTO.builder()
        .accountIdentifier(resourceGroup.getAccountIdentifier())
        .orgIdentifier(resourceGroup.getOrgIdentifier())
        .projectIdentifier(resourceGroup.getProjectIdentifier())
        .identifier(resourceGroup.getIdentifier())
        .name(resourceGroup.getName())
        .color(resourceGroup.getColor())
        .tags(convertToMap(resourceGroup.getTags()))
        .description(resourceGroup.getDescription())
        .allowedScopeLevels(
            resourceGroup.getAllowedScopeLevels() == null ? new HashSet<>() : resourceGroup.getAllowedScopeLevels())
        .includedScopes(resourceGroup.getIncludedScopes())
        .resourceFilter(resourceGroup.getResourceFilter())
        .build();
  }

  public static ResourceGroupResponse toResponseWrapper(ResourceGroup resourceGroup) {
    if (resourceGroup == null) {
      return null;
    }
    return ResourceGroupResponse.builder()
        .createdAt(resourceGroup.getCreatedAt())
        .lastModifiedAt(resourceGroup.getLastModifiedAt())
        .resourceGroup(toDTO(resourceGroup))
        .harnessManaged(Boolean.TRUE.equals(resourceGroup.getHarnessManaged()))
        .build();
  }

  public static ResourceGroup fromV1(io.harness.resourcegroup.v1.model.ResourceGroup resourceGroup) {
    if (resourceGroup == null) {
      return null;
    }

    ResourceGroup resourceGroupV2 =
        ResourceGroup.builder()
            .accountIdentifier(resourceGroup.getAccountIdentifier())
            .orgIdentifier(resourceGroup.getOrgIdentifier())
            .projectIdentifier(resourceGroup.getProjectIdentifier())
            .identifier(resourceGroup.getIdentifier())
            .name(resourceGroup.getName())
            .color(isBlank(resourceGroup.getColor()) ? HARNESS_BLUE : resourceGroup.getColor())
            .tags(resourceGroup.getTags())
            .description(resourceGroup.getDescription())
            .harnessManaged(resourceGroup.getHarnessManaged())
            .allowedScopeLevels(
                resourceGroup.getAllowedScopeLevels() == null ? new HashSet<>() : resourceGroup.getAllowedScopeLevels())
            .build();

    List<io.harness.resourcegroup.v2.model.ResourceSelector> resources = new ArrayList<>();
    List<ScopeSelector> includedScopes = new ArrayList<>();

    AtomicBoolean includeChildScopes = new AtomicBoolean(false);
    AtomicBoolean includeAllResources = new AtomicBoolean(resourceGroup.getHarnessManaged() ? true : false);

    for (Iterator<ResourceSelector> iterator = resourceGroup.getResourceSelectors().iterator(); iterator.hasNext();) {
      ResourceSelector resourceSelector = iterator.next();
      if (resourceSelector instanceof StaticResourceSelector) {
        resources.add(io.harness.resourcegroup.v2.model.ResourceSelector.builder()
                          .resourceType(((StaticResourceSelector) resourceSelector).getResourceType())
                          .identifiers(((StaticResourceSelector) resourceSelector).getIdentifiers())
                          .build());
      } else if (resourceSelector instanceof DynamicResourceSelector) {
        resources.add(io.harness.resourcegroup.v2.model.ResourceSelector.builder()
                          .resourceType(((DynamicResourceSelector) resourceSelector).getResourceType())
                          .build());
        if (((DynamicResourceSelector) resourceSelector).getIncludeChildScopes()) {
          includeChildScopes.set(true);
        }
      } else if (resourceSelector instanceof ResourceSelectorByScope) {
        includeAllResources.set(true);
        if (((ResourceSelectorByScope) resourceSelector).isIncludeChildScopes()) {
          includeChildScopes.set(true);
        }
      }
    }

    includedScopes.add(ScopeSelector.builder()
                           .accountIdentifier(resourceGroup.getAccountIdentifier())
                           .orgIdentifier(resourceGroup.getOrgIdentifier())
                           .projectIdentifier(resourceGroup.getProjectIdentifier())
                           .filter(includeChildScopes.get() ? ScopeFilterType.INCLUDING_CHILD_SCOPES
                                                            : ScopeFilterType.EXCLUDING_CHILD_SCOPES)
                           .build());

    resourceGroupV2.setIncludedScopes(includedScopes);
    resourceGroupV2.setResourceFilter(
        ResourceFilter.builder().includeAllResources(includeAllResources.get()).resources(resources).build());

    return resourceGroupV2;
  }
}
