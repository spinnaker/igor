/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.spinnaker.igor.config.pubsub.google

import com.netflix.spinnaker.igor.history.EchoService
import com.netflix.spinnaker.igor.pubsub.PubsubMessageCache
import com.netflix.spinnaker.igor.pubsub.PubsubSubscribers
import com.netflix.spinnaker.igor.pubsub.googlePubsub.GooglePubsubSubscriber
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

import javax.validation.Valid

@Configuration
@Slf4j
@CompileStatic
@ConditionalOnProperty("googlePubsub.enabled")
@EnableConfigurationProperties(GooglePubsubProperties)
class GooglePubsubConfig {

  @Bean
  Map<String, GooglePubsubSubscriber> googlePubsubSubscribers(PubsubSubscribers pubsubSubscribers,
                                                              EchoService echoService,
                                                              PubsubMessageCache pubsubMessageCache,
                                                              @Valid GooglePubsubProperties googlePubsubProperties) {
    log.info("Creating Google Pubsub Subscribers")
    Map<String, GooglePubsubSubscriber> subscriberMap = googlePubsubProperties?.subscriptions?.collectEntries { GooglePubsubProperties.GooglePubsubSubscription subscription ->
      log.info "Bootstrapping Google Pubsub Subscriber listening to topic: ${subscription.name} in project: ${subscription.project}"
      GooglePubsubSubscriber subscriber = GooglePubsubSubscriber
          .buildSubscriber(subscription.name, subscription.project, subscription.jsonPath, subscription.ackDeadlineSeconds, echoService, pubsubMessageCache)
      [("projects/${subscription.project}/topics/${subscription.name}".toString()): subscriber]
    }

    pubsubSubscribers.map.putAll subscriberMap
    subscriberMap
  }
}
