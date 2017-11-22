package com.netflix.spinnaker.igor.polling

import com.netflix.appinfo.InstanceInfo
import com.netflix.discovery.DiscoveryClient
import com.netflix.spinnaker.igor.IgorConfigurationProperties
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.event.ContextRefreshedEvent
import rx.Scheduler
import rx.functions.Action0
import rx.schedulers.Schedulers

import java.util.concurrent.TimeUnit

abstract class CommonPollingMonitor implements PollingMonitor {
    Logger log = LoggerFactory.getLogger(getClass())

    @Autowired(required = false)
    DiscoveryClient discoveryClient

    @Autowired
    IgorConfigurationProperties igorConfigurationProperties

    Scheduler scheduler = Schedulers.io()

    Scheduler.Worker worker = scheduler.createWorker()

    Long lastPoll

    @Override
    void onApplicationEvent(ContextRefreshedEvent event) {
        log.info('Started')
        initialize()
        worker.schedulePeriodically({
            if (isInService()) {
                lastPoll = System.currentTimeMillis()
                poll()
            } else {
                log.info("not in service (lastPoll: ${lastPoll ?: 'n/a'})")
                lastPoll = null
            }
        } as Action0, 0, pollInterval, TimeUnit.SECONDS)
    }

    abstract void initialize()
    abstract void poll()

    @Override
    boolean isInService() {
        if (discoveryClient == null) {
            log.info("no DiscoveryClient, assuming InService")
            true
        } else {
            def remoteStatus = discoveryClient.instanceRemoteStatus
            log.info("current remote status ${remoteStatus}")
            remoteStatus == InstanceInfo.InstanceStatus.UP
        }
    }

    @Override
    int getPollInterval() {
        return igorConfigurationProperties.spinnaker.build.pollInterval
    }

    @Override
    Long getLastPoll() {
        return lastPoll
    }
}
