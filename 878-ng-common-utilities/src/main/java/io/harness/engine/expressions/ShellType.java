/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.expressions;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.shell.ScriptType;

@OwnedBy(PL)
public enum ShellType {
  Bash(ScriptType.BASH);

  private final ScriptType scriptType;

  ShellType(ScriptType scriptType) {
    this.scriptType = scriptType;
  }

  public ScriptType getScriptType() {
    return scriptType;
  }
}