/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.gitops.steps;

import io.harness.annotation.RecasterAlias;

import java.util.Set;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.TypeAlias;

@Data
@Builder
@TypeAlias("envClusterRefs")
@RecasterAlias("io.harness.cdng.gitops.steps.EnvClusterRefs")
public class EnvClusterRefs {
  private String envRef;
  private String envName;
  private Set<String> clusterRefs;
  boolean deployToAll;
}
