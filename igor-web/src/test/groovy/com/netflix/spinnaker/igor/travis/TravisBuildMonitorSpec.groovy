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
package com.netflix.spinnaker.igor.travis

import com.netflix.spinnaker.igor.build.BuildCache
import com.netflix.spinnaker.igor.config.TravisProperties
import com.netflix.spinnaker.igor.history.EchoService
import com.netflix.spinnaker.igor.service.BuildMasters
import com.netflix.spinnaker.igor.travis.client.model.Repo
import com.netflix.spinnaker.igor.travis.client.model.v3.V3Build
import com.netflix.spinnaker.igor.travis.client.model.v3.V3Repository
import com.netflix.spinnaker.igor.travis.service.TravisService
import spock.lang.Specification

import java.util.concurrent.TimeUnit

class TravisBuildMonitorSpec extends Specification {
    BuildCache buildCache = Mock(BuildCache)
    TravisService travisService = Mock(TravisService)
    TravisBuildMonitor travisBuildMonitor

    final String MASTER = "MASTER"
    final int CACHED_JOB_TTL_SECONDS = 172800
    final int CACHED_JOB_TTL_DAYS = 2

    void setup() {
        def travisProperties = new TravisProperties(cachedJobTTLDays: CACHED_JOB_TTL_DAYS)
        travisBuildMonitor = new TravisBuildMonitor(buildCache: buildCache, buildMasters: new BuildMasters(map: [MASTER : travisService]), travisProperties: travisProperties)
    }

    void 'flag a new build not found in the cache'() {
        Repo repo = getRepo()
        List<Repo> repos = [repo]
        V3Build build = Mock(V3Build)
        V3Repository repository = Mock(V3Repository)

        given:
        buildCache.getJobNames(MASTER) >> ['test-org/test-repo/master']

        when:
        List<Map> receivedBuilds = travisBuildMonitor.changedBuilds(MASTER)

        then:
        build.branchedRepoSlug() >> "test-org/test-repo/master"
        build.getNumber() >> 4
        build.repository >> repository
        repository.slug >> 'test-org/test-repo'
        buildCache.getLastBuild(MASTER, 'test-org/test-repo/master') >> [lastBuildLabel: 3]
        buildCache.setLastBuild(MASTER, 'test-org/test-repo/master', 4, false, CACHED_JOB_TTL_SECONDS)
        buildCache.setLastBuild(MASTER, 'test-org/test-repo', 4, false, CACHED_JOB_TTL_SECONDS)
        travisService.getReposForAccounts() >> repos
        travisService.getBuilds(repo, 5) >> [ build ]

        receivedBuilds.size() == 1
        receivedBuilds[0].current.slug == 'test-org/test-repo'
        receivedBuilds[0].current.lastBuildNumber == 4
        receivedBuilds[0].previous.lastBuildLabel == 3
    }

    void 'ignore old build not found in the cache'() {
        Date now = new Date()
        Repo repo = getRepo()
        repo.lastBuildStartedAt = new Date(now.getTime() - TimeUnit.DAYS.toMillis(travisBuildMonitor.travisProperties.cachedJobTTLDays-1))
        Repo oldRepo = new Repo()
        oldRepo.lastBuildStartedAt = new Date(now.getTime() - TimeUnit.DAYS.toMillis(travisBuildMonitor.travisProperties.cachedJobTTLDays))
        Repo noLastBuildStartedAtRepo = new Repo()
        noLastBuildStartedAtRepo.lastBuildStartedAt = null
        List<Repo> repos = [oldRepo, repo, noLastBuildStartedAtRepo]
        V3Build build = Mock(V3Build)
        V3Repository repository = Mock(V3Repository)

        given:
        buildCache.getJobNames(MASTER) >> ['test-org/test-repo/master']

        when:
        List<Map> builds = travisBuildMonitor.changedBuilds(MASTER)

        then:
        build.branchedRepoSlug() >> "test-org/test-repo/master"
        build.getNumber() >> 4
        build.repository >> repository
        repository.slug >> 'test-org/test-repo'
        buildCache.getLastBuild(MASTER, 'test-org/test-repo/master') >> [lastBuildLabel: 3]
        travisService.getReposForAccounts() >> repos
        travisService.getBuilds(repo, 5) >> [ build ]

        expect:
        builds.size() == 1
        builds[0].current.slug == 'test-org/test-repo'
        builds[0].current.lastBuildNumber == 4
        builds[0].previous.lastBuildLabel == 3
    }

    void 'send events for build both on branch and on repository'() {
        travisBuildMonitor.echoService = Mock(EchoService)
        Repo repo = getRepo()
        List<Repo> repos = [repo]
        V3Build build = Mock(V3Build)
        V3Repository repository = Mock(V3Repository)

        given:
        buildCache.getJobNames(MASTER) >> ['test-org/test-repo/my_branch']

        when:
        travisBuildMonitor.changedBuilds(MASTER)

        then:
        build.branchedRepoSlug() >> "test-org/test-repo/my_branch"
        build.getNumber() >> 4
        build.repository >> repository
        build.getState() >> "passed"
        build.job_ids >> []
        repository.slug >> 'test-org/test-repo'
        travisService.getReposForAccounts() >> repos
        travisService.getBuilds(repo, 5) >> [ build ]
        travisService.getLog(build.job_ids) >> ""
        buildCache.getLastBuild(MASTER, 'test-org/test-repo/my_branch') >> [lastBuildLabel: 3]

        1 * travisBuildMonitor.echoService.postEvent({
            it.content.project.name == "test-org/test-repo"
            it.content.project.lastBuild.number == 4
        })
        1 * travisBuildMonitor.echoService.postEvent({
            it.content.project.name == "test-org/test-repo/my_branch"
            it.content.project.lastBuild.number == 4
        })

    }

