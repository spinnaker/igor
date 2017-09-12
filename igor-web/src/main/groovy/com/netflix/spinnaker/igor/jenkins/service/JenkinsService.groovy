/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package com.netflix.spinnaker.igor.jenkins.service

import com.netflix.spinnaker.hystrix.SimpleHystrixCommand
import com.netflix.spinnaker.igor.build.BuildController
import com.netflix.spinnaker.igor.build.model.GenericBuild
import com.netflix.spinnaker.igor.build.model.GenericGitRevision
import com.netflix.spinnaker.igor.jenkins.client.JenkinsClient
import com.netflix.spinnaker.igor.jenkins.client.model.Build
import com.netflix.spinnaker.igor.jenkins.client.model.BuildDependencies
import com.netflix.spinnaker.igor.jenkins.client.model.BuildsList
import com.netflix.spinnaker.igor.jenkins.client.model.JobConfig
import com.netflix.spinnaker.igor.jenkins.client.model.JobList
import com.netflix.spinnaker.igor.jenkins.client.model.Project
import com.netflix.spinnaker.igor.jenkins.client.model.ProjectsList
import com.netflix.spinnaker.igor.jenkins.client.model.QueuedJob
import com.netflix.spinnaker.igor.jenkins.client.model.ScmDetails
import com.netflix.spinnaker.igor.model.BuildServiceProvider
import com.netflix.spinnaker.igor.service.BuildService
import org.springframework.web.util.UriUtils
import retrofit.client.Response
import retrofit.mime.TypedByteArray

class JenkinsService implements BuildService {
    final String groupKey
    final JenkinsClient jenkinsClient

    JenkinsService(String jenkinsHostId, JenkinsClient jenkinsClient) {
        this.groupKey = "jenkins-${jenkinsHostId}"
        this.jenkinsClient = jenkinsClient
    }

    private String encode(uri) {
        return UriUtils.encodeFragment(uri, "UTF-8")
    }

    ProjectsList getProjects() {
        new SimpleHystrixCommand<ProjectsList>(
            groupKey, buildCommandKey("getProjects"), {
            List<Project> projects = []
            def recursiveGetProjects
            recursiveGetProjects = { list, prefix="" ->
                if (prefix) {
                    prefix = prefix + "/job/"
                }
                list.each {
                    if (it.list == null || it.list.empty) {
                        it.name = prefix + it.name
                        projects << it
                    } else {
                        recursiveGetProjects(it.list, prefix + it.name)
                    }
                }
            }
            recursiveGetProjects(jenkinsClient.getProjects().list)
            ProjectsList projectList = new ProjectsList()
            projectList.list = projects
            return projectList
        }).execute()
    }

    JobList getJobs() {
        new SimpleHystrixCommand<JobList>(
            groupKey, buildCommandKey("getJobs"), {
            return jenkinsClient.getJobs()
        }).execute()
    }

    BuildsList getBuilds(String jobName) {
        new SimpleHystrixCommand<BuildsList>(
            groupKey, buildCommandKey("getBuilds"), {
            return jenkinsClient.getBuilds(encode(jobName))
        }).execute()
    }

    BuildDependencies getDependencies(String jobName) {
        new SimpleHystrixCommand<BuildDependencies>(
            groupKey, buildCommandKey("getDependencies"), {
            return jenkinsClient.getDependencies(encode(jobName))
        }).execute()
    }

    Build getBuild(String jobName, Integer buildNumber) {
        return jenkinsClient.getBuild(encode(jobName), buildNumber)
    }

    @Override
    String getLog(String jobName, int buildNumber) {
        Response response = jenkinsClient.getLog(encode(jobName), buildNumber)
        return new String(((TypedByteArray) response.getBody()).getBytes())
    }

    @Override
    GenericBuild getGenericBuild(String jobName, int buildNumber) {
        return getBuild(jobName, buildNumber).genericBuild(jobName)
    }

    @Override
    int triggerBuildWithParameters(String job, Map<String, String> queryParameters) {
        Response response = buildWithParameters(job, queryParameters)
        if (response.status != 201) {
            throw new BuildController.BuildJobError("Received a non-201 status when submitting job '${job}' to master '${master}'")
        }

        log.info("Submitted build job `${job}`")
        def locationHeader = response.headers.find { it.name == "Location" }
        if (!locationHeader) {
            throw new BuildController.QueuedJobDeterminationError("Could not find Location header for job '${job}'")
        }
        def queuedLocation = locationHeader.value

        return queuedLocation.split('/')[-1].toInteger()
    }

    ScmDetails getGitDetails(String jobName, Integer buildNumber) {
        new SimpleHystrixCommand<ScmDetails>(
            groupKey, buildCommandKey("getGitDetails"), {
            return jenkinsClient.getGitDetails(encode(jobName), buildNumber)
        }).execute()
    }

    Build getLatestBuild(String jobName) {
        new SimpleHystrixCommand<Build>(
            groupKey, buildCommandKey("getLatestBuild"), {
            return jenkinsClient.getLatestBuild(encode(jobName))
        }).execute()
    }

    QueuedJob getQueuedItem(Integer item) {
        return jenkinsClient.getQueuedItem(item)
    }

    Response build(String jobName) {
        return jenkinsClient.build(encode(jobName), "")
    }

    Response buildWithParameters(String jobName, Map<String, String> queryParams) {
        return jenkinsClient.buildWithParameters(encode(jobName), queryParams, "")
    }

    JobConfig getJobConfig(String jobName) {
        return jenkinsClient.getJobConfig(encode(jobName))
    }

    Response getPropertyFile(String jobName, Integer buildNumber, String fileName) {
        return jenkinsClient.getPropertyFile(encode(jobName), buildNumber, fileName)
    }

    Response stopRunningBuild (String jobName, Integer buildNumber){
        return jenkinsClient.stopRunningBuild(encode(jobName), buildNumber, "")
    }

    Response stopQueuedBuild (String queuedBuild) {
        return jenkinsClient.stopQueuedBuild(queuedBuild, "")
    }

  /**
   * A CommandKey should be unique per group (to ensure broken circuits do not span Jenkins masters)
   */
    private String buildCommandKey(String id) {
        return "${groupKey}-${id}"
    }

    @Override
    BuildServiceProvider buildServiceProvider() {
        return BuildServiceProvider.JENKINS
    }

    @Override
    List<GenericGitRevision> getGenericGitRevisions(String job, int buildNumber) {
        ScmDetails scmDetails = getGitDetails(job, buildNumber)
        if (scmDetails?.action?.lastBuiltRevision?.branch?.name) {
            return scmDetails.genericGitRevisions()
        }
        return null
    }
}
