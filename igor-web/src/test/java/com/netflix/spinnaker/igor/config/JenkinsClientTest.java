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

package com.netflix.spinnaker.igor.config;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import com.netflix.spectator.api.NoopRegistry;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.config.OkHttp3ClientConfiguration;
import com.netflix.spinnaker.config.OkHttpClientComponents;
import com.netflix.spinnaker.igor.IgorConfigurationProperties;
import com.netflix.spinnaker.igor.config.client.JenkinsOkHttpClientProvider;
import com.netflix.spinnaker.igor.service.BuildServices;
import com.netflix.spinnaker.okhttp.OkHttpClientConfigurationProperties;
import com.netflix.spinnaker.okhttp.Retrofit2EncodeCorrectionInterceptor;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.circuitbreaker.internal.InMemoryCircuitBreakerRegistry;
import java.util.List;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.task.TaskExecutorBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import retrofit.RestAdapter;

@SpringBootTest(
    classes = {
      JenkinsConfig.class,
      OkHttp3ClientConfiguration.class,
      OkHttpClientComponents.class,
      JenkinsClientTest.TestConfiguration.class,
      TaskExecutorBuilder.class
    },
    properties = {"jenkins.enabled=true"})
public class JenkinsClientTest {

  @Autowired private JenkinsOkHttpClientProvider jenkinsOkHttpClientProvider;

  @Autowired private JenkinsProperties.JenkinsHost jenkinsHost;

  @Test
  public void test_if_Jenkins_Retrofit1_client_Has_Retrofit2Interceptor() {
    OkHttpClient client = jenkinsOkHttpClientProvider.provide(jenkinsHost);
    assertThat(client.interceptors())
        .noneMatch(interceptor -> interceptor instanceof Retrofit2EncodeCorrectionInterceptor);
  }

  static class TestConfiguration {
    @Bean
    public JenkinsProperties.JenkinsHost jenkinsHost() {
      JenkinsProperties.JenkinsHost jenkinsHost = new JenkinsProperties.JenkinsHost();
      jenkinsHost.setAddress("http://localhost:8080");
      jenkinsHost.setName("jenkins-dev");
      return jenkinsHost;
    }

    @Bean
    public JenkinsProperties jenkinsProperties(JenkinsProperties.JenkinsHost jenkinsHost) {
      JenkinsProperties jenkinsProperties = new JenkinsProperties();
      jenkinsProperties.setMasters(List.of(jenkinsHost));
      return jenkinsProperties;
    }

    @Bean
    public BuildServices buildServices() {
      return Mockito.mock(BuildServices.class);
    }

    @Bean
    public HttpLoggingInterceptor.Level retrofitInterceptorLogLevel() {
      return HttpLoggingInterceptor.Level.BODY;
    }

    @Primary
    @Bean
    public OkHttpClientConfigurationProperties okHttpClientConfigurationProperties() {
      return new OkHttpClientConfigurationProperties();
    }

    @Bean
    public IgorConfigurationProperties igorConfigurationProperties() {
      return new IgorConfigurationProperties();
    }

    @Bean
    Registry registry() {
      return new NoopRegistry();
    }

    @Bean
    CircuitBreakerRegistry circuitBreakerRegistry() {
      return new InMemoryCircuitBreakerRegistry();
    }

    @Bean
    RestAdapter.LogLevel retrofitLogLevel() {
      return RestAdapter.LogLevel.BASIC;
    }
  }
}
