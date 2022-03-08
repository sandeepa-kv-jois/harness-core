package io.harness.secretmanagerclient.dto;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@OwnedBy(PL)
@Data
@Builder
@EqualsAndHashCode(callSuper = true)
@JsonTypeName("K8s_AUTH")
public class VaultK8sCredentialDTO extends VaultCredentialDTO {
  private String vaultK8sAuthRole;
  private String serviceAccountTokenPath;
}
