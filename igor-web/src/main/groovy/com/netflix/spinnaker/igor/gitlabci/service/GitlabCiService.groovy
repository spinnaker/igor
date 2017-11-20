package com.netflix.spinnaker.igor.gitlabci.service

import com.netflix.spinnaker.igor.build.model.GenericBuild
import com.netflix.spinnaker.igor.build.model.GenericGitRevision
import com.netflix.spinnaker.igor.model.BuildServiceProvider
import com.netflix.spinnaker.igor.service.BuildService
import sun.reflect.generics.reflectiveObjects.NotImplementedException

class GitlabCiService implements BuildService {

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
}
