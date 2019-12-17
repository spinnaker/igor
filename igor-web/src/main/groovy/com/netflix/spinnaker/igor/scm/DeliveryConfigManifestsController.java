package com.netflix.spinnaker.igor.scm;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.netflix.spinnaker.igor.config.DeliveryConfigProperties;
import com.netflix.spinnaker.igor.scm.bitbucket.client.BitBucketMaster;
import com.netflix.spinnaker.igor.scm.github.client.GitHubMaster;
import com.netflix.spinnaker.igor.scm.gitlab.client.GitLabMaster;
import com.netflix.spinnaker.igor.scm.stash.client.StashMaster;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Exposes APIs to retrieve Managed Delivery declarative manifests from source control repos. */
@RestController
@EnableConfigurationProperties(DeliveryConfigProperties.class)
@RequestMapping("/delivery-config")
public class DeliveryConfigManifestsController {
  private static final Logger log = LoggerFactory.getLogger(AbstractCommitController.class);

  private final DeliveryConfigProperties configProperties;
  private final ObjectMapper jsonMapper;
  private final ObjectMapper yamlMapper;
  private final Optional<StashMaster> stashMaster;
  private final Optional<GitHubMaster> gitHubMaster;
  private final Optional<GitLabMaster> gitLabMaster;
  private final Optional<BitBucketMaster> bitBucketMaster;

  public DeliveryConfigManifestsController(
      Optional<DeliveryConfigProperties> configProperties,
      Optional<StashMaster> stashMaster,
      Optional<GitHubMaster> gitHubMaster,
      Optional<GitLabMaster> gitLabMaster,
      Optional<BitBucketMaster> bitBucketMaster,
      ObjectMapper jsonMapper) {
    this.configProperties =
        configProperties.isPresent() ? configProperties.get() : new DeliveryConfigProperties();
    this.stashMaster = stashMaster;
    this.gitHubMaster = gitHubMaster;
    this.gitLabMaster = gitLabMaster;
    this.bitBucketMaster = bitBucketMaster;
    this.jsonMapper = jsonMapper;
    this.yamlMapper =
        new ObjectMapper(new YAMLFactory())
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
  }

  /**
   * Given details about a supported git source control repository, and optional filters for a
   * sub-directory within the repo, the file extensions to look for, and the specific git reference
   * to use, returns a list of (potential) Managed Delivery config manifests found at that location.
   *
   * <p>Note that this method does not recurse the specified sub-directory when listing files.
   */
  @GetMapping("/manifests")
  public List<String> listManifests(
      @RequestParam String scmType,
      @RequestParam final String project,
      @RequestParam final String repository,
      @RequestParam(required = false, defaultValue = ".") final String directory,
      @RequestParam(required = false, defaultValue = "yml") String extension,
      @RequestParam(required = false, defaultValue = ScmMaster.DEFAULT_GIT_REF) final String ref) {

    // TODO: this started as a method to retrieve a list of keel resource manifests, but morphed
    //  into supposedly returning only "delivery config" manifests, even though there's not really
    //  a good way to find out if a file is a delivery config or not, even if we were to parse each
    //  file (it doesn't have a Kubernetes-like apiVersion  and kind like other resources.

    final String path = configProperties.getManifestBasePath() + "/" + directory;
    log.debug("Listing keel manifests at " + project + ":" + repository + "/" + path);
    return getScmMaster(scmType).listDirectory(project, repository, path, ref).stream()
        .filter(it -> it.endsWith("." + extension))
        .collect(Collectors.toList());
  }

  /**
   * Given details about a supported git source control repository, the filename of a Managed
   * Delivery config manifest, and optional filters for a sub-directory within the repo and the
   * specific git reference to use, returns the contents of the manifest.
   *
   * <p>This API supports both YAML and JSON for the format of the manifest in source control, but
   * always returns the contents as JSON.
   */
  @GetMapping(path = "/manifest")
  public ResponseEntity<Map<String, Object>> getManifest(
      @RequestParam String scmType,
      @RequestParam final String project,
      @RequestParam final String repository,
      @RequestParam final String manifest,
      @RequestParam(required = false, defaultValue = ".") final String directory,
      @RequestParam(required = false, defaultValue = ScmMaster.DEFAULT_GIT_REF) final String ref) {
    final String path =
        (configProperties.getManifestBasePath() + "/" + directory + "/" + manifest)
            .replace("/./", "/");
    log.debug("Retrieving keel manifest from " + project + ":" + repository + "/" + path);
    String manifestContents =
        getScmMaster(scmType).getTextFileContents(project, repository, path, ref);
    try {
      Map<String, Object> parsedManifest = null;
      if (manifest.endsWith(".yml") || manifest.endsWith(".yaml")) {
        parsedManifest = yamlMapper.readValue(manifestContents, Map.class);
      } else if (manifest.endsWith(".json")) {
        parsedManifest = jsonMapper.readValue(manifestContents, Map.class);
      } else {
        throw new IllegalArgumentException(
            String.format("Unrecognized file format for %s. Please use YAML or JSON.", manifest));
      }
      return new ResponseEntity<>(parsedManifest, HttpStatus.OK);
    } catch (IllegalArgumentException e) {
      log.error(e.getMessage());
      return new ResponseEntity<>(Collections.emptyMap(), HttpStatus.BAD_REQUEST);
    } catch (Exception e) {
      log.error(
          "Error reading or parsing contents of delivery config manifest {}: {}",
          manifest,
          e.getMessage(),
          e);
      return new ResponseEntity<>(Collections.emptyMap(), HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  private ResponseEntity<Map<String, Object>> buildErrorResponse(
      HttpStatus status, String errorMessage) {
    Map<String, Object> error = Collections.singletonMap("error", errorMessage);
    return new ResponseEntity<>(error, status);
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
