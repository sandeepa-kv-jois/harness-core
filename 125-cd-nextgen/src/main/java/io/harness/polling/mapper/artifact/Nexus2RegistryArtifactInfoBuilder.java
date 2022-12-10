/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.polling.mapper.artifact;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.polling.bean.PollingInfo;
import io.harness.polling.bean.artifact.Nexus2RegistryArtifactInfo;
import io.harness.polling.contracts.PollingPayloadData;
import io.harness.polling.mapper.PollingInfoBuilder;

@OwnedBy(HarnessTeam.CDP)
public class Nexus2RegistryArtifactInfoBuilder implements PollingInfoBuilder {
  @Override
  public PollingInfo toPollingInfo(PollingPayloadData pollingPayloadData) {
    return Nexus2RegistryArtifactInfo.builder()
        .connectorRef(pollingPayloadData.getConnectorRef())
        .repositoryName(pollingPayloadData.getNexus2RegistryPayload().getRepository())
        .repositoryFormat(pollingPayloadData.getNexus2RegistryPayload().getRepositoryFormat())
        .artifactId(pollingPayloadData.getNexus2RegistryPayload().getArtifactId())
        .groupId(pollingPayloadData.getNexus2RegistryPayload().getGroupId())
        .packageName(pollingPayloadData.getNexus2RegistryPayload().getPackageName())
        .classifier(pollingPayloadData.getNexus2RegistryPayload().getClassifier())
        .extension(pollingPayloadData.getNexus2RegistryPayload().getExtension())
        .build();
  }
}
