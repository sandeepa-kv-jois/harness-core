/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.anomalydetection;

import static io.harness.rule.OwnerRule.SANDESH;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.batch.processing.anomalydetection.alerts.SlackMessageGenerator;
import io.harness.batch.processing.anomalydetection.alerts.service.impl.AnomalyAlertsServiceImpl;
import io.harness.batch.processing.mail.CEMailNotificationService;
import io.harness.batch.processing.shard.AccountShardService;
import io.harness.category.element.UnitTests;
import io.harness.ccm.anomaly.AnomalyDataStub;
import io.harness.ccm.anomaly.service.itfc.AnomalyService;
import io.harness.ccm.communication.CESlackWebhookService;
import io.harness.ccm.communication.entities.CESlackWebhook;
import io.harness.rule.Owner;
import io.harness.timescaledb.TimeScaleDBService;

import com.slack.api.Slack;
import com.slack.api.webhook.Payload;
import com.slack.api.webhook.WebhookResponse;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class AnomalyAlertsServiceImplTest extends CategoryTest {
  @Mock private TimeScaleDBService timeScaleDBService;
  @Mock private CEMailNotificationService emailNotificationService;
  @Mock private Slack slack;
  @Mock private CESlackWebhookService ceSlackWebhookService;
  @Mock private AccountShardService accountShardService;
  @InjectMocks private AnomalyAlertsServiceImpl alertsService;
  @Mock private AnomalyService anomalyService;
  @Mock private SlackMessageGenerator slackMessageGenerator;

  private static final String ACCOUNT_ID = AnomalyDataStub.accountId;

  private String WEBHOOK_URL = "https://hooks.slack.com/services/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";
  private CESlackWebhook ceSlackWebhook;
  private Instant date;

  @Before
  public void setup() throws SQLException, IOException {
    MockitoAnnotations.initMocks(this);
    date = AnomalyDataStub.anomalyTime;

    Connection mockConnection = mock(Connection.class);
    Statement mockStatement = mock(Statement.class);
    when(timeScaleDBService.getDBConnection()).thenReturn(mockConnection);
    when(timeScaleDBService.isValid()).thenReturn(true);
    when(mockConnection.createStatement()).thenReturn(mockStatement);
    when(emailNotificationService.send(any())).thenReturn(true);
    when(accountShardService.getCeEnabledAccountIds()).thenReturn(Arrays.asList(ACCOUNT_ID));

    ceSlackWebhook = CESlackWebhook.builder()
                         .accountId(ACCOUNT_ID)
                         .sendCostReport(false)
                         .sendAnomalyAlerts(true)
                         .webhookUrl(WEBHOOK_URL)
                         .build();

    when(ceSlackWebhookService.getByAccountId(ACCOUNT_ID)).thenReturn(ceSlackWebhook);
    when(anomalyService.list(ACCOUNT_ID, date)).thenReturn(Arrays.asList(AnomalyDataStub.getClusterAnomaly()));
    when(slackMessageGenerator.generateDailyReport(Arrays.asList(AnomalyDataStub.getClusterAnomaly())))
        .thenReturn(Collections.emptyList());
    when(slack.send(anyString(), (Payload) anyObject())).thenReturn(WebhookResponse.builder().code(200).build());
  }

  @Test
  @Owner(developers = SANDESH)
  @Category(UnitTests.class)
  public void shouldSendAnomalyAlerts() throws IOException {
    alertsService.sendAnomalyDailyReport(ACCOUNT_ID, date);
    verify(slack).send(anyString(), (Payload) anyObject());
  }
}
