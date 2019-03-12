/*
 * Copyright 2016 Schibsted ASA.
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
package com.netflix.spinnaker.igor.service;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.netflix.spinnaker.fiat.model.resources.Permissions;
import com.netflix.spinnaker.igor.model.BuildServiceProvider;

public interface BuildService {
    String getName();

    BuildServiceProvider getBuildServiceProvider();

    Permissions getPermissions();

    @JsonIgnore
    default BuildServiceView getView() {
        return new BuildServiceView(this);
    }

    class BuildServiceView implements BuildService {
        final String name;
        final BuildServiceProvider buildServiceProvider;
        final Permissions permissions;

        private BuildServiceView(BuildService buildService) {
            this.name = buildService.getName();
            this.buildServiceProvider = buildService.getBuildServiceProvider();
            this.permissions = buildService.getPermissions();
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public BuildServiceProvider getBuildServiceProvider() {
            return buildServiceProvider;
        }

        @Override
        public Permissions getPermissions() {
            return permissions;
        }
    }
}
