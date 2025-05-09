/*
 * Copyright 2025 OpsMx, Inc.
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

package com.netflix.spinnaker.igor

import com.netflix.spectator.api.NoopRegistry
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.igor.config.JenkinsConfig
import com.netflix.spinnaker.igor.config.client.JenkinsOkHttpClientProvider
import com.netflix.spinnaker.igor.service.BuildServices
import com.netflix.spinnaker.okhttp.Retrofit2EncodeCorrectionInterceptor
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.github.resilience4j.circuitbreaker.internal.InMemoryCircuitBreakerRegistry;
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.task.TaskExecutorBuilder;
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Bean;
import com.netflix.spinnaker.config.okhttp3.RawOkHttpClientConfiguration;
import com.netflix.spinnaker.igor.config.JenkinsProperties
import com.netflix.spinnaker.config.OkHttpClientComponents
import retrofit.RestAdapter
import spock.lang.Specification;

@SpringBootTest(classes = [JenkinsConfig, RawOkHttpClientConfiguration, OkHttpClientComponents, TestConfiguration],
    properties = ["jenkins.enabled=true"])
class JenkinsClientSpec extends Specification {

  @Autowired
  JenkinsOkHttpClientProvider jenkinsOkHttpClientProvider

  @Autowired
  JenkinsProperties.JenkinsHost jenkinsHost

  def "test if jenkins retrofit1 client has retrofit2 interceptor - Retrofit2EncodeCorrectionInterceptor"() {
    when:
    def client = jenkinsOkHttpClientProvider.provide(jenkinsHost)
    def interceptors = client.interceptors()

    then:
    interceptors.size() != 0
    //TODO: Fix this. Retrofit2EncodeCorrectionInterceptor should not be added to a retrofit1 client
    interceptors.any { it instanceof Retrofit2EncodeCorrectionInterceptor }
  }
}

class TestConfiguration {
  @Bean
  TaskExecutorBuilder taskExecutorBuilder() {
    return new TaskExecutorBuilder()
  }

  @Bean
  JenkinsProperties.JenkinsHost jenkinsHost() {
    def host = new JenkinsProperties.JenkinsHost()
    host.address = "http://localhost:8080"
    host.name = "jenkins-dev"
    return host
  }

  @Bean
  JenkinsProperties jenkinsProperties(JenkinsProperties.JenkinsHost jenkinsHost) {
    def jenkinsProperties = new JenkinsProperties()
    jenkinsProperties.masters = [jenkinsHost]
    return jenkinsProperties
  }

  @Bean
  BuildServices buildServices() {
    def buildServices = new BuildServices()
    return buildServices
  }

  @Bean
  IgorConfigurationProperties igorConfigurationProperties() {
    new IgorConfigurationProperties()
  }

  @Bean
  Registry registry() {
    new NoopRegistry()
  }

  @Bean
  CircuitBreakerRegistry circuitBreakerRegistry() {
    new InMemoryCircuitBreakerRegistry()
  }

  @Bean
  RestAdapter.LogLevel retrofitLogLevel() {
    return RestAdapter.LogLevel.BASIC
  }
}
