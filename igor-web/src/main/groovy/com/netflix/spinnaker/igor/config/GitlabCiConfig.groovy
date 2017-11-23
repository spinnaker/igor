package com.netflix.spinnaker.igor.config

import com.netflix.spinnaker.igor.IgorConfigurationProperties
import com.netflix.spinnaker.igor.gitlabci.client.GitlabCiClient
import com.netflix.spinnaker.igor.gitlabci.service.GitlabCiService
import com.netflix.spinnaker.igor.service.BuildMasters
import com.squareup.okhttp.OkHttpClient
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import retrofit.Endpoints
import retrofit.RequestInterceptor
import retrofit.RestAdapter
import retrofit.client.OkClient

import java.util.concurrent.TimeUnit

@Configuration
@Slf4j
@CompileStatic
@ConditionalOnProperty('gitlab-ci.enabled')
@EnableConfigurationProperties(GitlabCiProperties)
class GitlabCiConfig {

    @Bean
    Map<String, GitlabCiService> gitlabCiMasters(BuildMasters buildMasters, IgorConfigurationProperties igorConfigurationProperties, GitlabCiProperties gitlabCiProperties) {
        log.info "creating gitlabCiMasters"
        Map<String, GitlabCiService> gitlabCiMasters = (gitlabCiProperties?.masters?.collectEntries { GitlabCiProperties.GitlabCiHost host ->
            String hostName = "gitlab-ci-${host.name}"
            log.info "bootstrapping ${host.address} as ${hostName}"

            [(hostName): gitlabCiService(igorConfigurationProperties, gitlabCiProperties)]
        })
        buildMasters.map.putAll gitlabCiMasters
        return gitlabCiMasters
    }

    // TODO can there really be multiple masters?
    static GitlabCiService gitlabCiService(IgorConfigurationProperties igorConfigurationProperties, GitlabCiProperties gitlabCiProperties) {
        def host = gitlabCiProperties.masters[0]
        return new GitlabCiService(gitlabCiClient(host.address, host.privateToken, igorConfigurationProperties.client.timeout), host.limitByMembership, host.limitByOwnership)
    }

    static GitlabCiClient gitlabCiClient(String address, String privateToken, int timeout = 30000) {
        OkHttpClient client = new OkHttpClient()
        client.setReadTimeout(timeout, TimeUnit.MILLISECONDS)

        //Need this code because without FULL log level, fetching logs will fail. Ref https://github.com/square/retrofit/issues/953.
        RestAdapter.Log fooLog = new RestAdapter.Log() {
            @Override public void log(String message) {
            }
        }
        new RestAdapter.Builder()
            .setEndpoint(Endpoints.newFixedEndpoint(address))
            .setRequestInterceptor(new GitlabCiHeaders(privateToken))
            .setClient(new OkClient(client))
            .setLog(fooLog)
            .setLogLevel(RestAdapter.LogLevel.FULL)
            .build()
            .create(GitlabCiClient)
    }

    static class GitlabCiHeaders implements RequestInterceptor {
        private String privateToken;

        GitlabCiHeaders(String privateToken) {
            this.privateToken = privateToken
        }

        @Override
        void intercept(RequestInterceptor.RequestFacade request) {
            request.addHeader("PRIVATE-TOKEN", privateToken)
        }
    }
}
