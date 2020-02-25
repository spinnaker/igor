/*
 * Copyright 2020 Netflix, Inc.
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

package com.netflix.spinnaker.igor.ci;

import com.netflix.spinnaker.igor.build.model.GenericBuild;
import java.util.Collections;
import java.util.List;

public class NoopCiService implements CiBuildService {

  /** Noop service that will be a default implementation for the CiBuildService. */
  @Override
  public List<GenericBuild> getBuilds(String projectKey, String repoSlug, String completionStatus) {
    return Collections.emptyList();
  }
}
