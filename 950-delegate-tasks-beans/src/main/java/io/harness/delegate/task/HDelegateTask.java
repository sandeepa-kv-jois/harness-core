/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.TaskDataV2;
import io.harness.tasks.Task;

import java.util.LinkedHashMap;
import java.util.Map;

@OwnedBy(CDC)
public interface HDelegateTask extends Task {
  String getAccountId();
  Map<String, String> getSetupAbstractions();
  TaskData getData();

  TaskDataV2 getTaskDataV2();
  LinkedHashMap<String, String> getLogStreamingAbstractions();
}
