package com.netflix.spinnaker.igor.gitlabci

import com.netflix.spinnaker.igor.polling.CommonPollingMonitor
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service

@Service
@ConditionalOnProperty('gitlab-ci.enabled')
class GitlabCiBuildMonitor extends CommonPollingMonitor {
    @Override
    void initialize() {
    }

    @Override
    void poll() {
        log.info('tickie')
    }

    @Override
    String getName() {
        return "gitlabCiBuildMonitor";
    }

    @Override
    Long getLastPoll() {
        return null;
    }
}
