/*
 * Copyright 2019 Pivotal, Inc.
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

package com.netflix.spinnaker.igor.artifactory

import com.netflix.spectator.api.NoopRegistry
import com.netflix.spinnaker.igor.IgorConfigurationProperties
import com.netflix.spinnaker.igor.artifactory.model.ArtifactorySearch
import com.netflix.spinnaker.igor.config.ArtifactoryProperties
import com.netflix.spinnaker.igor.history.EchoService
import com.netflix.spinnaker.igor.polling.LockService
import com.squareup.okhttp.mockwebserver.MockResponse
import com.squareup.okhttp.mockwebserver.MockWebServer
import rx.schedulers.Schedulers
import spock.lang.Specification

class ArtifactoryBuildMonitorSpec extends Specification {
  ArtifactoryCache cache = Mock(ArtifactoryCache)
  EchoService echoService = Mock()
  LockService lockService = Mock()
  IgorConfigurationProperties igorConfigurationProperties = new IgorConfigurationProperties()
  ArtifactoryBuildMonitor monitor

  MockWebServer mockArtifactory = new MockWebServer()

  ArtifactoryBuildMonitor monitor(url, lockService = null) {
    monitor = new ArtifactoryBuildMonitor(
      igorConfigurationProperties,
      new NoopRegistry(),
      Optional.empty(),
      Optional.ofNullable(lockService),
      Optional.of(echoService),
      cache,
      new ArtifactoryProperties(searches: [
        new ArtifactorySearch(
          baseUrl: url,
          repo: 'libs-releases-local',
        )
      ])
    )
    monitor.worker = Schedulers.immediate().createWorker()

    return monitor
  }

  def 'should handle any failure to talk to artifactory graciously' () {
    given:
    mockArtifactory.enqueue(new MockResponse().setResponseCode(400))

    when:
    monitor(mockArtifactory.url('')).poll(false)

    then:
    notThrown(Exception)
  }

  def 'does not add extra path separators with non-empty context root'() {
    given:
    mockArtifactory.enqueue(new MockResponse().setResponseCode(200).setBody('{"results": []}'))

    when:
    monitor(mockArtifactory.url(contextRoot)).poll(false)

    then:
    mockArtifactory.takeRequest().path == "/${contextRoot}api/search/aql"

    where:
    contextRoot << ['artifactory/', '']
  }

  def 'strips out invalid characters when creating a lock name'() {
    given:
    mockArtifactory.enqueue(new MockResponse().setResponseCode(200).setBody('{"results": []}'))

    when:
    monitor("http://localhost:64610", lockService).poll(false)

    then:
    1 * lockService.acquire("artifactoryPublishingMonitor.httplocalhost64610libs-releases-local", _, _)
  }
}
