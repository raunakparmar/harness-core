package io.harness.artifactory;

import software.wings.helpers.ext.jenkins.BuildDetails;

import java.util.List;
import java.util.Map;

public interface ArtifactoryNgService {
  /**
   * @param artifactoryConfig
   * @param repositoryName
   * @param artifactPath
   * @param maxVersions
   * @return
   */
  List<BuildDetails> getFilePaths(
      ArtifactoryConfigRequest artifactoryConfig, String repositoryName, String artifactPath, int maxVersions);

  Map<String, String> getRepositories(ArtifactoryConfigRequest artifactoryConfig, String packageType);
}
