/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.execution;

import static io.harness.pms.contracts.execution.ExecutionMode.APPROVAL;
import static io.harness.pms.contracts.execution.ExecutionMode.ASYNC;
import static io.harness.pms.contracts.execution.ExecutionMode.CHILD;
import static io.harness.pms.contracts.execution.ExecutionMode.CHILDREN;
import static io.harness.pms.contracts.execution.ExecutionMode.CHILD_CHAIN;
import static io.harness.pms.contracts.execution.ExecutionMode.CONSTRAINT;
import static io.harness.pms.contracts.execution.ExecutionMode.SYNC;
import static io.harness.pms.contracts.execution.ExecutionMode.TASK;
import static io.harness.pms.contracts.execution.ExecutionMode.TASK_CHAIN;
import static io.harness.pms.contracts.execution.ExecutionMode.WAIT_STEP;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.execution.ExecutionMode;

import java.util.EnumSet;
import java.util.Set;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(HarnessTeam.PIPELINE)
public class ExecutionModeUtils {
  private final Set<ExecutionMode> CHAIN_MODES = EnumSet.of(TASK_CHAIN, CHILD_CHAIN);

  private final Set<ExecutionMode> PARENT_MODES = EnumSet.of(CHILD_CHAIN, CHILDREN, CHILD);

  private final Set<ExecutionMode> TASK_MODES = EnumSet.of(TASK, TASK_CHAIN);

  private final Set<ExecutionMode> LEAF_MODES =
      EnumSet.of(TASK, TASK_CHAIN, ASYNC, SYNC, APPROVAL, CONSTRAINT, WAIT_STEP);

  public Set<ExecutionMode> chainModes() {
    return CHAIN_MODES;
  }

  public Set<ExecutionMode> leafModes() {
    return LEAF_MODES;
  }

  public Set<ExecutionMode> parentModes() {
    return PARENT_MODES;
  }

  public boolean isParentMode(ExecutionMode mode) {
    return PARENT_MODES.contains(mode);
  }

  public boolean isChainMode(ExecutionMode mode) {
    return CHAIN_MODES.contains(mode);
  }

  public boolean isTaskMode(ExecutionMode mode) {
    return TASK_MODES.contains(mode);
  }

  public boolean isLeafMode(ExecutionMode mode) {
    return LEAF_MODES.contains(mode);
  }
}
