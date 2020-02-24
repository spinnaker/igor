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

package com.netflix.spinnaker.igor.jenkins.client.model

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper
import com.netflix.spinnaker.igor.build.model.JobConfiguration

import javax.xml.bind.annotation.XmlElement
import javax.xml.bind.annotation.XmlElementWrapper
import javax.xml.bind.annotation.XmlRootElement

/**
 * Represents the basic Jenkins job configuration information
 */
@XmlRootElement
class JobConfig implements JobConfiguration {
    @XmlElement(required = false)
    String description

    @XmlElement
    String displayName

    @XmlElement
    String name

    @XmlElement
    boolean buildable

    @XmlElement
    String color

    @XmlElement
    String url

    @XmlElementWrapper(name = "property")
    @XmlElement(name = "parameterDefinition", required = false)
    List<ParameterDefinition> parameterDefinitionList

    @JacksonXmlElementWrapper(useWrapping = false)
    @XmlElement(name = "upstreamProject", required = false)
    List<UpstreamProject> upstreamProjectList

    @JacksonXmlElementWrapper(useWrapping = false)
    @XmlElement(name = "downstreamProject", required = false)
    List<DownstreamProject> downstreamProjectList

    @XmlElement
    boolean concurrentBuild
}
