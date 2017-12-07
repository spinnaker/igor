package com.netflix.spinnaker.igor.gitlabci.client

import com.netflix.spinnaker.igor.gitlabci.client.model.Commit
import com.netflix.spinnaker.igor.gitlabci.client.model.Pipeline
import com.netflix.spinnaker.igor.gitlabci.client.model.PipelineSummary
import com.netflix.spinnaker.igor.gitlabci.client.model.Project
import retrofit.http.GET
import retrofit.http.Path
import retrofit.http.Query

interface GitlabCiClient {
    int MAX_PAGE_SIZE = 100

    @GET("/api/v4/projects")
    List<Project> getProjects(@Query("membership") boolean limitByMembership, @Query("owned") boolean limitByOwnership, @Query("page") int page)

    @GET("/api/v4/projects/{projectId}/pipelines")
    List<PipelineSummary> getPipelineSummaries(@Path("projectId") String projectId, @Query("per_page") int pageLimit)

    @GET("/api/v4/projects/{projectId}/pipelines/{pipelineId}")
    Pipeline getPipeline(@Path("projectId") String projectId, @Path("pipelineId") String pipelineId)

    @GET("/api/v4/projects/{projectId}/repository/commits/{commitHash}")
    Commit getCommit(@Path("projectId") String projectId, @Path("commitHash") String commitHash)
}
