/*
 * Copyright 2016 Schibsted ASA.
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
package com.netflix.spinnaker.igor.service;

import com.netflix.spinnaker.igor.build.model.GenericBuild;
import com.netflix.spinnaker.igor.build.model.GenericGitRevision;
import com.netflix.spinnaker.igor.model.BuildServiceProvider;

import java.util.List;
import java.util.Map;

public interface BuildService {
    BuildServiceProvider buildServiceProvider();

    List<GenericGitRevision> getGenericGitRevisions(String job, int buildNumber);

    GenericBuild getGenericBuild(String job, int buildNumber);

    int triggerBuildWithParameters(String job, Map<String, String> queryParameters);

    List<?> getBuilds(String job);
}
