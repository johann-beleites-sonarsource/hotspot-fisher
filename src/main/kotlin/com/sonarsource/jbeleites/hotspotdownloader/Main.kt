package com.sonarsource.jbeleites.hotspotdownloader

import com.github.kittinunf.fuel.core.Parameters
import com.github.kittinunf.fuel.core.Request
import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.fuel.httpGet
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

private const val PAGE_SIZE = 500
val json = Json {
    isLenient = true
    ignoreUnknownKeys = true
}

fun main(vararg args: String) {
    val projectIdRegex = args[0].toRegex()
    gatherProjectIds(projectIdRegex).forEach { projectId ->
        getAllResolvePagination<HotspotResults> { page: Int -> hotspotRequest(projectId, page) }
            .flatMap { it.hotspots }
            .filter { it.message.contains("The content length limit of") || it.message.contains("Make sure not setting any maximum content length limit is safe here.") }
            //.filter { it.message.contains("accessing the Android external storage") }
            //.filter { it.key.equals("java:S5693") }
            .forEach { hotspot -> println("$projectId: ${hotspotWebApi(projectId, hotspot.key)}") }
    }
}

fun hotspotRequest(projectKey: String, page: Int) = "https://peach.sonarsource.com/api/hotspots/search".authGet(
    listOf(
        "projectKey" to projectKey,
        "ps" to 500,
        "p" to page
    )
)

fun projectsRequest(page: Int) = "https://peach.sonarsource.com/api/projects/search".authGet(
    listOf(
        "ps" to PAGE_SIZE,
        "p" to page
    )
)

fun hotspotWebApi(projectKey: String, issueKey: String) =
    "https://peach.sonarsource.com/security_hotspots?id=${projectKey}&hotspots=${issueKey}"

fun String.authGet(parameters: Parameters? = null) =
    httpGet(parameters).authentication().basic(username = System.getenv("PEACH_TOKEN"), password = "")

fun gatherProjectIds(regex: Regex) = gatherAllProjects().map { it.key }.filter { it.matches(regex) }

fun gatherAllProjects() =
    getAllResolvePagination<ProjectResults>(::projectsRequest)
        .flatMap { it.components }

inline fun <reified T : Paginated> getAllResolvePagination(crossinline urlGen: (Int) -> Request): Sequence<T> {
    val firstPage = sendGetRequest<T>(urlGen, 1)

    val lastPageIndex = firstPage.paging.let { (_, pageSize, total) ->
        (total / pageSize) + (if (total % pageSize > 0) 1 else 0)
    }


    return (sequenceOf(firstPage) + (2..lastPageIndex).asSequence().map { sendGetRequest(urlGen, it) })
}

inline fun <reified T> sendGetRequest(urlGen: (Int) -> Request, page: Int) =
    urlGen(page).response().let { (_, response, _) ->
        json.decodeFromString<T>(response.body().asString("application/json"))
    }

