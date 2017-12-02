/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.igor.jenkins

import com.netflix.appinfo.InstanceInfo
import com.netflix.discovery.DiscoveryClient
import com.netflix.spinnaker.igor.IgorConfigurationProperties
import com.netflix.spinnaker.igor.history.EchoService
import com.netflix.spinnaker.igor.history.model.BuildContent
import com.netflix.spinnaker.igor.history.model.BuildEvent
import com.netflix.spinnaker.igor.jenkins.client.model.Build
import com.netflix.spinnaker.igor.jenkins.client.model.Project
import com.netflix.spinnaker.igor.jenkins.service.JenkinsService
import com.netflix.spinnaker.igor.model.BuildServiceProvider
import com.netflix.spinnaker.igor.polling.PollingMonitor
import com.netflix.spinnaker.igor.service.BuildMasters
import groovy.time.TimeCategory
import groovy.util.logging.Slf4j
import org.apache.commons.lang3.time.DateUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.event.ContextRefreshedEvent
import org.springframework.core.env.Environment
import org.springframework.stereotype.Service
import rx.Scheduler
import rx.Scheduler.Worker
import rx.functions.Action0
import rx.schedulers.Schedulers

import javax.annotation.PreDestroy
import java.util.concurrent.TimeUnit

import static net.logstash.logback.argument.StructuredArguments.kv

/**
 * Monitors new jenkins builds
 */
@Slf4j
@Service
@SuppressWarnings('CatchException')
@ConditionalOnProperty('jenkins.enabled')
class JenkinsBuildMonitor implements PollingMonitor {

    @Autowired
    Environment environment

    @Value('${jenkins.polling.enabled:true}')
    boolean pollingEnabled = true

    Scheduler scheduler = Schedulers.io()
    Worker worker = scheduler.createWorker()

    @Autowired
    JenkinsCache cache

    @Autowired(required = false)
    EchoService echoService

    @Autowired
    BuildMasters buildMasters

    Long lastPoll

    @Override
    Long getLastPoll() {
        lastPoll
    }

    @Autowired
    IgorConfigurationProperties igorConfigurationProperties

    @Override
    int getPollInterval() {
        igorConfigurationProperties.spinnaker.build.pollInterval
    }

    @Autowired(required = false)
    DiscoveryClient discoveryClient

    @Override
    String getName() {
        "jenkinsBuildMonitor"
    }

    String lastStatus

    @Override
    boolean isInService() {
        if (discoveryClient == null) {
            log.info("no DiscoveryClient, assuming InService")
            return pollingEnabled
        } else {
            def remoteStatus = discoveryClient.instanceRemoteStatus
            if (remoteStatus != lastStatus) {
                log.info("current remote status ${remoteStatus}")
            }
            lastStatus=remoteStatus
            remoteStatus == InstanceInfo.InstanceStatus.UP && pollingEnabled
        }
    }

    @Override
    void onApplicationEvent(ContextRefreshedEvent event) {
        log.info('Started')
        worker.schedulePeriodically(
                {
                    if (isInService()) {
                        log.info "- Polling cycle started - ${new Date()}"
                        buildMasters.filteredMap(BuildServiceProvider.JENKINS).keySet().parallelStream().forEach(
                                { master -> changedBuilds(master) }
                        )
                        log.info "- Polling cycle done - ${new Date()}"
                    } else {
                        log.info("not in service (lastPoll: ${lastPoll ?: 'n/a'})")
                        lastPoll = null
                    }
                } as Action0, 0, pollInterval, TimeUnit.SECONDS
        )
    }

    @PreDestroy
    void stop() {
        log.info('Stopped')
        if (!worker.isUnsubscribed()) {
            worker.unsubscribe()
        }
    }

    /**
     * Gets a list of jobs for this master & processes builds between last poll stamp and a sliding upper bound stamp
     * Advances the cursor to the upper bound when all builds are completed
     * Post an event per completed build
     * @param master: a jenkins master
     */

