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

package com.netflix.spinnaker.igor.travis.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.netflix.spinnaker.igor.travis.client.model.v3.Config;
import com.netflix.spinnaker.igor.travis.client.model.v3.TravisBuildState;
import java.time.Instant;
import java.util.List;
import javax.xml.bind.annotation.XmlRootElement;
import lombok.Data;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@XmlRootElement(name = "builds")
public class Build {

  @JsonProperty("commit_id")
  private int commitId;

  private int duration;

  private int id;

  @JsonProperty("repository_id")
  private int repositoryId;

  private String number;

  private TravisBuildState state;

  @JsonProperty("finished_at")
  private Instant finishedAt;

  @JsonProperty("pull_request")
  private Boolean pullRequest;

  @JsonProperty(value = "job_ids")
  private List<Integer> job_ids;

  private Config config;

  public long getTimestamp() {
    if (finishedAt == null) {
      return 0;
    }
    return finishedAt.toEpochMilli();
  }
}
