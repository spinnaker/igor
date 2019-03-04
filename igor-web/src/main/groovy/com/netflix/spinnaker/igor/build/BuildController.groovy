/*
 * Copyright 2015 Netflix, Inc.
 * Copyright (c) 2017, 2018, Oracle Corporation and/or its affiliates. All rights reserved.
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

package com.netflix.spinnaker.igor.build

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.igor.build.model.GenericBuild
import com.netflix.spinnaker.igor.jenkins.client.model.JobConfig
import com.netflix.spinnaker.igor.jenkins.service.JenkinsService
import com.netflix.spinnaker.igor.model.BuildServiceProvider
import com.netflix.spinnaker.igor.service.ArtifactDecorator
import com.netflix.spinnaker.igor.service.BuildService
import com.netflix.spinnaker.igor.service.BuildServices
import com.netflix.spinnaker.kork.core.RetrySupport
import com.netflix.spinnaker.kork.web.exceptions.InvalidRequestException
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException
import groovy.transform.InheritConstructors
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.HandlerMapping
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import retrofit.RetrofitError

import javax.servlet.http.HttpServletRequest
import java.util.concurrent.ExecutorService

import static net.logstash.logback.argument.StructuredArguments.kv
import static org.springframework.http.HttpStatus.NOT_FOUND

@Slf4j
@RestController
class BuildController {

    @Autowired
    ExecutorService executor

    @Autowired
    BuildServices buildMasters

    @Autowired
    ObjectMapper objectMapper

    @Autowired
    RetrySupport retrySupport

    @Autowired(required = false)
    BuildArtifactFilter buildArtifactFilter

    @Autowired(required = false)
    ArtifactDecorator artifactDecorator

    @RequestMapping(value = '/builds/status/{buildNumber}/{master:.+}/**')
    GenericBuild getJobStatus(@PathVariable String master, @PathVariable
        Integer buildNumber, HttpServletRequest request) {
        def job = (String) request.getAttribute(
            HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE).split('/').drop(5).join('/')
        def buildService = getBuildService(master)
        GenericBuild build = buildService.getGenericBuild(job, buildNumber)
        try {
            build.genericGitRevisions = buildService.getGenericGitRevisions(job, buildNumber)
        } catch (Exception e) {
            log.error("could not get scm results for {} / {} / {}", kv("master", master), kv("job", job), kv("buildNumber", buildNumber))
        }

        if (artifactDecorator) {
            artifactDecorator.decorate(build)
        }

        if (buildArtifactFilter) {
            build.artifacts = buildArtifactFilter.filterArtifacts(build.artifacts)
        }

        return build
    }

    @RequestMapping(value = '/builds/queue/{master}/{item}')
    Object getQueueLocation(@PathVariable String master, @PathVariable int item) {
        def buildService = getBuildService(master)
        if (buildService.buildServiceProvider() == BuildServiceProvider.JENKINS) {
            try {
                return buildService.getQueuedItem(item)
            } catch (RetrofitError e) {
                if (e.response?.status == NOT_FOUND.value()) {
                    throw new NotFoundException("Queued job '${item}' not found for master '${master}'.")
                }
                throw e
            }
        } else if (buildService.buildServiceProvider() == BuildServiceProvider.TRAVIS) {
            return buildService.queuedBuild(item)
        }
    }

    @RequestMapping(value = '/builds/all/{master:.+}/**')
    List<Object> getBuilds(@PathVariable String master, HttpServletRequest request) {
        def job = (String) request.getAttribute(
            HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE).split('/').drop(4).join('/')

        def buildService = getBuildService(master)
        if (buildService.buildServiceProvider() == BuildServiceProvider.JENKINS) {
            return buildService.getBuilds(job).list
        } else {
            return buildService.getBuilds(job)
        }
    }

    @RequestMapping(value = "/masters/{name}/jobs/{jobName}/stop/{queuedBuild}/{buildNumber}", method = RequestMethod.PUT)
    String stop(
        @PathVariable("name") String master,
        @PathVariable String jobName,
        @PathVariable String queuedBuild,
        @PathVariable Integer buildNumber) {

        def buildService = getBuildService(master)
        // Jobs that haven't been started yet won't have a buildNumber
        // (They're still in the queue). We use 0 to denote that case
        if (buildNumber != 0 &&
            buildService.metaClass.respondsTo(buildService, 'stopRunningBuild')) {
            buildService.stopRunningBuild(jobName, buildNumber)
        }

        // The jenkins api for removing a job from the queue (http://<Jenkins_URL>/queue/cancelItem?id=<queuedBuild>)
        // always returns a 404. This try catch block insures that the exception is eaten instead
        // of being handled by the handleOtherException handler and returning a 500 to orca
        try {
            if (buildService.metaClass.respondsTo(buildService, 'stopQueuedBuild')) {
                buildService.stopQueuedBuild(queuedBuild)
            }
        } catch (RetrofitError e) {
            if (e.response?.status != NOT_FOUND.value()) {
                throw e
            }
        }

        "true"
    }

    @RequestMapping(value = '/masters/{name}/jobs/**', method = RequestMethod.PUT)
    String build(
        @PathVariable("name") String master,
        @RequestParam Map<String, String> requestParams, HttpServletRequest request) {
        def job = (String) request.getAttribute(
            HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE).split('/').drop(4).join('/')
        def buildService = getBuildService(master)
        if (buildService.buildServiceProvider() == BuildServiceProvider.JENKINS) {
            def response
            JenkinsService jenkinsService = (JenkinsService) buildService
            JobConfig jobConfig = jenkinsService.getJobConfig(job)
            if (!jobConfig.buildable) {
                throw new BuildJobError("Job '${job}' is not buildable. It may be disabled.")
            }

            if (jobConfig.parameterDefinitionList?.size() > 0) {
                validateJobParameters(jobConfig, requestParams)
            }
            if (requestParams && jobConfig.parameterDefinitionList?.size() > 0) {
                response = jenkinsService.buildWithParameters(job, requestParams)
            } else if (!requestParams && jobConfig.parameterDefinitionList?.size() > 0) {
                // account for when you just want to fire a job with the default parameter values by adding a dummy param
                response = jenkinsService.buildWithParameters(job, ['startedBy': "igor"])
            } else if (!requestParams && (!jobConfig.parameterDefinitionList || jobConfig.parameterDefinitionList.size() == 0)) {
                response = jenkinsService.build(job)
            } else { // Jenkins will reject the build, so don't even try
                // we should throw a BuildJobError, but I get a bytecode error : java.lang.VerifyError: Bad <init> method call from inside of a branch
                throw new RuntimeException("job : ${job}, passing params to a job which doesn't need them")
            }

            if (response.status != 201) {
                throw new BuildJobError("Received a non-201 status when submitting job '${job}' to master '${master}'")
            }

            log.info("Submitted build job '{}'", kv("job", job))
            def locationHeader = response.headers.find { it.name.toLowerCase() == "location" }
            if (!locationHeader) {
                throw new QueuedJobDeterminationError("Could not find Location header for job '${job}'")
            }
            def queuedLocation = locationHeader.value

            queuedLocation.split('/')[-1]
        } else {
            return buildService.triggerBuildWithParameters(job, requestParams)
        }
    }

    static void validateJobParameters(JobConfig jobConfig, Map<String, String> requestParams) {
        jobConfig.parameterDefinitionList.each { parameterDefinition ->
            String matchingParam = requestParams[parameterDefinition.name]
            if (matchingParam != null && parameterDefinition.type == 'ChoiceParameterDefinition' && !parameterDefinition.choices.contains(matchingParam)) {
                throw new InvalidJobParameterException("`${matchingParam}` is not a valid choice " +
                    "for `${parameterDefinition.name}`. Valid choices are: ${parameterDefinition.choices.join(', ')}")
            }
        }
    }

    @RequestMapping(value = '/builds/properties/{buildNumber}/{fileName}/{master:.+}/**')
    Map<String, Object> getProperties(
        @PathVariable String master,
        @PathVariable Integer buildNumber, @PathVariable
            String fileName, HttpServletRequest request) {
        def job = (String) request.getAttribute(
            HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE).split('/').drop(6).join('/')
        def buildService = getBuildService(master)
        if (buildService.buildServiceProvider() == BuildServiceProvider.JENKINS) {
            Map<String, Object> map = [:]
            try {
                String path = getArtifactPathFromBuild(buildService, master, job, buildNumber, fileName)

                def propertyStream = buildService.getPropertyFile(job, buildNumber, path).body.in()
                try {
                    if (fileName.endsWith('.yml') || fileName.endsWith('.yaml')) {
                        Yaml yml = new Yaml(new SafeConstructor())
                        map = yml.load(propertyStream)
                    } else if (fileName.endsWith('.json')) {
                        map = objectMapper.readValue(propertyStream, Map)
                    } else {
                        Properties properties = new Properties()
                        properties.load(propertyStream)
                        map = map << properties
                    }
                } finally {
                    propertyStream.close()
                }
            } catch (NotFoundException e) {
                throw e
            } catch (e) {
                log.error("Unable to get igorProperties '{}'", kv("job", job), e)
            }
            map
        } else if (buildService.buildServiceProvider() == BuildServiceProvider.TRAVIS) {
            try {
                buildService.getBuildProperties(job, buildNumber)
            } catch (e) {
                log.error("Unable to get igorProperties '{}'", kv("job", job), e)
            }
        }
    }

    private String getArtifactPathFromBuild(jenkinsService, master, job, buildNumber, String fileName) {
        return retrySupport.retry({ ->
            def artifact = jenkinsService.getBuild(job, buildNumber).artifacts.find {
                it.fileName == fileName
            }
            if (artifact) {
                return artifact.relativePath
            } else {
                log.error("Unable to get igorProperties: Could not find build artifact matching requested filename '{}' on '{}' build '{}",
                    kv("fileName", fileName), kv("master", master), kv("buildNumber", buildNumber))
                throw new ArtifactNotFoundException(master, job, buildNumber, fileName)
            }
        }, 5, 2000, false)
    }

    @ResponseStatus(NOT_FOUND)
    private static class ArtifactNotFoundException extends NotFoundException {
        ArtifactNotFoundException(String master, String job, Integer buildNumber, String fileName) {
            super("Could not find build artifact matching requested filename '$fileName' on '$master/$job' build $buildNumber")
        }
    }

    private BuildService getBuildService(String master) {
        def buildService = buildMasters.getService(master)
        if (buildService == null) {
            throw new NotFoundException("Master '${master}' not found}")
        }
        return buildService
    }

    @InheritConstructors
    static class BuildJobError extends InvalidRequestException {}

    @ResponseStatus(code = HttpStatus.SERVICE_UNAVAILABLE)
    static class QueuedJobDeterminationError extends RuntimeException {}

    @InheritConstructors
    static class InvalidJobParameterException extends InvalidRequestException {}

}
