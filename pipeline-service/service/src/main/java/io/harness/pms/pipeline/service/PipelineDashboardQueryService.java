/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.service;

import io.harness.exception.InvalidRequestException;
import io.harness.pms.dashboard.MeanAndMedian;
import io.harness.pms.dashboard.StatusAndTime;
import io.harness.timescaledb.DBUtils;
import io.harness.timescaledb.Tables;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.SelectField;
import org.jooq.Table;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;

public class PipelineDashboardQueryService {
  @Inject private DSLContext dsl;

  private final String CI_TableName = "pipeline_execution_summary_ci";
  private final String CD_TableName = "pipeline_execution_summary_cd";
  Table<?> fromCD = Tables.PIPELINE_EXECUTION_SUMMARY_CD;
  Table<?> fromCI = Tables.PIPELINE_EXECUTION_SUMMARY_CI;

  public List<StatusAndTime> getPipelineExecutionStatusAndTime(String accountId, String orgId, String projectId,
      String pipelineId, long startInterval, long endInterval, String tableName) {
    try {
      List<StatusAndTime> statusAndTime;
      List<Condition> conditionsCD =
          createConditions(CD_TableName, orgId, pipelineId, projectId, accountId, startInterval, endInterval);
      List<Condition> conditionsCI =
          createConditions(CI_TableName, orgId, pipelineId, projectId, accountId, startInterval, endInterval);
      if (tableName.equals(CD_TableName)) {
        statusAndTime = this.dsl
                            .select(new SelectField[] {Tables.PIPELINE_EXECUTION_SUMMARY_CD.STATUS,
                                Tables.PIPELINE_EXECUTION_SUMMARY_CD.STARTTS})
                            .from(fromCD)
                            .where(conditionsCD)
                            .fetch()
                            .into(StatusAndTime.class);
      } else if (Objects.equals(tableName, CI_TableName)) {
        statusAndTime = this.dsl
                            .select(new SelectField[] {Tables.PIPELINE_EXECUTION_SUMMARY_CI.STATUS,
                                Tables.PIPELINE_EXECUTION_SUMMARY_CI.STARTTS})
                            .from(fromCI)
                            .where(conditionsCI)
                            .fetch()
                            .into(StatusAndTime.class);
      } else {
        // Including PlanExecutionId in the projection because it is used as unique identifier for union operation
        Table table = dsl.select(new SelectField[] {Tables.PIPELINE_EXECUTION_SUMMARY_CD.STATUS,
                                     Tables.PIPELINE_EXECUTION_SUMMARY_CD.STARTTS,
                                     Tables.PIPELINE_EXECUTION_SUMMARY_CD.PLANEXECUTIONID})
                          .from(fromCD)
                          .where(conditionsCD)
                          .union(dsl.select(new SelectField[] {Tables.PIPELINE_EXECUTION_SUMMARY_CI.STATUS,
                                                Tables.PIPELINE_EXECUTION_SUMMARY_CI.STARTTS,
                                                Tables.PIPELINE_EXECUTION_SUMMARY_CI.PLANEXECUTIONID})
                                     .from(fromCI)
                                     .where(conditionsCI))
                          .asTable();
        statusAndTime = this.dsl
                            .select(new SelectField[] {table.field(Tables.PIPELINE_EXECUTION_SUMMARY_CD.STATUS),
                                table.field(Tables.PIPELINE_EXECUTION_SUMMARY_CD.STARTTS)})
                            .from(table)
                            .fetch()
                            .into(StatusAndTime.class);
      }
      return statusAndTime;
    } catch (DataAccessException ex) {
      if (DBUtils.isConnectionError(ex)) {
        throw new InvalidRequestException(
            "Unable to fetch Dashboard data for executions, Please ensure timescale is enabled", ex);
      } else {
        throw new InvalidRequestException("Unable to fetch Dashboard data for executions", ex);
      }
    } catch (Exception ex) {
      throw new InvalidRequestException("Unable to fetch Dashboard data for executions", ex);
    }
  }

