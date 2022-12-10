/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.ci;

import io.harness.delegate.task.TaskParameters;

public interface CICleanupTaskParams extends TaskParameters {
  enum Type { GCP_K8, VM, DLITE_VM }

  CICleanupTaskParams.Type getType();
}
