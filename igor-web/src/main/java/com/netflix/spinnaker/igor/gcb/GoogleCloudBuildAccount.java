/*
 * Copyright 2019 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.igor.gcb;

import com.google.api.services.cloudbuild.v1.model.Build;
import com.google.api.services.cloudbuild.v1.model.Operation;
import lombok.RequiredArgsConstructor;

/**
 * Handles getting and updating build information for a single account. Delegates operations to either the
 * GoogleCloudBuildCache or GoogleCloudBuildClient.
 */
@RequiredArgsConstructor
public class GoogleCloudBuildAccount {
  private final GoogleCloudBuildClient client;
  private final GoogleCloudBuildCache cache;
  private final GoogleCloudBuildParser googleCloudBuildParser;

  @SuppressWarnings("unchecked")
  public Build createBuild(Build build) {
    Operation operation = client.createBuild(build);
    return googleCloudBuildParser.convert(operation.getMetadata().get("build"), Build.class);
  }

  public void updateBuild(String buildId, String status, String serializedBuild) {
    cache.updateBuild(buildId, status, serializedBuild);
  }

  public String getBuild(String buildId) {
    return cache.getBuild(buildId);
  }
}
