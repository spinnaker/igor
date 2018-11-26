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

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper
import org.simpleframework.xml.ElementList

import javax.xml.bind.annotation.XmlElement
import javax.xml.bind.annotation.XmlType

/**
 * Represents a parameter for a Jenkins job
 */
@XmlType(propOrder=["defaultParameterValue","name","description","type","choice","defaultValue","defaultName"])
class ParameterDefinition {
    // used when deserializing the response from Jenkins
    @XmlElement
    DefaultParameterValue defaultParameterValue

    @XmlElement
    String name

    @XmlElement(required = false)
    String description

    @XmlElement
    String type

    @JacksonXmlElementWrapper(useWrapping = false)
    @XmlElement(name = "choice", required = false)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    List<String> choices

    // used when serializing
    @XmlElement(name = "defaultName")
    public String getDefaultName() {
        return defaultParameterValue.getName()
    }

    // used when serializing
    @XmlElement(name = "defaultValue")
    public String getDefaultValue() {
        return defaultParameterValue.getValue()
    }
}
