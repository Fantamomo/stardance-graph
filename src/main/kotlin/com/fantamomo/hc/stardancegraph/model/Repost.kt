package com.fantamomo.hc.stardancegraph.model

import kotlin.time.Instant

data class Repost(
    override val projectId: Int,
    override val author: User, // special case, repost author is the user who reposted, not the original author
    override val createdAt: Instant,
    val devlogAuthor: User,
    val devlogId: Int,
    val message: String?,
) : Post {
    override fun getScrapable(): MutableSet<Scrapable> {
        val scrapables = getScrapableInternal()
        scrapables.add(Scrapable.Devlog(projectId, devlogId))
        scrapables.add(Scrapable.User(devlogAuthor.name))
        return scrapables
    }
}