/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.igor.scm.github.client

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.igor.config.GitHubConfig
import com.netflix.spinnaker.igor.scm.github.client.model.CompareCommitsResponse
import com.squareup.okhttp.mockwebserver.MockResponse
import com.squareup.okhttp.mockwebserver.MockWebServer
import spock.lang.Shared
import spock.lang.Specification

import java.time.Instant

import static com.netflix.spinnaker.igor.helpers.TestUtils.createObjectMapper

/**
 * Tests that GitHubClient correctly binds to underlying model as expected
 */
class GitHubClientSpec extends Specification {

    @Shared
    GitHubClient client

    @Shared
    MockWebServer server

    @Shared
    ObjectMapper mapper

    void setup() {
        server = new MockWebServer()
        mapper = createObjectMapper()
    }

    void cleanup() {
        server.shutdown()
    }

    private void setResponse(String body) {
        server.enqueue(
            new MockResponse()
                .setBody(body)
                .setHeader('Content-Type', 'text/xml;charset=UTF-8')
        )
        server.start()
        client = new GitHubConfig().gitHubClient(server.getUrl('/').toString(), 'token', mapper)
    }

    void 'getCompareCommits'() {
        given:
        setResponse getCompareCommitsResponse()

        when:
        CompareCommitsResponse commitsResponse = client.getCompareCommits('foo', 'repo', 'abcd', 'defg')

        then:
        commitsResponse.html_url == 'https://github.com/my-project/module/compare/0a7c0c17992b15c73de25b2a94abb4c88862b53f...7890bc148475432b9e537e03d37f22d9018ef9c8'
        commitsResponse.url == 'https://api.github.com/repos/my-project/module/compare/0a7c0c17992b15c73de25b2a94abb4c88862b53f...7890bc148475432b9e537e03d37f22d9018ef9c8'

        commitsResponse.commits.size() == 2

        with(commitsResponse.commits.get(0)) {
            sha == '83bdadb570db40cab995e1f402dea2b096d0c1a1'
            commitInfo.author.name == 'Joe Coder'
            commitInfo.author.email == 'joecoder@company.com'
            commitInfo.message == "bug fix"
            html_url == "https://github.com/my-project/module/commit/83bdadb570db40cab995e1f402dea2b096d0c1a1"
            commitInfo.author.date == Instant.ofEpochMilli(1433192015000)
        }

        with(commitsResponse.commits.get(1)) {
            sha == '7890bc148475432b9e537e03d37f22d9018ef9c8'
            commitInfo.author.name == 'Joe Coder'
            commitInfo.author.email == 'joecoder@company.com'
            commitInfo.message == "new feature"
            html_url == "https://github.com/my-project/module/commit/7890bc148475432b9e537e03d37f22d9018ef9c8"
            commitInfo.author.date == Instant.ofEpochMilli(1433192281000)
        }
    }