    void changedBuilds(String master) {
        log.debug("Checking for new builds for ${master}")
        def startTime = System.currentTimeMillis()
        lastPoll = startTime

        try {
            JenkinsService jenkinsService = buildMasters.map[master] as JenkinsService
            List<Project> jobs = jenkinsService.getProjects()?.getList() ?:[]
            for (Project job : jobs) {
                if (!job.lastBuild) {
                    log.debug("[{}:{}] has no builds skipping...", kv("master", master), kv("job", job.name))
                    continue
                }

                Long cursor = cache.getLastPollCycleTimestamp(master, job.name)
                Long lastBuildStamp = job.lastBuild.timestamp as Long
                Date upperBound = new Date(lastBuildStamp)
                if (cursor == lastBuildStamp) {
                    log.debug("[${master}:${job.name}] is up to date. skipping")
                } else {
                    if (!cursor && !igorConfigurationProperties.spinnaker.build.handleFirstBuilds) {
                        cache.setLastPollCycleTimestamp(master, job.name, lastBuildStamp)
                        continue
                    }
                    // 1. get builds
                    List<Build> allBuilds = (jenkinsService.getBuilds(job.name).getList() ?: [])
                    if (!cursor) {
                        log.debug("[${master}:${job.name}] setting new cursor to ${lastBuildStamp}")
                        cursor = lastBuildStamp
                    } else {
                        // filter between last poll and jenkins last build included
                        allBuilds = (jenkinsService.getBuilds(job.name).getList() ?: []).findAll { build ->
                            Long buildStamp = build.timestamp as Long
                            return buildStamp <= lastBuildStamp && buildStamp > cursor
                        }
                    }

                    List<Build> currentlyBuilding = allBuilds.findAll { it.building }
                    List<Build> completedBuilds = allBuilds.findAll { !it.building }
                    Date lowerBound = new Date(cursor)

                    if (!igorConfigurationProperties.spinnaker.build.processBuildsOlderThanLookBackWindow) {
                        use (TimeCategory) {
                            def offsetSeconds = pollInterval.seconds
                            def lookBackWindowMins = igorConfigurationProperties.spinnaker.build.lookBackWindowMins.minutes
                            Date lookBackDate = (offsetSeconds + lookBackWindowMins).ago

                            def tooOldBuilds = completedBuilds.findAll {
                                Date buildEndDate = new Date(it.timestamp as Long)
                                DateUtils.addMilliseconds(buildEndDate,Math.toIntExact(it.duration))
                                return buildEndDate.before(lookBackDate)
                            }
                            log.debug("Filtering out builds older than {} from {} {}: build numbers{}",
                                lookBackDate,
                                kv("master", master),
                                kv("job", job.name),
                                tooOldBuilds.collect { it.number }
                            )
                            completedBuilds.removeAll(tooOldBuilds)
                        }
                    }

                    // 2. post events for finished builds
                    completedBuilds.forEach { build ->
                        Boolean eventPosted = cache.getEventPosted(master, job.name, cursor, build.number)
                        if (!eventPosted) {
                            log.debug("[${master}:${job.name}]:${build.number} event posted")
                            postEvent(echoService, new Project(name: job.name, lastBuild: build), master)
                            cache.setEventPosted(master, job.name, cursor, build.number)
                        }
                    }

                    // 3. advance cursor when all builds have completed in the interval
                    if (currentlyBuilding.isEmpty()) {
                        log.info("[{}:{}] has no other builds between [${lowerBound} - ${upperBound}], advancing cursor to ${lastBuildStamp}", kv("master", master), kv("job", job.name))
                        cache.pruneOldMarkers(master, job.name, cursor)
                        cache.setLastPollCycleTimestamp(master, job.name, lastBuildStamp)
                    }
                }
            }
        } catch (e) {
            log.error("Error processing builds for {}", kv("master", master), e)
        }

        log.debug("Took ${System.currentTimeMillis() - startTime}ms to retrieve projects (master: {})", kv("master", master))
    }

    static void postEvent(EchoService echoService,  Project project, String master) {
        if (echoService) {
            echoService.postEvent(
                new BuildEvent(content: new BuildContent(project: project, master: master))
            )
        }
    }
}
