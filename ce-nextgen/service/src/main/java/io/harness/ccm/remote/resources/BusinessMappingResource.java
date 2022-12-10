/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.remote.resources;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.outbox.TransactionOutboxModule.OUTBOX_TRANSACTION_TEMPLATE;
import static io.harness.springdata.PersistenceUtils.DEFAULT_RETRY_POLICY;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.audittrails.events.CostCategoryCreateEvent;
import io.harness.ccm.audittrails.events.CostCategoryDeleteEvent;
import io.harness.ccm.audittrails.events.CostCategoryUpdateEvent;
import io.harness.ccm.rbac.CCMRbacHelper;
import io.harness.ccm.views.businessMapping.entities.BusinessMapping;
import io.harness.ccm.views.businessMapping.service.intf.BusinessMappingService;
import io.harness.ccm.views.service.CEViewService;
import io.harness.exception.InvalidRequestException;
import io.harness.outbox.api.OutboxService;
import io.harness.rest.RestResponse;
import io.harness.security.annotations.NextGenManagerAuth;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import java.util.List;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.hibernate.validator.constraints.NotEmpty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Api("business-mapping")
@Path("/business-mapping")
@Produces("application/json")
@NextGenManagerAuth
@Slf4j
@Service
@OwnedBy(CE)
public class BusinessMappingResource {
  @Inject BusinessMappingService businessMappingService;
  @Inject @Named(OUTBOX_TRANSACTION_TEMPLATE) private TransactionTemplate transactionTemplate;
  @Inject private OutboxService outboxService;
  @Inject CCMRbacHelper rbacHelper;
  @Inject CEViewService ceViewService;

  private final RetryPolicy<Object> transactionRetryPolicy = DEFAULT_RETRY_POLICY;

  @POST
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "Create Business Mapping", nickname = "createBusinessMapping")
  public RestResponse<BusinessMapping> save(
      @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId, BusinessMapping businessMapping) {
    rbacHelper.checkCostCategoryEditPermission(accountId, null, null);
    if (!businessMappingService.isNamePresent(businessMapping.getName(), businessMapping.getAccountId())) {
      throw new InvalidRequestException("Cost category name already exists.");
    }
    if (businessMappingService.isInvalidBusinessMappingUnallocatedCostLabel(businessMapping)) {
      throw new InvalidRequestException("Unallocated cost bucket label does not allow Others or Unallocated");
    }
    BusinessMapping costCategory = businessMappingService.save(businessMapping);
    Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
      outboxService.save(new CostCategoryCreateEvent(accountId, costCategory.toDTO()));
      return true;
    }));
    return new RestResponse<>(costCategory);
  }

  @GET
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "Get List Of Business Mappings", nickname = "getBusinessMappingList")
  public RestResponse<List<BusinessMapping>> list(@QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId) {
    rbacHelper.checkCostCategoryViewPermission(accountId, null, null);
    return new RestResponse<>(businessMappingService.list(accountId));
  }

  @GET
  @Path("{id}")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "Get Business Mapping", nickname = "getBusinessMapping")
  public RestResponse<BusinessMapping> get(
      @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId, @PathParam("id") String businessMappingId) {
    rbacHelper.checkCostCategoryViewPermission(accountId, null, null);
    return new RestResponse<>(businessMappingService.get(businessMappingId, accountId));
  }

  @PUT
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "Update Business Mapping", nickname = "updateBusinessMapping")
  public RestResponse<String> update(
      @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId, BusinessMapping businessMapping) {
    rbacHelper.checkCostCategoryEditPermission(accountId, null, null);
    BusinessMapping oldCostCategory = businessMappingService.get(businessMapping.getUuid(), accountId);
    if (!oldCostCategory.getName().equals(businessMapping.getName())) {
      if (!businessMappingService.isNamePresent(businessMapping.getName(), businessMapping.getAccountId())) {
        throw new InvalidRequestException("Cost category name already exists.");
      }
    }
    if (businessMappingService.isInvalidBusinessMappingUnallocatedCostLabel(businessMapping)) {
      throw new InvalidRequestException("Unallocated cost bucket label does not allow Others or Unallocated");
    }
    BusinessMapping newCostCategory = businessMappingService.update(businessMapping);
    if (!oldCostCategory.getName().equals(newCostCategory.getName())) {
      ceViewService.updateBusinessMappingName(accountId, newCostCategory.getUuid(), newCostCategory.getName());
    }
    Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
      outboxService.save(new CostCategoryUpdateEvent(accountId, newCostCategory.toDTO(), oldCostCategory.toDTO()));
      return true;
    }));
    return new RestResponse<>("Successfully updated the Business Mapping");
  }

  @DELETE
  @Path("{id}")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "Delete Business Mapping", nickname = "deleteBusinessMapping")
  public RestResponse<String> delete(@NotEmpty @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @PathParam("id") String businessMappingId) {
    rbacHelper.checkCostCategoryDeletePermission(accountId, null, null);
    BusinessMapping costCategory = businessMappingService.get(businessMappingId, accountId);
    businessMappingService.delete(businessMappingId, accountId);
    Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(status -> {
      outboxService.save(new CostCategoryDeleteEvent(accountId, costCategory.toDTO()));
      return true;
    }));
    return new RestResponse<>("Successfully deleted the Business Mapping");
  }
}
