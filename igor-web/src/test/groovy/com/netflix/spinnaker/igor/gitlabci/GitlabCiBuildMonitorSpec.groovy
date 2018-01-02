/*
 * Copyright 2017 Netflix, Inc.
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
package com.netflix.spinnaker.igor.gitlabci

import com.netflix.spinnaker.igor.build.BuildCache
import com.netflix.spinnaker.igor.config.GitlabCiProperties
import com.netflix.spinnaker.igor.gitlabci.client.model.Pipeline
import com.netflix.spinnaker.igor.gitlabci.client.model.PipelineStatus
import com.netflix.spinnaker.igor.gitlabci.client.model.Project
import com.netflix.spinnaker.igor.gitlabci.service.GitlabCiService
import com.netflix.spinnaker.igor.history.EchoService
import com.netflix.spinnaker.igor.service.BuildMasters
import spock.lang.Specification
import spock.lang.Unroll

class GitlabCiBuildMonitorSpec extends Specification {
    BuildCache buildCache = Mock(BuildCache)
    GitlabCiService service = Mock(GitlabCiService)
    EchoService echoService = Mock(EchoService)
    GitlabCiBuildMonitor buildMonitor

    final String MASTER = "MASTER"
    final int CACHED_JOB_TTL_SECONDS = 172800
    final int CACHED_JOB_TTL_DAYS = 2

    void setup() {
        def properties = new GitlabCiProperties(cachedJobTTLDays: CACHED_JOB_TTL_DAYS)
        buildMonitor = new GitlabCiBuildMonitor(buildCache: buildCache, buildMasters: new BuildMasters(map: [MASTER: service]), gitlabCiProperties: properties, echoService: echoService)
    }

    @Unroll
    def "send 2 events for a new build and store them in cache"() {
        given:
        Project project = new Project(pathWithNamespace: 'user1/project1')
        Pipeline pipeline = new Pipeline(id: 101, tag: false, ref: 'master', finishedAt: new Date(), status: PipelineStatus.success)

        service.getProjects() >> [project]
        service.getPipelines(project, _) >> [pipeline]
        buildCache.getJobNames(MASTER) >> jobsInCache
        buildCache.getLastBuild(MASTER, _, false) >> lastBuildNr

        when:
        buildMonitor.changedBuilds(MASTER)

        then:
        1 * buildCache.setLastBuild(MASTER, "user1/project1", 101, false, CACHED_JOB_TTL_SECONDS)
        1 * buildCache.setLastBuild(MASTER, "user1/project1/master", 101, false, CACHED_JOB_TTL_SECONDS)

        and:
        1 * buildMonitor.echoService.postEvent({
            it.content.project.name == "user1/project1"
            it.content.project.lastBuild.number == 101
        })

        1 * buildMonitor.echoService.postEvent({
            it.content.project.name == "user1/project1/master"
            it.content.project.lastBuild.number == 101
        })

        where:
        jobsInCache                 | lastBuildNr
        []                          | 0
        ["user1/project1/master"]   | 100
    }

    def "ignore very old events"() {
        given:
        Project project = new Project(pathWithNamespace: 'user1/project1')
        Pipeline pipeline = new Pipeline(id: 101, tag: false, ref: 'master', finishedAt: new Date(0), status: PipelineStatus.success)

        service.getProjects() >> [project]
        service.getPipelines(project, _) >> [pipeline]
        buildCache.getJobNames(MASTER) >> []

        when:
        buildMonitor.changedBuilds(MASTER)

        then:
        0 * buildCache.setLastBuild(_, _, _, _, _)

        and:
        0 * buildMonitor.echoService.postEvent(_)
    }

    def "ignore previous builds"() {
        given:
        Project project = new Project(pathWithNamespace: 'user1/project1')
        Pipeline pipeline = new Pipeline(id: 101, tag: false, ref: 'master', finishedAt: new Date(), status: PipelineStatus.success)

        service.getProjects() >> [project]
        service.getPipelines(project, _) >> [pipeline]
        buildCache.getJobNames(MASTER) >> ["user1/project1/master"]
        buildCache.getLastBuild(MASTER, "user1/project1/master", false) >> 102

        when:
        buildMonitor.changedBuilds(MASTER)

        then:
        0 * buildCache.setLastBuild(_, _, _, _, _)

        and:
        0 * buildMonitor.echoService.postEvent(_)
    }
}
