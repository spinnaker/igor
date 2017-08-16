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

import com.google.api.core.ApiService
import com.google.api.gax.core.FixedCredentialsProvider
import com.google.auth.Credentials
import com.google.auth.oauth2.ServiceAccountCredentials
import com.google.cloud.pubsub.v1.AckReplyConsumer
import com.google.cloud.pubsub.v1.MessageReceiver
import com.google.cloud.pubsub.v1.Subscriber
import com.google.common.util.concurrent.MoreExecutors
import com.google.pubsub.v1.PubsubMessage
import com.google.pubsub.v1.SubscriptionName
import com.netflix.spinnaker.igor.history.EchoService
import com.netflix.spinnaker.igor.history.model.PubsubEvent
import com.netflix.spinnaker.igor.model.PubsubType
import com.netflix.spinnaker.igor.pubsub.PubsubMessageCache
import com.netflix.spinnaker.igor.pubsub.PubsubSubscriber
import com.netflix.spinnaker.igor.utils.NodeIdentity
import groovy.util.logging.Slf4j
import org.threeten.bp.Duration

import java.util.concurrent.TimeUnit

@Slf4j
class GooglePubsubSubscriber extends PubsubSubscriber {
  String name
  String project
  Subscriber subscriber
  Integer ackDeadlineSeconds


  @Override
  static PubsubType pubsubType() {
    return PubsubType.GOOGLE
  }

  static GooglePubsubSubscriber buildSubscriber(String name,
                                                String project,
                                                String jsonPath,
                                                Integer ackDeadlineSeconds,
                                                EchoService echoService,
                                                PubsubMessageCache pubsubMessageCache) {
    Subscriber subscriber
    GooglePubsubMessageReceiver messageReceiver = new GooglePubsubMessageReceiver(
        ackDeadlineSeconds: ackDeadlineSeconds,
        echoService: echoService,
        subscriptionName: name,
        pubsubMessageCache: pubsubMessageCache
    )

    if (jsonPath) {
      Credentials credentials = ServiceAccountCredentials.fromStream(new FileInputStream(jsonPath))
      subscriber = Subscriber
          .defaultBuilder(SubscriptionName.create(project, name), messageReceiver)
          .setCredentialsProvider(FixedCredentialsProvider.create(credentials))
          .setMaxAckExtensionPeriod(Duration.ofSeconds(0)) // Disables automatic deadline extensions for liveness.
          .build()
    } else {
      subscriber = Subscriber
          .defaultBuilder(SubscriptionName.create(project, name), messageReceiver)
          .build()
    }

    subscriber.addListener(new GooglePubsubFailureHandler(), MoreExecutors.directExecutor())

    return new GooglePubsubSubscriber(
        name: name,
        project: project,
        subscriber: subscriber,
        ackDeadlineSeconds: ackDeadlineSeconds
    )
  }

  static class GooglePubsubMessageReceiver implements MessageReceiver {

    EchoService echoService

    PubsubMessageCache pubsubMessageCache

    Integer ackDeadlineSeconds

    String subscriptionName

    NodeIdentity identity = new NodeIdentity()

    @Override
    void receiveMessage(PubsubMessage message, AckReplyConsumer consumer) {
      String messagePayload = message.data.toStringUtf8()
      log.debug("Received message with payload: ${messagePayload}")
      String messageKey = pubsubMessageCache.makeKey(messagePayload, pubsubType(), subscriptionName)
      // Acquire lock and set a high upper bound on message processing time.
      if (!pubsubMessageCache.acquireMessageLock(messageKey, identity.identity, 5 * TimeUnit.SECONDS.toMillis(ackDeadlineSeconds))) {
        consumer.nack()
        return
      }

      consumer.ack()
      postEvent(message)
      // Expire key after max retention time, which is 7 days.
      // See https://cloud.google.com/pubsub/docs/subscriber for details.
      pubsubMessageCache.setMessageHandled(messageKey, identity.identity, TimeUnit.DAYS.toMillis(7))
    }

    void postEvent(PubsubMessage message) {
      if (echoService) {
        log.info("Posted message: ${message.data.toStringUtf8()} as an Event to Echo")
        echoService.postEvent(
            new PubsubEvent(payload: message.data.toStringUtf8())
        )
      }
    }
  }

  static class GooglePubsubFailureHandler extends ApiService.Listener {
    @Override
    void failed(ApiService.State from, Throwable failure) {
      throw failure
    }
  }
}
