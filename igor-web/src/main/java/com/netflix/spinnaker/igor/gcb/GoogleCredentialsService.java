/*
 * Copyright 2019 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.igor.gcb;

import com.google.api.services.cloudbuild.v1.CloudBuildScopes;
import com.google.auth.oauth2.GoogleCredentials;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Factory for calling Google API code to create a GoogleCredentials, either from application
 * default credentials or from a supplied path to a JSON key.
 */
@Component
@ConditionalOnProperty("gcb.enabled")
public class GoogleCredentialsService {
  GoogleCredentials getFromKey(String jsonPath) {
    try {
      InputStream stream = getCredentialAsStream(jsonPath);
      return loadCredential(stream);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  GoogleCredentials getApplicationDefault() {
    try {
      GoogleCredentials credentials = GoogleCredentials.getApplicationDefault();

      return credentials.createScopedRequired()
          ? credentials.createScoped(CloudBuildScopes.all())
          : credentials;
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private InputStream getCredentialAsStream(String jsonPath) {
    try {
      return new FileInputStream(jsonPath);
    } catch (IOException e) {
      throw new IllegalArgumentException(
          String.format("Unable to read credential file: %s", jsonPath), e);
    }
  }

  private GoogleCredentials loadCredential(InputStream stream) throws IOException {
    return GoogleCredentials.fromStream(stream).createScoped(CloudBuildScopes.all());
  }
}
