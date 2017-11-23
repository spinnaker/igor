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

package com.netflix.spinnaker.igor.gitlabci.service

import com.netflix.spinnaker.igor.build.model.GenericBuild
import com.netflix.spinnaker.igor.gitlabci.client.model.Pipeline
import groovy.transform.CompileStatic

@CompileStatic
class GitlabCiPipelineConverter {
    static GenericBuild genericBuild(Pipeline pipeline, String repoSlug, String baseUrl) {
        GenericBuild genericBuild = new GenericBuild(building: GitlabCiResultConverter.running(pipeline.status),
            number: pipeline.id,
            duration: pipeline.duration,
            result: GitlabCiResultConverter.getResultFromGitlabCiState(pipeline.status),
            name: repoSlug,
            url: url(repoSlug, baseUrl, pipeline.id))

        if (pipeline.finished_at) {
            genericBuild.timestamp = pipeline.finished_at.getTime()
        }
        return genericBuild
    }

    static String url(String repoSlug, String baseUrl, long id) {
        return "${baseUrl}/${repoSlug}/pipelines/${id}"
    }
}
