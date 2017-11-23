package com.netflix.spinnaker.igor.gitlabci.service

import com.netflix.spinnaker.igor.build.model.GenericBuild
import com.netflix.spinnaker.igor.build.model.GenericGitRevision
import com.netflix.spinnaker.igor.gitlabci.client.GitlabCiClient
import com.netflix.spinnaker.igor.gitlabci.client.model.Pipeline
import com.netflix.spinnaker.igor.gitlabci.client.model.PipelineSummary
import com.netflix.spinnaker.igor.gitlabci.client.model.Project
import com.netflix.spinnaker.igor.model.BuildServiceProvider
import com.netflix.spinnaker.igor.service.BuildService
import groovy.transform.TailRecursive
import sun.reflect.generics.reflectiveObjects.NotImplementedException

class GitlabCiService implements BuildService {

    private GitlabCiClient client
    private String address
    private boolean limitByMembership
    private boolean limitByOwnership

    GitlabCiService(GitlabCiClient client, String address, boolean limitByMembership, boolean limitByOwnership) {
        this.client = client
        this.address = address
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

    List<Pipeline> getPipelines(Project project, int limit) {
        isValidPageSize(limit)

        List<PipelineSummary> pipelineSummaries = client.getPipelineSummaries(project.id, limit)

        return pipelineSummaries.collect({
            client.getPipeline(project.id, String.valueOf(it.id))
        })
    }

    static String getBranchedPipelineSlug(Project project, Pipeline pipeline) {
        return pipeline.isTag()?
            "${project.path_with_namespace}/tags" :
            "${project.path_with_namespace}/${pipeline.ref}"
    }

    String getAddress() {
        return address
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

    private static void isValidPageSize(int perPage) {
        if (perPage > GitlabCiClient.MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("Gitlab API call page size should be less than ${GitlabCiClient.MAX_PAGE_SIZE} " +
                "but was $perPage")
        }
    }
}
