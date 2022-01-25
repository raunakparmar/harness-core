package io.harness.artifactory;

import static io.harness.artifactory.ArtifactoryClientImpl.getArtifactoryClient;
import static io.harness.artifactory.ArtifactoryClientImpl.getBaseUrl;
import static io.harness.artifactory.ArtifactoryClientImpl.handleAndRethrow;
import static io.harness.artifactory.ArtifactoryClientImpl.handleErrorResponse;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.eraro.ErrorCode.ARTIFACT_SERVER_ERROR;
import static io.harness.eraro.ErrorCode.INVALID_ARTIFACT_SERVER;
import static io.harness.exception.WingsException.USER;

import static software.wings.helpers.ext.jenkins.BuildDetails.Builder.aBuildDetails;

import static java.lang.String.format;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.jfrog.artifactory.client.ArtifactoryRequest.ContentType.JSON;
import static org.jfrog.artifactory.client.ArtifactoryRequest.ContentType.TEXT;
import static org.jfrog.artifactory.client.ArtifactoryRequest.Method.GET;
import static org.jfrog.artifactory.client.ArtifactoryRequest.Method.POST;

import io.harness.eraro.ErrorCode;
import io.harness.exception.ArtifactoryServerException;
import io.harness.exception.WingsException;

import software.wings.common.AlphanumComparator;
import software.wings.helpers.ext.artifactory.FolderPath;
import software.wings.helpers.ext.jenkins.BuildDetails;

import com.google.inject.Singleton;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.jfrog.artifactory.client.Artifactory;
import org.jfrog.artifactory.client.ArtifactoryRequest;
import org.jfrog.artifactory.client.ArtifactoryResponse;
import org.jfrog.artifactory.client.impl.ArtifactoryRequestImpl;
import org.jfrog.artifactory.client.model.RepoPath;
import org.jfrog.artifactory.client.model.Repository;
import org.jfrog.artifactory.client.model.impl.PackageTypeImpl;
import org.jfrog.artifactory.client.model.repository.settings.RepositorySettings;

@Singleton
@Slf4j
public class ArtifactoryServiceHelper {
  private static final String REASON = "Reason:";
  private static final String KEY = "key";
  private static final String CREATED_BY = "created_by";
  private static final String SYSTEM = "_system_";
  private static final String RESULTS = "results";
  private static final String DOWNLOAD_FILE_FOR_GENERIC_REPO = "Downloading the file for generic repo";

  public Map<String, String> getRepositories(
      ArtifactoryConfigRequest artifactoryConfig, List<PackageTypeImpl> packageTypes) {
    log.info("Retrieving repositories for packages {}", packageTypes.toArray());
    Map<String, String> repositories = new HashMap<>();
    Artifactory artifactory = getArtifactoryClient(artifactoryConfig);
    ArtifactoryRequest repositoryRequest =
        new ArtifactoryRequestImpl().apiUrl("api/repositories/").method(GET).responseType(JSON);
    String errorOccurredWhileRetrievingRepositories = "Error occurred while retrieving repositories";
    try {
      ArtifactoryResponse response = artifactory.restCall(repositoryRequest);
      handleErrorResponse(response);
      List<Map<Object, Object>> responseList = response.parseBody(List.class);
      for (Map<Object, Object> repository : responseList) {
        String repoKey = repository.get(KEY).toString();
        try {
          Repository repo = artifactory.repository(repoKey).get();
          RepositorySettings settings = repo.getRepositorySettings();
          if (packageTypes.contains(settings.getPackageType())) {
            repositories.put(repository.get(KEY).toString(), repository.get(KEY).toString());
          }
        } catch (Exception e) {
          log.warn("Failed to get repository settings for repo {}, Reason {}", repoKey, ExceptionUtils.getMessage(e));
          // TODO : Get Settings api only works for Artifactory Pro
          repositories.put(repository.get(KEY).toString(), repository.get(KEY).toString());
        }
      }
      if (repositories.isEmpty()) {
        // Better way of handling Unauthorized access
        log.info("Repositories are not available of package types {} or User not authorized to access artifactory",
            packageTypes);
      }
      log.info("Retrieving repositories for packages {} success", packageTypes.toArray());
    } catch (SocketTimeoutException e) {
      log.error(errorOccurredWhileRetrievingRepositories, e);
      return repositories;
    } catch (Exception e) {
      log.error(errorOccurredWhileRetrievingRepositories, e);
      handleAndRethrow(e, USER);
    }
    return repositories;
  }

