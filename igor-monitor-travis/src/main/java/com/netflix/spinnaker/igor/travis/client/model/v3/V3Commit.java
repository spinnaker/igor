/*
 * Copyright 2018 Schibsted ASA.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
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

package com.netflix.spinnaker.igor.travis.client.model.v3;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.netflix.spinnaker.igor.build.model.GenericGitRevision;
import java.time.Instant;
import javax.xml.bind.annotation.XmlRootElement;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
@XmlRootElement(name = "commit")
public class V3Commit {
  private int id;
  private String sha;
  private String ref;
  private String message;
  private String compareUrl;
  private Instant committedAt;
  private Person author;
  private Person committer;

  public boolean isTag() {
    return ref != null && ref.split("/")[1].equals("tags");
  }

  public boolean isPullRequest() {
    return ref != null && ref.split("/")[1].equals("pull");
  }

  @Data
  @NoArgsConstructor
  @JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
  @JsonIgnoreProperties(ignoreUnknown = true)
  private static class Person {
    private String name;
    private String avatarUrl;
  }

  @JsonIgnore
  public GenericGitRevision getGenericGitRevision() {
    return GenericGitRevision.builder()
        .sha1(sha)
        .committer(getAuthor().getName())
        .compareUrl(compareUrl)
        .message(message)
        .timestamp(committedAt)
        .build();
  }
}
