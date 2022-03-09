/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.schema.query;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;

import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldNameConstants;

@Value
@Builder
@TargetModule(HarnessModule._380_CG_GRAPHQL)
@FieldNameConstants(innerTypeName = "QLInfrastructureDefinitionQueryParametersKeys")
public class QLInfrastructureDefinitionQueryParameters {
  private String infrastructureId;
  private String infrastructureName;
  private String environmentId;
}
