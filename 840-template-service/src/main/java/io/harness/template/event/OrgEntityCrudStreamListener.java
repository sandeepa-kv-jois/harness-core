/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.template.event;

import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.DELETE_ACTION;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ENTITY_TYPE;
import static io.harness.eventsframework.EventsFrameworkMetadataConstants.ORGANIZATION_ENTITY;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.entity_crud.organization.OrganizationEntityChangeDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.event.MessageListener;
import io.harness.template.services.NGTemplateService;

import com.google.inject.Inject;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.Map;

@OwnedBy(HarnessTeam.CDC)
public class OrgEntityCrudStreamListener implements MessageListener {
  private final NGTemplateService templateService;

  @Inject
  public OrgEntityCrudStreamListener(NGTemplateService templateService) {
    this.templateService = templateService;
  }

  @Override
  public boolean handleMessage(Message message) {
    if (message != null && message.hasMessage()) {
      Map<String, String> metadataMap = message.getMessage().getMetadataMap();
      if (metadataMap != null && metadataMap.get(ENTITY_TYPE) != null
          && ORGANIZATION_ENTITY.equals(metadataMap.get(ENTITY_TYPE))) {
        OrganizationEntityChangeDTO entityChangeDTO;
        try {
          entityChangeDTO = OrganizationEntityChangeDTO.parseFrom(message.getMessage().getData());
        } catch (InvalidProtocolBufferException e) {
          throw new InvalidRequestException(
              String.format("Exception in unpacking EntityChangeDTO for key %s", message.getId()), e);
        }
        String action = metadataMap.get(ACTION);
        if (action != null) {
          return processOrgEntityChangeEvent(entityChangeDTO, action);
        }
      }
    }
    return true;
  }

  private boolean processOrgEntityChangeEvent(OrganizationEntityChangeDTO entityChangeDTO, String action) {
    if (DELETE_ACTION.equals(action)) {
      processDeleteEvent(entityChangeDTO);
    }
    return true;
  }

  private void processDeleteEvent(OrganizationEntityChangeDTO entityChangeDTO) {
    templateService.deleteAllOrgLevelTemplates(entityChangeDTO.getAccountIdentifier(), entityChangeDTO.getIdentifier());
  }
}
