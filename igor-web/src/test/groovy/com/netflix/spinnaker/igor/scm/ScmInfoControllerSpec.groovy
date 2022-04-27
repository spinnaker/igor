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

import com.netflix.spinnaker.igor.scm.github.client.GitHubClient
import com.netflix.spinnaker.igor.scm.github.client.GitHubController
import com.netflix.spinnaker.igor.scm.stash.client.StashClient
import com.netflix.spinnaker.igor.scm.stash.client.StashController
import com.netflix.spinnaker.igor.scm.bitbucket.client.BitBucketClient
import com.netflix.spinnaker.igor.scm.bitbucket.client.BitBucketController
import spock.lang.Specification
import spock.lang.Subject

/**
 * Tests for InfoController
 */
class ScmInfoControllerSpec extends Specification {

    @Subject
    ScmInfoController controller

    StashClient stashClient = Mock(StashClient)
    GitHubClient gitHubClient = Mock(GitHubClient)
    BitBucketClient bitBucketClient = Mock(BitBucketClient)

    void setup() {
        controller = new ScmInfoController(gitHubController: new GitHubController(gitHubClient: gitHubClient, baseUrl: "https://github.com"),
          stashController: new StashController(stashClient: stashClient, baseUrl: "http://stash.com"),
          bitBucketController: new BitBucketController(bitBucketClient: bitBucketClient, baseUrl: "https://api.bitbucket.org"))
    }

    @Deprecated(forRemoval = true)
    void 'list masters'() {
        when:
        Map listControllersResponse = controller.listMasters()

        then:
        listControllersResponse.stash == "http://stash.com"
        listControllersResponse.gitHub == "https://github.com"
        listControllersResponse.bitBucket == "https://api.bitbucket.org"
    }

    void 'list controllers'() {
        when:
        Map listControllersResponse = controller.listControllers()

        then:
        listControllersResponse.stash == "http://stash.com"
        listControllersResponse.gitHub == "https://github.com"
        listControllersResponse.bitBucket == "https://api.bitbucket.org"
    }
}
