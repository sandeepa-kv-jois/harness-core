/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.azure.config.yaml;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigWrapper;
import io.harness.cdng.visitor.helpers.azure.StartupCommandConfigurationVisitorHelper;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.walktree.beans.VisitableChildren;
import io.harness.walktree.visitor.SimpleVisitorHelper;
import io.harness.walktree.visitor.Visitable;

import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@Value
@Builder
@SimpleVisitorHelper(helperClass = StartupCommandConfigurationVisitorHelper.class)
@TypeAlias("startupCommandConfiguration")
@RecasterAlias("io.harness.cdng.azure.config.yaml.StartupCommandConfiguration")
@OwnedBy(HarnessTeam.CDP)
public class StartupCommandConfiguration implements Visitable {
  @NotNull StoreConfigWrapper store;

  @Override
  public VisitableChildren getChildrenToWalk() {
    VisitableChildren children = VisitableChildren.builder().build();
    children.add(YAMLFieldNameConstants.STORE, store);
    return children;
  }
}
