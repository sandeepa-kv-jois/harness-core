/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.k8s.releasehistory;

import java.util.Map;
import lombok.experimental.UtilityClass;

@UtilityClass
public class K8sReleaseConstants {
  public static final String RELEASE_KEY = "release";
  public static final String RELEASE_NAME_DELIMITER = ".";
  public static final String SECRET_LABEL_DELIMITER = ",";
  public static final String RELEASE_NUMBER_LABEL_KEY = "release-number";
  public static final String RELEASE_OWNER_LABEL_KEY = "owner";
  public static final String RELEASE_OWNER_LABEL_VALUE = "harness";
  public static final String RELEASE_STATUS_LABEL_KEY = "status";
  public static final String RELEASE_SECRET_TYPE_KEY = "type";
  public static final String RELEASE_SECRET_TYPE_VALUE = "harness.io/release/v2";
  public static final String RELEASE_PRUNING_ENABLED_KEY = "harness.io/pruning-enabled";
  public static final Map<String, String> RELEASE_SECRET_TYPE_MAP =
      Map.of(RELEASE_SECRET_TYPE_KEY, RELEASE_SECRET_TYPE_VALUE);
  public static final Map<String, String> RELEASE_SECRET_LABELS_MAP =
      Map.of(RELEASE_OWNER_LABEL_KEY, RELEASE_OWNER_LABEL_VALUE);
  public static final int RELEASE_HISTORY_LIMIT = 5;
  public static final String RELEASE_LABEL_QUERY_SET_FORMAT = "%s in (%s)";
  public static final String RELEASE_LABEL_QUERY_LIST_FORMAT = "%s=%s";
}
