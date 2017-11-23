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

import com.netflix.spinnaker.igor.build.model.Result
import com.netflix.spinnaker.igor.gitlabci.client.model.PipelineStatus
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

@CompileStatic
@Slf4j
class GitlabCiResultConverter {
    static Result getResultFromGitlabCiState(PipelineStatus state) {
        switch (state) {
            case PipelineStatus.pending:
                return Result.NOT_BUILT
            case PipelineStatus.running:
                return Result.BUILDING
            case PipelineStatus.success:
                return Result.SUCCESS
            case PipelineStatus.canceled:
                return Result.ABORTED
            case PipelineStatus.failed:
                return Result.FAILURE
            case PipelineStatus.skipped:
                return Result.NOT_BUILT
            default:
                log.info("could not convert ${state}")
                throw new IllegalArgumentException("state: ${state} is not known")
        }
    }

    static Boolean running(PipelineStatus status) {
        switch (status) {
            case PipelineStatus.pending:
                return true
            case PipelineStatus.running:
                return true
            default:
                return false
        }
    }
}
