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
import groovy.util.logging.Slf4j

import java.security.MessageDigest

@Slf4j
class GooglePubsubSubscriber implements PubsubSubscriber {
  String name
  String project
  Subscriber subscriber
  Integer ackDeadlineSeconds


  @Override
  PubsubType pubsubType() {
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

    private MessageDigest digest = MessageDigest.getInstance("SHA-256")

    @Override
    void receiveMessage(PubsubMessage message, AckReplyConsumer consumer) {
      String messagePayload = message.data.toStringUtf8()
      log.debug("Received message with payload: ${messagePayload}")
      String messageKey = makeKey(pubsubMessageCache.prefix, messagePayload)
      if (pubsubMessageCache.handleMessage(messageKey)) {
        consumer.ack()
        postEvent(message)
      } else {
        consumer.nack()
      }
    }

    private String makeKey(String prefix, String messagePayload) {
      digest.reset()
      digest.update(messagePayload.bytes)
      String messageHash = new String(digest.digest())
      return "${prefix}:googlePubsub:${subscriptionName}:${messageHash}"
    }

    void postEvent(PubsubMessage message) {
      if (echoService) {
        log.info("Posted message: ${message.data.toStringUtf8()} to Echo")
//        echoService.postEvent(
//            new PubsubEvent(payload: message.data.toStringUtf8())
//        )
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
