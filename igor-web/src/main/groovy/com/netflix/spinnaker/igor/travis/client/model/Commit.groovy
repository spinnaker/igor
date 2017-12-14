/*
 * Copyright 2016 Schibsted ASA.
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

package com.netflix.spinnaker.igor.travis.client.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.netflix.spinnaker.igor.build.model.GenericGitRevision
import groovy.transform.CompileStatic
import org.simpleframework.xml.Default
import org.simpleframework.xml.Root

import java.time.Instant

@Default
@CompileStatic
@Root(name = 'commits')
@JsonIgnoreProperties(ignoreUnknown = true)
class Commit {
    int id
    String sha
    String branch
    String message

    @JsonProperty("author_name")
    String authorName

    @JsonProperty("compare_url")
    String compareUrl

    @JsonProperty("committed_at")
    Instant timestamp

    GenericGitRevision genericGitRevision() {
        return new GenericGitRevision(branch, branch, sha, authorName, compareUrl, message, timestamp)
    }

    boolean isTag(){
        if (!compareUrl) {
            return false
        }
        return compareUrl.split("/compare/").last().matches(branch)
    }

    String branchNameWithTagHandling() {
        if (isTag()) {
            return "tags"
        }
        return branch
    }
}
