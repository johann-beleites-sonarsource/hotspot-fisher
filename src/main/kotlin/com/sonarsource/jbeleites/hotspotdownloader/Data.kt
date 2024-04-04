package com.sonarsource.jbeleites.hotspotdownloader

import kotlinx.serialization.Serializable

interface Paginated {
    val paging: PagingInfo
}

open class Foo
data class Bar(val x: Int) : Foo()

@Serializable
data class ProjectResults(override val paging: PagingInfo, val components: List<Project>) : Paginated

@Serializable
data class PagingInfo(val pageIndex: Int, val pageSize: Int, val total: Int)

@Serializable
data class Project(
    val key: String,
    val name: String,
    val qualifier: String,
    val visibility: String,
    val revision: String? = null,
    val lastAnalysisDate: String? = null,
)

@Serializable
data class ReducedProject(
    val key: String,
    val name: String,
    val qualifier: String,
)

@Serializable
data class HotspotResults(override val paging: PagingInfo, val hotspots: List<Hotspot>) : Paginated

@Serializable
data class Hotspot(
    val key: String,
    val component: String,
    val project: String,
    val securityCategory: String,
    val vulnerabilityProbability: String,
    val status: String,
    val line: Int? = null,
    val message: String,
    val author: String,
    val creationDate: String,
    val updateDate: String,
)

data class DetailedHotspot(
    val hotspot: Hotspot,
    val showHotspotView: ShowHotspotView,
)

@Serializable
data class ShowHotspotView(val rule: Rule, val project: ReducedProject)

@Serializable
data class Rule(val key: String)
