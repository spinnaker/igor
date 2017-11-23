package com.netflix.spinnaker.igor.gitlabci

import com.netflix.spinnaker.igor.build.BuildCache
import com.netflix.spinnaker.igor.gitlabci.client.model.Project
import com.netflix.spinnaker.igor.gitlabci.service.GitlabCiService
import com.netflix.spinnaker.igor.model.BuildServiceProvider
import com.netflix.spinnaker.igor.polling.CommonPollingMonitor
import com.netflix.spinnaker.igor.service.BuildMasters
import com.netflix.spinnaker.igor.travis.client.model.Repo
import com.netflix.spinnaker.igor.travis.client.model.v3.V3Build
import com.netflix.spinnaker.igor.travis.service.TravisResultConverter
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import rx.Observable

import static net.logstash.logback.argument.StructuredArguments.kv

@Service
@ConditionalOnProperty('gitlab-ci.enabled')
class GitlabCiBuildMonitor extends CommonPollingMonitor {
    @Autowired
    BuildCache buildCache

    @Autowired
    BuildMasters buildMasters

    @Override
    void initialize() {
    }

    @Override
    void poll() {
        buildMasters.filteredMap(BuildServiceProvider.GITLAB_CI).keySet().each { master ->
            changedBuilds(master)
        }
    }

    List<Map> changedBuilds(String master) {
        log.info('Checking for new builds for {}', kv("master", master))
        List<String> cachedRepoSlugs = buildCache.getJobNames(master)
        List<Map> results = []
        int updatedBuilds = 0

        GitlabCiService gitlabCiService = buildMasters.map[master] as GitlabCiService

        def startTime = System.currentTimeMillis()

        List<Project> projects = gitlabCiService.getProjects()
        for (Project p : projects) {
            log.info(p.name_with_namespace)
        }

//        List<Repo> repos = filterOutOldBuilds(gitlabCiService.getReposForAccounts())
//        log.info("Took ${System.currentTimeMillis() - startTime}ms to retrieve ${repos.size()} repositories (master: {})", kv("master", master))
//        Observable.from(repos).subscribe(
//            { Repo repo ->
//                List<V3Build> builds = gitlabCiService.getBuilds(repo, 5)
//                for (V3Build build : builds) {
//                    boolean addToCache = false
//                    def cachedBuild = null
//                    String branchedRepoSlug = build.branchedRepoSlug()
//                    if (cachedRepoSlugs.contains(branchedRepoSlug)) {
//                        cachedBuild = buildCache.getLastBuild(master, branchedRepoSlug, TravisResultConverter.running(build.state))
//                        if (build.number > cachedBuild) {
//                            addToCache = true
//                            log.info("New build: {}: ${branchedRepoSlug} : ${build.number}", kv("master", master))
//                        }
//                    } else {
//                        addToCache = !TravisResultConverter.running(build.state)
//                    }
//                    if (addToCache) {
//                        updatedBuilds += 1
//                        log.info("Build update [${branchedRepoSlug}:${build.number}] [status:${build.state}] [running:${TravisResultConverter.running(build.state)}]")
//                        buildCache.setLastBuild(master, branchedRepoSlug, build.number, TravisResultConverter.running(build.state), buildCacheJobTTLSeconds())
//                        buildCache.setLastBuild(master, build.repository.slug, build.number, TravisResultConverter.running(build.state), buildCacheJobTTLSeconds())
//                        if (!build.spinnakerTriggered()) {
//                            sendEventForBuild(build, branchedRepoSlug, master, gitlabCiService)
//                        }
//                        results << [slug: branchedRepoSlug, previous: cachedBuild, current: build.number]
//                    }
//                }
//            }, {
//            log.error("Error: ${it.message} (master: {})", kv("master", master))
//        }
//        )
//        if (updatedBuilds) {
//            log.info("Found {} new builds (master: {})", updatedBuilds, kv("master", master))
//        }
//        log.info("Last poll took ${System.currentTimeMillis() - startTime}ms (master: {})", kv("master", master))
//        if (travisProperties.repositorySyncEnabled) {
//            startTime = System.currentTimeMillis()
//            gitlabCiService.syncRepos()
//            log.info("repositorySync: Took ${System.currentTimeMillis() - startTime}ms to sync repositories for {}", kv("master", master))
//        }
//        results

    }

    @Override
    String getName() {
        return "gitlabCiBuildMonitor";
    }

    @Override
    Long getLastPoll() {
        return null;
    }
}
