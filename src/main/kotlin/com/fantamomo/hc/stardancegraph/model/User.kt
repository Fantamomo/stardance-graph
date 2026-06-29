package com.fantamomo.hc.stardancegraph.model

import io.ktor.http.*
import kotlinx.datetime.LocalDate

sealed interface User : Sendable {
    val name: String
    val avatarUrl: String

    override fun getScrapable(result: MutableSet<Scrapable>) {
        result.add(Scrapable.User(name))
    }

    override fun getScrapable(): Set<Scrapable> = setOf(Scrapable.User(name))

    data class UnverifiedUser(
        override val name: String,
        override val avatarUrl: String,
        val internalId: Int?
    ) : User {
        override fun printable() = "Unverified User"

        init {
            if (name.contains("@")) throw IllegalArgumentException("User name cannot contain an @")
        }
    }

    data class FoundUser(
        override val name: String,
        override val avatarUrl: String,
        // val streak: Int? // i am not completely sure if at every place a user is shown, the streak is also available, so just to be sure we only add it to the scraped user
    ) : User {
        override fun printable() = "Found User"

        init {
            if (name.contains("@")) throw IllegalArgumentException("User name cannot contain an @")
        }
    }

    data class PagedUser(
        val original: ScrapedUser,
        val page: Int,
        val posts: List<Post>,
        val hasMorePages: Boolean
    ) : User by original {
        override fun printable() = "Paged User"

        override fun getScrapable(result: MutableSet<Scrapable>) {
//            result.addAll(original.getScrapable())
            when (posts.size) {
                  0 -> {}
                  1 -> posts[0].getScrapable(result)
                else -> posts.forEach { it.getScrapable(result) }
            }
            if (hasMorePages) result.add(Scrapable.PagedUser(name, page + 1, original))
        }

        override fun getScrapable(): Set<Scrapable> {
            val result = mutableSetOf<Scrapable>()
            return result.also { getScrapable(it) }
        }
    }

    data class ScrapedUser(
        override val name: String,
        override val avatarUrl: String,
        val bannerUrl: Url?,
        val internalId: Int?,
        val joinedDate: LocalDate,
        val bio: String,
        val devlogCount: Int,
        val projectsCount: Int,
        val shipCount: Int,
        val votesCount: Int,
        val followerCount: Int,
        val followingCount: Int,
        val streak: Int?,
        val achievements: List<String>,
        val posts: List<Post>,
        val hasMorePages: Boolean,
    ) : User {
        override fun printable() = "Scraped User"

        init {
            if (name.contains("@")) throw IllegalArgumentException("User name cannot contain an @")
        }

        override fun getScrapable(result: MutableSet<Scrapable>) {
            when (posts.size) {
                 0 -> {}
                 1 -> posts[0].getScrapable(result)
                else -> posts.forEach { it.getScrapable(result) }
            }
            if (followerCount > 0) result.add(Scrapable.UserFollowers(name, this))
            if (followingCount > 0) result.add(Scrapable.UserFollowing(name, this))
            if (hasMorePages) result.add(Scrapable.PagedUser(name, 2, this))
        }

        override fun getScrapable(): Set<Scrapable> {
            val result = mutableSetOf<Scrapable>()
            return result.also { getScrapable(it) }
        }
    }
}