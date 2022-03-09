/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cvng.beans.cvnglog;

public enum CVNGLogType {
  API_CALL_LOG("ApiCallLog"),
  EXECUTION_LOG("ExecutionLog");

  private String displayName;

  CVNGLogType(String displayName) {
    this.displayName = displayName;
  }

  public String getDisplayName() {
    return displayName;
  }

  public static CVNGLogType toCVNGLogType(String cvngLogType) {
    if (cvngLogType.equalsIgnoreCase("ApiCallLog")) {
      return CVNGLogType.API_CALL_LOG;
    } else if (cvngLogType.equalsIgnoreCase("ExecutionLog")) {
      return CVNGLogType.EXECUTION_LOG;
    } else {
      throw new UnsupportedOperationException("logType should either be ApiCallLog or ExecutionLog");
    }
  }
}
