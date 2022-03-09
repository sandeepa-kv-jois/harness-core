package io.harness.delegate.events;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.audit.ResourceTypeConstants;
import io.harness.delegate.beans.DelegateSetupDetails;
import io.harness.ng.core.Resource;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@OwnedBy(HarnessTeam.DEL)
@Getter
@SuperBuilder
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class DelegateUnregisterEvent extends AbstractDelegateConfigurationEvent {
  private String delegateId;
  private DelegateSetupDetails delegateSetupDetails;
  public static final String DELEGATE_UNREGISTER_EVENT = "DelegateUnRegisterEvent";

  @Override
  public Resource getResource() {
    return Resource.builder().identifier(delegateSetupDetails.getName()).type(ResourceTypeConstants.DELEGATE).build();
  }

  @Override
  public String getEventType() {
    return DELEGATE_UNREGISTER_EVENT;
  }
}
