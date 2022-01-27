/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.stepDetail;

import io.harness.annotation.HarnessRepo;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.stepDetail.StepDetailInstance;

import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.PagingAndSortingRepository;

@OwnedBy(HarnessTeam.PIPELINE)
@HarnessRepo
public interface StepDetailsInstanceRepository extends PagingAndSortingRepository<StepDetailInstance, String> {
  List<StepDetailInstance> findByNodeExecutionId(String nodeExecutionId);
  Optional<StepDetailInstance> findByNameAndNodeExecutionId(String name, String nodeExecutionId);
}
