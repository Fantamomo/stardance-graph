package com.fantamomo.hc.stardancegraph.model

import io.ktor.http.*
import kotlin.time.Instant

data class ShipEvent(
    override val projectId: Int,
    override val author: User,
    override val createdAt: Instant,
    val internalId: Int,
    val shipNumber: Int?,
    val pending: Boolean,
    val returned: Boolean,
    val body: String,
    val demoUrl: Url,
    val repoUrl: Url,
    val devlogCount: Int,
    val hourCount: Int,
    val mission: String?,
) : Post {
    override fun getScrapable() = getScrapableInternal()
}