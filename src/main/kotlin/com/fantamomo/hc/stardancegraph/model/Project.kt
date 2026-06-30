package com.fantamomo.hc.stardancegraph.model

import io.ktor.http.*
import kotlin.time.Duration

sealed interface Project : Sendable {
    val id: Int
    val owner: User

    fun getScrapableInternal(): MutableSet<Scrapable> = mutableSetOf(Scrapable.Project(id), Scrapable.User(owner.name))

    data class FoundProject(
        override val id: Int,
        override val owner: User
    ) : Project {
        override fun printable() = "Found Project"

        override fun getScrapable(result: MutableSet<Scrapable>) {
            result.addAll(getScrapableInternal())
        }

        override fun getScrapable(): Set<Scrapable> = getScrapableInternal()
    }

    data class UserProjectPageProject(
        override val id: Int,
        override val owner: User,
        val title: String,
        val bannerUrl: Url,
        val description: String,
        val devlogCount: Int,
        val hoursCount: Int,
        val lastUpdated: Duration,
    ) : Project {
        override fun printable() = "User Project Page Project"

        override fun getScrapable(result: MutableSet<Scrapable>) {
            result.addAll(getScrapableInternal())
        }
    }

    data class ScrapedProject(
        override val id: Int,
        override val owner: User,
        val title: String,
        val description: String?,
        val bannerUrl: Url?,
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
        override fun printable() = "Scraped Project"

        override fun getScrapable(result: MutableSet<Scrapable>) {
            result.add(Scrapable.User(owner.name))
            when (posts.size) {
                0 -> {}
                1 -> posts[0].getScrapable(result)
                else -> posts.forEach { it.getScrapable(result) }
            }
            if (followerCount > 0) result.add(Scrapable.ProjectFollowers(id, owner))
        }
    }
}