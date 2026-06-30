package com.fantamomo.hc.stardancegraph.model

import kotlin.time.Instant

data class SuperStar(
    override val projectId: Int,
    override val author: User, // special case, superstar author is the user who starred, not the author of the project
    override val createdAt: Instant,
    val internalId: Int,
    val views: Int,
    val reposts: Int,
) : Post {
    override fun printable() = "Superstar"

    override fun getScrapable(result: MutableSet<Scrapable>) {
        result.addAll(getScrapableInternal())
    }

    override fun getScrapable() = getScrapableInternal()
}