    void 'send events when two different branches build at the same time.'() {
        travisBuildMonitor.echoService = Mock(EchoService)
        Repo repo = getRepo()
        List<Repo> repos = [repo]
        V3Build build = Mock(V3Build)
        V3Build buildDifferentBranch = Mock(V3Build)
        V3Repository repository = Mock(V3Repository)

        given:
        buildCache.getJobNames(MASTER) >> ['test-org/test-repo/my_branch', 'test-org/test-repo/different_branch']

        when:
        travisBuildMonitor.changedBuilds(MASTER)

        then:
        build.branchedRepoSlug() >> "test-org/test-repo/my_branch"
        build.repository >> repository
        build.job_ids >> []
        build.getNumber() >> 4
        build.getState() >> "passed"
        buildDifferentBranch.job_ids >> []
        buildDifferentBranch.getNumber() >> 3
        buildDifferentBranch.repository >> repository
        buildDifferentBranch.getState() >> "passed"
        buildDifferentBranch.branchedRepoSlug() >> "test-org/test-repo/different_branch"
        repository.slug >> 'test-org/test-repo'
        buildCache.getLastBuild(MASTER, 'test-org/test-repo/my_branch') >> [lastBuildLabel: 2]
        buildCache.setLastBuild(MASTER, 'test-org/test-repo/my_branch', 4, false, CACHED_JOB_TTL_SECONDS)
        buildCache.setLastBuild(MASTER, 'test-org/test-repo', 4, false, CACHED_JOB_TTL_SECONDS)
        buildCache.setLastBuild(MASTER, 'test-org/test-repo', 3, false, CACHED_JOB_TTL_SECONDS)
        buildCache.getLastBuild(MASTER, 'test-org/test-repo/different_branch') >> [lastBuildLabel: 1]
        buildCache.setLastBuild(MASTER, 'test-org/test-repo/different_branch', 3, false, CACHED_JOB_TTL_SECONDS)
        travisService.getReposForAccounts() >> repos
        travisService.getBuilds(repo, 5) >> [ build, buildDifferentBranch ]
        travisService.getLog(build.job_ids) >> ""
        travisService.getLog(buildDifferentBranch.job_ids) >> ""

        1 * travisBuildMonitor.echoService.postEvent({
            it.content.project.name == "test-org/test-repo/my_branch" &&
                it.content.project.lastBuild.number == 4
        })
        1 * travisBuildMonitor.echoService.postEvent({
            it.content.project.name == "test-org/test-repo" &&
                it.content.project.lastBuild.number == 4
        })

        1 * travisBuildMonitor.echoService.postEvent({
            it.content.project.name == "test-org/test-repo/different_branch" &&
                it.content.project.lastBuild.number == 3
        })
    }

    void 'send events with artifacts in the event'() {
        travisBuildMonitor.echoService = Mock(EchoService)
        Repo repo = getRepo()
        List<Repo> repos = [repo]
        V3Build build = Mock(V3Build)
        V3Repository repository = Mock(V3Repository)

        given:
        buildCache.getJobNames(MASTER) >> ['test-org/test-repo/my_branch']

        when:
        travisBuildMonitor.changedBuilds(MASTER)

        then:
        build.branchedRepoSlug() >> "test-org/test-repo/my_branch"
        build.getNumber() >> 4
        build.repository >> repository
        build.getState() >> "passed"
        build.job_ids >> [1337]
        repository.slug >> 'test-org/test-repo'
        buildCache.getLastBuild(MASTER, 'test-org/test-repo/my_branch') >> [lastBuildLabel: 3]
        buildCache.setLastBuild(MASTER, 'test-org/test-repo/my_branch', 4, false, CACHED_JOB_TTL_SECONDS)
        buildCache.setLastBuild(MASTER, 'test-org/test-repo', 4, false, CACHED_JOB_TTL_SECONDS)
        travisService.getReposForAccounts() >> repos
        travisService.getBuilds(repo, 5) >> [ build ]

        travisService.getLog(build.job_ids) >> "Successfully pushed package-name.0.0-20160531141100_amd64.deb to org/repo"
        2 * travisBuildMonitor.echoService.postEvent({
            it.content.project.lastBuild.artifacts.first().fileName == "package-name.0.0-20160531141100_amd64.deb"
        })
    }

    private Repo getRepo() {
        Repo repo = new Repo()
        repo.slug = "test-org/test-repo"
        repo.lastBuildNumber = 4
        repo.lastBuildState = "passed"
        repo.lastBuildStartedAt = new Date()
        repo
    }
}
