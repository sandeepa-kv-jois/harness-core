package io.harness.gitsync.common.events;

import static io.harness.eventsframework.EventsFrameworkMetadataConstants.*;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.consumer.Message;
import io.harness.eventsframework.entity_crud.project.ProjectEntityChangeDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.gitsync.common.service.YamlGitConfigService;
import io.harness.ng.core.event.MessageListener;

import com.google.inject.Inject;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.Map;
import lombok.AllArgsConstructor;

@OwnedBy(HarnessTeam.PL)
@AllArgsConstructor(onConstructor = @__({ @Inject }))
public class GitSyncConfigEntityCRUDStreamListener implements MessageListener {
  private final YamlGitConfigService yamlGitConfigService;

  @Override
  public boolean handleMessage(Message message) {
    if (message != null && message.hasMessage()) {
      Map<String, String> metadataMap = message.getMessage().getMetadataMap();
      if (metadataMap != null && metadataMap.get(ENTITY_TYPE) != null) {
        String entityType = metadataMap.get(ENTITY_TYPE);
        if (PROJECT_ENTITY.equals(entityType)) {
          return processProjectChangeEvent(message);
        }
      }
    }
    return true;
  }

  private boolean processProjectChangeEvent(Message message) {
    ProjectEntityChangeDTO projectEntityChangeDTO;
    try {
      projectEntityChangeDTO = ProjectEntityChangeDTO.parseFrom(message.getMessage().getData());
    } catch (InvalidProtocolBufferException e) {
      throw new InvalidRequestException(
          String.format("Exception in unpacking ProjectEntityChangeDTO for key %s", message.getId()), e);
    }
    String action = message.getMessage().getMetadataMap().get(ACTION);
    if (action != null) {
      if (action.equals(DELETE_ACTION)) {
        return processProjectDeleteEvent(projectEntityChangeDTO);
      }
    }
    return true;
  }

  private boolean processProjectDeleteEvent(ProjectEntityChangeDTO projectEntityChangeDTO) {
    return yamlGitConfigService.deleteAll(projectEntityChangeDTO.getAccountIdentifier(),
        projectEntityChangeDTO.getOrgIdentifier(), projectEntityChangeDTO.getIdentifier());
  }
}