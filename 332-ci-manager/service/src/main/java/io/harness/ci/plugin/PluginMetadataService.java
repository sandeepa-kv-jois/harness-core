/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plugin;

import static io.harness.utils.PageUtils.getPageRequest;

import io.harness.NGResourceFilterConstants;
import io.harness.app.beans.entities.PluginMetadataConfig;
import io.harness.app.beans.entities.PluginMetadataConfig.PluginMetadataConfigKeys;
import io.harness.app.beans.entities.PluginMetadataStatus;
import io.harness.beans.PluginMetadata;
import io.harness.beans.SortOrder;
import io.harness.beans.plugin.api.PluginMetadataResponse;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.beans.PageResponse;
import io.harness.repositories.PluginMetadataRepository;
import io.harness.repositories.PluginMetadataStatusRepository;
import io.harness.utils.PageUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.ws.rs.InternalServerErrorException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;

@Singleton
public class PluginMetadataService {
  @Inject PluginMetadataRepository pluginMetadataRepository;
  @Inject PluginMetadataStatusRepository pluginMetadataStatusRepository;

  public PageResponse<PluginMetadataResponse> listPlugins(String searchTerm, int page, int size) {
    PluginMetadataStatus pluginMetadataStatus = pluginMetadataStatusRepository.find();
    if (pluginMetadataStatus == null) {
      throw new InternalServerErrorException("Plugin schema is not populated");
    }

    int version = pluginMetadataStatus.getVersion();
    Criteria criteria = Criteria.where(PluginMetadataConfigKeys.version).is(version);
    if (!StringUtils.isEmpty(searchTerm)) {
      criteria = criteria.orOperator(Criteria.where(PluginMetadataConfigKeys.metadata + ".name")
                                         .regex(searchTerm, NGResourceFilterConstants.CASE_INSENSITIVE_MONGO_OPTIONS));
    }
    Pageable pageable =
        getPageRequest(PageRequest.builder()
                           .pageIndex(page)
                           .pageSize(size)
                           .sortOrders(Collections.singletonList(
                               SortOrder.Builder.aSortOrder()
                                   .withField(PluginMetadataConfigKeys.metadata + ".name", SortOrder.OrderType.ASC)
                                   .build()))
                           .build());
    Page<PluginMetadataConfig> pluginMetadataConfigs = pluginMetadataRepository.findAll(criteria, pageable);
    return PageUtils.getNGPageResponse(pluginMetadataConfigs,
        pluginMetadataConfigs.getContent().stream().map(this::metadataMapper).collect(Collectors.toList()));
  }

  private PluginMetadataResponse metadataMapper(PluginMetadataConfig config) {
    PluginMetadata metadata = config.getMetadata();
    return PluginMetadataResponse.builder()
        .name(metadata.getName())
        .description(metadata.getDescription())
        .kind(metadata.getKind())
        .logo(metadata.getLogo())
        .image(metadata.getImage())
        .repo(metadata.getRepo())
        .inputs(Optional.ofNullable(metadata.getInputs())
                    .orElseGet(Collections::emptyList)
                    .stream()
                    .filter(Objects::nonNull)
                    .map(this::inputMapper)
                    .collect(Collectors.toList()))
        .uses(metadata.getUses())
        .build();
  }

  private PluginMetadataResponse.Input inputMapper(PluginMetadata.Input input) {
    return PluginMetadataResponse.Input.builder()
        .name(input.getName())
        .description(input.getDescription())
        .defaultVal(input.getDefaultVal())
        .allowedValues(input.getAllowedValues())
        .secret(input.isSecret())
        .required(input.isRequired())
        .build();
  }
}
