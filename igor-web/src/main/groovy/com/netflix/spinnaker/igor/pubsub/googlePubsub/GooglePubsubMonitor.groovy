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

package com.netflix.spinnaker.igor.pubsub.googlePubsub

import com.netflix.spinnaker.igor.config.pubsub.google.GooglePubsubProperties
import com.netflix.spinnaker.igor.model.PubsubType
import com.netflix.spinnaker.igor.polling.PollingMonitor
import com.netflix.spinnaker.igor.pubsub.PubsubSubscribers
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.event.ContextRefreshedEvent
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service

import javax.annotation.PreDestroy

/**
 * Monitors Google Cloud Pubsub subscriptions.
 */
@Slf4j
@Service
@Async
@ConditionalOnProperty('pubsub.google.enabled')
class GooglePubsubMonitor implements PollingMonitor {

  Long lastPoll

  @Autowired
  PubsubSubscribers pubsubSubscribers

  @Autowired
  GooglePubsubProperties pubsubProperties

  @PreDestroy
  void closeAsyncConnections() {
    log.info('Closing async connections for Google Pubsub subscribers')
    pubsubSubscribers.filteredMap(PubsubType.GOOGLE).keySet().parallelStream().forEach(
        { subscription -> closeConnection(subscription) }
    )
  }

  @Override
  void onApplicationEvent(ContextRefreshedEvent event) {
    // TODO(jacobkiefer): Register Igor as enabled on startup.
    log.info('Starting async connections for Google Pubsub subscribers')
    pubsubSubscribers.filteredMap(PubsubType.GOOGLE).keySet().parallelStream().forEach(
        { subscription -> openConnection(subscription) }
    )
  }

  void openConnection(String subscription) {
    log.info("Opening async connection to ${subscription}")
    def startTime = System.currentTimeMillis()
    lastPoll = startTime

    GooglePubsubSubscriber subscriber = pubsubSubscribers.get(subscription) as GooglePubsubSubscriber
    subscriber.subscriber.startAsync()
  }

  void closeConnection(String subscription) {
    GooglePubsubSubscriber subscriber = pubsubSubscribers.get(subscription) as GooglePubsubSubscriber
    subscriber.subscriber.stopAsync()
  }

  @Override
  String getName() {
    "GooglePubsubMonitor"
  }

  @Override
  boolean isInService() {
    return true
  }

  @Override
  Long getLastPoll() {
    lastPoll
  }

  @Override
  int getPollInterval() {
    -1
  }
}
