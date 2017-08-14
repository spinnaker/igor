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

package com.netflix.spinnaker.igor.pubsub

import com.netflix.spinnaker.igor.IgorConfigurationProperties
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import redis.clients.jedis.JedisPool

/**
 * Shared cache of received and handled pubsub message to synchronize clients.
 */
@Service
class PubsubMessageCache {
  @Autowired
  JedisPool jedisPool

  @Autowired
  IgorConfigurationProperties igorConfigurationProperties

  // TODO(jacobkiefer): This is a temporary in-memory store for handled
  // messages before we implement the distributed Redis solution.
  // This will function properly only with exactly one Igor instance.
  private HashSet<String> handledMessages = new HashSet<String>()

  String getPrefix() {
    igorConfigurationProperties.spinnaker.jedis.prefix
  }

  Boolean handleMessage(String messageKey) {
    if (handledMessages.contains(messageKey)) {
      // Message has already been processed.
      return false
    } else {
      return handledMessages.add(messageKey)
    }
  }
}
