package com.netflix.spinnaker.igor.gitlabci.service

import com.netflix.spinnaker.hystrix.SimpleHystrixCommand
import com.netflix.spinnaker.igor.build.model.GenericBuild
import com.netflix.spinnaker.igor.build.model.GenericGitRevision
import com.netflix.spinnaker.igor.build.model.GenericJobConfiguration
import com.netflix.spinnaker.igor.gitlabci.client.GitlabCiClient
import com.netflix.spinnaker.igor.gitlabci.client.model.Commit
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
    private String groupKey

    GitlabCiService(String hostName, GitlabCiClient client, String address, boolean limitByMembership, boolean limitByOwnership) {
        this.groupKey = hostName
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
    List<GenericGitRevision> getGenericGitRevisions(String job, long buildNumber) {
        new SimpleHystrixCommand<List<GenericGitRevision>>(
            groupKey, buildCommandKey("getGenericGitRevisions"),
            {
                // TODO maybe we have to strip the branch off the url
//                String repoSlug = cleanRepoSlug(inputRepoSlug)
                Pipeline pipeline = client.getPipeline(job, String.valueOf(buildNumber))
                String commitHash = pipeline.sha
                Commit commit = client.getCommit(job, commitHash)

                //http://localhost:8080/builds/status/2/travis-ci/wheleph/webdrivermanager
                //http://localhost:8080/builds/status/14843843/gitlab-ci-main/wheleph/the-adventures-of-dennis
                // todo put the proper value here
                return [new GenericGitRevision(commit.id, "none", commitHash, commit.committer_name, "none", commit.message)]
            }
        ).execute()
    }

    @Override
    GenericBuild getGenericBuild(String job, long buildNumber) {
        new SimpleHystrixCommand<GenericBuild>(
            groupKey, buildCommandKey("getGenericBuild"),
            {
                // TODO maybe we have to strip the branch off the url
//                String repoSlug = cleanRepoSlug(inputRepoSlug)
                Pipeline pipeline = client.getPipeline(job, String.valueOf(buildNumber))
                def genericBuild = GitlabCiPipelineConverter.genericBuild(pipeline, job, address)
                // TODO populate the artifacts
                genericBuild.artifacts = []//ArtifactParser.getArtifactsFromLog(getLog(build), artifactRegexes)
                return genericBuild
            }
        ).execute()
    }

    @Override
    int triggerBuildWithParameters(String job, Map<String, String> queryParameters) {
        throw new NotImplementedException()
    }

    @Override
    GenericJobConfiguration getJobConfig(String jobName) {
        return new GenericJobConfiguration(jobName, jobName, jobName, true, "$address/$jobName/pipelines", true, [])
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

    private String buildCommandKey(String id) {
        return "${groupKey}-${id}"
    }
}
