/*
 * Copyright 2014 Netflix, Inc.
 * Copyright (c) 2017, 2018, Oracle Corporation and/or its affiliates. All rights reserved.
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

import retrofit.http.GET
import retrofit.http.Path
import retrofit.http.Query

/**
 * Posts new build executions to echo
 */
interface Front50Service {
    //
    // Pipeline-related
    //
    @GET('/pipelines')
    List<Map> getAllPipelineConfigs()

    @GET('/pipelines/{app}')
    List<Map> getPipelineConfigsForApplication(@Path("app") String app, @Query("refresh") boolean refresh)
}
