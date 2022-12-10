/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.remote.resources.perspectives;

import static io.harness.NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE;
import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.ccm.commons.utils.BigQueryHelper.UNIFIED_TABLE;
import static io.harness.ccm.remote.resources.TelemetryConstants.DATA_SOURCES;
import static io.harness.ccm.remote.resources.TelemetryConstants.IS_CLONE;
import static io.harness.ccm.remote.resources.TelemetryConstants.MODULE;
import static io.harness.ccm.remote.resources.TelemetryConstants.MODULE_NAME;
import static io.harness.ccm.remote.resources.TelemetryConstants.PERSPECTIVE_CREATED;
import static io.harness.ccm.remote.resources.TelemetryConstants.PERSPECTIVE_ID;
import static io.harness.outbox.TransactionOutboxModule.OUTBOX_TRANSACTION_TEMPLATE;
import static io.harness.springdata.PersistenceUtils.DEFAULT_RETRY_POLICY;
import static io.harness.telemetry.Destination.AMPLITUDE;

import io.harness.NGCommonEntityConstants;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.audittrails.events.PerspectiveCreateEvent;
import io.harness.ccm.audittrails.events.PerspectiveDeleteEvent;
import io.harness.ccm.audittrails.events.PerspectiveUpdateEvent;
import io.harness.ccm.bigQuery.BigQueryService;
import io.harness.ccm.budget.BudgetBreakdown;
import io.harness.ccm.budget.BudgetPeriod;
import io.harness.ccm.budget.BudgetType;
import io.harness.ccm.budget.ValueDataPoint;
import io.harness.ccm.budget.utils.BudgetUtils;
import io.harness.ccm.commons.utils.BigQueryHelper;
import io.harness.ccm.graphql.core.budget.BudgetCostService;
import io.harness.ccm.graphql.core.budget.BudgetService;
import io.harness.ccm.rbac.CCMRbacHelper;
import io.harness.ccm.service.intf.CCMNotificationService;
import io.harness.ccm.utils.LogAccountIdentifier;
import io.harness.ccm.views.entities.CEView;
import io.harness.ccm.views.entities.ViewFieldIdentifier;
import io.harness.ccm.views.entities.ViewType;
import io.harness.ccm.views.graphql.QLCEView;
import io.harness.ccm.views.helper.AwsAccountFieldHelper;
import io.harness.ccm.views.service.CEReportScheduleService;
import io.harness.ccm.views.service.CEViewService;
import io.harness.ccm.views.service.ViewCustomFieldService;
import io.harness.enforcement.client.annotation.FeatureRestrictionCheck;
import io.harness.enforcement.constants.FeatureRestrictionName;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.outbox.api.OutboxService;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.telemetry.Category;
import io.harness.telemetry.TelemetryReporter;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.cloud.bigquery.BigQuery;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.hibernate.validator.constraints.NotBlank;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Api("perspective")
@Path("perspective")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@NextGenManagerAuth
@Slf4j
@Service
@OwnedBy(CE)
@Tag(name = "Cloud Cost Perspectives",
    description = "Group your resources using Perspectives in ways that are more meaningful to your business needs.")
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Bad Request",
    content = { @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = FailureDTO.class)) })
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal server error",
    content = { @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ErrorDTO.class)) })
public class PerspectiveResource {
  private final CEViewService ceViewService;
  private final CEReportScheduleService ceReportScheduleService;
  private final ViewCustomFieldService viewCustomFieldService;
  private final BigQueryService bigQueryService;
  private final BigQueryHelper bigQueryHelper;
  private final BudgetCostService budgetCostService;
  private final BudgetService budgetService;
  private final CCMNotificationService notificationService;
  private final AwsAccountFieldHelper awsAccountFieldHelper;
  private final TelemetryReporter telemetryReporter;

  private final RetryPolicy<Object> transactionRetryPolicy = DEFAULT_RETRY_POLICY;
  private final TransactionTemplate transactionTemplate;
  private final OutboxService outboxService;
  private final CCMRbacHelper rbacHelper;

