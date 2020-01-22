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
 */

package com.netflix.spinnaker.igor.codebuild;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.codebuild.AWSCodeBuildClient;
import com.amazonaws.services.codebuild.AWSCodeBuildClientBuilder;
import com.amazonaws.services.codebuild.model.BatchGetBuildsRequest;
import com.amazonaws.services.codebuild.model.Build;
import com.amazonaws.services.codebuild.model.StartBuildRequest;
import lombok.RequiredArgsConstructor;

/** Generates authenticated requests to AWS CodeBuild API for a single configured account */
@RequiredArgsConstructor
public class AwsCodeBuildAccount {
  private final AWSCodeBuildClient client;

  public AwsCodeBuildAccount(String accessKeyId, String secretAccessKey, String region) {
    BasicAWSCredentials awsCreds = new BasicAWSCredentials(accessKeyId, secretAccessKey);
    this.client =
        (AWSCodeBuildClient)
            AWSCodeBuildClientBuilder.standard()
                .withCredentials(new AWSStaticCredentialsProvider(awsCreds))
                .withRequestHandlers(new AwsCodeBuildRequestHandler())
                .withRegion(region)
                .build();
  }

  public Build startBuild(StartBuildRequest request) {
    return client.startBuild(request).getBuild();
  }

  public Build getBuild(String buildId) {
    return client.batchGetBuilds(new BatchGetBuildsRequest().withIds(buildId)).getBuilds().get(0);
  }
}
