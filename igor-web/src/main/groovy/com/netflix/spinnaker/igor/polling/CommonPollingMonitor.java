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
package com.netflix.spinnaker.igor.polling;

import com.netflix.appinfo.InstanceInfo.InstanceStatus;
import com.netflix.discovery.DiscoveryClient;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.igor.IgorConfigurationProperties;
import com.netflix.spinnaker.kork.eureka.RemoteStatusChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Scheduler;
import rx.schedulers.Schedulers;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static net.logstash.logback.argument.StructuredArguments.kv;

public abstract class CommonPollingMonitor<I extends DeltaItem, T extends PollingDelta<I>> implements PollingMonitor {

    protected Logger log = LoggerFactory.getLogger(getClass());

    private final Optional<DiscoveryClient> discoveryClient;
    protected Scheduler.Worker worker;

    protected final IgorConfigurationProperties igorProperties;
    protected final Registry registry;

    private Long lastPoll;

    private final Id itemsCachedId;
    private final Id itemsOverThresholdId;
    private final Id pollCycleFailedId;
    protected final Id missedNotificationId;

    public CommonPollingMonitor(IgorConfigurationProperties igorProperties,
                                Registry registry,
                                Optional<DiscoveryClient> discoveryClient) {
        this(igorProperties, registry, discoveryClient, Schedulers.io());
    }

    public CommonPollingMonitor(IgorConfigurationProperties igorProperties,
                                Registry registry,
                                Optional<DiscoveryClient> discoveryClient,
                                Scheduler scheduler) {
        this.igorProperties = igorProperties;
        this.registry = registry;
        this.discoveryClient = discoveryClient;
        this.worker = scheduler.createWorker();

        itemsCachedId = registry.createId("pollingMonitor.newItems");
        itemsOverThresholdId = registry.createId("pollingMonitor.itemsOverThreshold");
        pollCycleFailedId = registry.createId("pollingMonitor.failed");
        missedNotificationId = registry.createId("pollingMonitor.missedEchoNotification");
    }

    @Override
    public void onApplicationEvent(RemoteStatusChangedEvent event) {
        log.info("Started");
        initialize();
        worker.schedulePeriodically(() -> {
            if (isInService()) {
                lastPoll = System.currentTimeMillis();
                poll();
            } else {
                log.info("not in service (lastPoll: {})", (lastPoll == null) ? "n/a" : lastPoll.toString());
                lastPoll = null;
            }
        }, 0, getPollInterval(), TimeUnit.SECONDS);
    }

    protected abstract void initialize();

    /**
     * Poll entry point. Each poller should be capable of breaking its polling
     * work into partitions and then call internalPoll for each.
     */
    protected abstract void poll();

    /**
     * Returns a delta of stored state versus newly polled data. A polling
     * monitor must not perform writes in this method.
     */
    protected abstract T generateDelta(PollContext ctx);

    /**
     * Commits a delta of polled state that was created in generateDelta,
     * assuming circuit breakers have not been tripped.
     */
    protected abstract void commitDelta(T delta);

    protected void internalPoll(PollContext ctx) {
        String monitorName = getClass().getSimpleName();

        try {
            T delta = generateDelta(ctx);

            int upperThreshold = Optional
                .ofNullable(getPartitionUpperThreshold(ctx.partitionName))
                .orElse(igorProperties.getSpinnaker().getPollingSafeguard().getItemUpperThreshold());

            int deltaSize = delta.getItems().size();
            if (deltaSize > upperThreshold) {
                log.warn(
                    "Number of items ({}) to cache exceeds upper threshold ({}) in {} {}",
                    deltaSize, upperThreshold, kv("monitor", monitorName), kv("partition", ctx.partitionName)
                );
                registry.gauge(itemsOverThresholdId.withTags("monitor", monitorName, "partition", ctx.partitionName)).set(deltaSize);
                return;
            }

            commitDelta(delta);
            registry.gauge(itemsCachedId.withTags("monitor", monitorName, "partition", ctx.partitionName)).set(deltaSize);
            registry.gauge(itemsOverThresholdId.withTags("monitor", monitorName, "partition", ctx.partitionName)).set(deltaSize);
        } catch (Exception e) {
            log.error("Failed to update monitor items for {}:{}", kv("monitor", monitorName), kv("partition", ctx.partitionName), e);
            registry.counter(pollCycleFailedId.withTags("monitor", monitorName, "partition", ctx.partitionName)).increment();
            registry.gauge(itemsCachedId.withTags("monitor", monitorName, "partition", ctx.partitionName)).set(0);
            registry.gauge(itemsOverThresholdId.withTags("monitor", monitorName, "partition", ctx.partitionName)).set(0);
        }
    }

    @Override
    public boolean isInService() {
        if (discoveryClient.isPresent()) {
            InstanceStatus remoteStatus = discoveryClient.get().getInstanceRemoteStatus();
            log.info("current remote status {}", remoteStatus);
            return remoteStatus == InstanceStatus.UP;
        } else {
            log.info("no DiscoveryClient, assuming InService");
            return true;
        }
    }

    @Override
    public int getPollInterval() {
        return igorProperties.getSpinnaker().getBuild().getPollInterval();
    }

    @Override
    public Long getLastPoll() {
        return lastPoll;
    }

    protected @Nullable Integer getPartitionUpperThreshold(String partition) {
        return null;
    }
}
