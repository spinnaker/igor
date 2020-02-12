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
package com.netflix.spinnaker.igor.keel;

import java.util.Map;
import retrofit.http.Body;
import retrofit.http.POST;

public interface KeelService {
  /**
   * Events should be sent with this format (inherited from echo events):
   *
   * <pre>{@code
   * [
   *   payload: [
   *     artifacts: List<Artifact>, details: Map
   *   ],
   *   eventName: String
   * ]
   * }</pre>
   */
  @POST("/artifacts/events")
  Void sendArtifactEvent(@Body Map event);
}
