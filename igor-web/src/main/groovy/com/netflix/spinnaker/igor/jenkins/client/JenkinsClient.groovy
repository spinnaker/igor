/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.igor.jenkins.client

import com.netflix.spinnaker.igor.jenkins.client.model.*
import com.netflix.spinnaker.igor.jenkins.client.model.BuildsList
import com.netflix.spinnaker.igor.jenkins.client.model.JobConfig
import com.netflix.spinnaker.igor.jenkins.client.model.JobList
import com.netflix.spinnaker.igor.jenkins.client.model.ScmDetails
import com.netflix.spinnaker.igor.model.Crumb
import retrofit.client.Response
import retrofit.http.*

/**
 * Interface for interacting with a Jenkins Service via Xml
 */
@SuppressWarnings('LineLength')
interface JenkinsClient {
    /*
     * Jobs created with the Jenkins Folders plugin are nested.
     * Some queries look for jobs within folders with a depth of 10.
     */

    @GET('/api/xml?tree=jobs[name,lastBuild[actions[failCount,skipCount,totalCount,urlName],duration,number,timestamp,result,building,url],jobs[name,lastBuild[actions[failCount,skipCount,totalCount,urlName],duration,number,timestamp,result,building,url],jobs[name,lastBuild[actions[failCount,skipCount,totalCount,urlName],duration,number,timestamp,result,building,url],jobs[name,lastBuild[actions[failCount,skipCount,totalCount,urlName],duration,number,timestamp,result,building,url],jobs[name,lastBuild[actions[failCount,skipCount,totalCount,urlName],duration,number,timestamp,result,building,url],jobs[name,lastBuild[actions[failCount,skipCount,totalCount,urlName],duration,number,timestamp,result,building,url],jobs[name,lastBuild[actions[failCount,skipCount,totalCount,urlName],duration,number,timestamp,result,building,url],jobs[name,lastBuild[actions[failCount,skipCount,totalCount,urlName],duration,number,timestamp,result,building,url],jobs[name,lastBuild[actions[failCount,skipCount,totalCount,urlName],duration,number,timestamp,result,building,url],jobs[name,lastBuild[actions[failCount,skipCount,totalCount,urlName],duration,number,timestamp,result,building,url]]]]]]]]]]]&exclude=/*/*/*/action[not(totalCount)]')
    ProjectsList getProjects()

    @GET('/api/xml?tree=jobs[name,jobs[name,jobs[name,jobs[name,jobs[name,jobs[name,jobs[name,jobs[name,jobs[name,jobs[name]]]]]]]]]]')
    JobList getJobs()

    @GET('/job/{jobName}/api/xml?exclude=/*/build/action[not(totalCount)]&tree=builds[number,url,duration,timestamp,result,building,url,fullDisplayName,actions[failCount,skipCount,totalCount]]')
    BuildsList getBuilds(@EncodedPath('jobName') String jobName)

    @GET('/job/{jobName}/api/xml?tree=name,url,actions[processes[name]],downstreamProjects[name,url],upstreamProjects[name,url]')
    BuildDependencies getDependencies(@EncodedPath('jobName') String jobName)

    @GET('/job/{jobName}/{buildNumber}/api/xml?exclude=/*/action[not(totalCount)]&tree=actions[failCount,skipCount,totalCount,urlName],duration,number,timestamp,result,building,url,fullDisplayName,artifacts[displayPath,fileName,relativePath]')
    Build getBuild(@EncodedPath('jobName') String jobName, @Path('buildNumber') Long buildNumber)

    // The location of the SCM details in the build xml changed in version 4.0.0 of the jenkins-git plugin; see the
    // header comment in com.netflix.spinnaker.igor.jenkins.client.model.ScmDetails for more information.
    // The exclude and tree parameters to this call must continue to support both formats to remain compatible with
    // all versions of the plugin.
    @GET('/job/{jobName}/{buildNumber}/api/xml?exclude=/*/action[not(build|lastBuiltRevision)]&tree=actions[remoteUrls,lastBuiltRevision[branch[name,SHA1]],build[revision[branch[name,SHA1]]]]')
    ScmDetails getGitDetails(@EncodedPath('jobName') String jobName, @Path('buildNumber') Long buildNumber)

    @GET('/job/{jobName}/lastCompletedBuild/api/xml')
    Build getLatestBuild(@EncodedPath('jobName') String jobName)

    @GET('/job/{jobName}/lastCompletedBuild/consoleText')
    Response getLatestBuildOutput(@EncodedPath('jobName') String jobName)

    @GET('/job/{jobName}/{buildNumber}/consoleText')
    Response getBuildOutput(@EncodedPath('jobName') String jobName, @Path('buildNumber') String buildNumber)

    @GET('/queue/item/{itemNumber}/api/xml')
    QueuedJob getQueuedItem(@Path('itemNumber') Long item)

    @POST('/job/{jobName}/build')
    Response build(@EncodedPath('jobName') String jobName, @Body String emptyRequest, @Header("Jenkins-Crumb") String crumb)

    @POST('/job/{jobName}/buildWithParameters')
    Response buildWithParameters(@EncodedPath('jobName') String jobName, @QueryMap Map<String, String> queryParams, @Body String EmptyRequest, @Header("Jenkins-Crumb") String crumb)

    @FormUrlEncoded
    @POST('/job/{jobName}/{buildNumber}/submitDescription')
    Response submitDescription(@EncodedPath('jobName') String jobName, @Path('buildNumber') Long buildNumber, @Field("description") String description, @Header("Jenkins-Crumb") String crumb)

    @POST('/job/{jobName}/{buildNumber}/stop')
    Response stopRunningBuild(@EncodedPath('jobName') String jobName, @Path('buildNumber') Long buildNumber,  @Body String EmptyRequest, @Header("Jenkins-Crumb") String crumb)

    @POST('/queue/cancelItem')
    Response stopQueuedBuild(@Query('id') String queuedBuild, @Body String emptyRequest, @Header("Jenkins-Crumb") String crumb)

    @GET('/job/{jobName}/api/xml?exclude=/*/action&exclude=/*/build&exclude=/*/property[not(parameterDefinition)]')
    JobConfig getJobConfig(@EncodedPath('jobName') String jobName)

    @Streaming
    @GET('/job/{jobName}/{buildNumber}/artifact/{fileName}')
    Response getPropertyFile(
        @EncodedPath('jobName') String jobName,
        @Path('buildNumber') Long buildNumber, @Path(value = 'fileName', encode = false) String fileName)

    @GET('/crumbIssuer/api/xml')
    Crumb getCrumb()
}
