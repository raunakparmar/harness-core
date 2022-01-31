/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.steps;

import io.harness.cdng.k8s.K8sBGSwapServicesStepNode;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;

import com.google.common.collect.Sets;
import java.util.Set;

public class K8sBGSwapServicesStepPlanCreator extends CDPMSStepPlanCreatorV2<K8sBGSwapServicesStepNode> {
  @Override
  public Set<String> getSupportedStepTypes() {
    return Sets.newHashSet(StepSpecTypeConstants.K8S_BG_SWAP_SERVICES);
  }

  @Override
  public Class<K8sBGSwapServicesStepNode> getFieldClass() {
    return K8sBGSwapServicesStepNode.class;
  }

  @Override
  public PlanCreationResponse createPlanForField(PlanCreationContext ctx, K8sBGSwapServicesStepNode stepElement) {
    return super.createPlanForField(ctx, stepElement);
  }
}
