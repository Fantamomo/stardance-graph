package com.fantamomo.hc.stardancegraph.model

import kotlin.time.Duration
import kotlin.time.Instant

data class Devlog(
    val id: Int,
    val internalId: Int,
    override val author: User,
    val body: String,
    override val projectId: Int,
    override val createdAt: Instant,
    val timeLogged: Duration,
    val attachments: List<String>,
    val commentsCount: Int,
    val repostsCount: Int,
    val likesCount: Int,
    val viewsCount: Int,
    // if comments are null, the devlog was not scraped directly but was found on another site (user or project feed)
    val comments: List<Comment>?,
) : Post {
    fun wasDirectlyScraped(): Boolean = comments != null

    override fun printable(): String = "Devlog"

    override fun getScrapable(result: MutableSet<Scrapable>) {
        val result = getScrapableInternal()
        if (comments == null && commentsCount > 0) result.add(Scrapable.Devlog(projectId, id))
        else comments?.forEach { result.add(Scrapable.User(it.author.name)) }
    }
}