  public boolean validateArtifactPath(
      ArtifactoryConfigRequest artifactoryConfig, String repositoryName, String artifactPath, String repositoryType) {
    log.info("Validating artifact path {} for repository {} and repositoryType {}", artifactPath, repositoryName,
        repositoryType);
    if (isBlank(artifactPath)) {
      throw new ArtifactoryServerException("Artifact Pattern can not be empty", ARTIFACT_SERVER_ERROR, USER);
    }
    List<BuildDetails> filePaths = getFilePaths(artifactoryConfig, repositoryName, artifactPath, 1);

    if (isEmpty(filePaths)) {
      prepareAndThrowException("No artifact files matching with the artifact path [" + artifactPath + "]", USER, null);
    }
    log.info("Validating whether directory exists or not for Generic repository type by fetching file paths");
    return true;
  }

  public List<BuildDetails> getFilePaths(
      ArtifactoryConfigRequest artifactoryConfig, String repositoryName, String artifactPath, int maxVersions) {
    log.info("Retrieving file paths for repositoryName {} artifactPath {}", repositoryName, artifactPath);
    List<String> artifactPaths = new ArrayList<>();
    LinkedHashMap<String, String> map = new LinkedHashMap<>();
    Artifactory artifactory = getArtifactoryClient(artifactoryConfig);
    String artifactName;
    try {
      String aclQuery = "api/search/aql";
      String requestBody;
      if (isNotBlank(artifactPath)) {
        if (artifactPath.charAt(0) == '/') {
          artifactPath = artifactPath.substring(1);
        }
        String subPath;
        if (artifactPath.contains("/")) {
          String[] pathElems = artifactPath.split("/");
          subPath = getPath(Arrays.stream(pathElems).limit(pathElems.length - 1).collect(toList()));
          artifactName = pathElems[pathElems.length - 1];
          if (!artifactName.contains("?") && !artifactName.contains("*")) {
            artifactName = artifactName + "*";
          }
          requestBody = "items.find({\"repo\":\"" + repositoryName + "\"}, {\"path\": {\"$match\":\"" + subPath
              + "\"}}, {\"name\": {\"$match\": \"" + artifactName + "\"}}).sort({\"$desc\" : [\"created\"]}).limit("
              + maxVersions + ")";
        } else {
          artifactPath = artifactPath + "*";
          requestBody = "items.find({\"repo\":\"" + repositoryName + "\"}, {\"depth\": 1}, {\"name\": {\"$match\": \""
              + artifactPath + "\"}}).sort({\"$desc\" : [\"created\"]}).limit(" + maxVersions + ")";
        }
        ArtifactoryRequest repositoryRequest = new ArtifactoryRequestImpl()
                                                   .apiUrl(aclQuery)
                                                   .method(POST)
                                                   .requestBody(requestBody)
                                                   .requestType(TEXT)
                                                   .responseType(JSON);
        ArtifactoryResponse artifactoryResponse = artifactory.restCall(repositoryRequest);
        if (artifactoryResponse.getStatusLine().getStatusCode() == 403
            || artifactoryResponse.getStatusLine().getStatusCode() == 400) {
          log.warn(
              "User not authorized to perform or using OSS version deep level search. Trying with different search api. Message {}",
              artifactoryResponse.getStatusLine().getReasonPhrase());
          return getBuildDetails(artifactoryConfig, artifactory, repositoryName, artifactPath, maxVersions);
        }
        Map<String, List> response = artifactoryResponse.parseBody(Map.class);
        if (response != null) {
          List<Map<String, String>> results = response.get(RESULTS);
          if (results != null) {
            for (Map<String, String> result : results) {
              String createdBy = result.get(CREATED_BY);
              if (createdBy == null || !createdBy.equals(SYSTEM)) {
                String path = result.get("path");
                String name = result.get("name");
                String size = String.valueOf(result.get("size"));
                if (path != null && !path.equals(".")) {
                  artifactPaths.add(repositoryName + "/" + path + "/" + name);
                  map.put(repositoryName + "/" + path + "/" + name, size);
                } else {
                  artifactPaths.add(repositoryName + "/" + name);
                  map.put(repositoryName + "/" + name, size);
                }
              }
            }
          }
        }
        log.info("Artifact paths order from Artifactory Server" + artifactPaths);
        Collections.reverse(artifactPaths);
        String finalArtifactPath = artifactPath;
        return artifactPaths.stream()
            .map(path
                -> aBuildDetails()
                       .withNumber(constructBuildNumber(finalArtifactPath, path.substring(path.indexOf('/') + 1)))
                       .withArtifactPath(path)
                       .withBuildUrl(getBaseUrl(artifactoryConfig) + path)
                       .withArtifactFileSize(map.get(path))
                       .withUiDisplayName(
                           "Build# " + constructBuildNumber(finalArtifactPath, path.substring(path.indexOf('/') + 1)))
                       .build())
            .collect(toList());
      } else {
        throw new ArtifactoryServerException("Artifact path can not be empty", INVALID_ARTIFACT_SERVER, USER);
      }
    } catch (Exception e) {
      log.error("Error occurred while retrieving File Paths from Artifactory server {}",
          artifactoryConfig.getArtifactoryUrl(), e);
      handleAndRethrow(e, USER);
    }
    return new ArrayList<>();
  }

