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

import com.netflix.spinnaker.igor.IgorConfigurationProperties
import com.netflix.spinnaker.igor.history.EchoService
import com.netflix.spinnaker.igor.history.model.Event
import com.netflix.spinnaker.igor.jenkins.client.model.Build
import com.netflix.spinnaker.igor.jenkins.client.model.BuildsList
import com.netflix.spinnaker.igor.jenkins.client.model.Project
import com.netflix.spinnaker.igor.jenkins.client.model.ProjectsList
import com.netflix.spinnaker.igor.jenkins.service.JenkinsService
import com.netflix.spinnaker.igor.service.BuildMasters
import rx.schedulers.Schedulers
import spock.lang.Specification

/**
 * Tests for JenkinsBuildMonitor
 */
@SuppressWarnings(['DuplicateNumberLiteral', 'PropertyName'])
class JenkinsBuildMonitorSpec extends Specification {

    JenkinsCache cache = Mock(JenkinsCache)
    JenkinsService jenkinsService = Mock(JenkinsService)
    IgorConfigurationProperties igorConfigurationProperties = new IgorConfigurationProperties()
    JenkinsBuildMonitor monitor

    final MASTER = 'MASTER'

    void setup() {
        monitor = new JenkinsBuildMonitor(
            cache: cache,
            buildMasters: new BuildMasters(map: [MASTER: jenkinsService]),
            igorConfigurationProperties: igorConfigurationProperties
        )

        monitor.scheduler = Schedulers.immediate()
    }

    def 'should handle any failure to talk to jenkins graciously' () {
        given:
        jenkinsService.getProjects().getList() >> new Exception("failed")

        when:
        monitor.changedBuilds(MASTER)

        then:
        notThrown(Exception)
    }

    def 'should skip a job with no builds'() {
        given:
        jenkinsService.getProjects() >> new ProjectsList(list: [new Project(name: 'job2', lastBuild: null)])

        when:
        monitor.changedBuilds(MASTER)

        then:
        0 * cache.getLastPollCycleTimestamp(MASTER, 'job2')
        0 * cache.setLastPollCycleTimestamp(_,_,_)
    }

    def 'should process on first build'() {
        given: 'the first time a build is seen'
        Long previousCursor = null //indicating a first build
        def lastBuild = new Build(number: 1, timestamp: '1494624092610', building: false, result: 'SUCCESS')

        and:
        monitor.echoService = Mock(EchoService)
        cache.getLastPollCycleTimestamp(MASTER, 'job') >> previousCursor
        jenkinsService.getProjects() >> new ProjectsList(list: [ new Project(name: 'job', lastBuild: lastBuild) ])
        cache.getEventPosted(_,_,_,_) >> false
        jenkinsService.getBuilds('job') >> new BuildsList(list: [ lastBuild ])

        when:
        monitor.changedBuilds(MASTER)

        then:
        1 * monitor.echoService.postEvent({ it.content.project.lastBuild.number == 1 && it.content.project.lastBuild.result == 'SUCCESS'} as Event)
    }

    def 'should only post an event for completed builds between last poll and last build'() {
        given:
        def previousCursor = '1494624092609'
        def stamp1 = '1494624092610'
        def stamp2 = '1494624092611'
        def stamp3 = '1494624092612'
        def lastBuild = new Build(number: 40, timestamp: stamp3)
        def stamp4 = '1494624092613'

        and: 'previousCursor(lower bound) < stamp1 < stamp2 < stamp3(upper bound) < stamp4'
        assert new Date(previousCursor as Long) < new Date(stamp1 as Long)
        assert new Date(stamp1 as Long) < new Date(stamp2 as Long) &&  new Date(stamp2 as Long) < new Date(stamp3 as Long)
        assert new Date(stamp3 as Long) < new Date(stamp4 as Long)

        and:
        monitor.echoService = Mock(EchoService)
        cache.getLastPollCycleTimestamp(MASTER, 'job') >> (previousCursor as Long)
        jenkinsService.getProjects() >> new ProjectsList(list: [ new Project(name: 'job', lastBuild: lastBuild) ])
        cache.getEventPosted(_,_,_,_) >> false
        jenkinsService.getBuilds('job') >> new BuildsList(
            list: [
                new Build(number: 1, timestamp: stamp1, building: false, result: 'SUCCESS'),
                new Build(number: 2, timestamp: stamp1, building: true, result: null),
                new Build(number: 3, timestamp: stamp2, building: false, result: 'SUCCESS'),
                new Build(number: 4, timestamp: stamp4, building: false, result: 'SUCCESS'),
                new Build(number: 5, building: false, result: 'SUCCESS')
            ]
        )

        when:
        monitor.changedBuilds(MASTER)

        then: 'only builds between lowerBound(previousCursor) and upperbound(stamp3) will fire events'
        1 * monitor.echoService.postEvent({ it.content.project.lastBuild.number == 1 && it.content.project.lastBuild.result == 'SUCCESS'} as Event)
        1 * monitor.echoService.postEvent({ it.content.project.lastBuild.number == 3 && it.content.project.lastBuild.result == 'SUCCESS'} as Event)
    }

