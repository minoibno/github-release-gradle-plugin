package com.jlessing.gradle.plugins.github.release

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.internal.impldep.com.fasterxml.jackson.databind.ObjectMapper
import org.gradle.internal.impldep.com.fasterxml.jackson.databind.node.ObjectNode
import org.gradle.internal.impldep.org.eclipse.jgit.api.Git
import org.gradle.internal.impldep.org.eclipse.jgit.api.errors.GitAPIException
import org.slf4j.LoggerFactory
import org.springframework.boot.gradle.tasks.bundling.BootJar
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Path
import java.nio.file.Paths

fun httpRequest(url: String, method: String, body: ByteArray = ByteArray(0)): Pair<Int, String> {
    val con = (URL(url).openConnection() as HttpURLConnection).apply { this.doOutput = true }
    con.requestMethod = method
    con.outputStream.write(body)
    return Pair(con.responseCode, con.inputStream.bufferedReader().use { it.readText() })
}

fun main(args: Array<String>) {
    val git = Git.open(Paths.get("./").toFile())
    git.branchList().call().forEach { println(it.name) }
}

class GitHubReleaseException : GradleException {
    constructor() : super()
    constructor(message: String) : super(message)
    constructor(message: String, cause: Throwable?) : super(message, cause)
}

class GitHubReleasePlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.run {
            val preChecks = tasks.register("publishPreChecks").get().apply {
                doLast {
                    val git = Git.open(rootProject.projectDir)

                    if (githubRelease.onlyFromMaster && git.repository.branch != "master")
                        throw GitHubReleaseException("You can only create a Release from the master branch! This behaviour can be disabled in the configuration")

                    if (githubRelease.failOnUncommittedChanges) {
                        val status = git.status().call()
                        if (status.hasUncommittedChanges() || status.untracked.size > 0)
                            throw GitHubReleaseException("You can only create a Release if there aren't any uncommitted changes! This behaviour can be disabled in the configuration")
                    }

                }
            }

            val createReleaseBranch = tasks.register("createReleaseBranch").get().apply {
                dependsOn(preChecks)
                doLast {
                    val git = Git.open(rootProject.projectDir)
                    git.branchCreate().setName("/releases/${project.version}")
                }
            }

            val publish = tasks.register("publish").get().apply {
                val git = Git.open(rootProject.projectDir)
                dependsOn(preChecks)
                dependsOn(tasks.getByName("build"))
                doLast {
                    val logger = LoggerFactory.getLogger(this.javaClass)
                    val publishVersion = System.getProperty("publish.version", project.version as String)
                    val commit = Runtime.getRuntime().exec("git log -n 1 --oneline --no-abbrev-commit").inputStream.bufferedReader().use { it.readText() }.substringBefore(' ')
                    logger.info("Publishing a Release with Version<$publishVersion> on Commit<$commit>")

                    val releaseReq = httpRequest("http://api.github.com/repos/jlessing-git/mock-oauth2-server/releases", "POST", """
            {
                "tag_name":"v$publishVersion",
                "name":"v$publishVersion",
                "target_commitish":"$commit",
                "body":"Release v$publishVersion",
                "draft":false,
                "prerelease":false
            }
        """.trimIndent().toByteArray())
                    if (releaseReq.first != 201) throw GradleException("Unexpected Status Code while creating the GitHub Release: ${releaseReq.first}")
                    logger.info("GitHub Release published")
                    val releaseResp = ObjectMapper().readValue(releaseReq.second, ObjectNode::class.java)

                    //Upload_url aus antwort parsen und dann jar hochladen
                    val artifactReq = httpRequest(releaseResp.get("assets_url").asText(), "POST", (tasks.getByName("build") as BootJar).archiveFile.get().asFile.inputStream().use { it.readBytes() })

                    logger.info("Tagging the git commit")
                    if (Runtime.getRuntime().exec("git tag -a v$publishVersion -m \"Release v$publishVersion\"").waitFor() != 0) throw GradleException("Failed to tag the commit after GitHub Release was created")

                    try {
                        git.push().setPushTags().call()
                    } catch (e: GitAPIException) {
                        throw GitHubReleaseException("Failed to push tags", e)
                    }
                    logger.info("Git tag pushed")
                }
            }
        }
    }
}

data class GitHubReleaseConfiguration(
        /**
         * List of artifacts to include in the release.
         */
        var artifacts: MutableMap<String, Path> = mutableMapOf(),
        /**
         * Whether releases can only be created form the master branch.
         */
        var onlyFromMaster: Boolean = true,
        /**
         * Stops the release if there are uncommitted changes.
         */
        var failOnUncommittedChanges: Boolean = true
) {
    operator fun invoke(function: GitHubReleaseConfiguration.() -> Unit) = function(this)
}

val Project.githubRelease: GitHubReleaseConfiguration
    get() = GitHubReleaseConfiguration()