/*
 * Copyright (c) 2017, 2018, Oracle Corporation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.netflix.spinnaker.igor.wercker.model
import java.time.Instant;
import java.util.Date
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Represents a Wercker Run
 */
@groovy.transform.ToString
class Run {
    String id
    String url
    String branch
    String commitHash
	Date createdAt
	Date finishedAt
	Date startedAt
	
    String message
    int progress
    String result
    String status
	
	Owner user
    Pipeline pipeline
    Application application

    /*
    "id": "59bfcc67e134fb00016baf3f",
        "url": "https://dev.wercker.com/api/v3/runs/59bfcc67e134fb00016baf3f",
        "branch": "master",
        "commitHash": "ffe7317ce8deff23f14cf9d465fc136a4adcc59e",
        "createdAt": "2017-09-18T13:38:47.875Z",
        "finishedAt": "2017-09-18T13:39:01.768Z",
        "message": "Auto trigger from Pipeline \"push-quay\" to Pipeline \"deploy-kube-staging\"",
        "progress": 100,
        "result": "passed",
        "startedAt": "2017-09-18T13:38:48.199Z",
        "status": "finished",
        "user": {
            "userId": "4feadab2e755f5e15b000262",
            "meta": {
                "username": "bvdberg",
                "type": ""
            },
            "avatar": {
                "gravatar": "dff7a3e4eadab56aa69a24569cb61e98"
            },
            "name": "Benno",
            "type": "wercker"
        },
        "pipeline": {
            "id": "58347282dd22a501005268e7",
            "url": "https://dev.wercker.com/api/v3/pipelines/58347282dd22a501005268e7",
            "createdAt": "2016-11-22T16:29:54.792Z",
            "name": "deploy-kube-staging",
            "permissions": "read",
            "pipelineName": "deploy-kube",
            "setScmProviderStatus": false,
            "type": "pipeline"
        }
     */

	
	
//		public Instant getFinishedAt() {
//			return finishedAt;
//		}
//	
//		public void setFinishedAt(Instant finishedAt) {
//			this.finishedAt = finishedAt;
//		}
	
	public String toString() {
		return "Run(" +id+"," + application?.name + "/"+pipeline?.name+")";
	}
}
