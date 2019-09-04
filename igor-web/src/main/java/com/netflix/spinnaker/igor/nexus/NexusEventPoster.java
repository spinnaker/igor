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

package com.netflix.spinnaker.igor.nexus;

import com.netflix.spinnaker.igor.config.NexusProperties;
import com.netflix.spinnaker.igor.history.EchoService;
import com.netflix.spinnaker.igor.nexus.model.NexusAssetEvent;
import com.netflix.spinnaker.igor.nexus.model.NexusAssetWebhookPayload;
import com.netflix.spinnaker.igor.nexus.model.NexusRepo;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.apache.logging.log4j.util.Strings;

public class NexusEventPoster {

  private final NexusProperties nexusProperties;
  private final EchoService echoService;

  public NexusEventPoster(NexusProperties nexusProperties, EchoService echoService) {
    this.nexusProperties = nexusProperties;
    this.echoService = echoService;
  }

  public void postEvent(NexusAssetWebhookPayload payload) {
    if ((payload.getAction().equals("CREATED") || payload.getAction().equals("UPDATED"))
        && payload.getAsset().getFormat().equals("maven2")
        && payload.getAsset().getName().endsWith(".pom")) {

      final String[] nameTokens = payload.getAsset().getName().split("/");
      final List<String> nameList = Arrays.asList(nameTokens);
      final String version = nameList.get(nameList.size() - 2);
      final String artifactId = nameList.get(nameList.size() - 3);
      final String group = Strings.join(nameList.subList(0, nameList.size() - 3), '.');
      final Artifact artifact =
          Artifact.builder()
              .type("maven/file")
              .reference(group + ":" + artifactId + ":" + version)
              .name(group + ":" + artifactId)
              .version(version)
              .provenance(payload.getRepositoryName())
              .build();
      final Optional<NexusRepo> oRepo = findNexusRepo(payload);
      oRepo.ifPresent(
          repo -> {
            AuthenticatedRequest.allowAnonymous(
                () ->
                    echoService.postEvent(
                        new NexusAssetEvent(
                            new NexusAssetEvent.Content(repo.getName(), artifact))));
          });
    }
  }

  private Optional<NexusRepo> findNexusRepo(NexusAssetWebhookPayload payload) {
    if (payload.getNodeId() != null) {
      return nexusProperties.getRepos().stream()
          .filter(
              repo -> {
                return payload.getNodeId().equals(repo.getNodeId());
              })
          .findFirst();
    } else {
      return nexusProperties.getRepos().stream()
          .filter(
              repo -> {
                return payload.getRepositoryName().equals(repo.getRepo());
              })
          .findFirst();
    }
  }
}
