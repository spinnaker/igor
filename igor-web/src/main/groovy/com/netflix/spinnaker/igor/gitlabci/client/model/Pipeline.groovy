package com.netflix.spinnaker.igor.gitlabci.client.model

class Pipeline {
    long id
    String sha
    String ref
    PipelineStatus status
    boolean tag
    int duration
    Date finished_at
}
