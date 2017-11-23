package com.netflix.spinnaker.igor.config

import groovy.transform.CompileStatic
import org.hibernate.validator.constraints.NotEmpty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

import javax.validation.Valid

@CompileStatic
@ConfigurationProperties(prefix = 'gitlab-ci')
@Validated
class GitlabCiProperties {

    int cachedJobTTLDays = 60

    @Valid
    List<GitlabCiHost> masters

    static class GitlabCiHost {
        @NotEmpty
        String name
        @NotEmpty
        String address
        String privateToken
        boolean limitByMembership = false
        boolean limitByOwnership = true
    }
}
