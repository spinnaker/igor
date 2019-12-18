/*
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.igor.scm

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.netflix.spinnaker.igor.config.DeliveryConfigProperties
import com.netflix.spinnaker.igor.scm.stash.client.StashClient
import com.netflix.spinnaker.igor.scm.stash.client.StashMaster
import com.netflix.spinnaker.igor.scm.stash.client.model.DirectoryChild
import com.netflix.spinnaker.igor.scm.stash.client.model.DirectoryChildren
import com.netflix.spinnaker.igor.scm.stash.client.model.DirectoryListingResponse
import com.netflix.spinnaker.igor.scm.stash.client.model.PathDetails
import com.netflix.spinnaker.igor.scm.stash.client.model.TextLinesResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import spock.lang.Specification
import spock.lang.Subject

class DeliveryConfigManifestsControllerSpec extends Specification {
  @Subject
  DeliveryConfigManifestsController controller

  StashClient client = Mock(StashClient)
  final STASH_ADDRESS = "https://stash.com"

  ObjectMapper jsonMapper = new ObjectMapper()
  ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory())

  void setup() {
      controller = new DeliveryConfigManifestsController(
        Optional.of(new DeliveryConfigProperties(manifestBasePath: ".netflix")),
        Optional.of(new StashMaster(stashClient: client, baseUrl : STASH_ADDRESS)),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        jsonMapper)
  }

  void 'list manifests'() {
    given:
    1 * client.listDirectory(project, repo, ".netflix/dir", ref) >> expectedResponse

    when:
    List<String> response = controller.listManifests(scmType, project, repo, dir, extension, ref)

    then:
    response == expectedResponse.toChildFilenames()

    where:
    scmType = 'stash'
    project = 'key'
    repo = 'slug'
    dir = 'dir'
    extension = 'yml'
    ref = 'refs/heads/master'
    expectedResponse = new DirectoryListingResponse(
      children: new DirectoryChildren(values: [
        new DirectoryChild(type: "FILE", path: new PathDetails(name: "test.yml"))
      ])
    )
  }

  void 'get yaml manifest'() {
    given:
    1 * client.getTextFileContents(project, repo, ".netflix/dir/manifest.yml", ref) >> expectedResponse

    when:
    ResponseEntity<Map<String, Object>> response = controller.getManifest(scmType, project, repo, manifest, dir, ref)

    then:
    response == new ResponseEntity<>(yamlMapper.readValue(expectedResponse.toTextContents(), Map.class),
      HttpStatus.OK)

    where:
    scmType = 'stash'
    project = 'key'
    repo = 'slug'
    manifest = 'manifest.yml'
    dir = 'dir'
    ref = 'refs/heads/master'
    expectedResponse = new TextLinesResponse(
      lines: [
        [ text: "apiVersion: foo"],
        [ text: "kind: Foo"],
        [ text: "metadata: {}"],
        [ text: "spec: {}"]
      ]
    )
  }

  void 'get json manifest'() {
    given:
    1 * client.getTextFileContents(project, repo, ".netflix/dir/manifest.json", ref) >> expectedResponse

    when:
    ResponseEntity<Map<String, Object>> response = controller.getManifest(scmType, project, repo, manifest, dir, ref)

    then:
    response == new ResponseEntity<>(jsonMapper.readValue(expectedResponse.toTextContents(), Map.class),
      HttpStatus.OK)

    where:
    scmType = 'stash'
    project = 'key'
    repo = 'slug'
    manifest = 'manifest.json'
    dir = 'dir'
    ref = 'refs/heads/master'
    expectedResponse = new TextLinesResponse(
      lines: [
        [ text: '{ "apiVersion": "foo", "kind": "Foo", "metadata": {}, "spec": {} }']
      ]
    )
  }

  void 'retrieving anything other than yaml or json returns a 400'() {
    given:
    1 * client.getTextFileContents(project, repo, ".netflix/dir/somefile", ref) >> expectedResponse

    when:
    ResponseEntity<Map<String, Object>> response = controller.getManifest(scmType, project, repo, manifest, dir, ref)

    then:
    response == new ResponseEntity<>([:], HttpStatus.BAD_REQUEST)

    where:
    scmType = 'stash'
    project = 'key'
    repo = 'slug'
    manifest = 'somefile'
    dir = 'dir'
    ref = 'refs/heads/master'
    expectedResponse = new TextLinesResponse(
      lines: [
        [ text: "blah" ]
      ]
    )
  }
}
