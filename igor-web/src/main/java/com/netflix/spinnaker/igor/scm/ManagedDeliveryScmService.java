/*
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.igor.scm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.netflix.spinnaker.igor.config.ManagedDeliveryConfigProperties;
import com.netflix.spinnaker.igor.scm.bitbucket.client.BitBucketController;
import com.netflix.spinnaker.igor.scm.github.client.GitHubController;
import com.netflix.spinnaker.igor.scm.gitlab.client.GitLabController;
import com.netflix.spinnaker.igor.scm.stash.client.StashController;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

/** Support for retrieving Managed Delivery-related information from SCM systems. */
@Service
@EnableConfigurationProperties(ManagedDeliveryConfigProperties.class)
public class ManagedDeliveryScmService {
  private static final Logger log = LoggerFactory.getLogger(AbstractCommitController.class);

  private final ManagedDeliveryConfigProperties configProperties;
  private final ObjectMapper jsonMapper;
  private final ObjectMapper yamlMapper;
  private final Optional<StashController> stashController;
  private final Optional<GitHubController> gitHubController;
  private final Optional<GitLabController> gitLabController;
  private final Optional<BitBucketController> bitBucketController;

  public ManagedDeliveryScmService(
      Optional<ManagedDeliveryConfigProperties> configProperties,
      Optional<StashController> stashController,
      Optional<GitHubController> gitHubController,
      Optional<GitLabController> gitLabController,
      Optional<BitBucketController> bitBucketController) {
    this.configProperties =
        configProperties.isPresent()
            ? configProperties.get()
            : new ManagedDeliveryConfigProperties();
    this.stashController = stashController;
    this.gitHubController = gitHubController;
    this.gitLabController = gitLabController;
    this.bitBucketController = bitBucketController;
    this.jsonMapper = new ObjectMapper();
    this.yamlMapper = new ObjectMapper(new YAMLFactory());
  }

  /**
   * Given details about a supported git source control repository, and optional filters for a
   * sub-directory within the repo, the file extensions to look for, and the specific git reference
   * to use, returns a list of (potential) Managed Delivery config manifests found at that location.
   *
   * <p>Note that this method does not recurse the specified sub-directory when listing files.
   */
  public List<String> listDeliveryConfigManifests(
      final String scmType,
      final String project,
      final String repository,
      final String directory,
      final String extension,
      final String ref) {

    if (scmType == null || project == null || repository == null) {
      throw new IllegalArgumentException("scmType, project and repository are required arguments");
    }

    final String path =
        configProperties.getManifestBasePath() + "/" + ((directory != null) ? directory : "");

    log.debug(
        "Listing keel manifests at " + scmType + "://" + project + "/" + repository + "/" + path);

    return getScmController(scmType)
        .listDirectory(
            project, repository, path, (ref != null) ? ref : ScmController.DEFAULT_GIT_REF)
        .stream()
        .filter(it -> it.endsWith("." + ((extension != null) ? extension : "yml")))
        .collect(Collectors.toList());
  }

  /**
   * Given details about a supported git source control repository, the filename of a Managed
   * Delivery config manifest, and optional filters for a sub-directory within the repo and the
   * specific git reference to use, returns the contents of the manifest.
   *
   * <p>This API supports both YAML and JSON for the format of the manifest in source control, but
   * always returns the parsed contents as a Map.
   */
  public Map<String, Object> getDeliveryConfigManifest(
      final String scmType,
      final String project,
      final String repository,
      final String directory,
      final String manifest,
      final String ref) {

    if (scmType == null || project == null || repository == null) {
      throw new IllegalArgumentException("scmType, project and repository are required arguments");
    }

    if (!(manifest.endsWith(".yml") || manifest.endsWith(".yaml") || manifest.endsWith(".json"))) {
      throw new IllegalArgumentException(
          String.format("Unrecognized file format for %s. Please use YAML or JSON.", manifest));
    }

    final String path =
        (configProperties.getManifestBasePath()
            + "/"
            + ((directory != null) ? directory + "/" : "")
            + manifest);

    log.debug(
        "Retrieving delivery config manifest from " + project + ":" + repository + "/" + path);
    String manifestContents =
        getScmController(scmType).getTextFileContents(project, repository, path, ref);

    try {
      if (manifest.endsWith(".json")) {
        return (Map<String, Object>) jsonMapper.readValue(manifestContents, Map.class);
      } else {
        return (Map<String, Object>) yamlMapper.readValue(manifestContents, Map.class);
      }
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException(
          String.format(
              "Error parsing contents of delivery config manifest %s: %s",
              manifest, e.getMessage()));
    }
  }

  private ScmController getScmController(final String scmType) {
    Optional<? extends ScmController> scmController;

    if (scmType.equalsIgnoreCase("bitbucket")) {
      scmController = bitBucketController;
    } else if (scmType.equalsIgnoreCase("github")) {
      scmController = gitHubController;
    } else if (scmType.equalsIgnoreCase("gitlab")) {
      scmController = gitLabController;
    } else if (scmType.equalsIgnoreCase("stash")) {
      scmController = stashController;
    } else {
      throw new IllegalArgumentException("Unknown SCM type " + scmType);
    }

    if (scmController.isPresent()) {
      return scmController.get();
    } else {
      throw new IllegalArgumentException(scmType + " client requested but not configured");
    }
  }
}
