/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.resources;

import static io.harness.annotations.dev.HarnessTeam.DEL;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import static software.wings.security.PermissionAttribute.ResourceType.DELEGATE;

import io.harness.annotations.dev.BreakDependencyOn;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.logging.AccountLogContext;
import io.harness.logging.AutoLogContext;
import io.harness.perpetualtask.PerpetualTaskLogContext;
import io.harness.perpetualtask.instancesync.InstanceSyncResponsePublisher;
import io.harness.perpetualtask.instancesyncv2.CgInstanceSyncResponse;
import io.harness.perpetualtask.instancesyncv2.InstanceSyncTrackedDeploymentDetails;
import io.harness.rest.RestResponse;
import io.harness.security.annotations.DelegateAuth;

import software.wings.instancesyncv2.CgInstanceSyncServiceV2;
import software.wings.security.annotations.Scope;
import software.wings.service.impl.instance.InstanceHelper;

import com.google.inject.Inject;
import io.dropwizard.jersey.protobuf.ProtocolBufferMediaType;
import io.swagger.annotations.Api;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.validator.constraints.NotEmpty;

@Api("/instancesync")
@Path("/instancesync")
@Produces("application/json")
@Scope(DELEGATE)
@Slf4j
@TargetModule(HarnessModule._420_DELEGATE_SERVICE)
@OwnedBy(DEL)
@BreakDependencyOn("software.wings.service.impl.instance.InstanceHelper")
public class InstanceSyncResource {
  @Inject private InstanceHelper instanceHelper;
  @Inject private InstanceSyncResponsePublisher instanceSyncResponsePublisher;
  @Inject private CgInstanceSyncServiceV2 instanceSyncServiceV2;

  @DelegateAuth
  @POST
  @Path("instance-sync/{perpetualTaskId}")
  public RestResponse<Boolean> processInstanceSyncResult(@PathParam("perpetualTaskId") @NotEmpty String perpetualTaskId,
      @QueryParam("accountId") @NotEmpty String accountId, DelegateResponseData response) {
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR);
         AutoLogContext ignore2 = new PerpetualTaskLogContext(perpetualTaskId, OVERRIDE_ERROR)) {
      instanceHelper.processInstanceSyncResponseFromPerpetualTask(perpetualTaskId.replaceAll("[\r\n]", ""), response);
    } catch (Exception e) {
      log.error("Failed to process results for perpetual task: [{}]", perpetualTaskId.replaceAll("[\r\n]", ""), e);
    }
    return new RestResponse<>(true);
  }

  @DelegateAuth
  @POST
  @Path("instance-sync-ng/{perpetualTaskId}")
  public RestResponse<Boolean> processInstanceSyncNGResult(
      @PathParam("perpetualTaskId") @NotEmpty String perpetualTaskId,
      @QueryParam("accountId") @NotEmpty String accountId, DelegateResponseData response) {
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR);
         AutoLogContext ignore2 = new PerpetualTaskLogContext(perpetualTaskId, OVERRIDE_ERROR)) {
      instanceSyncResponsePublisher.publishInstanceSyncResponseToNG(
          accountId, perpetualTaskId.replaceAll("[\r\n]", ""), response);
    } catch (Exception e) {
      log.error("Failed to process results for perpetual task: [{}]", perpetualTaskId.replaceAll("[\r\n]", ""), e);
    }
    return new RestResponse<>(true);
  }

  @DelegateAuth
  @POST
  @Path("instance-sync-v2/{perpetualTaskId}")
  @Consumes(ProtocolBufferMediaType.APPLICATION_PROTOBUF)
  public RestResponse<Boolean> processInstanceSyncV2Result(
      @PathParam("perpetualTaskId") @NotEmpty String perpetualTaskId,
      @QueryParam("accountId") @NotEmpty String accountId, CgInstanceSyncResponse response) {
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR);
         AutoLogContext ignore2 = new PerpetualTaskLogContext(perpetualTaskId, OVERRIDE_ERROR)) {
      instanceSyncServiceV2.processInstanceSyncResult(perpetualTaskId.replaceAll("[\r\n]", ""), response);
    } catch (Exception e) {
      log.error("Failed to process results for perpetual task: [{}]", perpetualTaskId.replaceAll("[\r\n]", ""), e);
    }
    return new RestResponse<>(true);
  }

  @DelegateAuth
  @GET
  @Path("instance-sync-v2/task-details/{perpetualTaskId}")
  @Produces(ProtocolBufferMediaType.APPLICATION_PROTOBUF)
  public Response fetchInstanceSyncV2TrackedDeploymentDetails(
      @PathParam("perpetualTaskId") String perpetualTaskId, @QueryParam("accountId") String accountId) {
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR);
         AutoLogContext ignore2 = new PerpetualTaskLogContext(perpetualTaskId, OVERRIDE_ERROR)) {
      InstanceSyncTrackedDeploymentDetails details =
          instanceSyncServiceV2.fetchTaskDetails(perpetualTaskId.replaceAll("[\r\n]", ""), accountId);
      return Response.ok(details).build();
    } catch (Exception e) {
      log.error("Failed to process results for perpetual task: [{}]", perpetualTaskId.replaceAll("[\r\n]", ""), e);
    }
    return Response.noContent().build();
  }
}
