/*
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.igor.config;

import com.netflix.spinnaker.config.OkHttpClientConfiguration;
import com.netflix.spinnaker.igor.IgorConfigurationProperties;
import com.netflix.spinnaker.igor.keel.KeelService;
import com.netflix.spinnaker.retrofit.Slf4jRetrofitLogger;
import com.squareup.okhttp.OkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import retrofit.Endpoints;
import retrofit.RestAdapter;
import retrofit.client.OkClient;

@ConditionalOnProperty("services.keel.base-url")
@Configuration
public class KeelConfig {

  @Bean
  RestAdapter.LogLevel retrofitLogLevel(
      @Value("${retrofit.log-level:BASIC}") String retrofitLogLevel) {
    return RestAdapter.LogLevel.valueOf(retrofitLogLevel);
  }

  @Bean
  KeelService keelService(
      OkHttpClientConfiguration okHttpClientConfig,
      IgorConfigurationProperties igorProperties,
      RestAdapter.LogLevel logLevel) {
    String address = igorProperties.getServices().getKeel().getBaseUrl();

    OkHttpClient client = okHttpClientConfig.create();
    return new RestAdapter.Builder()
        .setEndpoint(Endpoints.newFixedEndpoint(address))
        .setClient(new OkClient(client))
        .setLogLevel(logLevel)
        .setLog(new Slf4jRetrofitLogger(KeelService.class))
        .build()
        .create(KeelService.class);
  }
}
