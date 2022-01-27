package software.wings.resources;

import static io.harness.annotations.dev.HarnessTeam.DEL;

import static software.wings.security.PermissionAttribute.Action.UPDATE;
import static software.wings.security.PermissionAttribute.PermissionType.ACCOUNT_MANAGEMENT;

import io.harness.annotations.dev.OwnedBy;
import io.harness.datahandler.services.AdminRingService;
import io.harness.exception.InvalidRequestException;
import io.harness.rest.RestResponse;

import software.wings.beans.User;
import software.wings.security.UserThreadLocal;
import software.wings.security.annotations.AdminPortalAuth;
import software.wings.security.annotations.ApiKeyAuthorized;
import software.wings.service.intfc.HarnessUserGroupService;

import com.google.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import retrofit2.http.Body;

@OwnedBy(DEL)
@Path("/admin/rings")
@Slf4j
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@AdminPortalAuth
@RequiredArgsConstructor(onConstructor = @__({ @Inject }))
public class AdminRingResource {
  private final HarnessUserGroupService userGroupService;
  private final AdminRingService adminRingService;

  @ApiKeyAuthorized(permissionType = ACCOUNT_MANAGEMENT, action = UPDATE)
  @PUT
  @Path("/{ringName}/delegate-tag")
  public RestResponse<Boolean> updateDelegateTag(
      @PathParam("ringName") final String ringName, @Body final String delegateTag) {
    final User user = getUser();
    if (userGroupService.isHarnessSupportUser(getUser().getUuid())) {
      log.info("Updating delegate image tag to {} for ring {}", delegateTag, ringName);
      return new RestResponse<>(adminRingService.updateDelegateImageTag(delegateTag, ringName));
    } else {
      log.warn("User {} not a harness support user, failing the request", user.getEmail());
      return new RestResponse<>(false);
    }
  }

  @ApiKeyAuthorized(permissionType = ACCOUNT_MANAGEMENT, action = UPDATE)
  @PUT
  @Path("/{ringName}/upgrader-tag")
  public RestResponse<Boolean> updateUpgraderTag(
      @PathParam("ringName") final String ringName, @Body final String upgraderTag) {
    final User user = getUser();
    if (userGroupService.isHarnessSupportUser(getUser().getUuid())) {
      log.info("Updating upgrader image tag to {} for ring {}", upgraderTag, ringName);
      return new RestResponse<>(adminRingService.updateUpgraderImageTag(upgraderTag, ringName));
    } else {
      log.warn("User {} not a harness support user, failing the request", user.getEmail());
      return new RestResponse<>(false);
    }
  }

  @ApiKeyAuthorized(permissionType = ACCOUNT_MANAGEMENT, action = UPDATE)
  @PUT
  @Path("/{ringName}/delegate-version")
  public RestResponse<Boolean> updateDelegateVersion(
      @PathParam("ringName") final String ringName, @Body final String delegateVersion) {
    final User user = getUser();
    if (userGroupService.isHarnessSupportUser(getUser().getUuid())) {
      log.info("Updating delegate jar version to {} for ring {}", delegateVersion, ringName);
      return new RestResponse<>(adminRingService.updateDelegateVersion(delegateVersion, ringName));
    } else {
      log.warn("User {} not a harness support user, failing the request", user.getEmail());
      return new RestResponse<>(false);
    }
  }

  @NotNull
  private User getUser() {
    final User user = UserThreadLocal.get();
    if (user == null) {
      throw new InvalidRequestException("Invalid User");
    }
    return user;
  }
}
