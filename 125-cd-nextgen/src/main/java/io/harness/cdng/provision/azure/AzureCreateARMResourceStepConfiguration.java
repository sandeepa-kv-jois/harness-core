/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.provision.azure;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SwaggerConstants;
import io.harness.cdng.manifest.ManifestStoreType;
import io.harness.cdng.manifest.yaml.GitStoreConfig;
import io.harness.cdng.manifest.yaml.harness.HarnessStore;
import io.harness.common.ParameterFieldHelper;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlNode;
import io.harness.validation.Validator;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModelProperty;
import java.util.Objects;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.CDP)
@RecasterAlias("io.harness.cdng.provision.azure.AzureCreateARMResourceStepConfiguration")
public class AzureCreateARMResourceStepConfiguration {
  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  String uuid;

  @NotNull @ApiModelProperty(dataType = SwaggerConstants.STRING_CLASSPATH) ParameterField<String> connectorRef;
  @NotNull AzureTemplateFile template;

  AzureCreateARMResourceParameterFile parameters;

  @NotNull AzureCreateARMResourceStepScope scope;

  public void validateParams() {
    Validator.notNullCheck("Template file can't be empty", template);
    Validator.notNullCheck("Connector ref can't be empty", connectorRef);
    Validator.notNullCheck("Scope can't be empty", scope);
    isNumberOfFilesValid(template, "Number of files in template file should be equal to 1");
    isNumberOfFilesValid(parameters, "Number of files in parameters file should be equal 1 or 0");
    scope.getSpec().validateParams();
  }

  public AzureCreateARMResourceStepConfigurationParameters toStepParameters() {
    return AzureCreateARMResourceStepConfigurationParameters.builder()
        .connectorRef(connectorRef)
        .templateFile(template)
        .parameters(parameters)
        .scope(scope)
        .build();
  }

  // Check that the number of parameters files is 1.

  private void isNumberOfFilesValid(AzureTemplateFile template, String errMsg) {
    if (ManifestStoreType.isInGitSubset(template.getStore().getSpec().getKind())) {
      GitStoreConfig gitStoreConfig = (GitStoreConfig) template.getStore().getSpec();
      if (gitStoreConfig.getPaths().getValue() == null || gitStoreConfig.getPaths().getValue().size() != 1) {
        throw new InvalidRequestException(errMsg);
      }
    } else if (Objects.equals(template.getStore().getSpec().getKind(), ManifestStoreType.HARNESS)) {
      HarnessStore harnessStore = (HarnessStore) template.getStore().getSpec();
      if (isNotEmpty(ParameterFieldHelper.getParameterFieldValue(harnessStore.getFiles()))
          && isNotEmpty(ParameterFieldHelper.getParameterFieldValue(harnessStore.getSecretFiles()))) {
        throw new InvalidRequestException("Only one one files / secretFiles should be set for template");
      }
      if (isEmpty(ParameterFieldHelper.getParameterFieldValue(harnessStore.getFiles()))
          && isEmpty(ParameterFieldHelper.getParameterFieldValue(harnessStore.getSecretFiles()))) {
        throw new InvalidRequestException("At least one one files / secretFiles should be set for template");
      }
      if (isNotEmpty(ParameterFieldHelper.getParameterFieldValue(harnessStore.getFiles()))) {
        if (ParameterFieldHelper.getParameterFieldValue(harnessStore.getFiles()).size() != 1) {
          throw new InvalidRequestException(errMsg);
        }
      }
      if (isNotEmpty(ParameterFieldHelper.getParameterFieldValue(harnessStore.getSecretFiles()))) {
        if (ParameterFieldHelper.getParameterFieldValue(harnessStore.getSecretFiles()).size() != 1) {
          throw new InvalidRequestException(errMsg);
        }
      }
    }
  }

  private void isNumberOfFilesValid(AzureCreateARMResourceParameterFile parameters, String errMsg) {
    if (parameters != null) {
      if (ManifestStoreType.isInGitSubset(parameters.getStore().getSpec().getKind())) {
        GitStoreConfig gitStoreConfig = (GitStoreConfig) parameters.getStore().getSpec();
        if (gitStoreConfig.getPaths().getValue() == null || gitStoreConfig.getPaths().getValue().size() > 1) {
          throw new InvalidRequestException(errMsg);
        }
      } else if (Objects.equals(parameters.getStore().getSpec().getKind(), ManifestStoreType.HARNESS)) {
        HarnessStore harnessStore = (HarnessStore) parameters.getStore().getSpec();
        if (isNotEmpty(ParameterFieldHelper.getParameterFieldValue(harnessStore.getFiles()))
            && isNotEmpty(ParameterFieldHelper.getParameterFieldValue(harnessStore.getSecretFiles()))) {
          throw new InvalidRequestException("Only one one files / secretFiles should be set for parameters");
        }
        if (isEmpty(ParameterFieldHelper.getParameterFieldValue(harnessStore.getFiles()))
            && isEmpty(ParameterFieldHelper.getParameterFieldValue(harnessStore.getSecretFiles()))) {
          throw new InvalidRequestException("At least one one files / secretFiles should be set for parameters");
        }
        if (isNotEmpty(ParameterFieldHelper.getParameterFieldValue(harnessStore.getFiles()))) {
          if (ParameterFieldHelper.getParameterFieldValue(harnessStore.getFiles()).size() > 1) {
            throw new InvalidRequestException(errMsg);
          }
        }
        if (isNotEmpty(ParameterFieldHelper.getParameterFieldValue(harnessStore.getSecretFiles()))) {
          if (ParameterFieldHelper.getParameterFieldValue(harnessStore.getSecretFiles()).size() > 1) {
            throw new InvalidRequestException(errMsg);
          }
        }
      }
    }
  }
}
