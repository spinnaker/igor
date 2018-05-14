/*
 * Copyright (c) 2017, 2018, Oracle Corporation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.netflix.spinnaker.igor.wercker.model

class Workflow {
    String url
    WorkflowData data
    /*
        {
        "url": "https://dev.wercker.com/api/v3/workflows/5ad7a56129bb860001efde00",
        "createdAt": "2018-04-18T20:06:57.355Z",
        "data": {
            "branch": "master",
            "commitHash": "2064a244e65ec1e07e4b2b6ac425830f6721363b",
            "message": "blah",
            "scm": {
                "type": "git",
                "owner": "desagar",
                "domain": "github.com",
                "repository": "hellogo"
            }
        },
        "id": "5ad7a56129bb860001efde00",
        "items": [
            {
                "data": {
                    "targetName": "build",
                    "pipelineId": "5ac37f9713a3000100666206",
                    "restricted": false,
                    "totalSteps": 6,
                    "currentStep": 6,
                    "stepName": "store",
                    "runId": "5ad7a56129bb860001efddff"
                },
                "id": "5ad7a56129bb860001efde01",
                "progress": 100,
                "result": "passed",
                "status": "finished",
                "type": "run",
                "updatedAt": "2018-04-18T20:07:07.088Z"
            },
            {
                "data": {
                    "targetName": "deploy",
                    "pipelineId": "5ac37fcc13a300010066620a",
                    "restricted": false,
                    "totalSteps": 5,
                    "currentStep": 5,
                    "stepName": "store",
                    "runId": "5ad7a56b29bb860001efde07"
                },
                "id": "5ad7a56129bb860001efde02",
                "parentItem": "5ad7a56129bb860001efde01",
                "progress": 100,
                "result": "passed",
                "status": "finished",
                "type": "run",
                "updatedAt": "2018-04-18T20:07:15.483Z"
            }
        ],
        "startedAt": "2018-04-18T20:06:59.556Z",
        "trigger": "git",
        "updatedAt": "2018-04-18T20:07:15.482Z",
        "user": {
            "userId": "5abe5aafa1fe0301005bcdb6",
            "meta": {
                "username": "desagar",
                "type": ""
            },
            "avatar": {
                "gravatar": "da2aaeec804e118066a4ea8a396f95a8"
            },
            "name": "desagar",
            "type": "wercker"
        }
    }
     */
    static class WorkflowData {
        String branch
        String commitHash
        String message

        /*{
            "branch": "master",
            "commitHash": "2064a244e65ec1e07e4b2b6ac425830f6721363b",
            "message": "blah",
            "scm": {
            "type": "git",
            "owner": "desagar",
            "domain": "github.com",
            "repository": "hellogo"
        }
        */
    }
}
