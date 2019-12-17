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
@RequestMapping("/delivery-config/manifests")
public class DeliveryConfigManifestsController {
  private static final String KEEL_MANIFESTS_BASE_PATH = ".netflix/spinnaker";
  private static final Logger log = LoggerFactory.getLogger(AbstractCommitController.class);

  private final Optional<StashMaster> stashMaster;
  private final Optional<GitHubMaster> gitHubMaster;
  private final Optional<GitLabMaster> gitLabMaster;
  private final Optional<BitBucketMaster> bitBucketMaster;

  private final ObjectMapper jsonMapper;
  private final ObjectMapper yamlMapper;

  public DeliveryConfigManifestsController(
      Optional<StashMaster> stashMaster,
      Optional<GitHubMaster> gitHubMaster,
      Optional<GitLabMaster> gitLabMaster,
      Optional<BitBucketMaster> bitBucketMaster,
      ObjectMapper jsonMapper) {
    this.stashMaster = stashMaster;
    this.gitHubMaster = gitHubMaster;
    this.gitLabMaster = gitLabMaster;
    this.bitBucketMaster = bitBucketMaster;
    this.jsonMapper = jsonMapper;
    this.yamlMapper =
        new ObjectMapper(new YAMLFactory())
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
  }

  @GetMapping("/")
  public List<String> listManifests(
      @RequestParam String scmType,
      @RequestParam final String projectKey,
      @RequestParam final String repositorySlug,
      @RequestParam(required = false, defaultValue = "refs/heads/master") String at) {
    final String path = KEEL_MANIFESTS_BASE_PATH;
    log.debug("Listing keel manifests at " + projectKey + ":" + repositorySlug + "/" + path);
    return getScmMaster(scmType).listDirectory(projectKey, repositorySlug, path, at);
  }

  @GetMapping(path = "/{manifest}")
  public ResponseEntity<Map<String, Object>> getManifest(
      @RequestParam String scmType,
      @RequestParam final String projectKey,
      @RequestParam final String repositorySlug,
      @PathVariable final String manifest,
      @RequestParam(required = false, defaultValue = "refs/heads/master") String at) {
    // TODO: make base path configurable
    final String path = KEEL_MANIFESTS_BASE_PATH + "/" + manifest;
    log.debug("Retrieving keel manifest from " + projectKey + ":" + repositorySlug + "/" + path);
    String manifestContents =
        getScmMaster(scmType).getTextFileContents(projectKey, repositorySlug, path, at);
    try {
      Map<String, Object> parsedManifest = null;
      if (manifest.endsWith(".yml") || manifest.endsWith(".yaml")) {
        parsedManifest = yamlMapper.readValue(manifestContents, Map.class);
      } else if (manifest.endsWith(".json")) {
        parsedManifest = jsonMapper.readValue(manifestContents, Map.class);
      } else {
        throw new IllegalArgumentException(
            String.format("Unrecognized file format for {}. Please use YAML or JSON.", manifest));
      }
      return new ResponseEntity<>(parsedManifest, HttpStatus.OK);
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