  private String getPath(List<String> pathElems) {
    StringBuilder groupIdBuilder = new StringBuilder();
    for (int i = 0; i < pathElems.size(); i++) {
      groupIdBuilder.append(pathElems.get(i));
      if (i != pathElems.size() - 1) {
        groupIdBuilder.append('/');
      }
    }
    return groupIdBuilder.toString();
  }

  private String constructBuildNumber(String artifactPattern, String path) {
    String[] tokens = artifactPattern.split("/");
    for (String token : tokens) {
      if (token.contains("*") || token.contains("+")) {
        return path.substring(artifactPattern.indexOf(token));
      }
    }
    return path;
  }

  private List<BuildDetails> getBuildDetails(ArtifactoryConfigRequest artifactoryConfig, Artifactory artifactory,
      String repositoryName, String artifactPath, int maxVersions) {
    List<String> artifactPaths = getFilePathsForAnonymousUser(artifactory, repositoryName, artifactPath, maxVersions);
    return artifactPaths.stream()
        .map(path
            -> aBuildDetails()
                   .withNumber(constructBuildNumber(artifactPath, path.substring(path.indexOf('/') + 1)))
                   .withArtifactPath(path)
                   .withBuildUrl(getBaseUrl(artifactoryConfig) + path)
                   .withUiDisplayName(
                       "Build# " + constructBuildNumber(artifactPath, path.substring(path.indexOf('/') + 1)))
                   .build())
        .collect(toList());
  }

  private List<String> getFilePathsForAnonymousUser(
      Artifactory artifactory, String repoKey, String artifactPath, int maxVersions) {
    log.info("Retrieving file paths for repoKey {} artifactPath {}", repoKey, artifactPath);
    List<String> artifactPaths = new ArrayList<>();

    List<software.wings.helpers.ext.artifactory.FolderPath> folderPaths;
    try {
      if (isNotBlank(artifactPath)) {
        if (artifactPath.charAt(0) == '/') {
          artifactPath = artifactPath.substring(1);
        }
        if (artifactPath.endsWith("/")) {
          artifactPath = artifactPath.substring(0, artifactPath.length() - 1);
        }
        Pattern pattern = Pattern.compile(artifactPath.replace(".", "\\.").replace("?", ".?").replace("*", ".*?"));
        if (artifactPath.contains("/")) {
          String[] pathElems = artifactPath.split("/");
          String subPath = getPath(Arrays.stream(pathElems).limit(pathElems.length - 1).collect(toList()));
          if (!subPath.contains("?") && !subPath.contains("*")) {
            folderPaths = getFolderPaths(artifactory, repoKey, "/" + subPath);
            if (folderPaths != null) {
              for (software.wings.helpers.ext.artifactory.FolderPath folderPath : folderPaths) {
                if (!folderPath.isFolder()) {
                  if (pattern.matcher(folderPath.getPath().substring(1) + folderPath.getUri()).find()) {
                    artifactPaths.add(repoKey + folderPath.getPath() + folderPath.getUri());
                  }
                }
              }
            }
          } else {
            String artifactName = pathElems[pathElems.length - 1];
            List<RepoPath> repoPaths =
                artifactory.searches().artifactsByName(artifactName).repositories(repoKey).doSearch();
            if (repoPaths != null) {
              for (RepoPath repoPath : repoPaths) {
                if (pattern.matcher(repoPath.getItemPath()).find()) {
                  artifactPaths.add(repoKey + "/" + repoPath.getItemPath());
                }
              }
            }
          }
        } else {
          folderPaths = getFolderPaths(artifactory, repoKey, "");
          if (folderPaths != null) {
            for (software.wings.helpers.ext.artifactory.FolderPath folderPath : folderPaths) {
              if (!folderPath.isFolder()) {
                if (pattern.matcher(folderPath.getUri()).find()) {
                  artifactPaths.add(repoKey + folderPath.getUri());
                }
              }
            }
          }
        }
        // Sort the alphanumeric order
        artifactPaths = artifactPaths.stream().sorted(new AlphanumComparator()).collect(toList());
        Collections.reverse(artifactPaths);
        artifactPaths = artifactPaths.stream().limit(maxVersions).collect(toList());
        Collections.reverse(artifactPaths);
      } else {
        throw new ArtifactoryServerException("Artifact path can not be empty", INVALID_ARTIFACT_SERVER);
      }
      log.info("Artifact paths order from Artifactory Server" + artifactPaths);
      return artifactPaths;
    } catch (Exception e) {
      log.error(
          format("Error occurred while retrieving File Paths from Artifactory server %s", artifactory.getUsername()),
          e);
      handleAndRethrow(e, USER);
    }
    return new ArrayList<>();
  }

