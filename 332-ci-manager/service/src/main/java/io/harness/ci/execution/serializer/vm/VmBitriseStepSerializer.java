/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.serializer.vm;

import static io.harness.beans.serializer.RunTimeInputHandler.resolveMapParameter;
import static io.harness.ci.commonconstants.CIExecutionConstants.PLUGIN_ENV_PREFIX;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.beans.serializer.RunTimeInputHandler;
import io.harness.beans.steps.stepinfo.BitriseStepInfo;
import io.harness.beans.sweepingoutputs.StageInfraDetails;
import io.harness.ci.config.CIExecutionServiceConfig;
import io.harness.delegate.beans.ci.vm.steps.VmRunStep;
import io.harness.exception.ngexception.CIStageExecutionException;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@Singleton
public class VmBitriseStepSerializer {
  @Inject CIExecutionServiceConfig ciExecutionServiceConfig;

  public VmRunStep serialize(BitriseStepInfo bitriseStepInfo, String identifier, StageInfraDetails stageInfraDetails) {
    if (stageInfraDetails.getType() != StageInfraDetails.Type.DLITE_VM) {
      throw new CIStageExecutionException("Bitrise step is only applicable for builds on cloud infrastructure");
    }

    String uses = RunTimeInputHandler.resolveStringParameter("Uses", "", identifier, bitriseStepInfo.getUses(), true);
    Map<String, String> with = resolveMapParameter("with", "Bitrise", identifier, bitriseStepInfo.getWith(), false);

    Map<String, String> env = resolveMapParameter("env", "Bitrise", identifier, bitriseStepInfo.getEnv(), false);
    if (env == null) {
      env = new HashMap<>();
    }

    if (!isEmpty(with)) {
      for (Map.Entry<String, String> entry : with.entrySet()) {
        String key = PLUGIN_ENV_PREFIX + entry.getKey().toUpperCase();
        env.put(key, entry.getValue());
      }
    }

    return VmRunStep.builder()
        .entrypoint(Arrays.asList("plugin", "-kind", "bitrise", "-repo"))
        .command(uses)
        .envVariables(env)
        .build();
  }
}
