/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.remote.resources.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.ccm.commons.beans.recommendation.ResourceType;
import io.harness.ccm.graphql.dto.recommendation.NodeRecommendationDTO;
import io.harness.ccm.graphql.dto.recommendation.RecommendationDetailsDTO;
import io.harness.ccm.graphql.dto.recommendation.RecommendationItemDTO;
import io.harness.ccm.graphql.dto.recommendation.WorkloadRecommendationDTO;
import io.harness.ccm.graphql.query.recommendation.RecommendationsDetailsQuery;
import io.harness.ccm.graphql.utils.GraphQLUtils;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import io.leangen.graphql.execution.ResolutionEnvironment;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class RESTWrapperRecommendationDetailsTest extends CategoryTest {
  @Mock private RecommendationsDetailsQuery detailsQuery;
  @InjectMocks private RESTWrapperRecommendationDetails restWrapperRecommendationDetails;

  private ArgumentCaptor<ResolutionEnvironment> envCaptor;

  private static final String ACCOUNT_ID = "ACCOUNT_ID";
  private static final String RECOMMENDATION_ID = "RECOMMENDATION_ID";
  private static final GraphQLUtils graphQLUtils = new GraphQLUtils();

  @Before
  public void setUp() throws Exception {
    envCaptor = ArgumentCaptor.forClass(ResolutionEnvironment.class);
  }

  @After
  public void tearDown() throws Exception {
    assertThat(graphQLUtils.getAccountIdentifier(envCaptor.getValue())).isEqualTo(ACCOUNT_ID);
  }

  @Test
  @Owner(developers = OwnerRule.UTSAV)
  @Category(UnitTests.class)
  public void testNodeRecommendationDetail() throws Exception {
    RecommendationDetailsDTO recommendationDetailsDTO = NodeRecommendationDTO.builder().id(RECOMMENDATION_ID).build();
    when(detailsQuery.recommendationDetails(
             any(String.class), eq(ResourceType.NODE_POOL), eq(null), eq(null), envCaptor.capture()))
        .thenReturn(recommendationDetailsDTO);

    NodeRecommendationDTO nodeRecommendationDTO =
        restWrapperRecommendationDetails.nodeRecommendationDetail(ACCOUNT_ID, RECOMMENDATION_ID).getData();

    assertThat(nodeRecommendationDTO).isInstanceOf(RecommendationDetailsDTO.class);
    assertThat(nodeRecommendationDTO).isEqualTo(recommendationDetailsDTO);

    verify(detailsQuery, times(0)).recommendationDetails(any(RecommendationItemDTO.class), any(), any(), any());
  }

  @Test
  @Owner(developers = OwnerRule.UTSAV)
  @Category(UnitTests.class)
  public void testWorkloadRecommendationDetail() throws Exception {
    RecommendationDetailsDTO recommendationDetailsDTO =
        WorkloadRecommendationDTO.builder().id(RECOMMENDATION_ID).build();
    when(detailsQuery.recommendationDetails(
             any(String.class), eq(ResourceType.WORKLOAD), any(), any(), envCaptor.capture()))
        .thenReturn(recommendationDetailsDTO);

    WorkloadRecommendationDTO workloadRecommendationDTO =
        restWrapperRecommendationDetails.workloadRecommendationDetail(ACCOUNT_ID, RECOMMENDATION_ID).getData();

    assertThat(workloadRecommendationDTO).isInstanceOf(RecommendationDetailsDTO.class);
    assertThat(workloadRecommendationDTO).isEqualTo(recommendationDetailsDTO);

    verify(detailsQuery, times(0)).recommendationDetails(any(RecommendationItemDTO.class), any(), any(), any());
  }
}
