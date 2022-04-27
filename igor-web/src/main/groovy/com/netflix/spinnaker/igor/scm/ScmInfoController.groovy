/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.igor.scm

import com.netflix.spinnaker.igor.scm.github.client.GitHubController
import com.netflix.spinnaker.igor.scm.gitlab.client.GitLabController
import com.netflix.spinnaker.igor.scm.stash.client.StashController
import com.netflix.spinnaker.igor.scm.bitbucket.client.BitBucketController
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController

/**
 * A controller that provides Source Control information
 */
@RestController
@Slf4j
@RequestMapping("/scm")
class ScmInfoController {
    @Autowired(required = false)
    StashController stashController

    @Autowired(required = false)
    GitHubController gitHubController

    @Autowired(required = false)
    GitLabController gitLabController

    @Autowired(required = false)
    BitBucketController bitBucketController

    @Deprecated(forRemoval = true)
    @RequestMapping(value = '/masters', method = RequestMethod.GET)
    Map listMasters() {
        listControllers()
    }

  @RequestMapping(value = '/controllers', method = RequestMethod.GET)
  Map listControllers() {
    def result = [:]
    if(stashController)
      result << [stash : stashController.baseUrl]

    if(gitHubController)
      result << [gitHub : gitHubController.baseUrl]

    if(gitLabController)
      result << [gitLab : gitLabController.baseUrl]

    if(bitBucketController)
      result << [bitBucket : bitBucketController.baseUrl]
    result
  }
}
