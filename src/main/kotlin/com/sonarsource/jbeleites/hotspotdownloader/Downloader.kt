package com.sonarsource.jbeleites.hotspotdownloader

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.kittinunf.fuel.core.Parameters
import com.github.kittinunf.fuel.core.Request
import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.fuel.httpGet
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class Downloader : CliktCommand() {
    val projectKeyFilterRegex by option(
        "--project-filter", "-f",
        help = "A regex to filter the project keys from which to download hotspots by. Not setting this will not filter the projects."
    ).convert { it.toRegex() }

    val pageSize by option(
        "--page-size",
        help = "Page size to use for pagination (may affect performance)."
    ).convert {
        it.toInt()
    }.default(500)

    val projectKeys by option(
        "--project", "-p",
        help = "Specify the project keys to download hotspots from (will ignore project key regex option and not collect projects). You " +
            "can specify multiple projects for which to download hotspots by using this option multiple times."
    ).multiple()

    val baseUrl by option(
        "--base-url", "-b",
        help = "Base URL of SonarCloud or your SonarQube instance. Defaults to sonarcloud.io."
    ).default("https://sonarcloud.io")

    val organization by option(
        "--organization", "-o",
        help = "On SonarCloud you are required to provide the organization. You can use this parameter to do so."
    )

    val messageFilter by option(
        "--message-filter", "-m",
        help = "Regular expression to filter by the hotspot messages."
    ).convert { it.toRegex() }

    val requestTimeout by option(
        "--timeout",
        help = "Set the request timeout"
    ).convert { it.toInt() }

    val authToken = System.getenv("SONAR_TOKEN")

    val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
    }

    override fun run() {
        runBlocking { runMultithreaded() }
    }

    private suspend fun runMultithreaded() {
        coroutineScope {
            relevantProjectKeys().map { projectId ->
                launch {
                    getAllResolvePagination<HotspotResults> { page: Int -> hotspotRequest(projectId, page) }
                        .flatMap { it.hotspots }
                        .filter { messageFilter?.matches(it.message) ?: true }
                        //.filter { it.message.contains("The content length limit of") || it.message.contains("Make sure not setting any maximum content length limit is safe here.") }
                        //.filter { it.message.contains("accessing the Android external storage") }
                        //.filter { it.key.equals("java:S5693") }
                        .forEach { hotspot -> println("$projectId: ${hotspotWebUrl(projectId, hotspot.key)}") }
                }
            }.forEach { it.join() }
        }
    }

    private fun hotspotWebUrl(projectKey: String, issueKey: String) =
        "$baseUrl/security_hotspots?id=${projectKey}&hotspots=${issueKey}"

    private fun relevantProjectKeys() =
        projectKeys.asSequence().ifEmpty {
            gatherProjectIds()
        }

    private fun gatherProjectIds() = gatherAllProjects().map {
        it.key
    }.filter {
        projectKeyFilterRegex?.let { regex -> it.matches(regex) } ?: true
    }

    private fun gatherAllProjects() =
        runBlocking {
            getAllResolvePagination<ProjectResults>(::projectsRequest)
                .flatMap { it.components }
        }

    private suspend inline fun <reified T : Paginated> getAllResolvePagination(crossinline urlGen: (Int) -> Request): Sequence<T> {
        val firstPage = sendGetRequest<T>(urlGen, 1)

        val lastPageIndex = firstPage.paging.let { (_, pageSize, total) ->
            (total / pageSize) + (if (total % pageSize > 0) 1 else 0)
        }

        return coroutineScope {
            sequenceOf(firstPage) + (2..lastPageIndex).map<Int, Pair<Int, Deferred<T?>>> {
                //println("Sending request ${urlGen(it).request.url} ($it)")
                it to async {
                    try {
                        sendGetRequest(urlGen, it)
                    } catch (e: Exception) {
                        "Could not get data for ${urlGen(it)} due to a ${e::class.simpleName}: ${e.message}"
                        null
                    }
                }
            }.toList().mapNotNull { (i: Int, deferred: Deferred<T?>) ->
                runBlocking {
                    //println("done $i")
                    deferred.await()
                }
            }
        }
    }

    private inline fun <reified T> sendGetRequest(urlGen: (Int) -> Request, page: Int) =
        urlGen(page).response().let { (_, response, _) ->
            json.decodeFromString<T>(response.body().asString("application/json"))
        }

    private fun hotspotRequest(projectKey: String, page: Int) = "$baseUrl/api/hotspots/search".authGet(
        listOf(
            "projectKey" to projectKey,
            "ps" to pageSize,
            "p" to page
        ).let { params ->
            organization?.let { org ->
                params + ("organization" to org)
            } ?: params
        }
    )

    fun projectsRequest(page: Int) = "$baseUrl/api/projects/search".authGet(
        listOf(
            "ps" to pageSize,
            "p" to page
        ).let { params ->
            organization?.let { org ->
                params + ("organization" to org)
            } ?: params
        }
    )

    private fun String.authGet(parameters: Parameters? = null) =
        httpGet(parameters).let { req ->
            requestTimeout?.let { timeout ->
                req.timeout(timeout)
            } ?: req
        }.let { req ->
            if (authToken.isNotBlank()) {
                req.authentication().basic(username = authToken, password = "")
            } else {
                req
            }
        }
}
