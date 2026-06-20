package com.fantamomo.hc.stardancegraph.model

import kotlin.time.Instant

sealed interface Post : Sendable {
    val projectId: Int
    val author: User
    val createdAt: Instant

    fun getScrapableInternal(): MutableSet<Scrapable> = mutableSetOf(Scrapable.User(author.name), Scrapable.Project(projectId))
}