package com.netflix.spinnaker.igor.gitlabci.service

import com.netflix.spinnaker.igor.gitlabci.client.GitlabCiClient
import com.netflix.spinnaker.igor.gitlabci.client.model.Project
import spock.lang.Shared
import spock.lang.Specification

class GitlabCiServiceSpec extends Specification {
    @Shared
    GitlabCiClient client

    @Shared
    GitlabCiService service

    void setup() {
        client = Mock(GitlabCiClient)
        service = new GitlabCiService(client, null, false, false)
    }

    def "verify project pagination"() {
        given:
        client.getProjects(_, _, 1) >> [new Project(path_with_namespace: "project1")]
        client.getProjects(_, _, 2) >> [new Project(path_with_namespace: "project2")]
        client.getProjects(_, _, 3) >> []

        when:
        def projects = service.getProjects()

        then:
        projects.size() == 2
    }
}
