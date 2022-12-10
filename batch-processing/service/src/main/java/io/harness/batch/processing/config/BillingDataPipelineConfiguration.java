/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.config;

import io.harness.batch.processing.ccm.BatchJobType;
import io.harness.batch.processing.reader.SettingAttributeReader;
import io.harness.batch.processing.svcmetrics.BatchJobExecutionListener;
import io.harness.batch.processing.tasklet.AwsBillingDataPipelineTasklet;
import io.harness.batch.processing.tasklet.AzureBillingDataPipelineTasklet;
import io.harness.batch.processing.tasklet.GcpBillingDataPipelineTasklet;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class BillingDataPipelineConfiguration {
  @Autowired private BatchJobExecutionListener batchJobExecutionListener;

  @Bean
  public Tasklet awsBillingDataPipelineTasklet() {
    return new AwsBillingDataPipelineTasklet();
  }

  @Bean
  public Tasklet azureBillingDataPipelineTasklet() {
    return new AzureBillingDataPipelineTasklet();
  }

  @Bean
  public Tasklet gcpBillingDataPipelineTasklet() {
    return new GcpBillingDataPipelineTasklet();
  }

  @Bean
  @Autowired
  @Qualifier(value = "billingDataPipelineJob")
  public Job billingDataPipelineJob(JobBuilderFactory jobBuilderFactory, Step awsBillingDataPipelineStep,
      Step gcpBillingDataPipelineStep, Step azureBillingDataPipelineStep, Step deletePipelineForExpiredAccountsStep) {
    return jobBuilderFactory.get(BatchJobType.BILLING_DATA_PIPELINE.name())
        .incrementer(new RunIdIncrementer())
        .listener(batchJobExecutionListener)
        .start(awsBillingDataPipelineStep)
        .next(gcpBillingDataPipelineStep)
        .next(azureBillingDataPipelineStep)
        .build();
  }

  @Bean
  public Step awsBillingDataPipelineStep(StepBuilderFactory stepBuilderFactory) {
    return stepBuilderFactory.get("awsBillingDataPipelineStep").tasklet(awsBillingDataPipelineTasklet()).build();
  }

  @Bean
  public Step azureBillingDataPipelineStep(StepBuilderFactory stepBuilderFactory) {
    return stepBuilderFactory.get("azureBillingDataPipelineStep").tasklet(azureBillingDataPipelineTasklet()).build();
  }

  @Bean
  public Step gcpBillingDataPipelineStep(
      StepBuilderFactory stepBuilderFactory, SettingAttributeReader settingAttributeReader) {
    return stepBuilderFactory.get("gcpBillingDataPipelineStep").tasklet(gcpBillingDataPipelineTasklet()).build();
  }
}
