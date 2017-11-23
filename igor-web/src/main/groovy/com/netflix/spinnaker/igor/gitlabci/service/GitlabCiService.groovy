package com.netflix.spinnaker.igor.gitlabci.service

import com.netflix.spinnaker.hystrix.SimpleHystrixCommand
import com.netflix.spinnaker.igor.build.model.GenericBuild
import com.netflix.spinnaker.igor.build.model.GenericGitRevision
import com.netflix.spinnaker.igor.gitlabci.client.GitlabCiClient
import com.netflix.spinnaker.igor.gitlabci.client.model.Project
import com.netflix.spinnaker.igor.model.BuildServiceProvider
import com.netflix.spinnaker.igor.service.BuildService
import groovy.transform.TailRecursive
import sun.reflect.generics.reflectiveObjects.NotImplementedException

class GitlabCiService implements BuildService {

    private GitlabCiClient client
    private boolean limitByMembership
    private boolean limitByOwnership

    GitlabCiService(GitlabCiClient client, boolean limitByMembership, boolean limitByOwnership) {
        this.client = client
        this.limitByMembership = limitByMembership
        this.limitByOwnership = limitByOwnership
    }

    @Override
    BuildServiceProvider buildServiceProvider() {
        return BuildServiceProvider.GITLAB_CI
    }

    @Override
    List<GenericGitRevision> getGenericGitRevisions(String job, int buildNumber) {
        throw new NotImplementedException()
    }

    @Override
    GenericBuild getGenericBuild(String job, int buildNumber) {
        throw new NotImplementedException()
    }

    @Override
    int triggerBuildWithParameters(String job, Map<String, String> queryParameters) {
        throw new NotImplementedException()
    }

    List<Project> getProjects() {
        return getProjectsRec([], 1)
    }

    @TailRecursive
    private List<Project> getProjectsRec(List<Project> projects, int page) {
        List<Project> slice = client.getProjects(limitByMembership, limitByOwnership, page)
        if (slice.isEmpty()) {
            return projects
        } else {
            projects.addAll(slice)
            return getProjectsRec(projects, page + 1)
        }
    }

}
