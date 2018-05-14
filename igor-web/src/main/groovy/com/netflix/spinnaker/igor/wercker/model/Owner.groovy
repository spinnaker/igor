/*
 * Copyright (c) 2017, 2018, Oracle Corporation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the Apache License Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * If a copy of the Apache License Version 2.0 was not distributed with this file,
 * You can obtain one at https://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.netflix.spinnaker.igor.wercker.model

class Owner {
    /*"owner": {
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
    },*/
    String name
    String type
    String userId
    OwnerMetadata meta

    static class OwnerMetadata {
        String username
        String type
        boolean werckerEmployee
    }
}
