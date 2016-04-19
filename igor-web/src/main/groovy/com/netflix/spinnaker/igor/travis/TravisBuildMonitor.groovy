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

import com.netflix.appinfo.InstanceInfo
import com.netflix.discovery.DiscoveryClient
import com.netflix.spinnaker.igor.build.BuildCache
import com.netflix.spinnaker.igor.build.model.GenericBuild
import com.netflix.spinnaker.igor.build.model.GenericProject
import com.netflix.spinnaker.igor.history.EchoService
import com.netflix.spinnaker.igor.history.model.GenericBuildContent
import com.netflix.spinnaker.igor.history.model.GenericBuildEvent
import com.netflix.spinnaker.igor.model.BuildServiceProvider
import com.netflix.spinnaker.igor.polling.PollingMonitor
import com.netflix.spinnaker.igor.service.BuildMasters
import com.netflix.spinnaker.igor.travis.client.model.Build
import com.netflix.spinnaker.igor.travis.client.model.Repo
import com.netflix.spinnaker.igor.travis.service.TravisBuildConverter
import com.netflix.spinnaker.igor.travis.service.TravisResultConverter
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.event.ContextRefreshedEvent
import org.springframework.stereotype.Service
import rx.Observable
import rx.Scheduler
import rx.functions.Action0
import rx.schedulers.Schedulers

import java.util.concurrent.TimeUnit


/**
 * Monitors new travis builds
 */
@Slf4j
@Service
@SuppressWarnings('CatchException')
@ConditionalOnProperty('travis.enabled')
class TravisBuildMonitor implements PollingMonitor{

    Scheduler.Worker worker = Schedulers.io().createWorker()

    Scheduler.Worker repositorySyncWorker = Schedulers.io().createWorker()

    @Autowired
    BuildCache buildCache

    @Autowired(required = false)
    EchoService echoService

    @Autowired(required = false)
    DiscoveryClient discoveryClient

    @Autowired
    BuildMasters buildMasters

    Long lastPoll

    Long lastRepositorySync

    static final String BUILD_IN_PROGRESS = 'started'

    @SuppressWarnings('GStringExpressionWithinString')
    @Value('${spinnaker.build.pollInterval:60}')
    int pollInterval

    @SuppressWarnings('GStringExpressionWithinString')
    @Value('${spinnaker.build.travis.repositorySyncInterval:300}')
    int repositorySyncInterval

    @Override
    void onApplicationEvent(ContextRefreshedEvent event) {
        log.info('Started')

        worker.schedulePeriodically(
            {
                if (isInService()) {
                    buildMasters.filteredMap(BuildServiceProvider.TRAVIS).keySet().each { master ->
                        changedBuilds(master)
                    }
                } else {
                    log.info("not in service (lastPoll: ${lastPoll ?: 'n/a'})")
                    lastPoll = null
                }
            } as Action0, 0, pollInterval, TimeUnit.SECONDS
        )

        worker.schedulePeriodically(
            {
                if (isInService()) {
                    buildMasters.filteredMap(BuildServiceProvider.TRAVIS).keySet().each { master ->
                        repositorySync(master)
                    }
                } else {
                    log.info("not in service (lastRepositorySync: ${lastRepositorySync ?: 'n/a'})")
                    lastRepositorySync = null
                }
            } as Action0, 0, repositorySyncInterval, TimeUnit.SECONDS
        )
    }

    @Override
    String getName() {
        return "travisBuildMonitor"
    }

    @Override
    boolean isInService() {
        if (discoveryClient == null) {
            log.info("no DiscoveryClient, assuming InService")
            true
        } else {
            def remoteStatus = discoveryClient.instanceRemoteStatus
            log.info("current remote status ${remoteStatus}")
            remoteStatus == InstanceInfo.InstanceStatus.UP
        }
    }

    @Override
    Long getLastPoll() {
        return lastPoll
    }

    @Override
    int getPollInterval() {
        return pollInterval
    }