  @Inject
  public PerspectiveResource(CEViewService ceViewService, CEReportScheduleService ceReportScheduleService,
      ViewCustomFieldService viewCustomFieldService, BigQueryService bigQueryService, BigQueryHelper bigQueryHelper,
      BudgetCostService budgetCostService, BudgetService budgetService, CCMNotificationService notificationService,
      AwsAccountFieldHelper awsAccountFieldHelper, TelemetryReporter telemetryReporter,
      @Named(OUTBOX_TRANSACTION_TEMPLATE) TransactionTemplate transactionTemplate, OutboxService outboxService,
      CCMRbacHelper rbacHelper) {
    this.ceViewService = ceViewService;
    this.ceReportScheduleService = ceReportScheduleService;
    this.viewCustomFieldService = viewCustomFieldService;
    this.bigQueryService = bigQueryService;
    this.bigQueryHelper = bigQueryHelper;
    this.budgetCostService = budgetCostService;
    this.budgetService = budgetService;
    this.notificationService = notificationService;
    this.awsAccountFieldHelper = awsAccountFieldHelper;
    this.telemetryReporter = telemetryReporter;
    this.transactionTemplate = transactionTemplate;
    this.outboxService = outboxService;
    this.rbacHelper = rbacHelper;
  }

  @GET
  @Path("lastMonthCost")
  @Timed
  @Hidden
  @LogAccountIdentifier
  @ExceptionMetered
  @ApiOperation(value = "Get last month cost for perspective", nickname = "getLastMonthCostV2")
  @Operation(operationId = "getLastMonthCostV2",
      description = "Fetch cost details of a Perspective for the previous month for the given  Perspective ID.",
      summary = "Fetch cost details of a Perspective for the previous month",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns a number having the cost of last month",
            content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public ResponseDTO<Double>
  getLastMonthCost(@Parameter(required = true, description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
                       NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId,
      @NotNull @Valid @QueryParam("perspectiveId") @Parameter(
          required = true, description = "Unique identifier for the Perspective") String perspectiveId) {
    rbacHelper.checkPerspectiveViewPermission(accountId, null, null);
    return ResponseDTO.newResponse(ceViewService.getLastMonthCostForPerspective(accountId, perspectiveId));
  }

  @GET
  @Path("lastPeriodCost")
  @Timed
  @LogAccountIdentifier
  @ExceptionMetered
  @ApiOperation(value = "Get last period cost for perspective", nickname = "getLastPeriodCost")
  @Operation(operationId = "getLastPeriodCost", description = "Get last period cost for a Perspective",
      summary = "Get the last period cost for a Perspective",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns a number having the cost of last period",
            content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public ResponseDTO<Double>
  getLastPeriodCost(@Parameter(required = true, description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
                        NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId,
      @NotNull @Valid @QueryParam("perspectiveId") @Parameter(
          required = true, description = "The Perspective identifier for which we want the cost") String perspectiveId,
      @NotNull @Valid @QueryParam("startTime") @Parameter(
          required = true, description = "The Start time (timestamp in millis) for the current period") long startTime,
      @NotNull @Valid @QueryParam("period") @Parameter(required = true,
          description = "The period (DAILY, WEEKLY, MONTHLY, QUARTERLY, YEARLY) for which we want the cost")
      BudgetPeriod period) {
    rbacHelper.checkPerspectiveViewPermission(accountId, null, null);
    return ResponseDTO.newResponse(budgetCostService.getLastPeriodCost(accountId, perspectiveId, startTime, period));
  }

  @GET
  @Path("lastYearMonthlyCost")
  @Timed
  @LogAccountIdentifier
  @ExceptionMetered
  @ApiOperation(value = "Get last twelve month cost for perspective", nickname = "lastYearMonthlyCost")
  @Operation(operationId = "getLastYearMonthlyCost", description = "Get last twelve month cost for a Perspective",
      summary = "Get the last twelve month cost for a Perspective",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Return list of actual monthly budget cost and respective month in epoch",
            content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public ResponseDTO<List<ValueDataPoint>>
  getLastYearMonthlyCost(@Parameter(required = true, description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
                             NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId,
      @NotNull @Valid @QueryParam("perspectiveId") @Parameter(
          required = true, description = "The Perspective identifier for which we want the cost") String perspectiveId,
      @NotNull @Valid @QueryParam("startTime") @Parameter(
          required = true, description = "The Start time (timestamp in millis) for the current period") long startTime,
      @NotNull @Valid @QueryParam("period") @Parameter(
          required = true, description = "Only support for YEARLY budget period") BudgetPeriod period,
      @NotNull @Valid @QueryParam("type") @Parameter(
          required = true, description = "Only support for PREVIOUS_PERIOD_SPEND budget type") BudgetType type,
      @NotNull @Valid @QueryParam("breakdown") @Parameter(
          required = true, description = "Only support for MONTHLY breakdown") BudgetBreakdown breakdown) {
    rbacHelper.checkPerspectiveViewPermission(accountId, null, null);
    List<ValueDataPoint> response = null;
    if (period == BudgetPeriod.YEARLY && type == BudgetType.PREVIOUS_PERIOD_SPEND
        && breakdown == BudgetBreakdown.MONTHLY) {
      Double[] lastYearMonthlyCost =
          budgetCostService.getLastYearMonthlyCost(accountId, perspectiveId, startTime, period);
      response = BudgetUtils.getYearlyMonthWiseKeyValuePairs(
          BudgetUtils.getStartOfLastPeriod(startTime, period), lastYearMonthlyCost);
    }
    return ResponseDTO.newResponse(response);
  }

  @GET
  @Path("forecastCost")
  @Timed
  @Hidden
  @ExceptionMetered
  @ApiOperation(value = "Get forecast cost for perspective", nickname = "getForecastCostV2")
  @Operation(operationId = "getForecastCostV2",
      description = "Fetch forecasted cost details of a Perspective for the next 30 days for the given Perspective ID.",
      summary = "Fetch forecasted cost details of a Perspective",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns a number having the forecast cost of a Perspective for next 30 days",
            content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public ResponseDTO<Double>
  getForecastCost(@Parameter(required = true, description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
                      NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId,
      @Valid @NotNull @Parameter(required = true, description = "Unique identifier for the Perspective") @QueryParam(
          "perspectiveId") String perspectiveId) {
    rbacHelper.checkPerspectiveViewPermission(accountId, null, null);
    return ResponseDTO.newResponse(ceViewService.getForecastCostForPerspective(accountId, perspectiveId));
  }

  @GET
  @Path("forecastCostForPeriod")
  @Timed
  @Hidden
  @ExceptionMetered
  @ApiOperation(value = "Get forecast cost for perspective for given period", nickname = "getForecastCostForPeriod")
  @Operation(operationId = "getForecastCostForPeriod",
      description = "Get the forecasted cost of a Perspective for next period",
      summary = "Get the forecasted cost of a Perspective for given period",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns a number having the forecast cost of a Perspective for next period",
            content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public ResponseDTO<Double>
  getForecastCostForPeriod(@Parameter(required = true, description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
                               NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId,
      @NotNull @Valid @QueryParam("perspectiveId") @Parameter(required = true,
          description = "The Perspective identifier for which we want the forecast cost") String perspectiveId,
      @NotNull @Valid @QueryParam("startTime") @Parameter(
          required = true, description = "The Start time (timestamp in millis) for the period") long startTime,
      @NotNull @Valid @QueryParam("period") @Parameter(required = true,
          description = "The period (DAILY, WEEKLY, MONTHLY, QUARTERLY, YEARLY) for which we want the forecast cost")
      BudgetPeriod period) {
    rbacHelper.checkPerspectiveViewPermission(accountId, null, null);
    return ResponseDTO.newResponse(budgetCostService.getForecastCost(accountId, perspectiveId, startTime, period));
  }

  @POST
  @Timed
  @ExceptionMetered
  @Consumes(MediaType.APPLICATION_JSON)
  @ApiOperation(value = "Create perspective", nickname = "createPerspective")
  @FeatureRestrictionCheck(FeatureRestrictionName.PERSPECTIVES)
  @LogAccountIdentifier
  @Operation(operationId = "createPerspective",
      description = "Create a Perspective. You can set the clone parameter as true to clone a Perspective.",
      summary = "Create a Perspective",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns a created CEView object with all the rules and filters",
            content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public ResponseDTO<CEView>
  create(@Parameter(required = true, description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
             NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId,
      @QueryParam("clone") @Parameter(
          required = true, description = "Set the clone parameter as true to clone a Perspective.") boolean clone,
      @RequestBody(
          required = true, description = "Request body containing Perspective's CEView object") @Valid CEView ceView) {
    rbacHelper.checkPerspectiveEditPermission(accountId, null, null);
    ceView.setAccountId(accountId);
    HashMap<String, Object> properties = new HashMap<>();
    properties.put(MODULE, MODULE_NAME);
    List<ViewFieldIdentifier> dataSourcesList = ceView.getDataSources();
    String dataSources = "";
    if (dataSourcesList != null) {
      dataSources = dataSourcesList.stream().map(Object::toString).collect(Collectors.joining(","));
    }
    properties.put(DATA_SOURCES, dataSources);
    properties.put(IS_CLONE, clone ? "YES" : "NO");
    if (clone) {
      // reset these fields which gets set downstream appropriately
      ceView.setCreatedBy(null);
      ceView.setCreatedAt(0);
      ceView.setUuid(null);
      ceView.setViewType(ViewType.CUSTOMER);
    }
    CEView ceViewCheck = updateTotalCost(ceViewService.save(ceView, clone));
    properties.put(PERSPECTIVE_ID, clone ? ceViewCheck.getUuid() : ceView.getUuid());
    telemetryReporter.sendTrackEvent(
        PERSPECTIVE_CREATED, null, accountId, properties, Collections.singletonMap(AMPLITUDE, true), Category.GLOBAL);
    return ResponseDTO.newResponse(
        Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
          outboxService.save(new PerspectiveCreateEvent(accountId, ceViewCheck.toDTO()));
          return ceViewCheck;
        })));
  }

  private CEView updateTotalCost(CEView ceView) {
    BigQuery bigQuery = bigQueryService.get();
    String cloudProviderTableName = bigQueryHelper.getCloudProviderTableName(ceView.getAccountId(), UNIFIED_TABLE);
    return ceViewService.updateTotalCost(ceView, bigQuery, cloudProviderTableName);
  }

  @GET
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "Get perspective", nickname = "getPerspective")
  @LogAccountIdentifier
  @Produces(MediaType.APPLICATION_JSON)
  @Operation(operationId = "getPerspective",
      description = "Fetch details of a Perspective for the given Perspective ID.",
      summary = "Fetch details of a Perspective",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            description =
                "Returns a CEView object with all the rules and filters, returns null if no Perspective exists for that particular identifier",
            content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public ResponseDTO<CEView>
  get(@Parameter(required = true, description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId,
      @QueryParam("perspectiveId") @Parameter(required = true,
          description = "Unique identifier for the Perspective") @NotBlank @Valid String perspectiveId) {
    rbacHelper.checkPerspectiveViewPermission(accountId, null, null);
    CEView ceView = ceViewService.get(perspectiveId);
    awsAccountFieldHelper.mergeAwsAccountNameInAccountRules(ceView.getViewRules(), accountId);
    return ResponseDTO.newResponse(ceView);
  }

  @GET
  @Path("getAllPerspectives")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "Get All perspectives", nickname = "getAllPerspectives")
  @LogAccountIdentifier
  @Produces(MediaType.APPLICATION_JSON)
  @Operation(operationId = "getAllPerspectives",
      description = "Return details of all the Perspectives for the given account ID.",
      summary = "Return details of all the Perspectives",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "Returns a List of Perspectives",
            content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public ResponseDTO<List<QLCEView>>
  getAll(@Parameter(required = true, description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
      NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId) {
    rbacHelper.checkPerspectiveViewPermission(accountId, null, null);
    return ResponseDTO.newResponse(ceViewService.getAllViews(accountId, true, null));
  }

  @PUT
  @Timed
  @ExceptionMetered
  @Consumes(MediaType.APPLICATION_JSON)
  @ApiOperation(value = "Update perspective", nickname = "updatePerspective")
  @LogAccountIdentifier
  @Operation(operationId = "updatePerspective",
      description =
          "Update a Perspective. It accepts a CEView object and upserts it using the uuid mentioned in the definition.",
      summary = "Update a Perspective",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Upserted CEView object with all the rules and filters",
            content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public ResponseDTO<CEView>
  update(@Parameter(required = true, description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
             NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId,
      @Valid @RequestBody(required = true, description = "Perspective's CEView object") CEView ceView) {
    rbacHelper.checkPerspectiveEditPermission(accountId, null, null);
    CEView oldPerspective = ceViewService.get(ceView.getUuid());
    ceView.setAccountId(accountId);
    log.info(ceView.toString());
    CEView newPerspective = updateTotalCost(ceViewService.update(ceView));
    if (ceView.getName() != null && !ceView.getName().equals("")
        && !oldPerspective.getName().equals(ceView.getName())) {
      budgetService.updatePerspectiveName(accountId, ceView.getUuid(), ceView.getName());
    }
    return ResponseDTO.newResponse(
        Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
          outboxService.save(new PerspectiveUpdateEvent(accountId, newPerspective.toDTO(), oldPerspective.toDTO()));
          return newPerspective;
        })));
  }

  @DELETE
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "Delete perspective", nickname = "deletePerspective")
  @LogAccountIdentifier
  @Operation(operationId = "deletePerspective", description = "Delete a Perspective for the given Perspective ID.",
      summary = "Delete a Perspective",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "A string text message whether the delete was successful or not",
            content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public ResponseDTO<String>
  delete(@Parameter(required = true, description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
             NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId,
      @QueryParam("perspectiveId") @Parameter(required = true,
          description = "Unique identifier for the Perspective") @NotNull @Valid String perspectiveId) {
    rbacHelper.checkPerspectiveDeletePermission(accountId, null, null);
    CEView perspective = ceViewService.get(perspectiveId);
    ceViewService.delete(perspectiveId, accountId);

    ceReportScheduleService.deleteAllByView(perspectiveId, accountId);
    viewCustomFieldService.deleteByViewId(perspectiveId, accountId);
    budgetService.deleteBudgetsForPerspective(accountId, perspectiveId);
    notificationService.delete(perspectiveId, accountId);

    return ResponseDTO.newResponse(
        Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
          outboxService.save(new PerspectiveDeleteEvent(accountId, perspective.toDTO()));
          return "Successfully deleted the view";
        })));
  }

  @POST
  @Path("clone/{perspectiveId}")
  @Hidden
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "Clone perspective", nickname = "clonePerspective")
  @FeatureRestrictionCheck(FeatureRestrictionName.PERSPECTIVES)
  @LogAccountIdentifier
  @Operation(operationId = "clonePerspective", description = "Clone the Perspective corresponding to the identifier.",
      summary = "Clone a Perspective",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns the cloned CEView object with all the rules and filters",
            content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public ResponseDTO<CEView>
  create(@Parameter(required = true, description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
             NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId,
      @Parameter(required = true, description = "Unique identifier for the Perspective") @PathParam(
          "perspectiveId") String perspectiveId,
      @Valid @NotNull @Parameter(required = true, description = "Name for the Perspective clone") @QueryParam(
          "cloneName") String cloneName) {
    rbacHelper.checkPerspectiveEditPermission(accountId, null, null);
    HashMap<String, Object> properties = new HashMap<>();
    properties.put(MODULE, MODULE_NAME);
    List<ViewFieldIdentifier> dataSourcesList = ceViewService.get(perspectiveId).getDataSources();
    String dataSources = "";
    if (dataSourcesList != null) {
      dataSources = dataSourcesList.stream().map(Object::toString).collect(Collectors.joining(","));
    }
    properties.put(DATA_SOURCES, dataSources);
    properties.put(IS_CLONE, "YES");
    CEView ceViewCheck = updateTotalCost(ceViewService.clone(accountId, perspectiveId, cloneName));
    properties.put(PERSPECTIVE_ID, ceViewCheck.getUuid());
    telemetryReporter.sendTrackEvent(
        PERSPECTIVE_CREATED, null, accountId, properties, Collections.singletonMap(AMPLITUDE, true), Category.GLOBAL);
    return ResponseDTO.newResponse(ceViewCheck);
  }
}
