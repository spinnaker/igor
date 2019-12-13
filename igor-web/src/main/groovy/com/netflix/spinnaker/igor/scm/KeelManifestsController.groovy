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

package com.netflix.spinnaker.igor.scm

import com.netflix.spinnaker.igor.scm.bitbucket.client.BitBucketMaster
import com.netflix.spinnaker.igor.scm.github.client.GitHubMaster
import com.netflix.spinnaker.igor.scm.gitlab.client.GitLabMaster
import com.netflix.spinnaker.igor.scm.stash.client.StashMaster
import com.netflix.spinnaker.igor.scm.stash.client.model.DirectoryListingResponse
import com.netflix.spinnaker.igor.scm.stash.client.model.TextLinesResponse
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import retrofit.RetrofitError

/**
 * Exposes APIs to retrieve keel declarative manifests from source control repos.
 */
@RestController
@Slf4j
@RequestMapping("/keel")
class KeelManifestsController {
  @Autowired(required = false)
  StashMaster stashMaster

  @Autowired(required = false)
  GitHubMaster gitHubMaster

  @Autowired(required = false)
  GitLabMaster gitLabMaster

  @Autowired(required = false)
  BitBucketMaster bitBucketMaster

  static final String KEEL_MANIFESTS_BASE_PATH = ".netflix/spinnaker"

  // Note: we expose a restricted endpoint here (as opposed to a full pass-thru to the SCM APIs) to limit the exposure
  // of accessing repo contents with igor's credentials

  @GetMapping(path = '/{scmType}/{projectKey}/{repositorySlug}/manifests')
  List<String> listKeelManifests(@PathVariable String scmType,
                                 @PathVariable String projectKey,
                                 @PathVariable String repositorySlug,
                                 @RequestParam(required = false, defaultValue = 'refs/heads/master') String at) {
    String path = KEEL_MANIFESTS_BASE_PATH
    log.info("Listing keel manifests at ${projectKey}:${repositorySlug}/${path}")
    return getScmMaster(scmType).listDirectory(projectKey, repositorySlug, path, at)
  }

  @GetMapping(path = '/{scmType}/{projectKey}/{repositorySlug}/manifests/{manifest}')
  String getKeelManifest(@PathVariable String scmType,
                         @PathVariable String projectKey,
                         @PathVariable String repositorySlug,
                         @PathVariable String manifest,
                         @RequestParam(required = false, defaultValue = 'refs/heads/master') String at) {
    String path = "${KEEL_MANIFESTS_BASE_PATH}/${manifest}"
    log.info("Retrieving keel manifest from ${projectKey}:${repositorySlug}/${path}")
    return getScmMaster(scmType).getTextFileContents(projectKey, repositorySlug, path, at)
  }

  private ScmMaster getScmMaster(String scmType) {
    switch (scmType) {
      case "bitbucket": return bitBucketMaster
      case "github": return gitHubMaster
      case "gitlab": return gitLabMaster
      case "stash" : return stashMaster
      default: throw new IllegalArgumentException("Unknown SCM type ${scmType}")
    }
  }
}