    def repositorySync(String master) {
        log.info('repositorySync: Syncing repositories for ' + master)
        lastRepositorySync = System.currentTimeMillis()
        List<String> cachedRepoSlugs = buildCache.getJobNames(master)
        buildMasters.map[master].setAccessToken()
        def startTime = System.currentTimeMillis()
        buildMasters.map[master].syncRepos()
        log.info("repositorySync: Took ${System.currentTimeMillis() - startTime}ms to sync repositories for ${master}")
        startTime = System.currentTimeMillis()
        Observable.from(cachedRepoSlugs).subscribe(
            { String repoSlug ->
                if(!buildMasters.map[master].hasRepo(repoSlug)) {
                    log.info "repositorySync: Removing ${master}:${repoSlug} from buildCache because it is not on ${master} anymore."
                        buildCache.remove(master, repoSlug)
                }
            }, {
            log.error("repositorySync: Error: ${it.message}")
        }, {} as Action0
        )
        log.info("repositorySync: Took ${System.currentTimeMillis() - startTime}ms validate build cache for ${master}")
        log.info("repositorySync: Last repositorySync took ${System.currentTimeMillis() - lastRepositorySync}ms (master: ${master})")

    }

    List<Map> changedBuilds(String master) {
        log.info('Checking for new builds for ' + master)
        List<String> cachedRepoSlugs = buildCache.getJobNames(master)
        List<Map> results = []
        buildMasters.map[master].setAccessToken()
        lastPoll = System.currentTimeMillis()
        buildMasters.map[master].setAccounts()
        def startTime = System.currentTimeMillis()
        List<Repo> repos = buildMasters.map[master].getReposForAccounts()
        log.info("Took ${System.currentTimeMillis() - startTime}ms to retrieve ${repos.size()} repositories (master: ${master})")

        Observable.from(repos).subscribe(
            { Repo repo ->
                boolean addToCache = false
                Map cachedBuild = null

                if (cachedRepoSlugs.contains(repo.slug)) {
                    cachedBuild = buildCache.getLastBuild(master, repo.slug)
                    if (((repo.lastBuildState == BUILD_IN_PROGRESS) != cachedBuild.lastBuildBuilding) ||
                        (repo.lastBuildNumber != Integer.valueOf(cachedBuild.lastBuildLabel))) {
                        addToCache = true
                        log.info "Build changed: ${master}: ${repo.slug} : ${repo.lastBuildNumber} : ${repo.lastBuildState}"
                        if (echoService) {
                            int currentBuild = repo.lastBuildNumber
                            int lastBuild = Integer.valueOf(cachedBuild.lastBuildLabel)

                            log.info "sending build events for builds between ${lastBuild} and ${currentBuild}"
                            for (int buildNumber = lastBuild+1; buildNumber < currentBuild; buildNumber++) {
                                Build build = buildMasters.map[master].getBuild(repo, buildNumber) //rewrite to afterNumber list thing
                                log.info "pushing event for ${master}:${repo.slug}:${build.number}"
                                String url = "${buildMasters.map[master].baseUrl}/${repo.slug}/builds/${build.id}"
                                GenericProject project = new GenericProject(repo.slug, new GenericBuild((build.state == BUILD_IN_PROGRESS), build.number, build.duration, TravisResultConverter.getResultFromTravisState(build.state), repo.slug, url))

                                echoService.postEvent(
                                    new GenericBuildEvent(content: new GenericBuildContent(project: project, master: master, type: 'travis')))
                            }
                        }
                    }
                } else {
                    addToCache = true
                }
                if (addToCache) {
                    log.info("Build update [${repo.slug}:${repo.lastBuildNumber}] [status:${repo.lastBuildState}] [running:${repo.lastBuildState == BUILD_IN_PROGRESS}]")
                    buildCache.setLastBuild(master, repo.slug, repo.lastBuildNumber, repo.lastBuildState == BUILD_IN_PROGRESS)
                    if (echoService) {
                        log.info "pushing event for ${master}:${repo.slug}:${repo.lastBuildNumber}"

                        GenericProject project = new GenericProject(repo.slug, TravisBuildConverter.genericBuild(repo, buildMasters.map[master].baseUrl))
                        echoService.postEvent(
                            new GenericBuildEvent(content: new GenericBuildContent(project: project, master: master, type: 'travis'))
                        )

                    }
                    results << [previous: cachedBuild, current: repo]
                }
            }, {
            log.error("Error: ${it.message} (${master})")
        }, {
        } as Action0
        )

        log.info("Last poll took ${System.currentTimeMillis() - lastPoll}ms (master: ${master})")
        results

    }
}
