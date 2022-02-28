package io.harness.app.resources;

import static io.harness.account.accesscontrol.AccountAccessControlPermissions.EDIT_ACCOUNT_PERMISSION;
import static io.harness.account.accesscontrol.AccountAccessControlPermissions.VIEW_ACCOUNT_PERMISSION;
import static io.harness.annotations.dev.HarnessTeam.CI;

import io.harness.NGCommonEntityConstants;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.account.accesscontrol.ResourceTypes;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ci.beans.entities.CIExecutionImages;
import io.harness.execution.CIExecutionConfigService;
import io.harness.execution.DeprecatedImageInfo;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;

import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import java.util.List;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.AllArgsConstructor;
import retrofit2.http.Body;

@OwnedBy(CI)
@Api("/execution-config")
@Path("/execution-config")
@Produces({"application/json"})
@Consumes({"application/json"})
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
public class CIExecutionConfigResource {
  @Inject CIExecutionConfigService configService;
  @POST
  @Path("/")
  @ApiOperation(value = "Update execution config", nickname = "updateExecutionConfig")
  @NGAccessControlCheck(resourceType = ResourceTypes.ACCOUNT, permission = EDIT_ACCOUNT_PERMISSION)
  public ResponseDTO<Boolean> updateExecutionConfig(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @RequestBody(
          required = true, description = "Details of the filters applied") @Body CIExecutionImages cIExecutionImages) {
    return ResponseDTO.newResponse(configService.updateCIContainerTag(accountIdentifier, cIExecutionImages));
  }

  @GET
  @Path("/")
  @ApiOperation(value = "Get execution config", nickname = "getExecutionConfig")
  @NGAccessControlCheck(resourceType = ResourceTypes.ACCOUNT, permission = VIEW_ACCOUNT_PERMISSION)
  public ResponseDTO<List<DeprecatedImageInfo>> getExecutionConfig(
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier) {
    return ResponseDTO.newResponse(configService.getDeprecatedTags(accountIdentifier));
  }
}
