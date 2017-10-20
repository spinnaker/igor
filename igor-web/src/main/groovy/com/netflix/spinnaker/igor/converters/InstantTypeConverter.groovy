/*
 * Copyright 2017 Schibsted ASA.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
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

package com.netflix.spinnaker.igor.converters

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.SerializerProvider
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import org.springframework.boot.jackson.JsonComponent

import java.lang.reflect.Type
import java.time.Instant

/**
 * Jackson/GSON frankenstein converter class that converts {@link Instant} to/from ISO-8601
 */
@JsonComponent
class InstantTypeConverter implements JsonSerializer<Instant>, JsonDeserializer<Instant> {
  @Override
  JsonElement serialize(Instant src, Type srcType, JsonSerializationContext context) {
    return new JsonPrimitive(src.toString())
  }

  @Override
  Instant deserialize(JsonElement json, Type type, JsonDeserializationContext context) throws JsonParseException {
    return Instant.parse(json.getAsString())
  }

  static class Serializer extends com.fasterxml.jackson.databind.JsonSerializer<Instant> {
    @Override
    void serialize(Instant value, JsonGenerator gen, SerializerProvider serializers) throws IOException, JsonProcessingException {
      gen.writeString(value.toString())
    }
  }

  static class Deserializer extends com.fasterxml.jackson.databind.JsonDeserializer<Instant> {
    @Override
    Instant deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JsonProcessingException {
      return Instant.parse(p.currentToken().asString())
    }
  }
}
