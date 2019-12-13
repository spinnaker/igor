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

package com.netflix.spinnaker.igor.scm.stash.client.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

import java.util.stream.Collectors

@JsonIgnoreProperties(ignoreUnknown = true)
class DirectoryListingResponse {
  PathDetails path
  String revision
  DirectoryChildren children

  List<String> toChildFilenames() {
    return children.values.stream().map { it.path.name }.collect(Collectors.toList())
  }
}

@JsonIgnoreProperties(ignoreUnknown = true)
class DirectoryChildren {
  int size
  int limit
  int start
  boolean isLastPage
  List<DirectoryChild> values
}

@JsonIgnoreProperties(ignoreUnknown = true)
class DirectoryChild {
  PathDetails path
  String type
  int size
}

@JsonIgnoreProperties(ignoreUnknown = true)
class PathDetails {
  List<String> components
  String parent
  String name
  String extension
}
