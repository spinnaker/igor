/*
 * Copyright (c) 2017, 2018, Oracle Corporation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.netflix.spinnaker.igor.wercker;

import static com.netflix.spinnaker.igor.wercker.model.Run.startedAtComparator;

import com.netflix.spinnaker.igor.IgorConfigurationProperties;
import com.netflix.spinnaker.igor.wercker.model.Run;
import com.netflix.spinnaker.kork.jedis.RedisClientDelegate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Shared cache of build details for jenkins */
@Service
public class WerckerCache {

  private static final String POLL_STAMP = "lastPollCycleTimestamp";
  private static final String PIPELINE_ID = "pipelineId";
  private static final String PIPELINE_NAME = "pipelineName";

  private final RedisClientDelegate redisClientDelegate;
  private final IgorConfigurationProperties igorConfigurationProperties;

  @Autowired
  public WerckerCache(
      RedisClientDelegate redisClientDelegate,
      IgorConfigurationProperties igorConfigurationProperties) {
    this.redisClientDelegate = redisClientDelegate;
    this.igorConfigurationProperties = igorConfigurationProperties;
  }

  public void setLastPollCycleTimestamp(String controller, String pipeline, Long timestamp) {
    String key = makeKey(controller, pipeline);
    redisClientDelegate.withCommandsClient(
        c -> {
          c.hset(key, POLL_STAMP, Long.toString(timestamp));
        });
  }

  public Long getLastPollCycleTimestamp(String controller, String pipeline) {
    return redisClientDelegate.withCommandsClient(
        c -> {
          String ts = c.hget(makeKey(controller, pipeline), POLL_STAMP);
          return ts == null ? null : Long.parseLong(ts);
        });
  }

  public String getPipelineID(String controller, String pipeline) {
    return redisClientDelegate.withCommandsClient(
        c -> {
          return c.hget(makeKey(controller, pipeline), PIPELINE_ID);
        });
  }

  public String getPipelineName(String controller, String id) {
    return redisClientDelegate.withCommandsClient(
        c -> {
          return c.hget(nameKey(controller, id), PIPELINE_NAME);
        });
  }

  public void setPipelineID(String controller, String pipeline, String id) {
    String key = makeKey(controller, pipeline);
    redisClientDelegate.withCommandsClient(
        c -> {
          c.hset(key, PIPELINE_ID, id);
        });

    String nameKey = nameKey(controller, id);
    redisClientDelegate.withCommandsClient(
        c -> {
          c.hset(nameKey, PIPELINE_NAME, pipeline);
        });
  }

  public String getRunID(String controller, String pipeline, final int buildNumber) {
    String key = makeKey(controller, pipeline) + ":runs";
    final Map<String, String> existing =
        redisClientDelegate.withCommandsClient(
            c -> {
              if (!c.exists(key)) {
                return null;
              }
              return c.hgetAll(key);
            });
    String build = Integer.toString(buildNumber);
    for (Entry<String, String> entry : existing.entrySet()) {
      if (entry.getValue().equals(build)) {
        return entry.getKey();
      }
    }
    return null;
  }

  /**
   * Creates entries in Redis for each run in the runs list (except if the run id already exists)
   * and generates build numbers for each run id (ordered by startedAt date)
   *
   * @param controller
   * @param appAndPipelineName
   * @param runs
   * @return a map containing the generated build numbers for each run created, keyed by run id
   */
  public Map<String, Integer> updateBuildNumbers(
      String controller, String appAndPipelineName, List<Run> runs) {
    String key = makeKey(controller, appAndPipelineName) + ":runs";
    final Map<String, String> existing =
        redisClientDelegate.withCommandsClient(
            c -> {
              if (!c.exists(key)) {
                return null;
              }
              return c.hgetAll(key);
            });
    List<Run> newRuns = runs;
    if (existing != null && existing.size() > 0) {
      newRuns =
          runs.stream()
              .filter(run -> !existing.containsKey(run.getId()))
              .collect(Collectors.toList());
    }
    Map<String, Integer> runIdToBuildNumber = new HashMap<>();
    int startNumber = (existing == null || existing.size() == 0) ? 0 : existing.size();
    newRuns.sort(startedAtComparator);
    for (int i = 0; i < newRuns.size(); i++) {
      int buildNum = startNumber + i;
      setBuildNumber(controller, appAndPipelineName, newRuns.get(i).getId(), buildNum);
      runIdToBuildNumber.put(newRuns.get(i).getId(), buildNum);
    }
    return runIdToBuildNumber;
  }

  public void setBuildNumber(String controller, String pipeline, String runID, int number) {
    String key = makeKey(controller, pipeline) + ":runs";
    redisClientDelegate.withCommandsClient(
        c -> {
          c.hset(key, runID, Integer.toString(number));
        });
  }

  public Long getBuildNumber(String controller, String pipeline, String runID) {
    return redisClientDelegate.withCommandsClient(
        c -> {
          String ts = c.hget(makeKey(controller, pipeline) + ":runs", runID);
          return ts == null ? null : Long.parseLong(ts);
        });
  }

  public Boolean getEventPosted(String controller, String job, String runID) {
    String key = makeEventsKey(controller, job);
    return redisClientDelegate.withCommandsClient(c -> c.hget(key, runID) != null);
  }

  public void setEventPosted(String controller, String job, String runID) {
    String key = makeEventsKey(controller, job);
    redisClientDelegate.withCommandsClient(
        c -> {
          c.hset(key, runID, "POSTED");
        });
  }

  public void pruneOldMarkers(String controller, String job, Long cursor) {
    remove(controller, job);
    redisClientDelegate.withCommandsClient(
        c -> {
          c.del(makeEventsKey(controller, job));
        });
  }

  public void remove(String controller, String job) {
    redisClientDelegate.withCommandsClient(
        c -> {
          c.del(makeKey(controller, job));
        });
  }

  private String makeEventsKey(String controller, String job) {
    return makeKey(controller, job) + ":" + POLL_STAMP + ":events";
  }

  private String makeKey(String controller, String job) {
    return prefix() + ":" + controller + ":" + job.toUpperCase() + ":" + job;
  }

  private String nameKey(String controller, String pipelineId) {
    return prefix() + ":" + controller + ":all_pipelines:" + pipelineId;
  }

  private String prefix() {
    return igorConfigurationProperties.getSpinnaker().getJedis().getPrefix() + "_wercker";
  }
}
