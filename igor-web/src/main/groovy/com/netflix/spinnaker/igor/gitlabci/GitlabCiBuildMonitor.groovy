package com.netflix.spinnaker.igor.gitlabci

import com.netflix.spinnaker.igor.build.BuildCache
import com.netflix.spinnaker.igor.build.model.GenericProject
import com.netflix.spinnaker.igor.config.GitlabCiProperties
import com.netflix.spinnaker.igor.gitlabci.client.model.Pipeline
import com.netflix.spinnaker.igor.gitlabci.client.model.Project
import com.netflix.spinnaker.igor.gitlabci.service.GitlabCiPipelineConverter
import com.netflix.spinnaker.igor.gitlabci.service.GitlabCiResultConverter
import com.netflix.spinnaker.igor.gitlabci.service.GitlabCiService
import com.netflix.spinnaker.igor.history.EchoService
import com.netflix.spinnaker.igor.history.model.GenericBuildContent
import com.netflix.spinnaker.igor.history.model.GenericBuildEvent
import com.netflix.spinnaker.igor.model.BuildServiceProvider
import com.netflix.spinnaker.igor.polling.CommonPollingMonitor
import com.netflix.spinnaker.igor.service.BuildMasters
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import rx.Observable

import java.util.concurrent.TimeUnit

import static net.logstash.logback.argument.StructuredArguments.kv

@Service
@ConditionalOnProperty('gitlab-ci.enabled')
class GitlabCiBuildMonitor extends CommonPollingMonitor {
    private static final int MAX_NUMBER_OF_PIPELINES = 5

    @Autowired(required = false)
    EchoService echoService

    @Autowired
    BuildCache buildCache

    @Autowired
    BuildMasters buildMasters

    @Autowired
    GitlabCiProperties gitlabCiProperties

    @Override
    void initialize() {
    }

    @Override
    void poll() {
        buildMasters.filteredMap(BuildServiceProvider.GITLAB_CI).keySet().each { master ->
            changedBuilds(master)
        }
    }

    void changedBuilds(String master) {
        log.info('Checking for new builds for {}', kv("master", master))
        List<String> cachedRepoSlugs = buildCache.getJobNames(master)
        int updatedBuilds = 0

        GitlabCiService gitlabCiService = buildMasters.map[master] as GitlabCiService

        def startTime = System.currentTimeMillis()

        try {
            List<Project> projects = gitlabCiService.getProjects()
            log.info("Took ${System.currentTimeMillis() - startTime}ms to retrieve ${projects.size()} repositories (master: {})", kv("master", master))
            Observable.from(projects).subscribe({ Project project ->
                List<Pipeline> pipelines = filterOldPipelines(gitlabCiService.getPipelines(project, MAX_NUMBER_OF_PIPELINES))
                for (Pipeline pipeline : pipelines) {
                    boolean addToCache = false
                    String branchedRepoSlug = gitlabCiService.getBranchedPipelineSlug(project, pipeline)

                    if (cachedRepoSlugs.contains(branchedRepoSlug)) {
                        def cachedBuildId = buildCache.getLastBuild(master, branchedRepoSlug, GitlabCiResultConverter.running(pipeline.status))
                        // In case of Gitlab CI the pipeline ids are increasing so we can use it for ordering
                        if (pipeline.id > cachedBuildId) {
                            addToCache = true
                            log.info("New build: {}: ${branchedRepoSlug} : ${pipeline.id}", kv("master", master))
                        }
                    } else {
                        addToCache = !GitlabCiResultConverter.running(pipeline.status)
                    }

                    if (addToCache) {
                        updatedBuilds += 1
                        log.info("Build update [${branchedRepoSlug}:${pipeline.id}] [status:${pipeline.status}] [running:${GitlabCiResultConverter.running(pipeline.status)}]")
                        buildCache.setLastBuild(master, branchedRepoSlug, pipeline.id, GitlabCiResultConverter.running(pipeline.status), buildCacheJobTTLSeconds())
                        buildCache.setLastBuild(master, pipeline.ref, pipeline.id, GitlabCiResultConverter.running(pipeline.status), buildCacheJobTTLSeconds())
                        sendEventForPipeline(project, pipeline, gitlabCiService.getAddress(), branchedRepoSlug, master)
                    }
                }
            }, {
                log.error("Error: ${it.message} (master: {})", kv("master", master))
            })
            if (updatedBuilds) {
                log.info("Found {} new builds (master: {})", updatedBuilds, kv("master", master))
            }
            log.info("Last poll took ${System.currentTimeMillis() - startTime}ms (master: {})", kv("master", master))
        } catch (Exception e) {
            log.error("Failed to obtain the list of projects", e)
        }
    }

    // TODO double check this method
    List<Pipeline> filterOldPipelines(List<Pipeline> pipelines) {
        Long threshold = new Date().getTime() - TimeUnit.DAYS.toMillis(gitlabCiProperties.cachedJobTTLDays)
        return pipelines.findAll({
            it.finished_at?.getTime() > threshold
        })
    }

    @Override
    String getName() {
        return "gitlabCiBuildMonitor"
    }

    private int buildCacheJobTTLSeconds() {
        return TimeUnit.DAYS.toSeconds(gitlabCiProperties.cachedJobTTLDays)
    }

    private void sendEventForPipeline(Project project, Pipeline pipeline, String address, String branchedSlug, String master) {
        if (echoService) {
            log.info("pushing event for {}:${pipeline.ref}:${pipeline.id}", kv("master", master))
            GenericProject genericProject = new GenericProject(pipeline.ref,
                GitlabCiPipelineConverter.genericBuild(pipeline, project.path_with_namespace, address))
            echoService.postEvent(
                new GenericBuildEvent(content: new GenericBuildContent(project: genericProject, master: master, type: 'gitlab-ci'))
            )
            log.info("pushing event for {}:${branchedSlug}:${pipeline.id}", kv("master", master))
            genericProject = new GenericProject(branchedSlug,
                GitlabCiPipelineConverter.genericBuild(pipeline, project.path_with_namespace, address))
            echoService.postEvent(
                new GenericBuildEvent(content: new GenericBuildContent(project: genericProject, master: master, type: 'gitlab-ci'))
            )
        }
    }
}
