/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.remote.resources.recommendation;

import static io.harness.NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE;
import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.NGCommonEntityConstants;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.commons.beans.recommendation.ResourceType;
import io.harness.ccm.graphql.dto.recommendation.NodeRecommendationDTO;
import io.harness.ccm.graphql.dto.recommendation.WorkloadRecommendationDTO;
import io.harness.ccm.graphql.query.recommendation.RecommendationsDetailsQuery;
import io.harness.ccm.remote.utils.GraphQLToRESTHelper;
import io.harness.ccm.utils.LogAccountIdentifier;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.security.annotations.NextGenManagerAuth;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.leangen.graphql.execution.ResolutionEnvironment;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.OffsetDateTime;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Api("recommendation/details")
@Path("recommendation/details")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@NextGenManagerAuth
@Slf4j
@Service
@OwnedBy(CE)
@Tag(name = "Cloud Cost Recommendations Details",
    description = "Get Cloud Cost Recommendation Detail for a particular Recommendation identifier.")
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Bad Request",
    content = { @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = FailureDTO.class)) })
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal server error",
    content = { @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ErrorDTO.class)) })
public class RESTWrapperRecommendationDetails {
  @Inject private RecommendationsDetailsQuery detailsQuery;

  @GET
  @Path("node-pool")
  @Timed
  @LogAccountIdentifier
  @ExceptionMetered
  @Consumes(MediaType.APPLICATION_JSON)
  @ApiOperation(value = "Node Pool Recommendation Details", nickname = "nodeRecommendationDetail")
  @Operation(operationId = "nodeRecommendationDetail", description = "Cloud Cost Node Pool Recommendation details.",
      summary = "Node Pool Recommendation detail.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Returns the complete Node Pool Recommendation for a particular identifier.",
            content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public ResponseDTO<NodeRecommendationDTO>
  nodeRecommendationDetail(@Parameter(required = true, description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
                               NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId,
      @Parameter(required = true, description = "Node Pool Recommendation identifier.") @QueryParam(
          "id") @NotNull @Valid String id) {
    final ResolutionEnvironment env = GraphQLToRESTHelper.createResolutionEnv(accountId);

    NodeRecommendationDTO nodeRecommendation =
        (NodeRecommendationDTO) detailsQuery.recommendationDetails(id, ResourceType.NODE_POOL, null, null, env);
    return ResponseDTO.newResponse(nodeRecommendation);
  }

  @GET
  @Path("workload")
  @Timed
  @LogAccountIdentifier
  @ExceptionMetered
  @Consumes(MediaType.APPLICATION_JSON)
  @ApiOperation(value = "Workload Recommendation Details", nickname = "workloadRecommendationDetail")
  @Operation(operationId = "workloadRecommendationDetail", description = "Cloud Cost Workload Recommendation details.",
      summary = "Workload Recommendation detail.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Returns the complete Workload Recommendation for a particular identifier.",
            content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public ResponseDTO<WorkloadRecommendationDTO>
  workloadRecommendationDetail(
      @Parameter(required = true, description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId,
      @Parameter(required = true, description = "Workload Recommendation identifier.") @QueryParam(
          "id") @NotNull @Valid String id) {
    final ResolutionEnvironment env = GraphQLToRESTHelper.createResolutionEnv(accountId);

    WorkloadRecommendationDTO workloadRecommendation = (WorkloadRecommendationDTO) detailsQuery.recommendationDetails(
        id, ResourceType.WORKLOAD, OffsetDateTime.now().minusDays(7), OffsetDateTime.now(), env);
    return ResponseDTO.newResponse(workloadRecommendation);
  }
}
