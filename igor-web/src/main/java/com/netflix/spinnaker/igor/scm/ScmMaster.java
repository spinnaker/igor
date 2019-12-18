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

package com.netflix.spinnaker.igor.scm;

import java.util.List;

/** Abstracts underlying implementation details of each SCM system under a common interface. */
public interface ScmMaster {
  public static final String DEFAULT_GIT_REF = "refs/heads/master";

  public abstract List<String> listDirectory(
      String projectKey, String repositorySlug, String path, String at);

  public abstract String getTextFileContents(
      String projectKey, String repositorySlug, String path, String at);
}
