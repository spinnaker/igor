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

package com.netflix.spinnaker.igor.scm.github.client

import com.netflix.spinnaker.igor.config.GitHubProperties
import com.netflix.spinnaker.igor.scm.AbstractScmMaster
import com.netflix.spinnaker.retrofit.Slf4jRetrofitLogger
import org.springframework.context.annotation.Bean
import retrofit.Endpoints
import retrofit.RequestInterceptor
import retrofit.RestAdapter
import retrofit.client.OkClient
import retrofit.converter.JacksonConverter

import javax.validation.Valid

/**
 * Wrapper class for a collection of GitHub clients
 */
class GitHubMaster  extends AbstractScmMaster {
    GitHubClient gitHubClient
    String baseUrl

    @Bean
    GitHubMaster gitHubMasters(@Valid GitHubProperties gitHubProperties) {
        new GitHubMaster(githubClient : gitHubClient(gitHubProperties.baseUrl, gitHubProperties.accessToken), baseUrl: gitHubProperties.baseUrl)
    }


    GitHubClient gitHubClient(String address, String username, String password) {
        new RestAdapter.Builder()
            .setEndpoint(Endpoints.newFixedEndpoint(address))
            .setRequestInterceptor(new BasicAuthRequestInterceptor(accessToken))
            .setClient(new OkClient())
            .setConverter(new JacksonConverter())
            .setLog(new Slf4jRetrofitLogger(GitHubClient))
            .build()
            .create(GitHubClient)
    }

    static class BasicAuthRequestInterceptor implements RequestInterceptor {

        private final String accessToken

        BasicAuthRequestInterceptor(String accessToken) {
            this.accessToken = accessToken
        }

        @Override
        void intercept(RequestInterceptor.RequestFacade request) {
            request.addQueryParam("access_token", accessToken)
        }
    }
}
