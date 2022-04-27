/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the 'License');
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.igor.build

import com.netflix.spinnaker.igor.IgorConfigurationProperties
import com.netflix.spinnaker.kork.jedis.EmbeddedRedis
import com.netflix.spinnaker.kork.jedis.JedisClientDelegate
import com.netflix.spinnaker.kork.jedis.RedisClientDelegate
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class BuildCacheSpec extends Specification {

    EmbeddedRedis embeddedRedis = EmbeddedRedis.embed()

    RedisClientDelegate redisClientDelegate = new JedisClientDelegate(embeddedRedis.pool as JedisPool)

    @Subject
    BuildCache cache = new BuildCache(redisClientDelegate, new IgorConfigurationProperties())

    def controller = 'controller'
    def test = 'test'
    def int TTL = 42

    void cleanup() {
        embeddedRedis.pool.resource.withCloseable { Jedis resource ->
            resource.flushDB()
        }
        embeddedRedis.destroy()
    }

    void 'new build numbers get overridden'() {
        when:
        cache.setLastBuild(controller, 'job1', 78, true, TTL)

        then:
        cache.getLastBuild(controller, 'job1', true) == 78

        when:
        cache.setLastBuild(controller, 'job1', 80, true, TTL)

        then:
        cache.getLastBuild(controller, 'job1', true) == 80
    }

    void 'running and completed builds are handled separately'() {
        when:
        cache.setLastBuild(controller, 'job1', 78, true, TTL)

        then:
        cache.getLastBuild(controller, 'job1', true) == 78

        when:
        cache.setLastBuild(controller, 'job1', 80, false, TTL)

        then:
        cache.getLastBuild(controller, 'job1', false) == 80
        cache.getLastBuild(controller, 'job1', true) == 78
    }

    void 'when value is not found, -1 is returned'() {
        expect:
        cache.getLastBuild('notthere', 'job1', true) == -1
    }

    void 'can set builds for multiple controllers'() {
        when:
        cache.setLastBuild(controller, 'job1', 78, true, TTL)
        cache.setLastBuild('example2', 'job1', 88, true, TTL)

        then:
        cache.getLastBuild(controller, 'job1', true) == 78
        cache.getLastBuild('example2', 'job1', true) == 88
    }

    void 'correctly retrieves all jobsNames for a controller'() {
        when:
        cache.setLastBuild(controller, 'job1', 78, true, TTL)
        cache.setLastBuild(controller, 'job2', 11, false, TTL)
        cache.setLastBuild(controller, 'blurb', 1, false, TTL)

        then:
        cache.getJobNames(controller) == ['blurb', 'job2']
    }

    @Unroll
    void 'retrieves all matching jobs for typeahead #query'() {
        when:
        cache.setLastBuild(controller, 'job1', 1, true, TTL)
        cache.setLastBuild(test, 'job1', 1, false, TTL)
        cache.setLastBuild(controller, 'job2', 1, false, TTL)
        cache.setLastBuild(test, 'job3', 1, false, TTL)

        then:
        cache.getTypeaheadResults(query) == expected

        where:
        query  || expected
        'job'  || ['controller:job1', 'controller:job2', 'test:job1', 'test:job3']
        'job1' || ['controller:job1', 'test:job1']
        'ob1'  || ['controller:job1', 'test:job1']
        'B2'   || ['controller:job2']
        '3'    || ['test:job3']
        'nope' || []
    }

    void 'a cache with another prefix does not pollute the current cache'() {
        given:
        def altCfg = new IgorConfigurationProperties()
        altCfg.spinnaker.jedis.prefix = 'newPrefix'
        BuildCache secondInstance = new BuildCache(redisClientDelegate, altCfg)

        when:
        secondInstance.setLastBuild(controller, 'job1', 1, false, TTL)

        then:
        secondInstance.getJobNames(controller) == ['job1']
        cache.getJobNames(controller) == []

        when:
        embeddedRedis.pool.resource.withCloseable {
            it.del(cache.makeKey(controller, 'job1'))
        }

        then:
        secondInstance.getJobNames(controller) == ['job1']
    }

    void 'should generate nice keys for completed jobs'() {
        when:
        String key = cache.makeKey("travis-ci", "myorg/myrepo", false)

        then:
        key == "igor:builds:completed:travis-ci:MYORG/MYREPO:myorg/myrepo"
    }

    void 'should generate nice keys for running jobs'() {
        when:
        String key = cache.makeKey("travis-ci", "myorg/myrepo", true)

        then:
        key == "igor:builds:running:travis-ci:MYORG/MYREPO:myorg/myrepo"
    }

    void 'completed and running jobs should live in separate key space'() {
        when:
        def controllerKey = 'travis-ci'
        def slug      = 'org/repo'

        then:
        cache.makeKey(controllerKey, slug, false) != cache.makeKey(controllerKey, slug, true)
    }
}
