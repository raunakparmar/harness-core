package io.harness.cdng.artifact.resources.artifactory.service;

import io.harness.beans.IdentifierRef;

import software.wings.helpers.ext.jenkins.BuildDetails;

import java.util.List;
import java.util.Map;

public interface ArtifactoryResourceService {
  Map<String, String> getRepositories(
      String repositoryType, IdentifierRef connectorRef, String orgIdentifier, String projectIdentifier);

  List<BuildDetails> getBuildDetails(String repositoryName, String filePath, int maxVersions,
      IdentifierRef connectorRef, String orgIdentifier, String projectIdentifier);
}
