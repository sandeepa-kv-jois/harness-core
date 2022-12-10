/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.k8s.releasehistory;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.k8s.releasehistory.K8sReleaseConstants.RELEASE_KEY;
import static io.harness.k8s.releasehistory.K8sReleaseConstants.RELEASE_LABEL_QUERY_LIST_FORMAT;
import static io.harness.k8s.releasehistory.K8sReleaseConstants.RELEASE_LABEL_QUERY_SET_FORMAT;
import static io.harness.k8s.releasehistory.K8sReleaseConstants.RELEASE_NAME_DELIMITER;
import static io.harness.k8s.releasehistory.K8sReleaseConstants.RELEASE_NUMBER_LABEL_KEY;
import static io.harness.k8s.releasehistory.K8sReleaseConstants.RELEASE_OWNER_LABEL_KEY;
import static io.harness.k8s.releasehistory.K8sReleaseConstants.RELEASE_OWNER_LABEL_VALUE;
import static io.harness.k8s.releasehistory.K8sReleaseConstants.RELEASE_SECRET_LABELS_MAP;
import static io.harness.k8s.releasehistory.K8sReleaseConstants.RELEASE_STATUS_LABEL_KEY;
import static io.harness.k8s.releasehistory.K8sReleaseConstants.SECRET_LABEL_DELIMITER;

import static org.apache.commons.lang3.StringUtils.EMPTY;

import io.harness.annotations.dev.OwnedBy;

import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1ObjectMetaBuilder;
import io.kubernetes.client.openapi.models.V1Secret;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import lombok.experimental.UtilityClass;

@OwnedBy(CDP)
@UtilityClass
public class K8sReleaseSecretHelper {
  public String createSetBasedArg(@NotNull String key, @NotNull Set<String> values) {
    return String.format(RELEASE_LABEL_QUERY_SET_FORMAT, key, String.join(SECRET_LABEL_DELIMITER, values));
  }

  public String createListBasedArg(@NotNull String key, @NotNull String value) {
    return String.format(RELEASE_LABEL_QUERY_LIST_FORMAT, key, value);
  }

  public String createCommaSeparatedKeyValueList(@NotNull Map<String, String> k8sArg) {
    return k8sArg.entrySet()
        .stream()
        .map(entry -> createListBasedArg(entry.getKey(), entry.getValue()))
        .collect(Collectors.joining(SECRET_LABEL_DELIMITER));
  }

  public String generateName(@NotNull String releaseName, int releaseNumber) {
    return String.join(RELEASE_NAME_DELIMITER, RELEASE_KEY, releaseName, String.valueOf(releaseNumber));
  }

  public Map<String, String> generateLabels(@NotNull String releaseName, int releaseNumber, @NotNull String status) {
    return Map.of(RELEASE_KEY, releaseName, RELEASE_NUMBER_LABEL_KEY, String.valueOf(releaseNumber),
        RELEASE_OWNER_LABEL_KEY, RELEASE_OWNER_LABEL_VALUE, RELEASE_STATUS_LABEL_KEY, status);
  }

  public Map<String, String> createLabelsMap(@NotNull String releaseName) {
    Map<String, String> labels = new HashMap<>(RELEASE_SECRET_LABELS_MAP);
    labels.put(RELEASE_KEY, releaseName);
    return labels;
  }

  public String getReleaseLabelValue(@NotNull V1Secret release, @NotNull String labelKey) {
    if (release.getMetadata() != null && release.getMetadata().getLabels() != null
        && release.getMetadata().getLabels().containsKey(labelKey)) {
      return release.getMetadata().getLabels().get(labelKey);
    }
    return EMPTY;
  }

  public V1Secret putLabelsItem(@NotNull V1Secret release, @NotNull String labelKey, @NotNull String labelValue) {
    V1ObjectMeta objectMeta = release.getMetadata();
    if (objectMeta == null) {
      objectMeta = new V1ObjectMetaBuilder().build();
    }

    objectMeta.putLabelsItem(labelKey, labelValue);
    release.setMetadata(objectMeta);
    return release;
  }
}
