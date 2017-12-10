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
package com.netflix.spinnaker.igor.jenkins;

import com.netflix.spinnaker.igor.AbstractRedisCache;
import com.netflix.spinnaker.igor.IgorConfigurationProperties;
import com.netflix.spinnaker.kork.jedis.RedisClientDelegate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Shared cache of build details for jenkins
 */
@Service
public class JenkinsCache extends AbstractRedisCache {

    private static final String POLL_STAMP = "lastPollCycleTimestamp";

    private final IgorConfigurationProperties igorConfigurationProperties;

    @Autowired
    public JenkinsCache(RedisClientDelegate redisClientDelegate,
                        IgorConfigurationProperties igorConfigurationProperties) {
        super(redisClientDelegate);
        this.igorConfigurationProperties = igorConfigurationProperties;
    }

    public List<String> getJobNames(String master) {
        List<String> jobs = scanAll(prefix() + ":" + master + ":*")
            .stream()
            .map(JenkinsCache::extractJobName)
            .collect(Collectors.toList());
        jobs.sort(Comparator.naturalOrder());
        return jobs;
    }

    public List<String> getTypeaheadResults(String search) {
        List<String> results = scanAll(prefix() + ":*:*" + search.toUpperCase() + "*:*")
            .stream()
            .map(JenkinsCache::extractTypeaheadResult)
            .collect(Collectors.toList());
        results.sort(Comparator.naturalOrder());
        return results;
    }

    public Map getLastBuild(String master, String job) {
        String key = makeKey(master, job);
        Map<String, String> result = redisClientDelegate.withCommandsClient(c -> {
            if (!c.exists(key)) {
                return new HashMap<>();
            }
            return c.hgetAll(key);
        });

        if (result.isEmpty()) {
            return result;
        }

        Map<String, Object> converted = new HashMap<>();
        converted.put("lastBuildLabel", Integer.parseInt(result.get("lastBuildLabel")));
        converted.put("lastBuildBuilding", Boolean.valueOf(result.get("lastBuildBuilding")));

        return converted;
    }

    public void setLastBuild(String master, String job, int lastBuild, boolean building) {
        String key = makeKey(master, job);
        redisClientDelegate.withCommandsClient(c -> {
            c.hset(key, "lastBuildLabel", Integer.toString(lastBuild));
            c.hset(key, "lastBuildBuilding", Boolean.toString(building));
        });
    }

    public void setLastPollCycleTimestamp(String master, String job, Long timestamp) {
        String key = makeKey(master, job);
        redisClientDelegate.withCommandsClient(c -> {
            c.hset(key, POLL_STAMP, Long.toString(timestamp));
        });
    }

    public Long getLastPollCycleTimestamp(String master, String job) {
        return redisClientDelegate.withCommandsClient(c -> {
            String ts = c.hget(makeKey(master, job), POLL_STAMP);
            return ts == null ? null : Long.parseLong(ts);
        });
    }

    public Boolean getEventPosted(String master, String job, Long cursor, Integer buildNumber) {
        String key = makeKey(master, job) + ":" + POLL_STAMP + ":" + cursor;
        return redisClientDelegate.withCommandsClient(c -> c.hget(key, Integer.toString(buildNumber)) != null);
    }

    public void setEventPosted(String master, String job, Long cursor, Integer buildNumber) {
        String key = makeKey(master, job) + ":" + POLL_STAMP + ":" + cursor;
        redisClientDelegate.withCommandsClient(c -> {
            c.hset(key, Integer.toString(buildNumber), "POSTED");
        });
    }

    public void pruneOldMarkers(String master, String job, Long cursor) {
        remove(master, job);
        redisClientDelegate.withCommandsClient(c -> {
            c.del(makeKey(master, job) + ":" + POLL_STAMP + ":" + cursor);
        });
    }

    public void remove(String master, String job) {
        redisClientDelegate.withCommandsClient(c -> {
            c.del(makeKey(master, job));
        });
    }

    private String makeKey(String master, String job) {
        return prefix() + ":" + master + ":" + job.toUpperCase() + ":" + job;
    }

    private static String extractJobName(String key) {
        return key.split(":")[3];
    }

    private static String extractTypeaheadResult(String key) {
        String[] parts = key.split(":");
        return parts[1] + ":" + parts[3];
    }

    private String prefix() {
        return igorConfigurationProperties.getSpinnaker().getJedis().getPrefix();
    }
}
