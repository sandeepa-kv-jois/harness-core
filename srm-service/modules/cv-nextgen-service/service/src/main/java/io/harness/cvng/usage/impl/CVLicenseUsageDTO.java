/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.usage.impl;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import io.harness.ModuleType;
import io.harness.licensing.usage.beans.LicenseUsageDTO;
import io.harness.licensing.usage.beans.UsageDataDTO;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(NON_NULL)
public class CVLicenseUsageDTO extends LicenseUsageDTO {
  UsageDataDTO activeServices;

  @Override
  public String getModule() {
    return ModuleType.CV.toString();
  }
}
