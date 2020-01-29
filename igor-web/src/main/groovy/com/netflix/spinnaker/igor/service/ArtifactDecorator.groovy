/*
 * Copyright 2017 Schibsted ASA.
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

package com.netflix.spinnaker.igor.service

import com.netflix.spinnaker.igor.build.artifact.decorator.ArtifactDetailsDecorator
import com.netflix.spinnaker.igor.build.artifact.decorator.ConfigurableFileDecorator
import com.netflix.spinnaker.igor.build.model.GenericArtifact
import com.netflix.spinnaker.igor.build.model.GenericBuild
import com.netflix.spinnaker.igor.config.ArtifactDecorationProperties
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

import javax.validation.Valid

@Slf4j
@Component
@ConditionalOnProperty('artifact.decorator.enabled')
class ArtifactDecorator {

    List<ArtifactDetailsDecorator> artifactDetailsDecorators;

    @Autowired(required = false)
    ArtifactDecorationProperties artifactDecorationProperties

    @Autowired
    ArtifactDecorator(List<ArtifactDetailsDecorator> artifactDetailsDecorators, @Valid ArtifactDecorationProperties artifactDecorationProperties) {
        this.artifactDetailsDecorators = artifactDetailsDecorators
        List<ArtifactDetailsDecorator> configuredArtifactDetailsDecorators = artifactDecorationProperties?.fileDecorators?.collect { ArtifactDecorationProperties.FileDecorator fileDecorator ->
            log.info "Configuring custom artifact decorator of type : ${fileDecorator.type}"
            (ArtifactDetailsDecorator) new ConfigurableFileDecorator(fileDecorator.type, fileDecorator.decoratorRegex, fileDecorator.identifierRegex)
        }
        if (configuredArtifactDetailsDecorators) {
            this.artifactDetailsDecorators.addAll(0, configuredArtifactDetailsDecorators)
        }
    }

    List<GenericArtifact> decorate(GenericArtifact genericArtifact) {
        List<ArtifactDetailsDecorator> filteredDecorators = artifactDetailsDecorators.findAll { it.handles(genericArtifact) }
        if (!filteredDecorators || genericArtifact.isDecorated()) {
            return [genericArtifact]
        }
        if (filteredDecorators.size() > 1) {
            // We want to be able to define multiple decorators of the same type, but we also want to be able to
            // override the built in decorators, so if any custom decorators are found, remove the ones built in.
            filteredDecorators = filteredDecorators.findAll { it instanceof ConfigurableFileDecorator }
        }
        filteredDecorators.collect {
            log.debug "Decorated artifact with decorator [${it.decoratorName()}]: ${genericArtifact.toString()}"
            def decoratedArtifact = it.decorate(genericArtifact)
            decoratedArtifact.decorated = true
            decoratedArtifact
        }
    }

    void decorate(GenericBuild genericBuild) {
        genericBuild.artifacts = genericBuild.artifacts?.collectMany {
            decorate(it)
        }
    }
}
