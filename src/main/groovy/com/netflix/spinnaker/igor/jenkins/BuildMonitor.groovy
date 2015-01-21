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

import com.netflix.spinnaker.igor.history.EchoService
import com.netflix.spinnaker.igor.history.model.BuildContent
import com.netflix.spinnaker.igor.history.model.BuildDetails
import com.netflix.spinnaker.igor.jenkins.client.JenkinsMasters
import com.netflix.spinnaker.igor.jenkins.client.model.Build
import com.netflix.spinnaker.igor.jenkins.client.model.BuildArtifact
import com.netflix.spinnaker.igor.jenkins.client.model.Project
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationListener
import org.springframework.context.event.ContextRefreshedEvent
import org.springframework.core.env.Environment
import org.springframework.stereotype.Service
import rx.Observable
import rx.Scheduler.Worker
import rx.functions.Action0
import rx.schedulers.Schedulers

import javax.annotation.PreDestroy
import java.util.concurrent.TimeUnit

/**
 * Monitors new jenkins builds
 */
@Slf4j
@Service
@SuppressWarnings('CatchException')
class BuildMonitor implements ApplicationListener<ContextRefreshedEvent> {

    @Autowired
    Environment environment

    Worker worker = Schedulers.io().createWorker()

    @Autowired
    JenkinsCache cache

    @Autowired(required = false)
    EchoService echoService

    @Autowired
    JenkinsMasters jenkinsMasters

    @SuppressWarnings('GStringExpressionWithinString')
    @Value('${spinnaker.build.pollInterval:60}')
    int pollInterval

    @Override
    void onApplicationEvent(ContextRefreshedEvent event) {
        log.info('Started')
        worker.schedulePeriodically(
            {
                jenkinsMasters.map.keySet().each { master ->
                    changedBuilds(master)
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

    /*
     * retrieves a list of new builds that are different than the ones in cache and keeps track of the builds it has
     */

    List<Map> changedBuilds(String master) {

        log.info('Checking for new builds for ' + master)
        List<Map> results = []

        try {

            List<String> cachedBuilds = cache.getJobNames(master)
            List<Project> builds = jenkinsMasters.map[master].projects?.list
            List<String> buildNames = builds*.name
            Observable.from(cachedBuilds).filter { String name ->
                !(name in buildNames)
            }.subscribe(
                { String jobName ->
                    log.info "Removing ${master}:${jobName}"
                    cache.remove(master, jobName)
                }, {
                log.error("Error: ${it.message}")
            }, {} as Action0
            )

            Observable.from(builds).subscribe(
                { Project project ->
                    boolean addToCache = false
                    Map cachedBuild = null
                    if (cachedBuilds.contains(project.name)) {
                        cachedBuild = cache.getLastBuild(master, project.name)
                        if ((project.lastBuildStatus != cachedBuild.lastBuildStatus) ||
                            (project.lastBuildLabel > cachedBuild.lastBuildLabel)) {
                            log.info "Build changed: ${master}: ${project.name} : ${project.lastBuildStatus} :" +
                                "${project.lastBuildLabel}"
                            addToCache = true
                        }
                    } else {
                        log.info "New Build: ${master}: ${project.name} : ${project.lastBuildStatus} : " +
                            "${project.lastBuildLabel}"
                        addToCache = true
                    }
                    if (addToCache) {
                        cache.setLastBuild(master, project.name, project.lastBuildLabel, project.lastBuildStatus)
                        if (echoService) {
                            project.artifacts = jenkinsMasters.map[master].getArtifacts(project.name, project.lastBuildLabel).artifactList

                            echoService.postBuild(
                                new BuildDetails(content: new BuildContent(project: project, master: master))
                            )
                        }
                        results << [previous: cachedBuild, current: project]
                    }
                }, {
                log.error("Error: ${it.message}")
            }, {
            } as Action0
            )

        } catch (e) {
            log.error("failed to update master $master", e)
        }

        results
    }

}
