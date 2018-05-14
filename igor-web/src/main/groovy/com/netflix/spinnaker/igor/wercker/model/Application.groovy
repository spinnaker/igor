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
 * Represents a Wercker application
 */
@groovy.transform.ToString
class Application {
    /*    {
        "id": "5ac37f978499f1010011a6df",
        "url": "https://dev.wercker.com/api/v3/applications/desagar/hellogo",
        "name": "hellogo",
        "owner": {
            "type": "wercker",
            "name": "desagar",
            "avatar": {
                "gravatar": "da2aaeec804e118066a4ea8a396f95a8"
            },
            "userId": "5abe5aafa1fe0301005bcdb6",
            "meta": {
                "username": "desagar",
                "type": "user",
                "werckerEmployee": false
            }
        },
        "createdAt": "2018-04-03T13:20:23.535Z",
        "updatedAt": "2018-04-03T13:20:23.535Z",
        "privacy": "public",
        "stack": 6,
        "theme": "Cosmopolitan"
    },*/
    String id;
    String url;
    String name;
    Owner owner;
    String privacy;
	List<Pipeline> pipelines;
}
