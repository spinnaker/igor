package com.netflix.spinnaker.igor.scm;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.netflix.spinnaker.igor.scm.bitbucket.client.BitBucketMaster;
import com.netflix.spinnaker.igor.scm.github.client.GitHubMaster;
import com.netflix.spinnaker.igor.scm.gitlab.client.GitLabMaster;
import com.netflix.spinnaker.igor.scm.stash.client.StashMaster;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Exposes APIs to retrieve keel declarative manifests from source control repos. */
@RestController
@RequestMapping("/keel")
public class KeelManifestsController {
  private static final String KEEL_MANIFESTS_BASE_PATH = ".netflix/spinnaker";
  private static final Logger log = LoggerFactory.getLogger(AbstractCommitController.class);

  private final Optional<StashMaster> stashMaster;
  private final Optional<GitHubMaster> gitHubMaster;
  private final Optional<GitLabMaster> gitLabMaster;
  private final Optional<BitBucketMaster> bitBucketMaster;

  private ObjectMapper yamlMapper;

  public KeelManifestsController(
      Optional<StashMaster> stashMaster,
      Optional<GitHubMaster> gitHubMaster,
      Optional<GitLabMaster> gitLabMaster,
      Optional<BitBucketMaster> bitBucketMaster) {
    this.stashMaster = stashMaster;
    this.gitHubMaster = gitHubMaster;
    this.gitLabMaster = gitLabMaster;
    this.bitBucketMaster = bitBucketMaster;
    this.yamlMapper =
        new ObjectMapper(new YAMLFactory())
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
  }

  @GetMapping(path = "/{scmType}/{projectKey}/{repositorySlug}/manifests")
  public List<String> listKeelManifests(
      @PathVariable String scmType,
      @PathVariable final String projectKey,
      @PathVariable final String repositorySlug,
      @RequestParam(required = false, defaultValue = "refs/heads/master") String at) {
    final String path = KEEL_MANIFESTS_BASE_PATH;
    log.debug("Listing keel manifests at " + projectKey + ":" + repositorySlug + "/" + path);
    return getScmMaster(scmType).listDirectory(projectKey, repositorySlug, path, at);
  }

  @GetMapping(path = "/{scmType}/{projectKey}/{repositorySlug}/manifests/{manifest}")
  public ResponseEntity<Map<String, Object>> getKeelManifest(
      @PathVariable String scmType,
      @PathVariable final String projectKey,
      @PathVariable final String repositorySlug,
      @PathVariable final String manifest,
      @RequestParam(required = false, defaultValue = "refs/heads/master") String at) {
    // TODO: make base path configurable
    final String path = KEEL_MANIFESTS_BASE_PATH + "/" + manifest;
    log.debug("Retrieving keel manifest from " + projectKey + ":" + repositorySlug + "/" + path);
    String manifestContents =
        getScmMaster(scmType).getTextFileContents(projectKey, repositorySlug, path, at);
    try {
      // TODO: support both JSON and YAML formats
      return new ResponseEntity<>(yamlMapper.readValue(manifestContents, Map.class), HttpStatus.OK);
    } catch (Exception e) {
      log.error(
          "Error reading or parsing contents of delivery config manifest {}: {}",
          manifest,
          e.getMessage(),
          e);
      return new ResponseEntity<>(Collections.emptyMap(), HttpStatus.BAD_REQUEST);
    }
  }

  private ScmMaster getScmMaster(final String scmType) {
    Optional<? extends ScmMaster> scmMaster;

    if (scmType.equalsIgnoreCase("bitbucket")) {
      scmMaster = bitBucketMaster;
    } else if (scmType.equalsIgnoreCase("github")) {
      scmMaster = gitHubMaster;
    } else if (scmType.equalsIgnoreCase("gitlab")) {
      scmMaster = gitLabMaster;
    } else if (scmType.equalsIgnoreCase("stash")) {
      scmMaster = stashMaster;
    } else {
      throw new IllegalArgumentException("Unknown SCM type " + scmType);
    }

    if (scmMaster.isPresent()) {
      return scmMaster.get();
    } else {
      throw new IllegalArgumentException(scmType + " client requested but not configured");
    }
  }
}
