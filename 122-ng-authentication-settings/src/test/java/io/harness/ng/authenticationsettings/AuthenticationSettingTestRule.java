/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.authenticationsettings;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.factory.ClosingFactory;
import io.harness.rule.InjectorRuleMixin;
import io.harness.testlib.module.MongoRuleMixin;

import com.google.inject.AbstractModule;
import com.google.inject.Module;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.junit.rules.MethodRule;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;

@OwnedBy(HarnessTeam.PL)
@Slf4j
public class AuthenticationSettingTestRule implements InjectorRuleMixin, MethodRule, MongoRuleMixin {
  ClosingFactory closingFactory;
  public AuthenticationSettingTestRule(ClosingFactory closingFactory) {
    this.closingFactory = closingFactory;
  }

  @Override
  public List<Module> modules(List<Annotation> annotations) throws Exception {
    List<Module> modules = new ArrayList<>();
    modules.add(new AbstractModule() {
      @Override
      protected void configure() {}
    });

    return modules;
  }

  @Override
  public Statement apply(Statement base, FrameworkMethod method, Object target) {
    return applyInjector(log, base, method, target);
  }
}