  public MeanAndMedian getPipelineExecutionMeanAndMedianDuration(String accountId, String orgId, String projectId,
      String pipelineId, long startInterval, long endInterval, String tableName) {
    try {
      List<Condition> conditionsCD =
          createConditions(CD_TableName, orgId, pipelineId, projectId, accountId, startInterval, endInterval);
      List<Condition> conditionsCI =
          createConditions(CI_TableName, orgId, pipelineId, projectId, accountId, startInterval, endInterval);
      conditionsCD.add(Tables.PIPELINE_EXECUTION_SUMMARY_CD.ENDTS.isNotNull());
      conditionsCI.add(Tables.PIPELINE_EXECUTION_SUMMARY_CI.ENDTS.isNotNull());
      List<MeanAndMedian> meanAndMedians;
      if (Objects.equals(tableName, CD_TableName)) {
        meanAndMedians = this.dsl
                             .select(new SelectField[] {DSL.avg(Tables.PIPELINE_EXECUTION_SUMMARY_CD.ENDTS.sub(
                                                                    Tables.PIPELINE_EXECUTION_SUMMARY_CD.STARTTS))
                                                            .div(1000),
                                 DSL.percentileDisc(0.5)
                                     .withinGroupOrderBy((Tables.PIPELINE_EXECUTION_SUMMARY_CD.ENDTS)
                                                             .sub(Tables.PIPELINE_EXECUTION_SUMMARY_CD.STARTTS))
                                     .div(1000)})
                             .from(fromCD)
                             .where(conditionsCD)
                             .fetch()
                             .into(MeanAndMedian.class);
      } else if (Objects.equals(tableName, CI_TableName)) {
        meanAndMedians = this.dsl
                             .select(new SelectField[] {DSL.avg(Tables.PIPELINE_EXECUTION_SUMMARY_CI.ENDTS.sub(
                                                                    Tables.PIPELINE_EXECUTION_SUMMARY_CI.STARTTS))
                                                            .div(1000),
                                 DSL.percentileDisc(0.5)
                                     .withinGroupOrderBy((Tables.PIPELINE_EXECUTION_SUMMARY_CI.ENDTS)
                                                             .sub(Tables.PIPELINE_EXECUTION_SUMMARY_CI.STARTTS))
                                     .div(1000)})
                             .from(fromCI)
                             .where(conditionsCI)
                             .fetch()
                             .into(MeanAndMedian.class);
      } else {
        // Including PlanExecutionId in the projection because it is used as unique identifier for union operation
        Table table = dsl.select(new SelectField[] {Tables.PIPELINE_EXECUTION_SUMMARY_CD.ENDTS,
                                     Tables.PIPELINE_EXECUTION_SUMMARY_CD.STARTTS,
                                     Tables.PIPELINE_EXECUTION_SUMMARY_CD.PLANEXECUTIONID})
                          .from(fromCD)
                          .where(conditionsCD)
                          .union(dsl.select(new SelectField[] {Tables.PIPELINE_EXECUTION_SUMMARY_CI.ENDTS,
                                                Tables.PIPELINE_EXECUTION_SUMMARY_CI.STARTTS,
                                                Tables.PIPELINE_EXECUTION_SUMMARY_CI.PLANEXECUTIONID})
                                     .from(fromCI)
                                     .where(conditionsCI))
                          .asTable();
        meanAndMedians =
            dsl.select(new SelectField[] {DSL.avg(table.field(Tables.PIPELINE_EXECUTION_SUMMARY_CD.ENDTS)
                                                      .sub(table.field(Tables.PIPELINE_EXECUTION_SUMMARY_CD.STARTTS)))
                                              .div(1000),
                           DSL.percentileDisc(0.5)
                               .withinGroupOrderBy(table.field(Tables.PIPELINE_EXECUTION_SUMMARY_CD.ENDTS)
                                                       .sub(table.field(Tables.PIPELINE_EXECUTION_SUMMARY_CD.STARTTS)))
                               .div(1000)})
                .from(table)
                .fetch()
                .into(MeanAndMedian.class);
      }
      return meanAndMedians.get(0);
    } catch (DataAccessException ex) {
      if (DBUtils.isConnectionError(ex)) {
        throw new InvalidRequestException(
            "Unable to fetch Dashboard data for executions, Please ensure timescale is enabled", ex);
      } else {
        throw new InvalidRequestException("Unable to fetch Dashboard data for executions", ex);
      }
    } catch (Exception ex) {
      throw new InvalidRequestException("Unable to fetch Dashboard data for executions", ex);
    }
  }

  private List<Condition> createConditions(String tableName, String orgId, String pipelineId, String projectId,
      String accountId, long startInterval, long endInterval) {
    List<Condition> conditions = new ArrayList<>();
    if (tableName.equals(CD_TableName)) {
      conditions.add(Tables.PIPELINE_EXECUTION_SUMMARY_CD.ORGIDENTIFIER.eq(orgId));
      conditions.add(Tables.PIPELINE_EXECUTION_SUMMARY_CD.PIPELINEIDENTIFIER.eq(pipelineId));
      conditions.add(Tables.PIPELINE_EXECUTION_SUMMARY_CD.PROJECTIDENTIFIER.eq(projectId));
      conditions.add(Tables.PIPELINE_EXECUTION_SUMMARY_CD.ACCOUNTID.eq(accountId));
      conditions.add(Tables.PIPELINE_EXECUTION_SUMMARY_CD.STARTTS.ge(startInterval));
      conditions.add(Tables.PIPELINE_EXECUTION_SUMMARY_CD.STARTTS.lt(endInterval));
    } else if (tableName.equals(CI_TableName)) {
      conditions.add(Tables.PIPELINE_EXECUTION_SUMMARY_CI.ORGIDENTIFIER.eq(orgId));
      conditions.add(Tables.PIPELINE_EXECUTION_SUMMARY_CI.PIPELINEIDENTIFIER.eq(pipelineId));
      conditions.add(Tables.PIPELINE_EXECUTION_SUMMARY_CI.PROJECTIDENTIFIER.eq(projectId));
      conditions.add(Tables.PIPELINE_EXECUTION_SUMMARY_CI.ACCOUNTID.eq(accountId));
      conditions.add(Tables.PIPELINE_EXECUTION_SUMMARY_CI.STARTTS.ge(startInterval));
      conditions.add(Tables.PIPELINE_EXECUTION_SUMMARY_CI.STARTTS.lt(endInterval));
    }
    return conditions;
  }
}
