package com.fantamomo.hc.stardancegraph.model

import kotlin.time.Instant

data class SuperStar(
    override val projectId: Int,
    override val author: User,
    override val createdAt: Instant,
    val internalId: Int,
    val views: Int,
    val reposts: Int,
) : Post {
    override fun getScrapable() = getScrapableInternal()
}