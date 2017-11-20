package com.netflix.spinnaker.igor.config

import com.netflix.spinnaker.igor.gitlabci.service.GitlabCiService
import com.netflix.spinnaker.igor.service.BuildMasters
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@Slf4j
@CompileStatic
@ConditionalOnProperty('gitlab-ci.enabled')
@EnableConfigurationProperties(GitlabCiProperties)
class GitlabCiConfig {

    // TODO timeout for the gitlab ci client
    @Bean
    Map<String, GitlabCiService> gitlabCiMasters(BuildMasters buildMasters, GitlabCiProperties gitlabCiProperties) {
        log.info "creating gitlabCiMasters"
        Map<String, GitlabCiService> gitlabCiMasters = (gitlabCiProperties?.masters?.collectEntries { GitlabCiProperties.GitlabCiHost host ->
            String hostName = "gitlab-ci-${host.name}"
            log.info "bootstrapping ${host.address} as ${hostName}"

            [(hostName): new GitlabCiService()]
        })
        buildMasters.map.putAll gitlabCiMasters
        return gitlabCiMasters
    }
}
