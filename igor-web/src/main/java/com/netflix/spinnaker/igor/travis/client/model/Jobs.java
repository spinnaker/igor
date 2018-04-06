/*
 * Copyright 2018 Schibsted ASA.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
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

package com.netflix.spinnaker.igor.travis.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.simpleframework.xml.Default;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Root;

@Default
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@Root(strict = false)
public class Jobs {
    @ElementList(required = false, name = "job", inline = true)
    private Job job;

    public Job getJob() {
        return job;
    }

    public void setJob(Job job) {
        this.job = job;
    }
}
