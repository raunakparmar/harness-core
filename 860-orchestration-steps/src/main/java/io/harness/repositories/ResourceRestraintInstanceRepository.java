/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotation.HarnessRepo;
import io.harness.annotations.dev.OwnedBy;
import io.harness.distribution.constraint.Consumer;
import io.harness.steps.resourcerestraint.beans.ResourceRestraintInstance;

import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.CrudRepository;

@OwnedBy(CDC)
@HarnessRepo
public interface ResourceRestraintInstanceRepository extends CrudRepository<ResourceRestraintInstance, String> {
  Optional<ResourceRestraintInstance> findFirstByResourceRestraintIdOrderByOrderDesc(String resourceRestraintId);
  List<ResourceRestraintInstance> findByReleaseEntityTypeAndReleaseEntityId(
      String releaseEntityType, String releaseEntityId);
  Optional<ResourceRestraintInstance> findByUuidAndResourceUnitAndStateIn(
      String uuid, String resourceUnit, List<Consumer.State> states);
}