  private List<software.wings.helpers.ext.artifactory.FolderPath> getFolderPaths(Artifactory artifactory, String repoKey, String repoPath) {
    // Add first level paths
    List<software.wings.helpers.ext.artifactory.FolderPath> folderPaths = new ArrayList<>();
    try {
      String apiStorageQuery = "api/storage/";
      apiStorageQuery = apiStorageQuery + repoKey + repoPath;
      ArtifactoryRequest repositoryRequest =
          new ArtifactoryRequestImpl().apiUrl(apiStorageQuery).method(GET).responseType(JSON);
      ArtifactoryResponse artifactoryResponse = artifactory.restCall(repositoryRequest);
      handleErrorResponse(artifactoryResponse);
      LinkedHashMap<String, Object> response = artifactoryResponse.parseBody(LinkedHashMap.class);
      if (response == null) {
        return folderPaths;
      }
      List<LinkedHashMap<String, Object>> results = (List<LinkedHashMap<String, Object>>) response.get("children");
      if (isEmpty(results)) {
        return folderPaths;
      }
      for (LinkedHashMap<String, Object> result : results) {
        folderPaths.add(FolderPath.builder()
                            .path((String) response.get("path"))
                            .uri((String) result.get("uri"))
                            .folder((boolean) result.get("folder"))
                            .build());
      }
    } catch (Exception e) {
      log.error("Exception occurred in retrieving folder paths", e);
    }
    return folderPaths;
  }

  private void prepareAndThrowException(
      String message, EnumSet<WingsException.ReportTarget> reportTargets, Exception e) {
    throw new ArtifactoryServerException(message, ErrorCode.INVALID_ARTIFACT_SERVER, reportTargets, e);
  }

  public InputStream downloadArtifacts(ArtifactoryConfigRequest artifactoryConfig, String repoKey,
      Map<String, String> metadata, String artifactPathMetadataKey, String artifactFileNameMetadataKey) {
    Artifactory artifactory = getArtifactoryClient(artifactoryConfig);
    Set<String> artifactNames = new HashSet<>();
    String artifactPath = metadata.get(artifactPathMetadataKey).replaceFirst(repoKey, "").substring(1);
    String artifactName = metadata.get(artifactFileNameMetadataKey);

    try {
      log.info("Artifact name {}", artifactName);
      if (artifactNames.add(artifactName)) {
        if (metadata.get(artifactPathMetadataKey) != null) {
          log.info(DOWNLOAD_FILE_FOR_GENERIC_REPO);
          log.info("Downloading file {} ", artifactPath);
          InputStream inputStream = artifactory.repository(repoKey).download(artifactPath).doDownload();
          log.info("Downloading file {} success", artifactPath);
          return inputStream;
        }
      }
    } catch (Exception e) {
      log.error("Failed to download the artifact of repository {} from path {}", repoKey, artifactPath, e);
      String msg =
          "Failed to download the latest artifacts  of repository [" + repoKey + "] file path [" + artifactPath;
      throw new ArtifactoryServerException(
          msg + REASON + ExceptionUtils.getRootCauseMessage(e), ARTIFACT_SERVER_ERROR, USER);
    }
    log.info(
        "Downloading artifacts from artifactory for repository  {} and file path {} success", repoKey, artifactPath);
    return null;
  }
}
