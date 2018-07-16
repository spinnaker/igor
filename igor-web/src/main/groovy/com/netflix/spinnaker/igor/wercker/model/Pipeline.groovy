/*
 * Copyright (c) 2017, 2018, Oracle Corporation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.netflix.spinnaker.igor.wercker.model

/**
 * Represents a Wercker Pipeline
 */
@groovy.transform.ToString
class Pipeline {
    String id
    String url
    String name
    String permissions
    String pipelineName //In the wercker.yml, I think
    boolean setScmProviderStatus
    String type
	Application application
    /*
                "id": "58347282dd22a501005268e7",
            "url": "https://dev.wercker.com/api/v3/pipelines/58347282dd22a501005268e7",
            "createdAt": "2016-11-22T16:29:54.792Z",
            "name": "deploy-kube-staging",
            "permissions": "read",
            "pipelineName": "deploy-kube",
            "setScmProviderStatus": false,
            "type": "pipeline"
     */
}
