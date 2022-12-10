/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.scm.gitlab;

import io.harness.delegate.beans.connector.scm.gitlab.outcome.GitlabCredentialsOutcomeDTO;
import io.harness.delegate.beans.connector.scm.gitlab.outcome.GitlabHttpCredentialsOutcomeDTO;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@ApiModel("GitlabHttpCredentials")
@Schema(name = "GitlabHttpCredentials",
    description = "This contains details of the Gitlab credentials used via HTTP connections")
public class GitlabHttpCredentialsDTO implements GitlabCredentialsDTO {
  @NotNull GitlabHttpAuthenticationType type;
  @JsonProperty("spec")
  @JsonTypeInfo(
      use = JsonTypeInfo.Id.NAME, property = "type", include = JsonTypeInfo.As.EXTERNAL_PROPERTY, visible = true)
  @Valid
  @NotNull
  GitlabHttpCredentialsSpecDTO httpCredentialsSpec;

  @Builder
  public GitlabHttpCredentialsDTO(GitlabHttpAuthenticationType type, GitlabHttpCredentialsSpecDTO httpCredentialsSpec) {
    this.type = type;
    this.httpCredentialsSpec = httpCredentialsSpec;
  }

  @Override
  public GitlabCredentialsOutcomeDTO toOutcome() {
    return GitlabHttpCredentialsOutcomeDTO.builder().type(this.type).spec(this.httpCredentialsSpec).build();
  }
}