    def 'should advance the lower bound cursor when all jobs complete'() {
        given:
        def previousCursor = '1494624092609'
        def stamp1 = '1494624092610'
        def stamp2 = '1494624092611'
        def stamp3 = '1494624092612'
        def lastBuild = new Build(number: 40, timestamp: stamp3)
        def stamp4 = '1494624092613'

        and: 'previousCursor(lower bound) < stamp1 < stamp2 < stamp3(upper bound) < stamp4'
        assert new Date(previousCursor as Long) < new Date(stamp1 as Long)
        assert new Date(stamp1 as Long) < new Date(stamp2 as Long) &&  new Date(stamp2 as Long) < new Date(stamp3 as Long)
        assert new Date(stamp3 as Long) < new Date(stamp4 as Long)

        and:
        monitor.echoService = Mock(EchoService)
        cache.getLastPollCycleTimestamp(MASTER, 'job') >> (previousCursor as Long)
        jenkinsService.getProjects() >> new ProjectsList(list: [ new Project(name: 'job', lastBuild: lastBuild) ])
        cache.getEventPosted(_,_,_,_) >> false
        jenkinsService.getBuilds('job') >> new BuildsList(
            list: [
                new Build(number: 1, timestamp: stamp1, building: false, result: 'SUCCESS'),
                new Build(number: 2, timestamp: stamp1, building: false, result: 'FAILURE'),
                new Build(number: 3, timestamp: stamp2, building: false, result: 'SUCCESS'),
                new Build(number: 4, timestamp: stamp4, building: false, result: 'SUCCESS')
            ]
        )

        when:
        monitor.changedBuilds(MASTER)

        then: 'only builds between lowerBound(previousCursor) and upperbound(stamp3) will fire events'
        1 * monitor.echoService.postEvent({ it.content.project.lastBuild.number == 1 && it.content.project.lastBuild.result == 'SUCCESS'} as Event)
        1 * monitor.echoService.postEvent({ it.content.project.lastBuild.number == 2 && it.content.project.lastBuild.result == 'FAILURE'} as Event)
        1 * monitor.echoService.postEvent({ it.content.project.lastBuild.number == 3 && it.content.project.lastBuild.result == 'SUCCESS'} as Event)

        and: 'prune old markers and set new cursor'
        1 * cache.pruneOldMarkers(MASTER, 'job', 1494624092609)
        1 * cache.setLastPollCycleTimestamp(MASTER, 'job', 1494624092612)
    }


    def 'should filter out builds older than look back window'() {
        given:
        long now = System.currentTimeMillis()
        long nowMinus30min = now - (30 * 60 * 1000) // 30 minutes ago
        long nowMinus10min = now - (10 * 60 * 1000) // 10 minutes ago
        long nowMinus5min = now - (5 * 60 * 1000)  // 5  minutes ago
        long durationOf1min = 60000

        and: 'a 6 minutes total lookBack window'
        igorConfigurationProperties.spinnaker.build.pollInterval = 60
        igorConfigurationProperties.spinnaker.build.lookBackWindowMins = 5
        igorConfigurationProperties.spinnaker.build.processBuildsOlderThanLookBackWindow = false

        and:
        monitor.echoService = Mock(EchoService)
        cache.getLastPollCycleTimestamp(MASTER, 'job') >> nowMinus30min
        jenkinsService.getProjects() >> new ProjectsList(list: [ new Project(name: 'job', lastBuild: new Build(number: 3, timestamp: now)) ])
        cache.getEventPosted(_,_,_,_) >> false
        jenkinsService.getBuilds('job') >> new BuildsList(
            list: [
                new Build(number: 1, timestamp: nowMinus30min, building: false, result: 'SUCCESS', duration: durationOf1min),
                new Build(number: 2, timestamp: nowMinus10min, building: false, result: 'FAILURE', duration: durationOf1min),
                new Build(number: 3, timestamp: nowMinus5min, building: false, result: 'SUCCESS', duration: durationOf1min)
            ]
        )

        when:
        monitor.changedBuilds(MASTER)

        then: 'build #3 only will be processed'
        0 * monitor.echoService.postEvent({ it.content.project.lastBuild.number == 1 && it.content.project.lastBuild.result == 'SUCCESS'} as Event)
        0 * monitor.echoService.postEvent({ it.content.project.lastBuild.number == 2 && it.content.project.lastBuild.result == 'FAILURE'} as Event)
        1 * monitor.echoService.postEvent({ it.content.project.lastBuild.number == 3 && it.content.project.lastBuild.result == 'SUCCESS'} as Event)
    }
}
