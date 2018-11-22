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
import com.netflix.spinnaker.igor.build.model.GenericGitRevision
import groovy.transform.CompileStatic

import javax.xml.bind.annotation.XmlElement

/**
 * Represents git details
 */
@CompileStatic
class ScmDetails {
    @JacksonXmlElementWrapper(useWrapping = false)
    @XmlElement(name = "action")
    ArrayList<Action> actions

    List<GenericGitRevision> genericGitRevisions() {
        List<GenericGitRevision> genericGitRevisions = new ArrayList<GenericGitRevision>()

        if (actions == null) {
            return null
        }

        for (Action action : actions) {
            if (action?.lastBuiltRevision?.branch?.name) {
                genericGitRevisions.addAll(action.lastBuiltRevision.branch.collect() { Branch branch ->
                    new GenericGitRevision(branch.name, branch.name.split('/').last(), branch.sha1, action.remoteUrl)
                })
            }
        }

        return genericGitRevisions
    }
}

class Action {
    @XmlElement(required = false)
    LastBuiltRevision lastBuiltRevision

    @XmlElement(required = false)
    String remoteUrl
}

class LastBuiltRevision {
    @JacksonXmlElementWrapper(useWrapping = false)
    @XmlElement(name = "branch")
    List<Branch> branch
}

class Branch {
    @XmlElement(required = false)
    String name

    @XmlElement(required = false, name = "SHA1")
    String sha1
}