    String getCompareCommitsResponse() {
        return '\n' +
            '{\n' +
            '  "url": "https://api.github.com/repos/my-project/module/compare/0a7c0c17992b15c73de25b2a94abb4c88862b53f...7890bc148475432b9e537e03d37f22d9018ef9c8",\n' +
            '  "html_url": "https://github.com/my-project/module/compare/0a7c0c17992b15c73de25b2a94abb4c88862b53f...7890bc148475432b9e537e03d37f22d9018ef9c8",\n' +
            '  "permalink_url": "https://github.com/my-project/module/compare/my-project:0a7c0c1...my-project:7890bc1",\n' +
            '  "diff_url": "https://github.com/my-project/module/compare/0a7c0c17992b15c73de25b2a94abb4c88862b53f...7890bc148475432b9e537e03d37f22d9018ef9c8.diff",\n' +
            '  "patch_url": "https://github.com/my-project/module/compare/0a7c0c17992b15c73de25b2a94abb4c88862b53f...7890bc148475432b9e537e03d37f22d9018ef9c8.patch",\n' +
            '  "base_commit": {\n' +
            '    "sha": "0a7c0c17992b15c73de25b2a94abb4c88862b53f",\n' +
            '    "commit": {\n' +
            '      "author": {\n' +
            '        "name": "Jane Coder",\n' +
            '        "email": "janecoder@company.com",\n' +
            '        "date": "2015-06-01T20:44:52Z"\n' +
            '      },\n' +
            '      "committer": {\n' +
            '        "name": "Jane Coder",\n' +
            '        "email": "janecoder@company.com",\n' +
            '        "date": "2015-06-01T20:44:52Z"\n' +
            '      },\n' +
            '      "message": "Merge pull request #398 from my-project/deterministic-parallel-stage-id\\n\\nEnsure the initialization stage receives a deterministic stage id",\n' +
            '      "tree": {\n' +
            '        "sha": "36fed076acfd54be79ce31f4f774eddf2a22ef19",\n' +
            '        "url": "https://api.github.com/repos/my-project/module/git/trees/36fed076acfd54be79ce31f4f774eddf2a22ef19"\n' +
            '      },\n' +
            '      "url": "https://api.github.com/repos/my-project/module/git/commits/0a7c0c17992b15c73de25b2a94abb4c88862b53f",\n' +
            '      "comment_count": 0\n' +
            '    },\n' +
            '    "url": "https://api.github.com/repos/my-project/module/commits/0a7c0c17992b15c73de25b2a94abb4c88862b53f",\n' +
            '    "html_url": "https://github.com/my-project/module/commit/0a7c0c17992b15c73de25b2a94abb4c88862b53f",\n' +
            '    "comments_url": "https://api.github.com/repos/my-project/module/commits/0a7c0c17992b15c73de25b2a94abb4c88862b53f/comments",\n' +
            '    "author": {\n' +
            '      "login": "jcoder",\n' +
            '      "id": 388652,\n' +
            '      "avatar_url": "https://avatars.githubusercontent.com/u/11111?v=3",\n' +
            '      "gravatar_id": "",\n' +
            '      "url": "https://api.github.com/users/jcoder",\n' +
            '      "html_url": "https://github.com/jcoder",\n' +
            '      "followers_url": "https://api.github.com/users/jcoder/followers",\n' +
            '      "following_url": "https://api.github.com/users/jcoder/following{/other_user}",\n' +
            '      "gists_url": "https://api.github.com/users/jcoder/gists{/gist_id}",\n' +
            '      "starred_url": "https://api.github.com/users/jcoder/starred{/owner}{/repo}",\n' +
            '      "subscriptions_url": "https://api.github.com/users/jcoder/subscriptions",\n' +
            '      "organizations_url": "https://api.github.com/users/jcoder/orgs",\n' +
            '      "repos_url": "https://api.github.com/users/jcoder/repos",\n' +
            '      "events_url": "https://api.github.com/users/jcoder/events{/privacy}",\n' +
            '      "received_events_url": "https://api.github.com/users/jcoder/received_events",\n' +
            '      "type": "User",\n' +
            '      "site_admin": false\n' +
            '    },\n' +
            '    "committer": {\n' +
            '      "login": "jcoder",\n' +
            '      "id": 388652,\n' +
            '      "avatar_url": "https://avatars.githubusercontent.com/u/11111?v=3",\n' +
            '      "gravatar_id": "",\n' +
            '      "url": "https://api.github.com/users/jcoder",\n' +
            '      "html_url": "https://github.com/jcoder",\n' +
            '      "followers_url": "https://api.github.com/users/jcoder/followers",\n' +
            '      "following_url": "https://api.github.com/users/jcoder/following{/other_user}",\n' +
            '      "gists_url": "https://api.github.com/users/jcoder/gists{/gist_id}",\n' +
            '      "starred_url": "https://api.github.com/users/jcoder/starred{/owner}{/repo}",\n' +
            '      "subscriptions_url": "https://api.github.com/users/jcoder/subscriptions",\n' +
            '      "organizations_url": "https://api.github.com/users/jcoder/orgs",\n' +
            '      "repos_url": "https://api.github.com/users/jcoder/repos",\n' +
            '      "events_url": "https://api.github.com/users/jcoder/events{/privacy}",\n' +
            '      "received_events_url": "https://api.github.com/users/jcoder/received_events",\n' +
            '      "type": "User",\n' +
            '      "site_admin": false\n' +
            '    },\n' +
            '    "parents": [\n' +
            '      {\n' +
            '        "sha": "ca58ebfd0e74370246c328bdb61bceabdf9ea506",\n' +
            '        "url": "https://api.github.com/repos/my-project/module/commits/ca58ebfd0e74370246c328bdb61bceabdf9ea506",\n' +
            '        "html_url": "https://github.com/my-project/module/commit/ca58ebfd0e74370246c328bdb61bceabdf9ea506"\n' +
            '      },\n' +
            '      {\n' +
            '        "sha": "e108baa8022059b68cafe182759b1f224155ff80",\n' +
            '        "url": "https://api.github.com/repos/my-project/module/commits/e108baa8022059b68cafe182759b1f224155ff80",\n' +
            '        "html_url": "https://github.com/my-project/module/commit/e108baa8022059b68cafe182759b1f224155ff80"\n' +
            '      }\n' +
            '    ]\n' +
            '  },\n' +
            '  "merge_base_commit": {\n' +
            '    "sha": "0a7c0c17992b15c73de25b2a94abb4c88862b53f",\n' +
            '    "commit": {\n' +
            '      "author": {\n' +
            '        "name": "Jane Coder",\n' +
            '        "email": "janecoder@company.com",\n' +
            '        "date": "2015-06-01T20:44:52Z"\n' +
            '      },\n' +
            '      "committer": {\n' +
            '        "name": "Jane Coder",\n' +
            '        "email": "janecoder@company.com",\n' +
            '        "date": "2015-06-01T20:44:52Z"\n' +
            '      },\n' +
            '      "message": "Merge pull request #398 from my-project/deterministic-parallel-stage-id\\n\\nEnsure the initialization stage receives a deterministic stage id",\n' +
            '      "tree": {\n' +
            '        "sha": "36fed076acfd54be79ce31f4f774eddf2a22ef19",\n' +
            '        "url": "https://api.github.com/repos/my-project/module/git/trees/36fed076acfd54be79ce31f4f774eddf2a22ef19"\n' +
            '      },\n' +
            '      "url": "https://api.github.com/repos/my-project/module/git/commits/0a7c0c17992b15c73de25b2a94abb4c88862b53f",\n' +
            '      "comment_count": 0\n' +
            '    },\n' +
            '    "url": "https://api.github.com/repos/my-project/module/commits/0a7c0c17992b15c73de25b2a94abb4c88862b53f",\n' +
            '    "html_url": "https://github.com/my-project/module/commit/0a7c0c17992b15c73de25b2a94abb4c88862b53f",\n' +
            '    "comments_url": "https://api.github.com/repos/my-project/module/commits/0a7c0c17992b15c73de25b2a94abb4c88862b53f/comments",\n' +
            '    "author": {\n' +
            '      "login": "jcoder",\n' +
            '      "id": 388652,\n' +
            '      "avatar_url": "https://avatars.githubusercontent.com/u/11111?v=3",\n' +
            '      "gravatar_id": "",\n' +
            '      "url": "https://api.github.com/users/jcoder",\n' +
            '      "html_url": "https://github.com/jcoder",\n' +
            '      "followers_url": "https://api.github.com/users/jcoder/followers",\n' +
            '      "following_url": "https://api.github.com/users/jcoder/following{/other_user}",\n' +
            '      "gists_url": "https://api.github.com/users/jcoder/gists{/gist_id}",\n' +
            '      "starred_url": "https://api.github.com/users/jcoder/starred{/owner}{/repo}",\n' +
            '      "subscriptions_url": "https://api.github.com/users/jcoder/subscriptions",\n' +
            '      "organizations_url": "https://api.github.com/users/jcoder/orgs",\n' +
            '      "repos_url": "https://api.github.com/users/jcoder/repos",\n' +
            '      "events_url": "https://api.github.com/users/jcoder/events{/privacy}",\n' +
            '      "received_events_url": "https://api.github.com/users/jcoder/received_events",\n' +
            '      "type": "User",\n' +
            '      "site_admin": false\n' +
            '    },\n' +
            '    "committer": {\n' +
            '      "login": "jcoder",\n' +
            '      "id": 388652,\n' +
            '      "avatar_url": "https://avatars.githubusercontent.com/u/11111?v=3",\n' +
            '      "gravatar_id": "",\n' +
            '      "url": "https://api.github.com/users/jcoder",\n' +
            '      "html_url": "https://github.com/jcoder",\n' +
            '      "followers_url": "https://api.github.com/users/jcoder/followers",\n' +
            '      "following_url": "https://api.github.com/users/jcoder/following{/other_user}",\n' +
            '      "gists_url": "https://api.github.com/users/jcoder/gists{/gist_id}",\n' +
            '      "starred_url": "https://api.github.com/users/jcoder/starred{/owner}{/repo}",\n' +
            '      "subscriptions_url": "https://api.github.com/users/jcoder/subscriptions",\n' +
            '      "organizations_url": "https://api.github.com/users/jcoder/orgs",\n' +
            '      "repos_url": "https://api.github.com/users/jcoder/repos",\n' +
            '      "events_url": "https://api.github.com/users/jcoder/events{/privacy}",\n' +
            '      "received_events_url": "https://api.github.com/users/jcoder/received_events",\n' +
            '      "type": "User",\n' +
            '      "site_admin": false\n' +
            '    },\n' +
            '    "parents": [\n' +
            '      {\n' +
            '        "sha": "ca58ebfd0e74370246c328bdb61bceabdf9ea506",\n' +
            '        "url": "https://api.github.com/repos/my-project/module/commits/ca58ebfd0e74370246c328bdb61bceabdf9ea506",\n' +
            '        "html_url": "https://github.com/my-project/module/commit/ca58ebfd0e74370246c328bdb61bceabdf9ea506"\n' +
            '      },\n' +
            '      {\n' +
            '        "sha": "e108baa8022059b68cafe182759b1f224155ff80",\n' +
            '        "url": "https://api.github.com/repos/my-project/module/commits/e108baa8022059b68cafe182759b1f224155ff80",\n' +
            '        "html_url": "https://github.com/my-project/module/commit/e108baa8022059b68cafe182759b1f224155ff80"\n' +
            '      }\n' +
            '    ]\n' +
            '  },\n' +
            '  "status": "ahead",\n' +
            '  "ahead_by": 2,\n' +
            '  "behind_by": 0,\n' +
            '  "total_commits": 2,\n' +
            '  "commits": [\n' +
            '    {\n' +
            '      "sha": "83bdadb570db40cab995e1f402dea2b096d0c1a1",\n' +
            '      "commit": {\n' +
            '        "author": {\n' +
            '          "name": "Joe Coder",\n' +
            '          "email": "joecoder@company.com",\n' +
            '          "date": "2015-06-01T20:53:35Z"\n' +
            '        },\n' +
            '        "committer": {\n' +
            '          "name": "Joe Coder",\n' +
            '          "email": "joecoder@company.com",\n' +
            '          "date": "2015-06-01T20:53:35Z"\n' +
            '        },\n' +
            '        "message": "bug fix",\n' +
            '        "tree": {\n' +
            '          "sha": "9804641ea18f9dcba331ffacdc654578be611e72",\n' +
            '          "url": "https://api.github.com/repos/my-project/module/git/trees/9804641ea18f9dcba331ffacdc654578be611e72"\n' +
            '        },\n' +
            '        "url": "https://api.github.com/repos/my-project/module/git/commits/83bdadb570db40cab995e1f402dea2b096d0c1a1",\n' +
            '        "comment_count": 0\n' +
            '      },\n' +
            '      "url": "https://api.github.com/repos/my-project/module/commits/83bdadb570db40cab995e1f402dea2b096d0c1a1",\n' +
            '      "html_url": "https://github.com/my-project/module/commit/83bdadb570db40cab995e1f402dea2b096d0c1a1",\n' +
            '      "comments_url": "https://api.github.com/repos/my-project/module/commits/83bdadb570db40cab995e1f402dea2b096d0c1a1/comments",\n' +
            '      "author": null,\n' +
            '      "committer": null,\n' +
            '      "parents": [\n' +
            '        {\n' +
            '          "sha": "0a7c0c17992b15c73de25b2a94abb4c88862b53f",\n' +
            '          "url": "https://api.github.com/repos/my-project/module/commits/0a7c0c17992b15c73de25b2a94abb4c88862b53f",\n' +
            '          "html_url": "https://github.com/my-project/module/commit/0a7c0c17992b15c73de25b2a94abb4c88862b53f"\n' +
            '        }\n' +
            '      ]\n' +
            '    },\n' +
            '    {\n' +
            '      "sha": "7890bc148475432b9e537e03d37f22d9018ef9c8",\n' +
            '      "commit": {\n' +
            '        "author": {\n' +
            '          "name": "Joe Coder",\n' +
            '          "email": "joecoder@company.com",\n' +
            '          "date": "2015-06-01T20:58:01Z"\n' +
            '        },\n' +
            '        "committer": {\n' +
            '          "name": "Joe Coder",\n' +
            '          "email": "joecoder@company.com",\n' +
            '          "date": "2015-06-01T20:58:01Z"\n' +
            '        },\n' +
            '        "message": "new feature",\n' +
            '        "tree": {\n' +
            '          "sha": "4467002945b6cd53132e7e185f91c33e288b854d",\n' +
            '          "url": "https://api.github.com/repos/my-project/module/git/trees/4467002945b6cd53132e7e185f91c33e288b854d"\n' +
            '        },\n' +
            '        "url": "https://api.github.com/repos/my-project/module/git/commits/7890bc148475432b9e537e03d37f22d9018ef9c8",\n' +
            '        "comment_count": 0\n' +
            '      },\n' +
            '      "url": "https://api.github.com/repos/my-project/module/commits/7890bc148475432b9e537e03d37f22d9018ef9c8",\n' +
            '      "html_url": "https://github.com/my-project/module/commit/7890bc148475432b9e537e03d37f22d9018ef9c8",\n' +
            '      "comments_url": "https://api.github.com/repos/my-project/module/commits/7890bc148475432b9e537e03d37f22d9018ef9c8/comments",\n' +
            '      "author": null,\n' +
            '      "committer": null,\n' +
            '      "parents": [\n' +
            '        {\n' +
            '          "sha": "83bdadb570db40cab995e1f402dea2b096d0c1a1",\n' +
            '          "url": "https://api.github.com/repos/my-project/module/commits/83bdadb570db40cab995e1f402dea2b096d0c1a1",\n' +
            '          "html_url": "https://github.com/my-project/module/commit/83bdadb570db40cab995e1f402dea2b096d0c1a1"\n' +
            '        }\n' +
            '      ]\n' +
            '    }\n' +
            '  ],\n' +
            '  "files": [\n' +
            '    {\n' +
            '      "sha": "e90c98aad552c2827f3a2465ec383db5a8690805",\n' +
            '      "filename": "gradle.properties",\n' +
            '      "status": "modified",\n' +
            '      "additions": 1,\n' +
            '      "deletions": 1,\n' +
            '      "changes": 2,\n' +
            '      "blob_url": "https://github.com/my-project/module/blob/7890bc148475432b9e537e03d37f22d9018ef9c8/gradle.properties",\n' +
            '      "raw_url": "https://github.com/my-project/module/raw/7890bc148475432b9e537e03d37f22d9018ef9c8/gradle.properties",\n' +
            '      "contents_url": "https://api.github.com/repos/my-project/module/contents/gradle.properties?ref=7890bc148475432b9e537e03d37f22d9018ef9c8",\n' +
            '      "patch": "@@ -1,2 +1,2 @@\\n-version=0.308-SNAPSHOT\\n+version=0.309-SNAPSHOT\\n "\n' +
            '    }\n' +
            '  ]\n' +
            '}'
    }
}
