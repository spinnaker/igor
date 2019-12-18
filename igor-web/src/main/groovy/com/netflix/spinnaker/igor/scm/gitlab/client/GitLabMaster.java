/*
 * Copyright 2017 bol.com
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

package com.netflix.spinnaker.igor.scm.gitlab.client;

import com.netflix.spinnaker.igor.scm.AbstractScmMaster;

/** Wrapper class for a collection of GitLab clients */
public class GitLabMaster extends AbstractScmMaster {
  private final GitLabClient gitLabClient;
  private final String baseUrl;

  public GitLabMaster(GitLabClient gitLabClient, String baseUrl) {
    this.gitLabClient = gitLabClient;
    this.baseUrl = baseUrl;
  }

  public GitLabClient getGitLabClient() {
    return gitLabClient;
  }

  public String getBaseUrl() {
    return baseUrl;
  }
}
