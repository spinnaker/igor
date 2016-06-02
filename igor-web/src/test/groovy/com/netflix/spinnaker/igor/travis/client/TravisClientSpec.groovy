/*
 * Copyright 2016 Schibsted ASA.
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

package com.netflix.spinnaker.igor.travis.client

import com.netflix.spinnaker.igor.config.TravisConfig
import com.netflix.spinnaker.igor.travis.client.model.AccessToken
import com.netflix.spinnaker.igor.travis.client.model.Account
import com.netflix.spinnaker.igor.travis.client.model.Accounts
import com.netflix.spinnaker.igor.travis.client.model.Build
import com.netflix.spinnaker.igor.travis.client.model.Builds
import com.netflix.spinnaker.igor.travis.client.model.GithubAuth
import com.netflix.spinnaker.igor.travis.client.model.Job
import com.netflix.spinnaker.igor.travis.client.model.Jobs
import com.netflix.spinnaker.igor.travis.client.model.RepoRequest
import com.netflix.spinnaker.igor.travis.client.model.RepoWrapper
import com.netflix.spinnaker.igor.travis.client.model.Repos
import com.netflix.spinnaker.igor.travis.client.model.TriggerResponse
import com.squareup.okhttp.mockwebserver.MockResponse
import com.squareup.okhttp.mockwebserver.MockWebServer
import retrofit.client.Response
import retrofit.mime.TypedByteArray
import spock.lang.Shared
import spock.lang.Specification

class TravisClientSpec extends Specification {

    @Shared
    TravisClient client

    @Shared
    MockWebServer server

    void setup() {
        server = new MockWebServer()
    }

    void cleanup() {
        server.shutdown()
    }

    def "AccessToken"() {
        given:
        setResponse '''{"access_token":"aCCeSSToKeN"}'''

        when:
        AccessToken accessToken = client.accessToken(new GithubAuth("foo"))

        then:
        accessToken.accessToken == "aCCeSSToKeN"
    }

    def "Accounts"() {
        given:
        setResponse '''{"accounts":[{"id":337980,"name":null,"login":"gardalize","type":"user","repos_count":1,"avatar_url":null}]}'''

        when:
        Accounts accounts = client.accounts("someToken")

        then:
        Account account = accounts.accounts.first()
        account.id    == 337980
        account.login == 'gardalize'
    }

    def "Builds"() {
        given:
        setResponse '''{
        "builds": [
        {
            "commit_id": 6534711,
            "config": { },
            "duration": 2648,
            "finished_at": "2014-04-08T19:52:56Z",
            "id": 22555277,
            "job_ids": [22555278, 22555279, 22555280, 22555281],
            "number": "784",
            "pull_request": true,
            "pull_request_number": "1912",
            "pull_request_title": "Example PR",
            "repository_id": 82,
            "started_at": "2014-04-08T19:37:44Z",
            "state": "failed"
        }
        ],
        "jobs": [ ],
        "commits": [ ]
        }'''

        when:
        Builds builds = client.builds("someToken")

        then:
        builds.builds.first().commitId == 6534711
        builds.builds.first().job_ids   == [22555278, 22555279, 22555280, 22555281]

    }



    def "Repos"() {
        given:
        setResponse '''{"repos":[{"id":8059977,"slug":"gardalize/travistest","description":"testing travis stuff","last_build_id":118583435,"last_build_number":"5","last_build_state":"passed","last_build_duration":39,"last_build_language":null,"last_build_started_at":"2016-03-25T22:29:44Z","last_build_finished_at":"2016-03-25T22:30:23Z","active":true,"github_language":"Ruby"}]}'''

        when:
        Repos repos = client.repos("someToken", "gardalize")

        then:
        repos.repos.first().id   == 8059977
        repos.repos.first().slug == "gardalize/travistest"
        repos.repos.first().lastBuildId == 118583435
    }

    def "repoWrapper(accessToken, repoSlug)"() {
        given:
        setResponse '''
        {
        "repo": {
            "id": 8059977,
            "slug": "gardalize/travistest",
            "active": true,
            "description": "testing travis stuff",
            "last_build_id": 123621158,
            "last_build_number": "51",
            "last_build_state": "passed",
            "last_build_duration": 38,
            "last_build_language": null,
            "last_build_started_at": "2016-04-16T21:26:35Z",
            "last_build_finished_at": "2016-04-16T21:27:13Z",
            "github_language": "Ruby"
            }
        }
        '''

        when:
        RepoWrapper repoWrapper = client.repoWrapper("someToken", "gardalize/travistest")

        then:
        repoWrapper.repo.slug == "gardalize/travistest"
        repoWrapper.repo.lastBuildDuration == 38
    }


    def "Job"() {
        given:
        setResponse '''{"job":{"id":118582578,"repository_id":8059977,"repository_slug":"gardalize/travistest","build_id":118582577,"commit_id":33511919,"log_id":85633918,"number":"4.1","config":{"language":"ruby","rvm":"1.9.3",".result":"configured","group":"stable","dist":"precise","os":"linux"},"state":"passed","started_at":"2016-03-25T22:26:13Z","finished_at":"2016-03-25T22:26:48Z","queue":"builds.docker","allow_failure":false,"tags":null,"annotation_ids":[]},"commit":{"id":33511919,"sha":"f8f8a8defe21ddc337185850cf894fe40ada2b9f","branch":"master","branch_is_default":true,"message":"rake","committed_at":"2016-03-25T22:25:19Z","author_name":"Gard Rimestad","author_email":"gardalize@gurters.com","committer_name":"Gard Rimestad","committer_email":"gardalize@gurters.com","compare_url":"https://github.com/gardalize/travistest/compare/2a9956914194...f8f8a8defe21"},"annotations":[]}'''

        when:
        Jobs jobs = client.jobs("someToken", 118582578)

        then:
        Job job = jobs.job
        job.id     == 118582578
        job.logId == 85633918
    }

    def "triggerBuild()" () {
        given:
        setResponse '''{
            "@type": "pending",
            "remaining_requests": 10,
            "repository": {
                "@type": "repository",
                "@href": "/repo/8059977",
                "@representation": "minimal",
                "id": 8059977,
                "name": "travistest",
                "slug": "gardalize/travistest"
            },
            "request": {
                "repository": {
                    "id": 54667513,
                    "owner_name": "gardalize",
                    "name": "travistest"
                },
                "user": {
                    "id": 337980
                },
                "message": "Triggered from spinnaker",
                "branch": "master",
                "config": {
                }
            },
            "resource_type": "request"
        }'''

        when:
        TriggerResponse triggerResponse = client.triggerBuild("someToken", "some/build", new RepoRequest("master"))

        then:
        triggerResponse.remainingRequests == 10
    }

    def "Log"() {
        given:
        setPlainTextResponse '''ERROR: An error occured while trying to parse your .travis.yml file.

            Please make sure that the file is valid YAML.

            http://lint.travis-ci.org can check your .travis.yml.

            The error was "undefined method `merge' for false:FalseClass".'''

        when:
        Response response = client.log("someToken", 123)
        String logMylog = new String(((TypedByteArray) response.getBody()).getBytes());

        then:
        logMylog.contains "false:FalseClass"

    }

    def "getBuilds(accessToken, repoSlug, buildNumber"() {
        given:
        setResponse '''
            {
            "builds":[
                {
                "id":241991,
                "repository_id":2838,
                "commit_id":133851,
                "number":"31",
                "pull_request":false,
                "pull_request_title":null,
                "pull_request_number":null,
                "config":{
                    "install":"script/bootstrap",
                    "script":"script/build",
                    "deploy":{
                        "skip_cleanup":true,
                        "provider":"script",
                        "script":"script/deploy",
                        "true":{"tags":true}
                    },
                    ".result":"configured",
                    "global_env":[
                        "SOME_PARAM=some_value",
                        {"secure":"xyz"}
                    ],
                    "language":"ruby",
                    "os":"linux"
                },
                "state":"passed",
                "started_at":"2016-03-15T14:11:16Z",
                "finished_at":"2016-03-15T14:11:24Z",
                "duration":8,"job_ids":[241992]}],
                "commits":[
                    {
                        "id":1337,
                        "sha":"def",
                        "branch":"master","message":"Merge pull request #4 from my pull-request",
                        "committed_at":"2016-03-15T13:46:11Z",
                        "author_name":"Some User",
                        "author_email":"some.user@some.domain",
                        "committer_name":"Some User",
                        "committer_email":"some.user@some.domain",
                        "compare_url":"https://github.some.domain/some-org/some-repo/compare/abc...def",
                        "pull_request_number":null
                    }
                ]
            }'''

        when:
        Builds builds = client.builds("someToken", "some/build", 31)

        then:
        Build build = builds.builds.first()
        build.number == 31
        build.duration == 8
        build.finishedAt.getTime() == 1458051084000
    }

    def "extract config from getBuild(accessToken, repoSlug)"() {
        given:
        setResponse '''{
            "builds": [{
                "id": 134545105,
                "repository_id": 8906939,
                "commit_id": 38108155,
                "number": "3",
                "event_type": "push",
                "pull_request": false,
                "pull_request_title": null,
                "pull_request_number": null,
                "config": {
                    "language": "python",
                    "env": ["TARGET_ENV=test.environment"],
                    "script": "./travis.sh",
                    ".result": "configured",
                    "group": "stable",
                    "dist": "precise"
                },
                "state": "passed",
                "started_at": "2016-06-01T18:58:08Z",
                "finished_at": "2016-06-01T18:58:29Z",
                "duration": 21,
                "job_ids": [134545109]
            }],
            "commits": [{
                "id": 38108155,
                "sha": "f290f2af03826999c6004404378a5bc750e834b0",
                "branch": "master",
                "message": "Update README.md",
                "committed_at": "2016-06-01T18:57:48Z",
                "author_name": "Gard Rimestad",
                "author_email": "gardalize@gurters.com",
                "committer_name": "Gard Rimestad",
                "committer_email": "gardalize@gurters.com",
                "compare_url": "https://github.com/gardalize/travis-trigger-test/compare/bd005f51cb1e...f290f2af0382",
                "pull_request_number": null
            }]
            }'''

        when:
        Builds builds = client.builds("someToken", "some/build", 31)

        then:
        Build build = builds.builds.first()
        build.number == 3
        build.config.env.size() == 1
        build.config.env.first() == "TARGET_ENV=test.environment"
    }

    def "getBuilds(accessToken, repoSlug, buildNumber) with no build found"() {
        given:
        setResponse '''{"builds":[],"commits":[]}'''

        when:
        Builds builds = client.builds("someToken", "some/build", 31)

        then:
        builds.builds.size() == 0
    }

    def "commits, identify tag"() {
        given:
        setResponse '''{
            "builds": [{
                           "id": 281721,
                           "repository_id": 1993,
                           "commit_id": 156529,
                           "number": "39",
                           "pull_request": false,
                           "pull_request_title": null,
                           "pull_request_number": null,
                           "state": "passed",
                           "started_at": "2016-04-19T09:19:25Z",
                           "finished_at": "2016-04-19T09:23:08Z",
                           "duration": 223,
                           "job_ids": [281722]
                       }],
            "commits": [{
                            "id": 156529,
                            "sha": "1537853a99a3b90abe53d74ff7d908eec7641138",
                            "branch": "v1.17.4",
                            "message": "Some message",
                            "committed_at": "2016-04-12T15:46:54Z",
                            "author_name": "Some user",
                            "author_email": "some.user@domain.com",
                            "committer_name": "Some user",
                            "committer_email": "some.user@domain.com",
                            "compare_url": "https://github.domain/org/repo/compare/v1.17.4",
                            "pull_request_number": null
                        }]
        }'''

        when:
        Builds builds = client.builds("someToken", "org/repo", 39)

        then:
        builds.commits.first().isTag()

    }

    def "commits, dont mark regular branches as tags"() {
        given:
        setResponse '''
            {
                "builds": [{
                               "id": 273844,
                               "repository_id": 1993,
                               "commit_id": 152035,
                               "number": "38",
                               "pull_request": false,
                               "pull_request_title": null,
                               "pull_request_number": null,
                               "state": "passed",
                               "started_at": "2016-04-12T15:47:18Z",
                               "finished_at": "2016-04-12T15:49:57Z",
                               "duration": 159,
                               "job_ids": [273845]
                           }],
                "commits": [{
                            "id": 152035,
                            "sha": "153abe7853a99a3b3553d74ff7d908eec7641138",
                            "branch": "sch_master",
                            "message": "some commit",
                            "committed_at": "2016-04-12T15:46:54Z",
                            "author_name": "Some user",
                            "author_email": "some.user@domain.com",
                            "committer_name": "Some user",
                            "committer_email": "some.user@domain.com",
                                "compare_url": "https://github.domain/org/repo/compare/845cddf3543f...153abe7853a9",
                                "pull_request_number": null
                            }]
            }
        '''

        when:
        Builds builds = client.builds("someToken", "org/repo", 38)

        then:
        builds.commits.first().isTag() == false

    }

    private void setResponse(String body) {
        server.enqueue(
            new MockResponse()
                .setBody(body)
                .setHeader('Content-Type', 'application/json;charset=utf-8')
        )
        server.start()
        client = new TravisConfig().travisClient(server.getUrl('/').toString())
    }

    private void setPlainTextResponse(String body) {
        server.enqueue(
            new MockResponse()
                .setBody(body)
                .setHeader('Content-Type', 'text/plain;charset=utf-8')
        )
        server.start()
        client = new TravisConfig().travisClient(server.getUrl('/').toString())

    }
}
