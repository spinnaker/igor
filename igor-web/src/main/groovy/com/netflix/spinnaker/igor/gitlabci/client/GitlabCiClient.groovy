package com.netflix.spinnaker.igor.gitlabci.client

import com.netflix.spinnaker.igor.gitlabci.client.model.Project
import retrofit.http.GET
import retrofit.http.Query

interface GitlabCiClient {
    @GET("/api/v4/projects")
    List<Project> getProjects(@Query("membership") boolean limitByMembership, @Query("owned") boolean limitByOwnership, @Query("page") int page)
}
