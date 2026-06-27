package com.fantamomo.hc.stardancegraph.model

import io.ktor.http.*

sealed interface Project : Sendable {
    val id: Int
    val owner: User

    fun getScrapableInternal(): MutableSet<Scrapable> = mutableSetOf(Scrapable.Project(id), Scrapable.User(owner.name))

    data class FoundProject(
        override val id: Int,
        override val owner: User
    ) : Project {
        override fun getScrapable(): Set<Scrapable> = getScrapableInternal()
    }

    data class ScrapedProject(
        override val id: Int,
        override val owner: User,
        val title: String,
        val description: String?,
        val superstar: Boolean,
        val sourceUrl: Url?,
        val followerCount: Int,
        val devlogCount: Int,
        val hourCount: Int,
        val isHardware: Boolean,
        val attachedMission: String?,
        val missionShipped: Boolean?,
        val posts: List<Post>
    ) : Project {
        override fun getScrapable(): Set<Scrapable> {
            val result = mutableSetOf<Scrapable>()
            when (posts.size) {
                 0 -> {}
                 1 -> result.addAll(posts[0].getScrapable())
                else -> posts.flatMapTo(result) { it.getScrapable() }
            }
            if (followerCount > 0) result.add(Scrapable.ProjectFollowers(id, owner))
            return result
        }
    }
}