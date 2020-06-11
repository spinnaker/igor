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

package com.netflix.spinnaker.igor.concourse.client.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.netflix.spinnaker.igor.build.model.Result;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Setter
public class Build implements Comparable<Build> {
  @Getter private String id;
  private String name; // the readable build number

  @Getter private long startTime; // milliseconds since Unix epoch

  @Getter private String status;

  @JsonIgnore
  public BigDecimal getDecimalNumber() {
    return new BigDecimal(name);
  }

  public int getNumber() {
    return getDecimalNumber().intValue();
  }

  public boolean isSuccessful() {
    return "succeeded".equals(status);
  }

  public Result getResult() {
    if ("succeeded".equals(status)) {
      return Result.SUCCESS;
    } else if ("failed".equals(status)) {
      return Result.FAILURE;
    } else if ("started".equals(status)) {
      return Result.BUILDING;
    } else if ("aborted".equals(status)) {
      return Result.ABORTED;
    }
    return Result.NOT_BUILT;
  }

  @Override
  public int compareTo(Build o) {
    return o.getDecimalNumber().compareTo(getDecimalNumber());
  }
}
