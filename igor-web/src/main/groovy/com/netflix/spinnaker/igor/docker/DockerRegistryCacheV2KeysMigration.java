/*
 * Copyright 2018 Netflix, Inc.
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
package com.netflix.spinnaker.igor.docker;

import com.google.common.collect.Iterables;
import com.netflix.dyno.connectionpool.CursorBasedResult;
import com.netflix.dyno.jedis.DynoJedisClient;
import com.netflix.spinnaker.igor.IgorConfigurationProperties;
import com.netflix.spinnaker.kork.dynomite.DynomiteClientDelegate;
import com.netflix.spinnaker.kork.jedis.RedisClientDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.MultiKeyCommands;
import redis.clients.jedis.ScanParams;
import redis.clients.jedis.ScanResult;
import rx.Scheduler;
import rx.schedulers.Schedulers;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static java.lang.String.format;

/**
 * Migrates docker registry cache keys from v1 to v2. This migrator is backwards-incompatible as the key format
 * is destructive (data is being removed from the key). When this migrator is run, the old keys will be copied
 * to the new key format, and the old keys TTL'ed.
 */
@Component
@ConditionalOnProperty(value = "redis.dockerV1KeyMigration.enabled", matchIfMissing = true)
public class DockerRegistryCacheV2KeysMigration {

    private final static Logger log = LoggerFactory.getLogger(DockerRegistryCacheV2KeysMigration.class);

    private final RedisClientDelegate redis;
    private final IgorConfigurationProperties properties;
    private final Scheduler scheduler;

    private final AtomicBoolean running = new AtomicBoolean();

    @Autowired
    public DockerRegistryCacheV2KeysMigration(RedisClientDelegate redis,
                                              IgorConfigurationProperties properties) {
        this(redis, properties, Schedulers.io());
    }

    public DockerRegistryCacheV2KeysMigration(RedisClientDelegate redis,
                                              IgorConfigurationProperties properties,
                                              Scheduler scheduler) {
        this.redis = redis;
        this.properties = properties;
        this.scheduler = scheduler;
    }

    public boolean isRunning() {
        return running.get();
    }

    @PostConstruct
    void run() {
        running.set(true);
        try {
            scheduler.createWorker().schedule(this::migrate);
        } finally {
            running.set(false);
        }
    }

    private void migrate() {
        log.info("Starting migration");

        List<String> oldKeys = redis.withMultiClient(this::getV1Keys);
        log.info("Migrating {} v1 keys", oldKeys.size());

        int batchSize = properties.getRedis().getDockerV1KeyMigration().getBatchSize();
        for (List<String> oldKeyBatch : Iterables.partition(oldKeys, batchSize)) {
            // For each key: Check if old exists, if so, copy to new key, set ttl on old key, remove ttl on new key
            migrateBatch(oldKeyBatch);
        }
    }

    private void migrateBatch(List<String> oldKeys) {
        int expireSeconds = (int) Duration.ofDays(properties.getRedis().getDockerV1KeyMigration().getTtlDays()).getSeconds();
        redis.withCommandsClient(c -> {
            for (String oldKey : oldKeys) {
                String newKey = convertToNewFormat(oldKey);
                if (c.exists(newKey)) {
                    // Nothing to do here, just move on with life
                    continue;
                }

                Map<String, String> value = c.hgetAll(oldKey);
                c.hmset(newKey, value);
                c.expire(oldKey, expireSeconds);
            }
        });
    }

    private List<String> getV1Keys(MultiKeyCommands client) {
        // TODO rz - dyno does not yet support `scan` interface; exposed as `dyno_scan`
        if (redis instanceof DynomiteClientDelegate) {
            return keys((DynoJedisClient) client);
        }
        return keys((Jedis) client);
    }

    private List<String> keys(DynoJedisClient dyno) {
        List<String> keys = new ArrayList<>();

        String pattern = oldIndexPattern();
        CursorBasedResult<String> result;
        do {
            result = dyno.dyno_scan(pattern);
            keys.addAll(result.getResult().stream().filter(this::isOldKey).collect(Collectors.toList()));
        } while (!result.isComplete());

        return keys;
    }

    private List<String> keys(Jedis jedis) {
        List<String> keys = new ArrayList<>();

        ScanParams params = new ScanParams().match(oldIndexPattern()).count(1000);
        String cursor = ScanParams.SCAN_POINTER_START;

        ScanResult<String> result;
        do {
            result = jedis.scan(cursor, params);
            keys.addAll(result.getResult().stream().filter(this::isOldKey).collect(Collectors.toList()));
        } while (!result.getStringCursor().equals("0"));

        return keys;
    }

    private boolean isOldKey(String key) {
        // Target v1 keys. They include repository URLs, which can include ports, so eq/gt 6 parts.
        return key.split(":").length >= 6;
    }

    private String convertToNewFormat(String oldKey) {
        String[] tagParts = oldKey.split("/");
        String[] parts = tagParts[0].split(":");

        String account = parts[0];
        String registry = parts[1];
        String tag = tagParts[1];

        return DockerRegistryCache.makeKey(prefix(), account, registry, tag);
    }

    private String oldIndexPattern() {
        return format("%s:%s:*", prefix(), DockerRegistryCache.ID);
    }

    private String prefix() {
        return properties.getSpinnaker().getJedis().getPrefix();
    }
}
