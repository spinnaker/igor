/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.igor.config

import com.netflix.spinnaker.igor.scm.stash.client.StashClient
import com.netflix.spinnaker.igor.scm.stash.client.StashMaster
import com.squareup.okhttp.Credentials
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import retrofit.Endpoints
import retrofit.RequestInterceptor
import retrofit.RestAdapter
import retrofit.client.OkClient
import retrofit.converter.JacksonConverter

import javax.validation.Valid

/**
 * Converts the list of Stash Configuration properties a collection of clients to access the Stash hosts
 */
@Configuration
@ConditionalOnProperty('stash.baseUrl')
@Slf4j
@CompileStatic
@EnableConfigurationProperties(StashProperties)
class StashConfig {

    @Bean
    StashMaster stashMaster(@Valid StashProperties stashProperties) {
        log.info "bootstrapping ${stashProperties.baseUrl} as stash"
        new StashMaster(
            stashClient: stashClient(stashProperties.baseUrl, stashProperties.username, stashProperties.password),
            baseUrl: stashProperties.baseUrl)
    }

    StashClient stashClient(String address, String username, String password) {
        new RestAdapter.Builder()
            .setEndpoint(Endpoints.newFixedEndpoint(address))
            .setRequestInterceptor(new BasicAuthRequestInterceptor(username, password))
            .setClient(new OkClient())
            .setConverter(new JacksonConverter())
            .build()
            .create(StashClient)
    }

    static class BasicAuthRequestInterceptor implements RequestInterceptor {

        private final String username
        private final String password

        BasicAuthRequestInterceptor(String username, String password) {
            this.username = username
            this.password = password
        }

        @Override
        void intercept(RequestInterceptor.RequestFacade request) {
            request.addHeader("Authorization", Credentials.basic(username, password))
        }
    }

}
