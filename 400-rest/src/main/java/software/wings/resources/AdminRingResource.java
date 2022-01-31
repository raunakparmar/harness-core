package software.wings.resources;

import static io.harness.annotations.dev.HarnessTeam.DEL;

import static software.wings.security.PermissionAttribute.Action.UPDATE;
import static software.wings.security.PermissionAttribute.PermissionType.ACCOUNT_MANAGEMENT;

import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.OwnedBy;
import io.harness.datahandler.services.AdminRingService;
import io.harness.exception.InvalidRequestException;
import io.harness.rest.RestResponse;
import io.harness.security.annotations.InternalApi;

import software.wings.security.annotations.ApiKeyAuthorized;

import com.google.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import retrofit2.http.Body;

@OwnedBy(DEL)
@Path("/admin/rings")
@Slf4j
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor(onConstructor = @__({ @Inject }))
public class AdminRingResource {
  private final AdminRingService adminRingService;

  @InternalApi
  @PUT
  @Path("/{ringName}/delegate-tag")
  public RestResponse<Boolean> updateDelegateTag(
      @PathParam("ringName") final String ringName, @Body final String delegateTag) {
    if (isBlank(delegateTag) || isBlank(ringName)) {
      throw new InvalidRequestException("Empty delegate tag or ring name");
    }
    return new RestResponse<>(adminRingService.updateDelegateImageTag(delegateTag, ringName));
  }

  @ApiKeyAuthorized(permissionType = ACCOUNT_MANAGEMENT, action = UPDATE)
  @PUT
  @Path("/{ringName}/upgrader-tag")
  public RestResponse<Boolean> updateUpgraderTag(
      @PathParam("ringName") final String ringName, @Body final String upgraderTag) {
    if (isBlank(upgraderTag) || isBlank(ringName)) {
      throw new InvalidRequestException("Empty upgrader tag or ring name");
    }
    return new RestResponse<>(adminRingService.updateUpgraderImageTag(upgraderTag, ringName));
  }

  @ApiKeyAuthorized(permissionType = ACCOUNT_MANAGEMENT, action = UPDATE)
  @PUT
  @Path("/{ringName}/delegate-version")
  public RestResponse<Boolean> updateDelegateVersion(
      @PathParam("ringName") final String ringName, @Body final String delegateVersion) {
    if (isBlank(delegateVersion) || isBlank(ringName)) {
      throw new InvalidRequestException("Empty delegate version or ring name");
    }
    return new RestResponse<>(adminRingService.updateDelegateVersion(delegateVersion, ringName));
  }
}
