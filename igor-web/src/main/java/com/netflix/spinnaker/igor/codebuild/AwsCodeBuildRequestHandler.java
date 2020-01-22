/*
 * Copyright 2020 Amazon.com, Inc.
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
 *
 */

package com.netflix.spinnaker.igor.codebuild;

import com.amazonaws.Request;
import com.amazonaws.Response;
import com.amazonaws.handlers.RequestHandler2;
import com.amazonaws.services.codebuild.model.AWSCodeBuildException;
import com.netflix.spinnaker.igor.exceptions.BuildJobError;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AwsCodeBuildRequestHandler extends RequestHandler2 {
  @Override
  public void afterError(Request<?> request, Response<?> response, Exception e) {
    if (e instanceof AWSCodeBuildException) {
      log.warn(e.getMessage());
      throw new BuildJobError(e.getMessage());
    } else {
      log.error(e.getMessage());
      throw new RuntimeException(e);
    }
  }
}
