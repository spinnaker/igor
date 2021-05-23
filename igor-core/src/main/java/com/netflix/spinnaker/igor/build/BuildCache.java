/*
 * Copyright 2017 Netflix, Inc.
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
package com.netflix.spinnaker.igor.build;

import com.netflix.spinnaker.igor.IgorConfigurationProperties;
import com.netflix.spinnaker.kork.jedis.RedisClientDelegate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Shared cache of build details */
@Service
public class BuildCache {

  private static final String ID = "builds";

  private final RedisClientDelegate redisClientDelegate;
  private final IgorConfigurationProperties igorConfigurationProperties;

  @Autowired
  public BuildCache(
      RedisClientDelegate redisClientDelegate,
      IgorConfigurationProperties igorConfigurationProperties) {
    this.redisClientDelegate = redisClientDelegate;
    this.igorConfigurationProperties = igorConfigurationProperties;
  }

  public List<String> getJobNames(String controller) {
    List<String> jobs = new ArrayList<>();
    redisClientDelegate.withKeyScan(
        baseKey() + ":completed:" + controller + ":*",
        1000,
        page ->
            jobs.addAll(
                page.getResults().stream()
                    .map(BuildCache::extractJobName)
                    .collect(Collectors.toList())));
    jobs.sort(Comparator.naturalOrder());
    return jobs;
  }

  public List<String> getTypeaheadResults(String search) {
    var results = new ArrayList<String>();
    redisClientDelegate.withKeyScan(
        baseKey() + ":*:*:*" + search.toUpperCase() + "*:*",
        1000,
        page ->
            results.addAll(
                page.getResults().stream()
                    .map(BuildCache::extractTypeaheadResult)
                    .collect(Collectors.toList())));
    results.sort(Comparator.naturalOrder());
    return results;
  }

  public int getLastBuild(String controller, String job, boolean running) {
    String key = makeKey(controller, job, running);
    return redisClientDelegate.withCommandsClient(
        c -> {
          if (!c.exists(key)) {
            return -1;
          }
          return Integer.parseInt(c.get(key));
        });
  }

  public Long getTTL(String controller, String job) {
    final String key = makeKey(controller, job);
    return getTTL(key);
  }

  private Long getTTL(String key) {
    return redisClientDelegate.withCommandsClient(
        c -> {
          return c.ttl(key);
        });
  }

  public void setTTL(String key, int ttl) {
    redisClientDelegate.withCommandsClient(
        c -> {
          c.expire(key, ttl);
        });
  }

  public void setLastBuild(
      String controller, String job, int lastBuild, boolean building, int ttl) {
    if (!building) {
      setBuild(makeKey(controller, job), lastBuild, false, controller, job, ttl);
    }
    storeLastBuild(makeKey(controller, job, building), lastBuild, ttl);
  }

  public List<String> getDeprecatedJobNames(String controller) {
    List<String> jobs = new ArrayList<>();
    redisClientDelegate.withKeyScan(
        baseKey() + ":" + controller + ":*",
        1000,
        page ->
            jobs.addAll(
                page.getResults().stream()
                    .map(BuildCache::extractDeprecatedJobName)
                    .collect(Collectors.toList())));
    jobs.sort(Comparator.naturalOrder());
    return jobs;
  }

  public Map<String, Object> getDeprecatedLastBuild(String controller, String job) {
    String key = makeKey(controller, job);
    Map<String, String> result =
        redisClientDelegate.withCommandsClient(
            c -> {
              if (!c.exists(key)) {
                return null;
              }
              return c.hgetAll(key);
            });

    if (result == null) {
      return new HashMap<>();
    }

    Map<String, Object> converted = new HashMap<>();
    converted.put("lastBuildLabel", Integer.parseInt(result.get("lastBuildLabel")));
    converted.put("lastBuildBuilding", Boolean.valueOf(result.get("lastBuildBuilding")));

    return converted;
  }

  public List<Map<String, String>> getTrackedBuilds(String controller) {
    List<Map<String, String>> builds = new ArrayList<>();
    redisClientDelegate.withKeyScan(
        baseKey() + ":track:" + controller + ":*",
        1000,
        page ->
            builds.addAll(
                page.getResults().stream()
                    .map(BuildCache::getTrackedBuild)
                    .collect(Collectors.toList())));
    return builds;
  }

  public void setTracking(String controller, String job, int buildId, int ttl) {
    String key = makeTrackKey(controller, job, buildId);
    redisClientDelegate.withCommandsClient(
        c -> {
          c.set(key, "marked as running");
        });
    setTTL(key, ttl);
  }

  public void deleteTracking(String controller, String job, int buildId) {
    String key = makeTrackKey(controller, job, buildId);
    redisClientDelegate.withCommandsClient(
        c -> {
          c.del(key);
        });
  }

  private static Map<String, String> getTrackedBuild(String key) {
    Map<String, String> build = new HashMap<>();
    build.put("job", extractJobName(key));
    build.put("buildId", extractBuildIdFromTrackingKey(key));
    return build;
  }

  private void setBuild(
      String key, int lastBuild, boolean building, String controller, String job, int ttl) {
    redisClientDelegate.withCommandsClient(
        c -> {
          c.hset(key, "lastBuildLabel", Integer.toString(lastBuild));
          c.hset(key, "lastBuildBuilding", Boolean.toString(building));
        });
    setTTL(key, ttl);
  }

  private void storeLastBuild(String key, int lastBuild, int ttl) {
    redisClientDelegate.withCommandsClient(
        c -> {
          c.set(key, Integer.toString(lastBuild));
        });
    setTTL(key, ttl);
  }

  protected String makeKey(String controller, String job) {
    return baseKey() + ":" + controller + ":" + job.toUpperCase() + ":" + job;
  }

  protected String makeKey(String controller, String job, boolean running) {
    String buildState = running ? "running" : "completed";
    return baseKey() + ":" + buildState + ":" + controller + ":" + job.toUpperCase() + ":" + job;
  }

  protected String makeTrackKey(String controller, String job, int buildId) {
    return baseKey() + ":track:" + controller + ":" + job.toUpperCase() + ":" + job + ":" + buildId;
  }

  private static String extractJobName(String key) {
    return key.split(":")[5];
  }

  private static String extractBuildIdFromTrackingKey(String key) {
    return key.split(":")[6];
  }

  private static String extractDeprecatedJobName(String key) {
    return key.split(":")[4];
  }

  private static String extractTypeaheadResult(String key) {
    String[] parts = key.split(":");
    return parts[3] + ":" + parts[5];
  }

  private String baseKey() {
    return igorConfigurationProperties.getSpinnaker().getJedis().getPrefix() + ":" + ID;
  }
}
